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

    /** Abstand zum Gesang von B (Sekunden): so viel Luft, bevor B zu singen beginnt. */
    const val VOCAL_MARGIN = 0.5

    /** Abstand nach dem letzten erkannten Gesang von A, bevor der Übergang beginnt (Sekunden). An den Übungs-Songs
     *  bestimmt (zusammen mit Schwelle und Ende-Regel der Gesangserkennung): fängt die meisten Fälle ab, in denen die
     *  Erkennung das Ende der letzten Phrase etwas zu früh sieht, und lässt den instrumentalen Schluss nutzbar. */
    const val RELEASE_MARGIN = 3.0

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
        // (Gilt nur, wenn beide Songs gemeinsam im Takt laufen – bei Schnitt, Echo, Hall und Filter-Sweep endet A beim Einsatz.)
        val aStops = p.type == TransitionType.CUT || p.type == TransitionType.ECHO_OUT || p.type == TransitionType.REVERB_WASH ||
            (p.type == TransitionType.FILTER && p.lengthBars <= 2)
        if (p.matchTempo && !aStops && p.lengthSec <= 0) {
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
        val release = max(lead, if (lastVocal > 0) lastVocal + RELEASE_MARGIN else 0.0)
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
        // --- 5) Harte Regel: nie zwei Gesänge voll übereinander ---
        // A singt nach der Freigabe nicht mehr (oben). Bei Übergängen, in denen A weiterspielt (Blend, Loop, Filter
        // im Takt), darf B erst in der zweiten Hälfte zu singen beginnen – dann ist A schon deutlich leiser und spielt
        // nur noch instrumental (erlaubt: neuer Gesang über dem instrumentalen Teil des alten Songs). Hört A beim
        // Einsatz auf (Schnitt, Echo, Hall, Filter-Sweep), darf B sofort singen: Vom alten Song klingt höchstens das
        // letzte Wort leise im Echo aus. B setzt nie mitten in einer Gesangsphrase ein (siehe Einstieg).
        fun room(bS: Double) = b.nextVocalStart(bS)?.let { it - VOCAL_MARGIN - bS } ?: Double.MAX_VALUE
        fun vocalOk(p: TransitionPlan): Boolean {
            if (p.type == TransitionType.CUT || p.type == TransitionType.ECHO_OUT || p.type == TransitionType.REVERB_WASH) return true
            if (p.type == TransitionType.FILTER && p.lengthBars <= 2) return true      // Filter-Sweep: A ist nach 1 Takt weg
            val lenB = if (p.lengthSec > 0) p.lengthSec else p.lengthBars * b.barSec
            return room(p.bStartSec) >= lenB * 0.5
        }
        fun guard(p: TransitionPlan): TransitionPlan {
            if (vocalOk(p)) return p
            val note = "B singt ab ${mmss(b.nextVocalStart(p.bStartSec) ?: 0.0)}"
            if (p.matchTempo && p.lengthSec <= 0) {
                val keepB = abs(p.bStartSec - bFirst) < 1e-3
                var l = p.lengthBars
                while (l > 2) {
                    l /= 2
                    val bS = if (keepB) p.bStartSec else bStartFor(l)
                    if (room(bS) >= l * b.barSec * 0.5) {
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
        /** Blend-Plan nur übernehmen, wenn er die Gesangsregel erfüllt (sonst nächste Möglichkeit prüfen). */
        fun ifVocalOk(p: TransitionPlan): TransitionPlan? = p.takeIf { vocalOk(it) && it.type != TransitionType.FADE }

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

        // ===== Planer v3: richtige Übergänge, Fade nur als letzter Ausweg =====
        // Prüfreihenfolge: (a) Blend/Bass-Swap im Takt → (d) Loop als Brücke → (b) Echo-Out/Filter-Sweep →
        // (c) Schnitt auf die 1 → (e) Fade. Gleich gute Arten wechseln sich ab.
        val fadeOut = a.fadeOutStart.takeIf { it > 0 && it >= release - 4.0 }
        val bodyEnd = min(aEnd, fadeOut ?: aEnd)                 // bis hier spielt A "voll" (vor Fade-out / Schluss)

        // (a) Elektronisch: langer Blend auf der Phrase im Outro (16–32 Takte)
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
                    val p = guard(beatPlan(type, b2, l, bEntryBlend(b, l), "elektronisch: Blend im Outro, Phrase (Takt $b2 von ${a.endBar}, $l Takte)"))
                    if (p.matchTempo && p.type != TransitionType.CUT) return p
                    why.removeAt(why.lastIndex)
                    break
                }
                len /= 2
            }
        }

        // (a) Songs: kurzer Blend/Bass-Swap im Takt (4–8 Takte) im instrumentalen Schluss bzw. im Fade-out von A
        val tailBars = endBar - releaseBar
        if (beatOk && !elec) {
            val want = if (settings.lengthBars in 1..8) settings.lengthBars else if (settings.style == MixStyle.CLUB) 4 else 8
            for (len in listOf(want, 4).distinct().filter { it in 4..tailBars }) {
                // im Fade-out: B kommt, wenn A leiser wird; sonst fertig am Ende von A
                val target = if (fadeOut != null) a.nextBoundary(fadeOut, 1) else endBar - len
                // auf einer Phrase (8, sonst 4, sonst 2 Takte) – höchstens 4 Takte früher als nötig
                val bar = target.coerceIn(releaseBar, endBar - len).let { b0 ->
                    val r = b0 downTo max(releaseBar, b0 - 4)
                    r.firstOrNull { it % 8 == 0 } ?: r.firstOrNull { it % 4 == 0 } ?: r.firstOrNull { it % 2 == 0 } ?: b0
                }
                val type = if (recent.lastOrNull() == TransitionType.BLEND && TransitionType.BASS_SWAP in settings.enabled) TransitionType.BASS_SWAP
                    else TransitionType.BLEND
                val p = beatPlan(type, bar, len, bEntryBlend(b, len),
                    (if (fadeOut != null) "Blend im Fade-out" else "Blend im instrumentalen Schluss") + " (Takt $bar von ${a.endBar}, $len Takte)")
                ifVocalOk(p)?.let { return it }
                why.removeAt(why.lastIndex)
            }
        }
        // (d) Loop als Brücke: nach dem Gesang nur 1–3 Takte Instrumental – den ersten davon loopen, B kommt im Takt darüber
        if (beatOk && tailBars in 1..3 && releaseBar + 1 <= endBar && a.barTime(releaseBar + 1) <= aEnd - 0.1 &&
            a.energyIn(releaseBar, releaseBar + 1) >= 0.5f * a.avgEnergy) {
            val bar = releaseBar + 1
            val p = beatPlan(TransitionType.LOOP, bar, 4, bEntryBlend(b, 4), "Loop: letzter instrumentaler Takt (Takt $releaseBar) als Brücke, 4 Takte")
            if (p.type == TransitionType.LOOP) ifVocalOk(p)?.let { return it }
            why.removeAt(why.lastIndex)
        }

        // (b)/(c) A klingt aus bzw. endet auf der 1, B setzt auf seinem Downbeat ein (im Tempo von A, wenn möglich)
        exitPlan(a, b, aPos, ratio, beatOk, tempoClose, release, releaseBar, bodyEnd, aEnd, lead, settings, recent, why)?.let { return it }

        // (e) Fade – nur wenn nichts anderes geht
        val tail = aEnd - release
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

    /**
     * B-Einstieg für einen Blend über [len] Takte: das Intro von B so legen, dass es mit dem Blend endet
     * (B ist dann "voll" da, wenn A weg ist). Nie mitten in einer Gesangsphrase – sonst ab dem ersten Downbeat.
     */
    private fun bEntryBlend(b: TrackStructure, len: Int): Double {
        val introPhrase = (b.introEndBar / 8) * 8
        val t = b.barTime(max(0, introPhrase - len)).coerceAtLeast(0.0)
        if (!b.midVocal(t) && t >= b.soundStart - 0.3) return t
        return b.barTime(b.nextBoundary(b.soundStart - 0.05, 1)).coerceAtLeast(0.0)
    }

    /**
     * B-Einstieg, wenn A beim Einsatz endet (Echo, Schnitt, Filter-Sweep): auf dem ersten Downbeat mit Ton.
     * Ist das Intro lang und ruhig (≥ 8 Takte deutlich leiser als danach), startet B auf der Phrase, an der es
     * "voll" wird. Nie mitten in einer Gesangsphrase. Unsicheres Raster: ab dem ersten Ton.
     */
    fun bEntryHit(b: TrackStructure): Double {
        if (!(b.confidence < 0f || b.confidence >= 0.4f)) return b.soundStart
        val first = b.nextBoundary(b.soundStart - 0.05, 1)
        val body = b.energyIn(b.introEndBar, b.introEndBar + 16).coerceAtLeast(0.05f)
        // Ein Einstieg taugt, wenn dort Musik ist (nicht fast still), B nicht mitten in einer Phrase steht und keine
        // Phrase knapp davor oder gleich danach beginnt (Auftakt; die Erkennung meldet Gesang etwas verspätet).
        fun ok(bar: Int): Boolean {
            if (bar >= b.barCount) return false
            val t = b.barTime(bar)
            if (b.energyIn(bar, bar + 1) < 0.15f * body) return false
            if (b.midVocal(t)) return false
            val vs = b.nextVocalStart(t - 1.0)
            return vs == null || vs < t - 1.0 || vs >= t + 0.6
        }
        val cands = mutableListOf<Int>()
        // langes, ruhiges Intro (≥ 8 Takte deutlich leiser als danach): bevorzugt auf der Phrase, wo B "voll" wird
        if (b.introEndBar - first >= 8 && b.energyIn(first, b.introEndBar) < 0.6f * body)
            cands += (b.introEndBar downTo first + 4).filter { (it - first) % 4 == 0 }
        cands += (first until first + 8)
        cands.firstOrNull { ok(it) }?.let { return b.barTime(it).coerceAtLeast(0.0) }
        val t = b.barTime(first).coerceAtLeast(0.0)
        return if (b.midVocal(t)) b.soundStart else t
    }

    /**
     * (b) Echo-Out / Filter-Sweep und (c) Schnitt auf die 1: A endet auf einer Phrasengrenze nach dem letzten Gesang
     * (die letzte, an der A noch voll spielt) – singt A bis zum Schluss, bekommt das letzte Wort ein Echo.
     * B setzt auf seinem Downbeat ein, im Tempo von A, wenn Raster und Tempo passen. Gleich gute Arten wechseln ab.
     */
    private fun exitPlan(
        a: TrackStructure, b: TrackStructure, aPos: Double, ratio: Float?, beatOk: Boolean, tempoClose: Boolean,
        release: Double, releaseBar: Int, bodyEnd: Double, aEnd: Double, lead: Double,
        settings: PlannerSettings, recent: List<TransitionType>, why: MutableList<String>,
    ): TransitionPlan? {
        val aGrid = a.beatReliable
        var exitBar: Int? = null
        if (aGrid) {
            val last = a.barAt(bodyEnd - 0.05)
            val range = (last downTo releaseBar).filter { a.barTime(it) >= max(release, lead) - 1e-6 && a.barTime(it) <= bodyEnd }
            exitBar = range.firstOrNull { it % 4 == 0 } ?: range.firstOrNull()
        }
        val target = min(max(release, lead), aEnd)
        // Ausstieg auf dem Raster von A: auf der Phrase (oben), sonst – am Schluss von A – auf der nächsten "1", wenn
        // sie höchstens einen Beat später kommt, sonst auf dem nächsten Beat. So bleibt das Echo im Takt von A und B
        // setzt im Takt ein. Ohne sicheres Raster: zeitlich nach dem Gesang (B dann im eigenen Tempo).
        val exitSec = exitBar?.let { a.barTime(it) } ?: if (aGrid) {
            val tBar = a.barTime(a.nextBoundary(target, 1))
            val tBeat = a.firstDownbeat + kotlin.math.ceil((target - a.firstDownbeat) / a.beatSec - 1e-6) * a.beatSec
            if (tBar - target <= a.beatSec + 1e-6) tBar else tBeat
        } else target
        // A spielt am Ausstieg noch voll (nicht im Ausklang)? Nur dann ist ein Schnitt / Sweep musikalisch
        val aFull = exitBar != null && exitSec <= bodyEnd - 0.05
        val bStartHit = bEntryHit(b)
        // im Takt von A nur, wenn beide Raster genau dort sicher sind (A vor dem Ausstieg, B ab seinem Einsatz)
        val match = beatOk && ratio != null && aGrid &&
            a.gridSteady(exitSec - a.barSec, exitSec + 0.1) && b.gridSteady(bStartHit, bStartHit + b.barSec)
        val opts = mutableListOf(TransitionType.ECHO_OUT)
        if (aFull && exitSec + a.barSec <= aEnd) opts += TransitionType.FILTER
        if (aFull && a.gridSteady(exitSec - a.barSec, exitSec + 0.1)) opts += TransitionType.CUT
        // Singt A bis zum Schluss, bleibt nur das letzte Wort: Echo oder (dezenter) Hall – abwechselnd
        if (!aFull) opts += TransitionType.REVERB_WASH
        val usable = opts.filter { it in settings.enabled }
        if (usable.isEmpty()) return null
        val last2 = recent.takeLast(2)
        val type = usable.firstOrNull { it !in last2 } ?: usable.firstOrNull { it != recent.lastOrNull() } ?: usable.first()
        val bStart = bStartHit
        val len = when (type) { TransitionType.CUT -> 1; else -> 2 }
        why += when (type) {
            TransitionType.ECHO_OUT -> if (aFull) "Echo-Out nach dem Gesang (Takt ${exitBar ?: a.barAt(exitSec)})" else "Echo-Out auf dem Schluss von A"
            TransitionType.REVERB_WASH -> "Hall auf dem Schluss von A"
            TransitionType.FILTER -> "Filter-Sweep nach dem Gesang (Takt ${exitBar ?: a.barAt(exitSec)})"
            else -> "Schnitt auf die 1 nach dem Gesang (Takt $exitBar)"
        } + (if (match) " · B im Takt von A" else if (!tempoClose) " · Tempo zu verschieden: B im eigenen Tempo" else " · Raster unsicher: B im eigenen Tempo") +
            " · B ab ${mmss(bStart)}"
        return TransitionPlan(
            type = type, mixBar = exitBar ?: a.barAt(exitSec), mixSec = exitSec, bStartSec = bStart,
            lengthBars = len, tempoRatio = if (match) ratio!! else 1f, matchTempo = match,
            reason = why.joinToString(" · "),
        )
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
        val release = if (a.lastVocalEnd > 0) a.lastVocalEnd + RELEASE_MARGIN else 0.0
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
