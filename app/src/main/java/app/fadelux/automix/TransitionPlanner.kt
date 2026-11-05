package app.fadelux.automix

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Wählt für jedes Song-Paar den passenden Übergang:
 *  - Tempo-Abstand: klein → Beats angleichen (Blends möglich); gross → Echo-Out, Schnitt oder Hall
 *  - Gesang: nie zwei Gesangsteile übereinander (sonst kürzer oder ohne Überlappung)
 *  - Intro/Outro-Länge, Energie-Verlauf und Stil (ruhig / ausgewogen / Club)
 *  - Abwechslung: derselbe Typ nicht mehrmals hintereinander
 * Alles im Takt und an Phrasengrenzen (8/16/32 Takte).
 */
object TransitionPlanner {

    /** Typen, bei denen beide Songs gleichzeitig hörbar sind (Beats müssen passen). */
    val blendTypes = setOf(TransitionType.BASS_SWAP, TransitionType.FILTER, TransitionType.LOOP, TransitionType.LONG_BLEND)

    /**
     * @param aPos aktuelle Position in A (Sekunden)
     * @param aTempo aktuelles Tempo-Verhältnis von A
     * @param recent zuletzt benutzte Typen (neuester zuletzt)
     * @param now true = möglichst bald (nächster passender Takt) statt am Outro
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
        // --- 1) Tempo ---
        val aBpm = a.bpm * aTempo
        val ratio = TrackAnalysis.tempoRatio(aBpm, b.bpm)
        val maxStretch = when (settings.style) {
            MixStyle.CALM -> 0.06f
            MixStyle.BALANCED -> 0.08f
            MixStyle.CLUB -> 0.10f
        }
        val matchTempo = ratio != null && abs(ratio - 1f) <= maxStretch
        val keyFit = Keys.compatibility(a.musicalKey.takeIf { it >= 0 }, b.musicalKey.takeIf { it >= 0 })
        val why = mutableListOf<String>()
        why += if (ratio == null) "Tempo nicht vergleichbar (${fmt(aBpm)} → ${fmt(b.bpm)} BPM)"
        else if (matchTempo) "Tempo ${fmt(aBpm)} → ${fmt(b.bpm)} BPM: angleichen ${pct(ratio - 1f)}"
        else "Tempo ${fmt(aBpm)} → ${fmt(b.bpm)} BPM: zu weit (${pct(ratio - 1f)}) → kein Blend"

        // --- 2) Länge ---
        var length = when {
            settings.lengthBars > 0 -> settings.lengthBars
            settings.style == MixStyle.CALM -> 32
            settings.style == MixStyle.BALANCED -> 16
            else -> 8
        }
        if (settings.lengthBars == 0) {
            // Länge passend zum Song-Paar: ruhige Songs länger, Club-Tracks knapp und im Takt
            val calm = (aBpm < 105f && b.bpm < 105f) ||
                (a.trackEnergy in 0f..0.45f && b.trackEnergy in 0f..0.45f)
            val club = a.trackEnergy >= 0.7f && b.trackEnergy >= 0.7f && aBpm in 118f..135f
            if (calm) { length = min(32, length * 2); why += "ruhiges Paar → länger" }
            else if (club && settings.style != MixStyle.CALM) { length = min(length, 16); why += "Club-Paar → knapp" }
        }
        val bIntroBars = b.introEndBar
        // Kurzes Intro beim neuen Song → kürzere Überblendung
        if (settings.lengthBars == 0 && bIntroBars in 1 until length) {
            length = max(4, roundDownPow2(bIntroBars))
            why += "Intro von B nur $bIntroBars Takte"
        }

        if (a.musicalKey >= 0 && b.musicalKey >= 0) why += "Tonart ${Keys.label(a.musicalKey)} → ${Keys.label(b.musicalKey)}" +
            (if (keyFit >= 0.85f) " passt" else if (keyFit < 0.3f) " reibt" else "")

        // --- 3) Startpunkt in A (Phrasengrenze) ---
        val currentBar = a.barAt(aPos)
        var (mixBar, fitLength) = if (atBar != null) atBar to min(length, max(4, a.endBar - atBar))
        else chooseMixBar(a, currentBar, length, now)
        if (fitLength < length) why += "wenig Zeit bis zum Ende → $fitLength Takte"
        length = fitLength
        why += when {
            atBar != null -> "Früh-Übergang: Songs passen perfekt (Takt $mixBar von ${a.endBar})"
            now -> "jetzt mixen (Takt $mixBar)"
            mixBar >= a.outroStartBar - 4 && a.outroStartBar < a.endBar -> "Start im Outro (Takt $mixBar von ${a.endBar})"
            else -> "Start vor dem Ende, Phrase ${a.phraseLevel(mixBar).takeIf { it > 0 } ?: 4} (Takt $mixBar von ${a.endBar})"
        }

        // --- 4) Startpunkt in B: Intro so legen, dass der Hauptteil am Ende der Überblendung kommt ---
        fun bStartFor(len: Int): Int {
            val introPhrase = (b.introEndBar / 8) * 8
            return max(0, introPhrase - len)
        }

        // --- 5) Gesang prüfen: überlappen sich Gesangsteile? Dann kürzer, sonst kein Blend ---
        var vocalClash = false
        run {
            var len = length
            while (len >= 4) {
                val bs = bStartFor(len)
                val clash = a.vocalIn(mixBar, mixBar + len) && b.vocalIn(bs, bs + len)
                if (!clash) { length = len; return@run }
                len /= 2
            }
            vocalClash = true
        }
        if (vocalClash) why += "Gesang in A und B → nicht übereinander"
        else if (length < fitLength) why += "Gesang → kürzer ($length Takte)"

        // Langer Blend: nur bei (fast) gleichem Tempo, passender Tonart und genug Platz ohne Gesangs-Überlappung
        val longLen = if (atBar != null || now) 32 else min(32, a.endBar - mixBar)
        val longOk = matchTempo && abs(ratio!! - 1f) <= 0.03f && keyFit >= 0.85f && longLen >= 24 &&
            !(a.vocalIn(mixBar, mixBar + longLen) && b.vocalIn(bStartFor(longLen), bStartFor(longLen) + longLen))

        // --- 6) Typ wählen ---
        val allowed = settings.enabled.ifEmpty { setOf(TransitionType.CUT) }.filter { t ->
            when {
                t == TransitionType.LONG_BLEND -> longOk
                t in blendTypes && (!matchTempo || vocalClash) -> false
                // Song endet vor dem Ende der Überblendung (z.B. Auto-Mix kurz vor Schluss gestartet)
                t in blendTypes && !now && mixBar + length > a.endBar -> false
                t == TransitionType.LOOP && a.vocalIn(mixBar - 1, mixBar) -> false   // Gesang nicht loopen
                else -> true
            }
        }.ifEmpty { listOf(TransitionType.ECHO_OUT, TransitionType.CUT).filter { it in settings.enabled }.ifEmpty { listOf(TransitionType.CUT) } }

        val forcedOk = forced?.takeIf { it in allowed || (it !in blendTypes) }
        val type = forcedOk ?: chooseType(a, b, mixBar, length, allowed, settings.style, recent, keyFit)
        if (forcedOk != null) why += "vom DJ gewählt"
        else {
            val dE = b.energyIn(b.introEndBar, b.introEndBar + 8) - a.energyIn(mixBar - 4, mixBar)
            if (dE > 0.2f) why += "Energie steigt"
            if (dE < -0.2f) why += "Energie fällt"
            if (recent.isNotEmpty()) why += "Abwechslung (zuletzt ${recent.last().name})"
        }

        // Schnitt am liebsten genau auf die "1" nach einem Break im Outro-Bereich
        if (type == TransitionType.CUT && atBar == null && !now) {
            val lo = max(currentBar + 2, a.endBar - 40)
            a.breaks.map { it.last + 1 }.filter { it in lo until a.endBar }.minByOrNull { abs(it - mixBar) }?.let {
                mixBar = it
                why += "Schnitt auf die 1 nach dem Break (Takt $it)"
            }
        }

        // Blends brauchen die volle Länge; Echo/Hall/Schnitt sind kurz
        val finalLength = when (type) {
            TransitionType.LONG_BLEND -> longLen.coerceAtMost(32).let { if (it >= 32) 32 else 16.coerceAtLeast(roundDownPow2(it)) }
            TransitionType.CUT -> 1
            TransitionType.ECHO_OUT, TransitionType.REVERB_WASH -> min(length, 4)
            else -> length
        }
        val bStartBar = when (type) {
            // Bei Schnitt/Echo/Hall setzt B direkt mit Energie ein: am Ende seines Intros (Club) bzw. am Anfang
            TransitionType.CUT, TransitionType.ECHO_OUT, TransitionType.REVERB_WASH ->
                if (settings.style == MixStyle.CLUB && b.introEndBar >= 8) (b.introEndBar / 8) * 8 else 0
            else -> bStartFor(finalLength)
        }
        return TransitionPlan(
            type = type,
            mixBar = mixBar,
            mixSec = a.barTime(mixBar),
            bStartSec = b.barTime(bStartBar).coerceAtLeast(0.0),
            lengthBars = finalLength,
            tempoRatio = if (matchTempo) ratio!! else 1f,
            matchTempo = matchTempo,
            reason = why.joinToString(" · "),
        )
    }

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
     * Früh-Übergang: Passen zwei Songs fast perfekt (Tempo ±2 %, Tonart harmonisch), darf schon vor
     * dem Outro gemischt werden – auf einer 32er-Phrase (sonst 16er), frühestens ab ca. 55 % des
     * Songs, nur ohne Gesang auf beiden Seiten. Gibt den Takt zurück oder null.
     * @param normalBar Einsatz-Takt des normalen Plans (Früh-Übergang muss deutlich davor liegen)
     */
    fun earlyBar(a: TrackStructure, aPos: Double, aTempo: Float, b: TrackStructure, normalBar: Int): Int? {
        val ratio = TrackAnalysis.tempoRatio(a.bpm * aTempo, b.bpm) ?: return null
        if (abs(ratio - 1f) > 0.02f) return null
        if (a.musicalKey < 0 || b.musicalKey < 0) return null
        if (Keys.compatibility(a.musicalKey, b.musicalKey) < 0.85f) return null
        val len = 16
        val bStart = max(0, (b.introEndBar / 8) * 8 - len)
        if (b.vocalIn(bStart, bStart + len)) return null
        val first = max(a.barAt(aPos) + 12, (a.endBar * 0.55f).toInt())   // genug Zeit für die Frage (10 s)
        val last = normalBar - 24                                          // lohnt sich nur deutlich vor dem Ende
        if (first > last) return null
        for (step in listOf(32, 16)) {
            for (bar in first..last) {
                if (bar % step != 0) continue
                if (a.vocalIn(bar, bar + len)) continue
                return bar
            }
        }
        return null
    }

