package app.fadelux

import app.fadelux.automix.MixStyle
import app.fadelux.automix.PlannerSettings
import app.fadelux.automix.StructureAnalyzer
import app.fadelux.automix.TrackStructure
import app.fadelux.automix.TransitionPlanner
import app.fadelux.automix.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests für Struktur-Analyse und Übergangs-Planung (ohne Handy, mit künstlichen Titeln). */
class TransitionPlannerTest {

    /**
     * Künstlicher Titel: introBars leise, dann laut, outroBars leise.
     * Bass betont auf Beat "downbeatOffset" jedes Takts. vocalBars = Takte mit Gesang.
     */
    private fun features(
        bars: Int, introBars: Int, outroBars: Int,
        downbeatOffset: Int = 0, vocalBars: Set<Int> = emptySet(), breakBars: Set<Int> = emptySet(),
    ): ByteArray {
        val beats = bars * 4 + downbeatOffset
        val out = ByteArray(beats * 4)
        for (i in 0 until beats) {
            val bar = (i - downbeatOffset).coerceAtLeast(0) / 4
            val beatInBar = Math.floorMod(i - downbeatOffset, 4)
            val loud = bar >= introBars && bar < bars - outroBars
            var e = if (loud) 0.9f else 0.4f
            var b = if (beatInBar == 0) 0.95f else 0.6f
            if (!loud) b *= 0.8f
            if (bar in breakBars) { b = 0.05f; e = 0.5f }
            val m = if (bar in vocalBars) 0.9f else 0.3f
            out[i * 4] = (e * 255).toInt().toByte()
            out[i * 4 + 1] = (b * 255).toInt().toByte()
            out[i * 4 + 2] = (m * 255).toInt().toByte()
            out[i * 4 + 3] = (0.5f * 255).toInt().toByte()
        }
        return out
    }

    private fun track(
        bpm: Float = 124f, bars: Int = 96, intro: Int = 16, outro: Int = 16,
        vocal: Set<Int> = emptySet(), breaks: Set<Int> = emptySet(), offset: Int = 0,
    ): TrackStructure {
        val beatSec = 60.0 / bpm
        return StructureAnalyzer.analyze(bpm, 0.5f, features(bars, intro, outro, offset, vocal, breaks), bars * 4 * beatSec + 1)!!
    }

    @Test
    fun structure_findsDownbeatIntroOutroAndBreak() {
        val s = track(offset = 1, breaks = setOf(40, 41, 42))
        assertEquals(0.5 + 60.0 / 124 * 1, s.firstDownbeat, 0.001)   // Downbeat ist Beat 2 → Versatz 1
        assertTrue("intro=${s.introEndBar}", s.introEndBar in 15..17)
        assertTrue("outro=${s.outroStartBar}", s.outroStartBar in 79..81)
        assertTrue("breaks=${s.breaks}", s.breaks.any { 41 in it })
        assertEquals(32, s.phraseLevel(64))
        assertEquals(8, s.phraseLevel(24))
    }

    @Test
    fun structure_detectsVocals() {
        val s = track(vocal = (30..45).toSet())
        assertTrue(s.vocalIn(32, 40))
        assertFalse(s.vocalIn(60, 70))
    }

    @Test
    fun plan_mixesOnPhraseNearOutro_andMatchesTempo() {
        val a = track(bpm = 124f)
        val b = track(bpm = 126f)
        val p = TransitionPlanner.plan(a, aPos = 30.0, aTempo = 1f, b = b, settings = PlannerSettings())
        assertEquals(0, p.mixBar % 8)                     // Phrasengrenze
        assertTrue("mixBar=${p.mixBar}", p.mixBar in 72..80)
        assertTrue(p.matchTempo)
        assertEquals(124f / 126f, p.tempoRatio, 0.001f)
        assertTrue(p.mixBar + p.lengthBars <= a.barCount)
    }

    @Test
    fun plan_bigTempoGap_noBlend() {
        val a = track(bpm = 124f)
        val b = track(bpm = 95f)
        val p = TransitionPlanner.plan(a, 30.0, 1f, b, PlannerSettings())
        assertFalse(p.matchTempo)
        assertTrue(p.type !in TransitionPlanner.blendTypes)
        assertEquals(1f, p.tempoRatio)
    }

    @Test
    fun plan_neverTwoVocalsOverlapping() {
        // A singt im ganzen Outro, B singt im ganzen Intro → kein Blend möglich
        val a = track(vocal = (60..95).toSet())
        val b = track(vocal = (0..30).toSet())
        val p = TransitionPlanner.plan(a, 30.0, 1f, b, PlannerSettings())
        assertTrue("type=${p.type}", p.type !in TransitionPlanner.blendTypes)
    }

    @Test
    fun plan_variety_avoidsSameTypeTwice() {
        val a = track()
        val b = track(bpm = 125f)
        val first = TransitionPlanner.plan(a, 30.0, 1f, b, PlannerSettings())
        val second = TransitionPlanner.plan(a, 30.0, 1f, b, PlannerSettings(), recent = listOf(first.type))
        assertNotEquals(first.type, second.type)
    }

    @Test
    fun plan_respectsDisabledTypesAndForcedType() {
        val a = track()
        val b = track(bpm = 125f)
        val only = PlannerSettings(enabled = setOf(TransitionType.FILTER))
        assertEquals(TransitionType.FILTER, TransitionPlanner.plan(a, 30.0, 1f, b, only).type)
        val forced = TransitionPlanner.plan(a, 30.0, 1f, b, PlannerSettings(), forced = TransitionType.ECHO_OUT)
        assertEquals(TransitionType.ECHO_OUT, forced.type)
    }

    @Test
    fun plan_now_startsSoon() {
        val a = track()
        val b = track()
        val p = TransitionPlanner.plan(a, aPos = 30.0, aTempo = 1f, b = b, settings = PlannerSettings(), now = true)
        val currentBar = a.barAt(30.0)
        assertTrue("mixBar=${p.mixBar} current=$currentBar", p.mixBar in (currentBar + 2)..(currentBar + 6))
        assertEquals(0, p.mixBar % 4)
    }

    @Test
    fun plan_styleChangesLength() {
        val a = track(bars = 128, intro = 32, outro = 32)
        val b = track(bars = 128, intro = 32, outro = 32)
        val calm = TransitionPlanner.plan(a, 20.0, 1f, b, PlannerSettings(enabled = setOf(TransitionType.BASS_SWAP), style = MixStyle.CALM))
        val club = TransitionPlanner.plan(a, 20.0, 1f, b, PlannerSettings(enabled = setOf(TransitionType.BASS_SWAP), style = MixStyle.CLUB))
        assertTrue(calm.lengthBars > club.lengthBars)
        assertNotNull(calm)
    }
}
