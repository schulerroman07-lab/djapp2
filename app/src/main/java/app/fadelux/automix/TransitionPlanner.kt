package app.fadelux.automix

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Wählt für jedes Song-Paar Zeitpunkt und Art des Übergangs (Version 2, an echter Musik geprüft):
 *  - ZEITPUNKT: frühestens nach dem letzten Gesang von A ("Freigabe"), sonst so spät wie möglich und
 *    fertig vor dem hörbaren Ende. Nie mitten in einer Gesangsphrase. Damit liegen nie zwei
 *    Gesangsteile übereinander: A singt nach der Freigabe nicht mehr.
 *  - BEATMATCHING nur, wenn beide Beat-Raster zuverlässig sind (Vertrauen ≥ 0,6, festes Tempo), auch genau
 *    an der Mix-Stelle passen und das Tempo nah beieinander liegt – sonst ein kurzer, sauberer Fade ohne
 *    Tempo-Angleichung.
 *  - GENRE: Techno/House nutzen ihre langen Outros (langer Blend, Bass-Swap, Filter auf der Phrase);
 *    Pop/Schlager/Rock bekommen einfache, kurze Übergänge (kurzer Blend im Takt, Fade, Schnitt auf die 1)
 *    in den letzten Sekunden bzw. im Fade-out.
 *  - GESANG: nie zwei Gesänge gleichzeitig – A singt nach der Freigabe nicht mehr, und B setzt in einem
 *    gesangsfreien Teil ein (die Überblendung ist fertig, bevor B singt; sonst kürzer bzw. Schnitt).
 *  - KLANG: Effekte (Echo) nur, wenn sie nötig sind (elektronisch, Tempo zu weit auseinander) –
 *    nie nur der Abwechslung wegen.
 */
object TransitionPlanner {

    /** Typen, bei denen beide Songs gleichzeitig hörbar sind (Beats müssen passen). */
    val blendTypes = setOf(TransitionType.BASS_SWAP, TransitionType.FILTER, TransitionType.LOOP, TransitionType.LONG_BLEND, TransitionType.BLEND)

    /** Abstand nach dem letzten Gesang von A, bevor der Übergang beginnen darf (Sekunden). */
    const val VOCAL_MARGIN = 0.5

    /** Einblenden von B, wenn A schon zu Ende ist (Sekunden): kurz, damit zwischen den Songs kein Loch entsteht. */
    const val QUICK_IN = 0.4

    /** Elektronische Musik mit langen Outros (Blends über Phrasen)? */
    fun electronic(s: TrackStructure): Boolean = when (s.family) {
        GenreFamily.HOUSE, GenreFamily.TECHNO -> true
        null, GenreFamily.OTHER -> s.beatReliable && s.confidence >= 0.8f &&
            s.bars.count { it.vocal } < s.barCount * 0.2 && s.endBar - s.outroStartBar >= 16
        else -> false
    }

    /** Ersatz, falls ein Typ in den Einstellungen abgeschaltet ist. */
    private fun allowedOr(t: TransitionType, enabled: Set<TransitionType>): TransitionType {
        val chain = when (t) {
            TransitionType.LONG_BLEND -> listOf(t, TransitionType.BASS_SWAP, TransitionType.BLEND, TransitionType.FILTER, TransitionType.FADE, TransitionType.CUT)
            TransitionType.BASS_SWAP -> listOf(t, TransitionType.BLEND, TransitionType.FILTER, TransitionType.FADE, TransitionType.CUT)
            TransitionType.FILTER -> listOf(t, TransitionType.BASS_SWAP, TransitionType.BLEND, TransitionType.FADE, TransitionType.CUT)
            TransitionType.BLEND -> listOf(t, TransitionType.BASS_SWAP, TransitionType.FADE, TransitionType.CUT)
            TransitionType.ECHO_OUT -> listOf(t, TransitionType.FADE, TransitionType.CUT)
            TransitionType.CUT -> listOf(t, TransitionType.FADE)
            else -> listOf(t, TransitionType.FADE, TransitionType.CUT)
        }
        return chain.firstOrNull { it in enabled } ?: TransitionType.FADE
    }

