package app.fadelux.automix

import kotlin.math.max
import kotlin.math.min

/** Ein Takt (4 Beats) mit seinen Eigenschaften. Werte 0..1. */
data class BarInfo(
    val index: Int,
    val energy: Float,
    val bass: Float,
    val vocal: Boolean,
)

/**
 * Aufbau eines Titels: Beat-Raster, Takte (mit Downbeat), Phrasen, Intro/Outro,
 * Breaks (Bass weg) und grobe Gesangs-Erkennung. Alles in "Titel-Sekunden".
 */
data class TrackStructure(
    val bpm: Float,
    val firstDownbeat: Double,     // Zeit des ersten Downbeats ("die 1" im Takt)
    val duration: Double,
    val bars: List<BarInfo>,
    val introEndBar: Int,           // erster Takt mit "voller" Energie
    val outroStartBar: Int,         // erster Takt des Outros (Energie fällt ab)
    val breaks: List<IntRange>,     // Takte ohne Bass (Break)
    val endBar: Int = bars.size,    // erster Takt NACH dem hörbaren Ende (Stille am Schluss zählt nicht)
    val trackEnergy: Float = -1f,   // Energie des ganzen Titels (0..1, aus der Bibliothek), -1 = unbekannt
    val musicalKey: Int = -1,       // Tonart (0..23, siehe [Keys]), -1 = unbekannt
    val confidence: Float = -1f,    // Vertrauen ins Beat-Raster 0..1 (-1 = unbekannt)
    val steady: Boolean = true,     // festes Tempo über den ganzen Song
    /** Hörbarer Gesang als Abschnitte [Start, Ende] in Sekunden (genauer als pro Takt); leer = keiner/unbekannt. */
    val vocalSegments: List<ClosedFloatingPointRange<Double>> = emptyList(),
    val vocalKnown: Boolean = false,  // true = Gesang aus der genauen Erkennung (sonst grob pro Takt)
    val soundEnd: Double = duration,  // ab hier nur noch Stille
    val soundStart: Double = 0.0,     // erster hörbarer Ton (Stille am Dateianfang überspringen)
    val fadeOutStart: Double = -1.0,  // Beginn des Ausblendens (-1 = kein Fade-out)
    val family: GenreFamily? = null,  // Genre-Familie (aus dem ID3-Tag bzw. geschätzt)
) {
    /** Ende des letzten Gesangs (Sekunden), 0 = kein Gesang. */
    val lastVocalEnd: Double get() =
        if (vocalKnown) vocalSegments.lastOrNull()?.endInclusive ?: 0.0
        else bars.indexOfLast { it.vocal }.let { if (it < 0) 0.0 else barTime(it + 1) }

    /** Ist im Zeitraum [from, to) (Sekunden) Gesang hörbar? */
    fun vocalBetween(from: Double, to: Double): Boolean =
        if (vocalKnown) vocalSegments.any { it.start < to && it.endInclusive > from }
        else vocalIn(barAt(from), kotlin.math.ceil((to - firstDownbeat) / barSec).toInt())

    /** Beginn des ersten Gesangs ab [from] (Sekunden), oder null. */
    fun nextVocalStart(from: Double): Double? =
        if (vocalKnown) vocalSegments.firstOrNull { it.endInclusive > from }?.start?.coerceAtLeast(from)
        else (barAt(from) until barCount).firstOrNull { bars[it].vocal }?.let { barTime(it) }

    /** Beat-Raster zuverlässig genug, um Tempo und Takt anzugleichen? */
    val beatReliable: Boolean get() = confidence < 0f || (confidence >= RELIABLE && steady)

    val beatSec: Double get() = 60.0 / bpm
    val barSec: Double get() = 4 * beatSec
    val barCount: Int get() = bars.size

    fun barTime(bar: Int): Double = firstDownbeat + bar * barSec
    fun barAt(sec: Double): Int = ((sec - firstDownbeat) / barSec).toInt().coerceAtLeast(0)

    /** Nächster Takt-Anfang ab sec, der ein Vielfaches von `multiple` Takten ist. */
    fun nextBoundary(sec: Double, multiple: Int): Int {
        val bar = kotlin.math.ceil((sec - firstDownbeat) / barSec - 1e-6).toInt().coerceAtLeast(0)
        val r = bar % multiple
        return if (r == 0) bar else bar + (multiple - r)
    }

    /** Phrasen-Ebene eines Takts: 32, 16, 8 oder 0 (keine Phrasengrenze). */
    fun phraseLevel(bar: Int): Int = when {
        bar % 32 == 0 -> 32
        bar % 16 == 0 -> 16
        bar % 8 == 0 -> 8
        else -> 0
    }

    fun vocalIn(from: Int, to: Int): Boolean =
        (max(0, from) until min(barCount, to)).any { bars[it].vocal }

    fun energyIn(from: Int, to: Int): Float {
        val r = max(0, from) until min(barCount, to)
        if (r.isEmpty()) return 0f
        return r.map { bars[it].energy }.average().toFloat()
    }

    val avgEnergy: Float get() = if (bars.isEmpty()) 0f else bars.map { it.energy }.average().toFloat()
}

