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
    val blendTypes = setOf(TransitionType.BASS_SWAP, TransitionType.FILTER, TransitionType.LOOP)

    /**
     * @param aPos aktuelle Position in A (Sekunden)
     * @param aTempo aktuelles Tempo-Verhältnis von A
     * @param recent zuletzt benutzte Typen (neuester zuletzt)
     * @param now true = möglichst bald (nächster passender Takt) statt am Outro
     * @param forced vom Benutzer gewählter Typ (oder null)
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
    ): TransitionPlan {
        // --- 1) Tempo ---
        val aBpm = a.bpm * aTempo
        val ratio = TrackAnalysis.tempoRatio(aBpm, b.bpm)
        val maxStretch = when (settings.style) {
            MixStyle.CALM -> 0.06f
            MixStyle.BALANCED -> 0.08f
            MixStyle.CLUB -> 0.10f
        }
        val matchTempo = ratio != null && abs(ratio - 1f) <= maxStretch

        // --- 2) Länge ---
        var length = when {
            settings.lengthBars > 0 -> settings.lengthBars
            settings.style == MixStyle.CALM -> 32
            settings.style == MixStyle.BALANCED -> 16
            else -> 8
        }
        val bIntroBars = b.introEndBar
        // Kurzes Intro beim neuen Song → kürzere Überblendung
        if (settings.lengthBars == 0 && bIntroBars in 1 until length) length = max(4, roundDownPow2(bIntroBars))

        // --- 3) Startpunkt in A (Phrasengrenze) ---
        val currentBar = a.barAt(aPos)
        val (mixBar, fitLength) = chooseMixBar(a, currentBar, length, now)
        length = fitLength

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

        // --- 6) Typ wählen ---
        val allowed = settings.enabled.ifEmpty { setOf(TransitionType.CUT) }.filter { t ->
            when {
                t in blendTypes && (!matchTempo || vocalClash) -> false
                // Song endet vor dem Ende der Überblendung (z.B. Auto-Mix kurz vor Schluss gestartet)
                t in blendTypes && !now && mixBar + length > a.endBar -> false
                t == TransitionType.LOOP && a.vocalIn(mixBar - 1, mixBar) -> false   // Gesang nicht loopen
                else -> true
            }
        }.ifEmpty { listOf(TransitionType.ECHO_OUT, TransitionType.CUT).filter { it in settings.enabled }.ifEmpty { listOf(TransitionType.CUT) } }

        val type = forced?.takeIf { it in allowed || (it !in blendTypes) } ?: chooseType(a, b, mixBar, length, allowed, settings.style, recent)

        // Blends brauchen die volle Länge; Echo/Hall/Schnitt sind kurz
        val finalLength = when (type) {
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
        )
    }

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
    ): TransitionType {
        val base = when (style) {
            MixStyle.CALM -> mapOf(
                TransitionType.BASS_SWAP to 3.0, TransitionType.FILTER to 3.0, TransitionType.REVERB_WASH to 2.0,
                TransitionType.LOOP to 1.2, TransitionType.ECHO_OUT to 1.0, TransitionType.CUT to 0.3,
            )
            MixStyle.BALANCED -> mapOf(
                TransitionType.BASS_SWAP to 3.0, TransitionType.FILTER to 2.2, TransitionType.ECHO_OUT to 2.0,
                TransitionType.LOOP to 1.8, TransitionType.REVERB_WASH to 1.5, TransitionType.CUT to 1.0,
            )
            MixStyle.CLUB -> mapOf(
                TransitionType.BASS_SWAP to 2.2, TransitionType.FILTER to 2.0, TransitionType.ECHO_OUT to 2.5,
                TransitionType.LOOP to 2.0, TransitionType.REVERB_WASH to 1.0, TransitionType.CUT to 2.5,
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