    /**
     * @param aPos aktuelle Position in A (Sekunden)
     * @param aTempo aktuelles Tempo-Verhältnis von A
     * @param recent zuletzt benutzte Typen (neuester zuletzt)
     * @param now true = möglichst bald (vom DJ gewünscht) statt am Ende
     * @param forced vom Benutzer gewählter Typ (oder null)
     * @param quick true = "Weiter"/"Zurück": kurzer, sauberer Übergang ab dem nächsten Takt
     * @param atBar fester Einsatz-Takt in A (Früh-Übergang), sonst null
     */
    fun plan(
        a: TrackStructure,
        aPos: Double,
        aTempo: Float,
        b: TrackStructure,
        settings: PlannerSettings,
        recent: List<TransitionType> = emptyList(),
        now: Boolean = false,
        forced: TransitionType? = null,
        quick: Boolean = false,
        atBar: Int? = null,
    ): TransitionPlan {
        if (quick) return planQuick(a, aPos, aTempo, b, settings, recent)
        val p = planFull(a, aPos, aTempo, b, settings, recent, now, forced, atBar, allowBeat = true)
        // Örtliches Beat-Vertrauen: Beide Raster müssen genau dort passen, wo gemischt wird (A ab dem Einsatz,
        // B ab seinem Startpunkt). Sonst ohne Beatmatching – kurzer, sauberer Fade (wie bei unsicherem Raster).
        if (p.matchTempo && p.type != TransitionType.CUT && p.lengthSec <= 0) {
            val lenA = p.lengthBars * a.barSec
            val lenB = p.lengthBars * b.barSec
            if (!a.gridSteady(p.mixSec, p.mixSec + lenA) || !b.gridSteady(p.bStartSec, p.bStartSec + lenB))
                return planFull(a, aPos, aTempo, b, settings, recent, now, forced, atBar, allowBeat = false)
        }
        return p
    }