/**
 * Ergebnis der Song-Analyse (Engine, Version 2): Beat-Raster mit Vertrauen, Merkmale pro Beat
 * (5 Bytes: Energie, Bass, Mitten, Höhen, Gesang) und Gesangs-Wahrscheinlichkeit alle 0,25 s.
 */
class AnalysisData(
    val bpm: Float,
    val firstBeat: Double,
    val downbeat: Int,              // Index (0..3) des ersten Downbeats
    val confidence: Float,
    val steady: Boolean,
    val features: ByteArray,        // 5 Bytes pro Beat
    val vocal: ByteArray,           // 0..255 pro 0,25 s
    val duration: Double,
)

/** Schwelle fürs Beatmatching (an echter Musik bestimmt, siehe tools/realmusic). */
const val RELIABLE = 0.6f

object StructureAnalyzer {
    /** Schwelle der Gesangs-Wahrscheinlichkeit (an den Übungs-Songs bestimmt, siehe train_vocal_net.py):
     *  bewusst tief – lieber Gesang zu viel erkennen als zu wenig. */
    var vocalThreshold = 0.2f

    /**
     * Fenster am Song-Ende: Endet der erkannte Gesang weniger als so viele Sekunden vor dem hörbaren Ende, gilt
     * er bis zum Ende – die Erkennung verpasst leise Gesangsreste am Schluss (Ad-libs, Chor im Fade-out) oft.
     * Pop/Schlager/Rock mischen ohnehin ganz am Ende (30 s); Techno/House behalten ihr langes Outro (12 s).
     * An den 102 Übungs-Songs bestimmt: letzter Gesang > 0,5 s zu früh erkannt bei 8 statt 32 Songs.
     */
    const val VOCAL_TAIL_SONG = 30.0
    const val VOCAL_TAIL_ELECTRONIC = 12.0
    const val VOCAL_HOP = 0.25

    /** Gesangs-Abschnitte aus der Wahrscheinlichkeit: Median über 1,25 s, Lücken < 1 s schliessen, Stücke < 0,5 s weg. */
    fun vocalSegments(vocal: ByteArray, threshold: Float = vocalThreshold): List<ClosedFloatingPointRange<Double>> {
        val n = vocal.size
        if (n == 0) return emptyList()
        val p = FloatArray(n) { (vocal[it].toInt() and 0xFF) / 255f }
        val med = FloatArray(n) { i ->
            val w = (max(0, i - 2)..min(n - 1, i + 2)).map { p[it] }.sorted()
            w[w.size / 2]
        }
        val segs = mutableListOf<DoubleArray>()
        var start = -1
        for (i in 0..n) {
            val on = i < n && med[i] >= threshold
            if (on && start < 0) start = i
            if (!on && start >= 0) { segs += doubleArrayOf(start * VOCAL_HOP, i * VOCAL_HOP); start = -1 }
        }
        val merged = mutableListOf<DoubleArray>()
        for (s in segs) {
            val last = merged.lastOrNull()
            if (last != null && s[0] - last[1] < 1.0) last[1] = s[1] else merged += s
        }
        return merged.filter { it[1] - it[0] >= 0.5 }.map { it[0]..it[1] }
    }

