package app.fadelux

import app.fadelux.automix.PlannerSettings
import app.fadelux.automix.StructureAnalyzer
import app.fadelux.automix.TempoRamp
import app.fadelux.automix.TrackStructure
import app.fadelux.automix.TransitionPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Tempo-Angleichung und Rückführung über mehrere Song-Paare mit unterschiedlichen BPM. */
class TempoRampTest {

    private fun song(bpm: Float, bars: Int = 96): TrackStructure {
        val beats = bars * 4
        val f = ByteArray(beats * 4)
        for (i in 0 until beats) {
            val bar = i / 4
            val loud = bar in 16 until bars - 16
            f[i * 4] = ((if (loud) 0.9f else 0.4f) * 255).toInt().toByte()
            f[i * 4 + 1] = ((if (i % 4 == 0) 0.95f else 0.5f) * 255).toInt().toByte()
            f[i * 4 + 2] = (0.3f * 255).toInt().toByte()
            f[i * 4 + 3] = (0.5f * 255).toInt().toByte()
        }
        return StructureAnalyzer.analyze(bpm, 0.2f, f, bars * 4 * 60.0 / bpm + 1)!!
    }

    @Test
    fun ramp_isSmoothAndEndsExactlyAtOriginal() {
        for (start in listOf(1.08f, 1.04f, 1.01f, 0.97f, 0.92f)) {
            assertEquals(start, TempoRamp.value(start, 0f), 1e-6f)
            assertEquals(1f, TempoRamp.value(start, 1f), 0f)          // exakt Originaltempo
            // 16 Takte bei 128 BPM ≈ 30 s, alle 50 ms ein Schritt → winzige Schritte (unhörbar)
            val steps = 600
            var prev = start
            var maxStep = 0f
            for (i in 1..steps) {
                val v = TempoRamp.value(start, i / steps.toFloat())
                assertTrue("monoton", if (start > 1f) v <= prev + 1e-6f else v >= prev - 1e-6f)
                maxStep = maxOf(maxStep, abs(v - prev))
                prev = v
            }
            assertTrue("start=$start maxStep=$maxStep", maxStep < 0.0003f)   // < 0,03 % pro Schritt
        }
    }

    @Test
    fun rampLength_8to16Bars() {
        assertEquals(16, TempoRamp.bars(60))
        assertEquals(8, TempoRamp.bars(15))
        assertEquals(4, TempoRamp.bars(5))
    }

    /**
     * Kette von Songs mit verschiedenen Tempi: Jeder neue Song wird für den Übergang an den
     * ORIGINAL-Takt des laufenden Songs angeglichen und danach auf 100 % zurückgeführt.
     * Das Tempo darf also nie "wandern" (früherer Fehler: Song lief dauerhaft schneller).
     */
    @Test
    fun chainOfPairs_matchesThenReturnsHome() {
        val bpms = listOf(120f, 124f, 118f, 126f, 63f, 128f, 122f)
        var aTempo = 1f
        for (i in 0 until bpms.size - 1) {
            val a = song(bpms[i])
            val b = song(bpms[i + 1])
            val p = TransitionPlanner.plan(a, 20.0, aTempo, b, PlannerSettings())
            assertTrue("pair ${bpms[i]}→${bpms[i + 1]}", p.matchTempo)
            // Während des Übergangs laufen die Beats gleich schnell (halbes Tempo zählt mit)
            val aBpm = bpms[i] * aTempo
            val bBpm = bpms[i + 1] * p.tempoRatio
            val diff = listOf(bBpm, bBpm * 2, bBpm / 2).minOf { abs(it - aBpm) }
            assertTrue("pair ${bpms[i]}→${bpms[i + 1]}: a=$aBpm b=$bBpm", diff < 0.01f)
            // Danach: Rückführung → B läuft wieder mit Originaltempo, nächster Song plant mit 1.0
            aTempo = TempoRamp.value(p.tempoRatio, 1f)
            assertEquals(1f, aTempo, 0f)
        }
    }
}