    private fun planFull(
        a: TrackStructure, aPos: Double, aTempo: Float, b: TrackStructure, settings: PlannerSettings,
        recent: List<TransitionType>, now: Boolean, forced: TransitionType?, atBar: Int?, allowBeat: Boolean,
    ): TransitionPlan {
        val why = mutableListOf<String>()

        // --- 1) Tempo und Beat-Raster ---
        val aBpm = a.bpm * aTempo
        val ratio = TrackAnalysis.tempoRatio(aBpm, b.bpm)
        val maxStretch = when (settings.style) {
            MixStyle.CALM -> 0.04f
            MixStyle.BALANCED -> 0.06f
            MixStyle.CLUB -> 0.08f
        }
        val tempoClose = ratio != null && abs(ratio - 1f) <= maxStretch
        val gridsOk = a.beatReliable && b.beatReliable
        val beatOk = tempoClose && gridsOk && allowBeat
        why += when {
            ratio == null -> "Tempo nicht vergleichbar (${fmt(aBpm)} → ${fmt(b.bpm)} BPM)"
            !tempoClose -> "Tempo ${fmt(aBpm)} → ${fmt(b.bpm)} BPM zu weit (${pct(ratio - 1f)})"
            !gridsOk -> "Beat-Raster unsicher (Vertrauen ${conf(a)} / ${conf(b)}) → ohne Beatmatching"
            !allowBeat -> "Beat-Raster an der Mix-Stelle nicht fest (Timing verschiebt sich) → ohne Beatmatching"
            else -> "Tempo ${fmt(aBpm)} → ${fmt(b.bpm)} BPM: angleichen ${pct(ratio - 1f)} (Vertrauen ${conf(a)} / ${conf(b)})"
        }
        val keyFit = Keys.compatibility(a.musicalKey.takeIf { it >= 0 }, b.musicalKey.takeIf { it >= 0 })
        if (a.musicalKey >= 0 && b.musicalKey >= 0) why += "Tonart ${Keys.label(a.musicalKey)} → ${Keys.label(b.musicalKey)}" +
            (if (keyFit >= 0.85f) " passt" else if (keyFit < 0.3f) " reibt" else "")

        // --- 2) Freigabe: frühestens nach dem letzten Gesang von A; hörbares Ende ---
        val barSec = a.barSec / aTempo                       // A-Takt in Echtzeit
        val aEnd = max(a.soundEnd, aPos + 2.0)
        val lead = aPos + max(2 * a.barSec, 4.0)             // Vorbereitungszeit
        val lastVocal = a.lastVocalEnd
        val release = max(lead, if (lastVocal > 0) lastVocal + VOCAL_MARGIN else 0.0)
        why += if (lastVocal > 0) "letzter Gesang bis ${mmss(lastVocal)}" else "kein Gesang in A"
        val elec = electronic(a) && electronic(b)
        val releaseBar = a.nextBoundary(release, 1)
        val endBar = min(a.endBar, a.barAt(aEnd + 0.01))

        // --- 3) B: Einstieg ---
        fun bStartFor(len: Int): Double {
            val introPhrase = (b.introEndBar / 8) * 8
            return b.barTime(max(0, introPhrase - len)).coerceAtLeast(0.0)
        }
        val bFirst = if (b.beatReliable) b.barTime(0).coerceAtLeast(0.0) else 0.0

        fun fadePlan(start: Double, lengthSec: Double, note: String): TransitionPlan {
            val t = allowedOr(TransitionType.FADE, settings.enabled)
            why += note
            return TransitionPlan(
                type = t, mixBar = a.barAt(start), mixSec = start, bStartSec = b.soundStart,
                lengthBars = max(1, kotlin.math.ceil(lengthSec / max(0.5, b.barSec)).toInt()),
                tempoRatio = 1f, matchTempo = false, reason = why.joinToString(" · "),
                lengthSec = if (t == TransitionType.FADE) lengthSec else -1.0,
            )
        }

        fun beatPlan(type: TransitionType, bar: Int, len: Int, bStart: Double, note: String): TransitionPlan {
            val t = allowedOr(type, settings.enabled)
            if (t == TransitionType.FADE) return fadePlan(a.barTime(bar), (len * barSec).coerceIn(1.5, 6.0), "$note · ${t.name} statt ${type.name} (Einstellung)")
            why += note
            return TransitionPlan(
                type = t, mixBar = bar, mixSec = a.barTime(bar), bStartSec = if (t == TransitionType.CUT) bFirst else bStart,
                lengthBars = if (t == TransitionType.CUT) 1 else len, tempoRatio = ratio!!, matchTempo = true,
                reason = why.joinToString(" · "),
            )
        }
        // --- 5) Harte Regel: nie zwei Gesänge gleichzeitig ---
        // A singt nach der Freigabe nicht mehr (oben). Weil die Erkennung am Song-Ende nie ganz sicher ist, muss
        // zusätzlich B in einem gesangsfreien Teil einsetzen: Die Überblendung ist fertig (A ganz weg), bevor B
        // zu singen beginnt. Sonst wird sie gekürzt (gleiches Ende, späterer Einsatz) – notfalls bis zum Schnitt.
        fun guard(p: TransitionPlan): TransitionPlan {
            if (p.type == TransitionType.CUT) return p                    // A stoppt beim Einsatz: keine Überlappung
            fun room(bS: Double) = b.nextVocalStart(bS)?.let { it - VOCAL_MARGIN - bS } ?: Double.MAX_VALUE
            val lenB = if (p.lengthSec > 0) p.lengthSec else p.lengthBars * b.barSec
            if (room(p.bStartSec) >= lenB) return p
            val note = "B singt ab ${mmss(b.nextVocalStart(p.bStartSec) ?: 0.0)}"
            if (p.matchTempo && p.lengthSec <= 0 && p.type != TransitionType.ECHO_OUT && p.type != TransitionType.REVERB_WASH) {
                val keepB = abs(p.bStartSec - bFirst) < 1e-3
                var l = p.lengthBars
                while (l > 2) {
                    l /= 2
                    val bS = if (keepB) p.bStartSec else bStartFor(l)
                    if (room(bS) >= l * b.barSec) {
                        val bar = p.mixBar + (p.lengthBars - l)
                        val t = if (l < 8 && p.type == TransitionType.LONG_BLEND) allowedOr(TransitionType.BLEND, settings.enabled) else p.type
                        return p.copy(type = t, mixBar = bar, mixSec = a.barTime(bar), lengthBars = l, bStartSec = bS,
                            reason = "${p.reason} · $note → nur $l Takte (Takt $bar)")
                    }
                }
                val cutBar = max(releaseBar, endBar - 1)
                if (TransitionType.CUT in settings.enabled && a.barTime(cutBar) < aEnd + 1.0) {
                    return TransitionPlan(TransitionType.CUT, cutBar, a.barTime(cutBar), bFirst, 1, p.tempoRatio, true,
                        "${p.reason} · $note → Schnitt auf die 1 (Takt $cutBar)")
                }
            }
            // ohne Beatmatching: kurzer Fade ganz am Ende, fertig bevor B singt
            val l = room(b.soundStart).coerceIn(0.3, 4.0)
            val start = max(p.mixSec, aEnd - l)
            val t = allowedOr(TransitionType.FADE, settings.enabled)
            return TransitionPlan(
                type = t, mixBar = a.barAt(start), mixSec = start, bStartSec = b.soundStart,
                lengthBars = max(1, kotlin.math.ceil(l / max(0.5, b.barSec)).toInt()), tempoRatio = 1f, matchTempo = false,
                reason = "${p.reason} · $note → kurzer Fade ${String.format(java.util.Locale.ROOT, "%.1f", l)} s am Ende",
                lengthSec = if (t == TransitionType.FADE) l else -1.0,
            )
        }

        // --- 4a) Vom DJ: "jetzt mischen" / Früh-Übergang ---
        if (now) {
            val bar = a.nextBoundary(lead, 4)
            return if (beatOk) beatPlan(TransitionType.BLEND, bar, 4, bFirst, "jetzt mixen (Takt $bar)")
            else fadePlan(lead, 3.0, "jetzt mixen")
        }
        if (atBar != null && beatOk) {
            val len = 16
            val type = if (keyFit >= 0.85f && abs(ratio!! - 1f) <= 0.03f) TransitionType.LONG_BLEND else TransitionType.BASS_SWAP
            return guard(beatPlan(type, atBar, len, bStartFor(len), "Früh-Übergang: passt perfekt, A ohne Gesang ab hier (Takt $atBar von ${a.endBar})"))
        }
        // vom DJ gewählter Typ (falls möglich)
        if (forced != null && !(forced in blendTypes && !beatOk)) {
            val len = when (forced) { TransitionType.CUT -> 1; TransitionType.ECHO_OUT, TransitionType.REVERB_WASH -> 2; TransitionType.LONG_BLEND -> 16; TransitionType.FADE, TransitionType.BLEND -> 4; else -> 8 }
            val bar = max(releaseBar, endBar - len).let { if (beatOk) it - it % 4 else it }.coerceAtLeast(releaseBar)
            if (forced == TransitionType.FADE || !beatOk) return fadePlan(max(release, aEnd - 3.0), min(3.0, max(1.0, aEnd - release)), "vom DJ gewählt")
            return beatPlan(forced, bar, len, if (forced in blendTypes) bStartFor(len) else bFirst, "vom DJ gewählt")
        }

        // --- 4b) Elektronisch: langes Outro für einen Blend auf der Phrase nutzen ---
        if (elec && beatOk) {
            val wantLong = settings.lengthBars.takeIf { it > 0 } ?: when (settings.style) {
                MixStyle.CALM -> 32
                MixStyle.BALANCED -> 32
                MixStyle.CLUB -> 16
            }
            var len = wantLong
            while (len >= 8) {
                // späteste Phrasengrenze (8 Takte), die nach der Freigabe liegt und vor dem Ende fertig ist
                val latest = endBar - len
                val bar = (latest downTo releaseBar).firstOrNull { it % 8 == 0 } ?: (latest downTo releaseBar).firstOrNull { it % 4 == 0 }
                if (bar != null) {
                    val longOk = len >= 16 && keyFit >= 0.85f && abs(ratio!! - 1f) <= 0.03f
                    var type = when {
                        longOk && len >= 16 -> TransitionType.LONG_BLEND
                        keyFit < 0.3f -> TransitionType.FILTER          // Tonarten reiben: Filter überdeckt das
                        else -> TransitionType.BASS_SWAP
                    }
                    // leichte Abwechslung zwischen gleich guten Blends (nie ein Effekt nur deswegen)
                    if (type == recent.lastOrNull() && type == TransitionType.BASS_SWAP && len >= 16 && keyFit >= 0.5f) type = TransitionType.LONG_BLEND
                    else if (type == recent.lastOrNull() && type == TransitionType.LONG_BLEND) type = TransitionType.BASS_SWAP
                    val l = if (type == TransitionType.BASS_SWAP) min(len, 16) else len
                    val b2 = if (l < len) (endBar - l).let { e -> (e downTo bar).firstOrNull { it % 8 == 0 } ?: bar } else bar
                    return guard(beatPlan(type, b2, l, bStartFor(l), "elektronisch: Blend im Outro, Phrase (Takt $b2 von ${a.endBar}, $l Takte)"))
                }
                len /= 2
            }
        }
        if (elec && !beatOk && ratio != null && a.beatReliable && !tempoClose && aEnd - release >= 2 * barSec) {
            // Tempo zu weit auseinander: A klingt im eigenen Takt mit (dezentem) Echo aus, B setzt danach ein
            val bar = (endBar - 2 downTo releaseBar).firstOrNull { it % 4 == 0 } ?: max(releaseBar, endBar - 2)
            val t = allowedOr(TransitionType.ECHO_OUT, settings.enabled)
            if (t == TransitionType.ECHO_OUT) {
                why += "elektronisch, Tempo zu weit → Echo-Ausklang im Takt von A (Takt $bar)"
                return guard(TransitionPlan(t, bar, a.barTime(bar), bFirst, 2, 1f, false, why.joinToString(" · ")))
            }
        }

        // --- 4c) Songs (Pop/Schlager/Rock …): einfach und kurz, in den letzten Sekunden ---
        val tail = aEnd - release
        val fadeOut = a.fadeOutStart.takeIf { it > 0 && it >= release - 4.0 }
        if (beatOk) {
            val tailBars = endBar - releaseBar
            val wantLen = if (settings.lengthBars in 1..8) settings.lengthBars else if (settings.style == MixStyle.CALM) 8 else 4
            var len = wantLen
            while (len > 1 && len > tailBars) len /= 2
            if (tailBars >= 2 && len >= 2) {
                // im Fade-out: B kommt, wenn A leiser wird; sonst so spät wie möglich (fertig am Ende)
                val target = if (fadeOut != null) a.nextBoundary(fadeOut + (aEnd - fadeOut) * 0.25, 1) else endBar - len
                val bar = target.coerceIn(releaseBar, endBar - len).let { b0 -> (b0 downTo releaseBar).firstOrNull { it % 2 == 0 } ?: b0 }
                return guard(beatPlan(TransitionType.BLEND, bar, min(len, endBar - bar).coerceAtLeast(2), bFirst,
                    (if (fadeOut != null) "im Fade-out" else "kurzer Blend am Ende") + " (Takt $bar von ${a.endBar}, ${min(len, endBar - bar)} Takte)"))
            }
            // kaum Platz nach dem letzten Gesang: Schnitt auf die "1" nach dem Gesang – nur solange A noch klingt
            // (ist A schon zu Ende, wäre das Warten auf den nächsten Takt nur ein Loch)
            if (releaseBar < a.barCount && a.barTime(releaseBar) < aEnd - 0.05) {
                return beatPlan(TransitionType.CUT, releaseBar, 1, bFirst, "Gesang bis kurz vor Schluss → Schnitt auf die 1 (Takt $releaseBar)")
            }
        }
        // ohne Beatmatching: kurzer Fade, nach dem Gesang, so spät wie möglich
        val fadeLen = when {
            fadeOut != null -> (aEnd - max(release, fadeOut + (aEnd - fadeOut) * 0.3)).coerceIn(1.5, 8.0)
            settings.style == MixStyle.CALM -> min(6.0, max(1.5, tail))
            else -> min(4.0, max(1.5, tail))
        }
        val start = max(release, aEnd - fadeLen)
        // A ist schon zu Ende (Gesang bis zum Schluss): B kommt sofort, nur ganz kurz eingeblendet – kein Loch
        val len = if (start >= aEnd - 0.05) QUICK_IN else max(1.0, min(fadeLen, aEnd - start + 0.5))
        return guard(fadePlan(start, len,
            (if (fadeOut != null) "Fade im Fade-out" else "kurzer Fade am Ende") + " ab ${mmss(start)}"))
    }