    /** Struktur aus der Analyse Version 2 (Beat-Raster mit Vertrauen + genaue Gesangserkennung). */
    fun analyze(d: AnalysisData, soundEnd: Double = d.duration, family: GenreFamily? = null): TrackStructure? {
        val nf = 5
        val n = d.features.size / nf
        if (d.bpm <= 0f || n < 16) return null
        // Merkmale ohne den Gesangs-Kanal in das alte 4-Byte-Format übernehmen
        val f4 = ByteArray(n * 4)
        for (i in 0 until n) for (b in 0 until 4) f4[i * 4 + b] = d.features[i * nf + b]
        val base = analyzeFixedDownbeat(d.bpm, d.firstBeat.toFloat(), f4, d.duration, soundEnd, d.downbeat) ?: return null
        val raw = vocalSegments(d.vocal)
        val tail = if (family == GenreFamily.HOUSE || family == GenreFamily.TECHNO) VOCAL_TAIL_ELECTRONIC else VOCAL_TAIL_SONG
        val lastSeg = raw.lastOrNull()
        // Gesang kurz vor dem Ende: gilt bis zum hörbaren Ende (der neue Song beginnt danach, mit dem üblichen Abstand)
        val segs = if (lastSeg != null && soundEnd - lastSeg.endInclusive < tail && soundEnd > lastSeg.endInclusive)
            raw.dropLast(1) + listOf(lastSeg.start..soundEnd)
        else raw
        val bars = base.bars.map { bar ->
            val a = base.barTime(bar.index)
            bar.copy(vocal = segs.any { it.start < a + base.barSec && it.endInclusive > a })
        }
        // Fade-out: ab wann wird der Song bis zum Ende nur noch leiser? (Energie pro Beat)
        val beatSec = 60.0 / d.bpm
        val e = FloatArray(n) { (d.features[it * nf].toInt() and 0xFF) / 255f }
        val body = quantile(e.copyOfRange(n / 5, max(n / 5 + 1, n * 4 / 5)), 0.5f)
        val endBeat = min(n - 1, ((soundEnd - d.firstBeat) / beatSec).toInt())
        var lastFull = endBeat
        while (lastFull > 0 && e[lastFull] < body * 0.75f) lastFull--
        val fadeStart = d.firstBeat + (lastFull + 1) * beatSec
        val fadeOut = if (soundEnd - fadeStart >= 4.0 && lastFull < endBeat) fadeStart else -1.0
        return base.copy(
            bars = bars, confidence = d.confidence, steady = d.steady, vocalSegments = segs, vocalKnown = true,
            soundEnd = soundEnd, fadeOutStart = fadeOut, family = family,
        )
    }

    /**
     * Baut die Struktur aus den Merkmalen pro Beat (je 4 Bytes: Energie, Bass, Mitten, Höhen).
     * Gibt null zurück, wenn kein Tempo erkannt wurde.
     */
    /**
     * @param soundEnd Zeit (Sekunden), ab der der Titel nur noch still ist (aus der Wellenform),
     *                 oder [duration], wenn unbekannt.
     */
    fun analyze(bpm: Float, firstBeat: Float, features: ByteArray, duration: Double, soundEnd: Double = duration): TrackStructure? =
        analyzeFixedDownbeat(bpm, firstBeat, features, duration, soundEnd, -1)

