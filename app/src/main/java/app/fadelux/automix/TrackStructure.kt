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
) {
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

object StructureAnalyzer {
    /**
     * Baut die Struktur aus den Merkmalen pro Beat (je 4 Bytes: Energie, Bass, Mitten, Höhen).
     * Gibt null zurück, wenn kein Tempo erkannt wurde.
     */
    /**
     * @param soundEnd Zeit (Sekunden), ab der der Titel nur noch still ist (aus der Wellenform),
     *                 oder [duration], wenn unbekannt.
     */
    fun analyze(bpm: Float, firstBeat: Float, features: ByteArray, duration: Double, soundEnd: Double = duration): TrackStructure? {
        val n = features.size / 4
        if (bpm <= 0f || n < 16) return null
        fun f(i: Int, band: Int) = (features[i * 4 + band].toInt() and 0xFF) / 255f
        val beatSec = 60.0 / bpm

        // 1) Downbeat: auf welchem der 4 Beats ist der Bass am stärksten? (Bassdrum auf der "1")
        val offset = (0..3).maxBy { off ->
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

        // 3) Gesang (grob): Mitten deutlich stärker als typisch für diesen Titel
        val medRatio = median(midRatio)
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
        return TrackStructure(bpm, downbeat, duration, bars, introEnd, outroStart, breaks, endBar)
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

    private fun median(a: FloatArray): Float {
        if (a.isEmpty()) return 0f
        val s = a.copyOf().apply { sort() }
        return s[s.size / 2]
    }
}