    private fun conf(s: TrackStructure) = if (s.confidence < 0) "?" else String.format(java.util.Locale.ROOT, "%.2f", s.confidence)
    private fun mmss(sec: Double) = String.format(java.util.Locale.ROOT, "%d:%04.1f", (sec / 60).toInt(), sec % 60)

    /**
     * Kurzer, sauberer Übergang für "Weiter"/"Zurück": Einsatz auf dem nächsten Takt (mind. 1 Takt
     * Vorlauf), 4 Takte Filter/Bass-Swap bei passendem Tempo, sonst Echo-Out oder Schnitt.
     * Nie ein harter Schnitt mitten im Takt.
     */
    private fun planQuick(
        a: TrackStructure, aPos: Double, aTempo: Float, b: TrackStructure,
        settings: PlannerSettings, recent: List<TransitionType>,
    ): TransitionPlan {
        val aBpm = a.bpm * aTempo
        val ratio = TrackAnalysis.tempoRatio(aBpm, b.bpm)
        val matchTempo = ratio != null && abs(ratio - 1f) <= 0.08f
        val mixBar = a.barAt(aPos) + 2           // nächster ganzer Takt mit etwas Vorlauf
        val prefs = (if (matchTempo) listOf(TransitionType.FILTER, TransitionType.BASS_SWAP) else emptyList()) +
            listOf(TransitionType.ECHO_OUT, TransitionType.CUT)
        val usable = prefs.filter { it in settings.enabled }.ifEmpty { listOf(TransitionType.CUT) }
        val type = usable.firstOrNull { it != recent.lastOrNull() } ?: usable.first()
        val length = when (type) {
            TransitionType.CUT -> 1
            TransitionType.ECHO_OUT -> 2
            else -> 4
        }
        val introPhrase = (b.introEndBar / 8) * 8
        val bStartBar = if (type in blendTypes) max(0, introPhrase - length) else 0
        return TransitionPlan(
            type = type, mixBar = mixBar, mixSec = a.barTime(mixBar),
            bStartSec = b.barTime(bStartBar).coerceAtLeast(0.0), lengthBars = length,
            tempoRatio = if (matchTempo) ratio!! else 1f, matchTempo = matchTempo,
            reason = "Weiter/Zurück: kurzer Übergang ${type.name} ab Takt $mixBar",
        )
    }