    private fun analyzeFixedDownbeat(bpm: Float, firstBeat: Float, features: ByteArray, duration: Double, soundEnd: Double, fixedDownbeat: Int): TrackStructure? {
        val n = features.size / 4
        if (bpm <= 0f || n < 16) return null
        fun f(i: Int, band: Int) = (features[i * 4 + band].toInt() and 0xFF) / 255f
        val beatSec = 60.0 / bpm

        // 1) Downbeat: von der Analyse vorgegeben, sonst: auf welchem der 4 Beats ist der Bass am stärksten?
        val offset = if (fixedDownbeat in 0..3) fixedDownbeat else (0..3).maxBy { off ->
            var s = 0.0
            var c = 0
            var i = off
            while (i < n) { s += f(i, 1); c++; i += 4 }
            if (c == 0) 0.0 else s / c
        }
        val downbeat = firstBeat + offset * beatSec

        // 2) Takte bilden
        val barCount = (n - offset) / 4
        val e = FloatArray(barCount)
        val b = FloatArray(barCount)
        val midRatio = FloatArray(barCount)
        val mid = FloatArray(barCount)
        for (bar in 0 until barCount) {
            var se = 0f; var sb = 0f; var sm = 0f
            for (k in 0 until 4) {
                val i = offset + bar * 4 + k
                se += f(i, 0); sb += f(i, 1); sm += f(i, 2)
            }
            e[bar] = se / 4; b[bar] = sb / 4; mid[bar] = sm / 4
            midRatio[bar] = mid[bar] / (e[bar] + 0.05f)
        }

        // 2b) Hörbares Ende: Stille am Schluss ignorieren (Merkmale + Wellenform)
        var lastLoud = barCount - 1
        while (lastLoud > 0 && e[lastLoud] < SILENCE) lastLoud--
        val endByWave = kotlin.math.floor((soundEnd - downbeat) / (4 * beatSec) + 1e-6).toInt()
        val endBar = min(lastLoud + 1, endByWave).coerceIn(min(8, barCount), barCount)

        // 3) Gesang (grob): Mitten deutlich stärker als in den instrumentalen Teilen dieses Titels.
        //    Referenz = unteres Viertel (nicht der Median – sonst fällt Gesang in >50 % des Songs durch)
        val medRatio = quantile(midRatio, 0.25f)
        val vocal = BooleanArray(barCount) { midRatio[it] > medRatio * 1.15f && mid[it] > 0.35f }
        // einzelne Ausreisser glätten (Gesang dauert meist mehrere Takte)
        val vocalSmooth = BooleanArray(barCount) { i ->
            val w = (max(0, i - 1)..min(barCount - 1, i + 1)).count { vocal[it] }
            w >= 2
        }

        // 4) Intro/Outro: wo erreicht/verlässt die Energie das "volle" Niveau?
        val medE = median(e)
        val smooth = FloatArray(barCount) { i -> (max(0, i - 1)..min(barCount - 1, i + 1)).map { e[it] }.average().toFloat() }
        val thr = medE * 0.8f
        val introEnd = smooth.indexOfFirst { it >= thr }.coerceAtLeast(0)
        val outroStart = (smooth.indexOfLast { it >= thr } + 1).coerceIn(introEnd, max(introEnd, endBar))

        // 5) Breaks: mindestens 2 Takte fast ohne Bass, aber nicht still, zwischen Intro und Outro
        val medB = median(b)
        val breaks = mutableListOf<IntRange>()
        var start = -1
        for (i in introEnd until outroStart) {
            val isBreak = b[i] < medB * 0.35f && e[i] > 0.12f
            if (isBreak && start < 0) start = i
            if ((!isBreak || i == outroStart - 1) && start >= 0) {
                val end = if (isBreak) i else i - 1
                if (end - start + 1 >= 2) breaks += start..end
                start = -1
            }
        }

        val bars = (0 until barCount).map { BarInfo(it, e[it], b[it], vocalSmooth[it]) }
        return TrackStructure(bpm, downbeat, duration, bars, introEnd, outroStart, breaks, endBar, soundEnd = soundEnd)
    }

    /** Energie pro Beat, unter der ein Takt als "still" gilt. */
    private const val SILENCE = 0.05f

    /**
     * Ab welcher Zeit (Sekunden) ist der Titel nur noch still? Aus der Wellenform (100 Werte/s).
     * Leise Ausklänge unter ca. -34 dB zählen als Stille.
     */
    fun soundEnd(peaks: FloatArray, duration: Double): Double {
        if (peaks.isEmpty()) return duration
        var i = peaks.size - 1
        while (i > 0 && peaks[i] < 0.02f) i--
        return min(duration, (i + 1) / 100.0 + 0.1)
    }

    /** Erster hörbarer Ton (Sekunden, 100 Spitzenwerte pro Sekunde): Stille am Dateianfang zählt nicht. */
    fun soundStart(peaks: FloatArray): Double {
        val i = peaks.indexOfFirst { it >= 0.02f }
        return if (i <= 0) 0.0 else max(0.0, i / 100.0 - 0.05)
    }

    private fun quantile(a: FloatArray, q: Float): Float {
        if (a.isEmpty()) return 0f
        val s = a.copyOf().apply { sort() }
        return s[((s.size - 1) * q).toInt()]
    }

    private fun median(a: FloatArray): Float {
        if (a.isEmpty()) return 0f
        val s = a.copyOf().apply { sort() }
        return s[s.size / 2]
    }
}