    private fun fmt(v: Float) = String.format(java.util.Locale.ROOT, "%.1f", v)
    private fun pct(v: Float) = String.format(java.util.Locale.ROOT, "%+.1f %%", v * 100)

    /**
     * Takt in A, an dem der Übergang beginnt. Regeln:
     *  - nie mitten im Song: Start frühestens in den letzten max(32, Länge) Takten vor dem hörbaren Ende
     *  - ideal am Outro-Beginn, sonst so, dass der Übergang genau am Ende fertig ist (Fallback)
     *  - immer auf einer Phrasengrenze (8 Takte), notfalls auf 4 Takten
     *  - fertig, bevor A natürlich endet (Stille am Schluss zählt nicht)
     * Gibt (Takt, Länge) zurück – die Länge wird gekürzt, wenn es sonst nicht mehr reicht.
     */
    internal fun chooseMixBar(a: TrackStructure, currentBar: Int, length: Int, now: Boolean): Pair<Int, Int> {
        val earliest = currentBar + 2   // mindestens 2 Takte Vorlauf (Vorbereitung, Hall aufbauen)
        if (now) {
            // bald: nächste 4-Takt-Grenze
            val r = earliest % 4
            return (if (r == 0) earliest else earliest + (4 - r)) to length
        }
        val end = a.endBar
        var len = length
        // Reicht die Zeit bis zum Ende nicht mehr? Dann kürzer überblenden.
        while (len > 4 && end - len < earliest) len /= 2
        val latest = end - len
        if (latest < earliest) {
            // Ganz knapp vor dem Ende: so bald wie möglich (nächster Takt mit 4er-Raster, sonst sofort)
            val r = earliest % 4
            val b4 = if (r == 0) earliest else earliest + (4 - r)
            return (if (b4 < end) b4 else earliest) to len
        }
        val lo = max(earliest, end - max(32, len))
        val target = a.outroStartBar.coerceIn(lo, latest)
        fun nearest(step: Int): Int? = (lo..latest).filter { it % step == 0 }
            .minWithOrNull(compareBy<Int>({ abs(it - target) }, { -it }))
        val bar = nearest(16)?.takeIf { abs(it - target) <= 4 } ?: nearest(8) ?: nearest(4) ?: latest
        return bar to len
    }