    /**
     * Früh-Übergang: Passen zwei Songs fast perfekt (Beat-Raster sehr sicher, Tempo ±2 %, Tonart
     * harmonisch) UND singt A ab dort nicht mehr (langes Instrumental-Outro, typisch Techno/House),
     * darf früher gemischt werden – auf einer 32er- (sonst 16er-)Phrase. Gibt den Takt zurück oder null.
     * @param normalBar Einsatz-Takt des normalen Plans (Früh-Übergang muss deutlich davor liegen)
     */
    fun earlyBar(a: TrackStructure, aPos: Double, aTempo: Float, b: TrackStructure, normalBar: Int): Int? {
        if (a.confidence < EARLY_CONF || b.confidence < EARLY_CONF || !a.steady || !b.steady) return null
        val ratio = TrackAnalysis.tempoRatio(a.bpm * aTempo, b.bpm) ?: return null
        if (abs(ratio - 1f) > 0.02f) return null
        if (a.musicalKey < 0 || b.musicalKey < 0) return null
        if (Keys.compatibility(a.musicalKey, b.musicalKey) < 0.85f) return null
        val len = 16
        val bStart = max(0, (b.introEndBar / 8) * 8 - len)
        if (b.vocalIn(bStart, bStart + len / 2)) return null              // B nicht gleich mit Gesang
        val release = if (a.lastVocalEnd > 0) a.lastVocalEnd + VOCAL_MARGIN else 0.0
        val first = maxOf(a.barAt(aPos) + 12, a.nextBoundary(release, 1), (a.endBar * 0.5f).toInt())   // genug Zeit für die Frage
        val last = normalBar - 24                                          // lohnt sich nur deutlich vor dem Ende
        if (first > last) return null
        for (step in listOf(32, 16)) {
            for (bar in first..last) if (bar % step == 0 &&
                a.gridSteady(a.barTime(bar), a.barTime(bar + len)) && b.gridSteady(b.barTime(bStart), b.barTime(bStart + len))) return bar
        }
        return null
    }

    /** Mindest-Vertrauen beider Beat-Raster für einen Früh-Übergang. */
    const val EARLY_CONF = 0.8f

    private fun fmt(v: Float) = String.format(java.util.Locale.ROOT, "%.1f", v)
    private fun pct(v: Float) = String.format(java.util.Locale.ROOT, "%+.1f %%", v * 100)

}