    private fun chooseType(
        a: TrackStructure,
        b: TrackStructure,
        mixBar: Int,
        length: Int,
        allowed: List<TransitionType>,
        style: MixStyle,
        recent: List<TransitionType>,
        keyFit: Float = 0.5f,
    ): TransitionType {
        val base = when (style) {
            MixStyle.CALM -> mapOf(
                TransitionType.BASS_SWAP to 3.0, TransitionType.FILTER to 3.0, TransitionType.REVERB_WASH to 2.0,
                TransitionType.LOOP to 1.2, TransitionType.ECHO_OUT to 1.0, TransitionType.CUT to 0.3,
                TransitionType.LONG_BLEND to 4.0,
            )
            MixStyle.BALANCED -> mapOf(
                TransitionType.BASS_SWAP to 3.0, TransitionType.FILTER to 2.2, TransitionType.ECHO_OUT to 2.0,
                TransitionType.LOOP to 1.8, TransitionType.REVERB_WASH to 1.5, TransitionType.CUT to 1.0,
                TransitionType.LONG_BLEND to 3.2,
            )
            MixStyle.CLUB -> mapOf(
                TransitionType.BASS_SWAP to 2.2, TransitionType.FILTER to 2.0, TransitionType.ECHO_OUT to 2.5,
                TransitionType.LOOP to 2.0, TransitionType.REVERB_WASH to 1.0, TransitionType.CUT to 2.5,
                TransitionType.LONG_BLEND to 1.5,
            )
        }
        val aEnergy = a.energyIn(mixBar - 4, mixBar)
        val bEnergy = b.energyIn(b.introEndBar, b.introEndBar + 8)
        val delta = bEnergy - aEnergy
        val aHasBreakBefore = a.breaks.any { it.last in (mixBar - 2) until mixBar }
        val shortIntro = b.introEndBar < 4

        fun score(t: TransitionType): Double {
            var s = base[t] ?: 1.0
            // Energie steigt stark → Schnitt oder Filter (Spannung), fällt → Echo/Hall (ausklingen)
            if (delta > 0.2f && (t == TransitionType.CUT || t == TransitionType.FILTER)) s *= 1.6
            if (delta < -0.2f && (t == TransitionType.ECHO_OUT || t == TransitionType.REVERB_WASH)) s *= 1.6
            // Nach einem Break im alten Song passt ein harter Schnitt auf die "1"
            if (aHasBreakBefore && t == TransitionType.CUT) s *= 2.0
            // Kurzes Intro beim neuen Song: lange Blends wirken leer
            if (shortIntro && t in blendTypes) s *= 0.6
            if (length >= 16 && t == TransitionType.LOOP) s *= 0.8
            // Tonarten reiben → keine langen Überlagerungen; Echo/Filter kaschieren das besser
            if (keyFit < 0.3f && (t == TransitionType.BASS_SWAP || t == TransitionType.LOOP)) s *= 0.6
            if (keyFit < 0.3f && (t == TransitionType.ECHO_OUT || t == TransitionType.FILTER)) s *= 1.3
            if (keyFit >= 0.85f && t == TransitionType.LONG_BLEND) s *= 1.3
            // Abwechslung
            if (recent.lastOrNull() == t) s *= 0.2
            else if (recent.takeLast(3).contains(t)) s *= 0.6
            return s
        }
        return allowed.maxBy { score(it) }
    }

    private fun roundDownPow2(x: Int): Int {
        var p = 1
        while (p * 2 <= x) p *= 2
        return p
    }
}
