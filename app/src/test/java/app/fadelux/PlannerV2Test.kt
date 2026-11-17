package app.fadelux

import app.fadelux.automix.AnalysisData
import app.fadelux.automix.GenreFamily
import app.fadelux.automix.MixStyle
import app.fadelux.automix.PlannerSettings
import app.fadelux.automix.StructureAnalyzer
import app.fadelux.automix.TrackStructure
import app.fadelux.automix.TransitionPlanner
import app.fadelux.automix.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Planer Version 2 (Regeln aus dem Test mit echter Musik): nie vor dem Ende des letzten Gesangs,
 * so spät wie möglich, Beatmatching nur bei sicherem Raster, Genre bestimmt die Art.
 */
class PlannerV2Test {

    /** Künstlicher Song über den Analyse-Weg der App (Version 2). vocal = Gesang in Sekunden. */
    fun song(
        bpm: Float = 124f, bars: Int = 96, intro: Int = 8, outro: Int = 8, conf: Float = 0.95f,
        vocal: List<ClosedFloatingPointRange<Double>> = emptyList(), family: GenreFamily? = GenreFamily.POP,
        fadeBars: Int = 0, key: Int = 21,
    ): TrackStructure {
        val beatSec = 60.0 / bpm
        val beats = bars * 4
        val f = ByteArray(beats * 5)
        for (i in 0 until beats) {
            val bar = i / 4
            val loud = bar >= intro && bar < bars - outro
            var e = if (loud) 0.9f else 0.45f
            if (fadeBars > 0 && bar >= bars - fadeBars) e = 0.9f * (bars - bar).toFloat() / (fadeBars + 1)
            f[i * 5] = (e * 255).toInt().toByte()
            f[i * 5 + 1] = ((if (i % 4 == 0) 0.95f else 0.6f) * 255).toInt().toByte()
            f[i * 5 + 2] = 80; f[i * 5 + 3] = 100
        }
        val dur = beats * beatSec + 0.6
        val v = ByteArray((dur / 0.25).toInt()) { k -> if (vocal.any { (k * 0.25 + 0.125) in it }) 230.toByte() else 10 }
        val d = AnalysisData(bpm, 0.3, 0, conf, conf >= 0.6f, f, v, dur)
        return StructureAnalyzer.analyze(d, beats * beatSec + 0.3, family)!!.copy(musicalKey = key)
    }

    private val pop = PlannerSettings()

    private fun endOf(p: app.fadelux.automix.TransitionPlan, a: TrackStructure, b: TrackStructure): Double =
        p.mixSec + if (p.lengthSec > 0) p.lengthSec else p.lengthBars * b.barSec / p.tempoRatio

    @Test
    fun vocalSegments_fromProbability() {
        val s = song(vocal = listOf(20.0..60.0, 60.6..90.0, 150.0..170.0))
        assertTrue(s.vocalKnown)
        assertEquals("kurze Lücke geschlossen", 2, s.vocalSegments.size)
        assertEquals(170.0, s.lastVocalEnd, 0.3)
        assertTrue(s.vocalBetween(165.0, 166.0))
        assertFalse(s.vocalBetween(100.0, 140.0))
    }

    @Test
    fun pop_neverBeforeLastVocal_andLate() {
        val a = song(vocal = listOf(10.0..120.0, 130.0..170.0))            // 96 Takte à 1,94 s = 186 s
        val b = song(bpm = 126f, vocal = listOf(20.0..100.0))
        val p = TransitionPlanner.plan(a, 30.0, 1f, b, pop)
        assertTrue("Start ${p.mixSec} nach dem letzten Gesang", p.mixSec >= 170.0 + TransitionPlanner.VOCAL_MARGIN - 1e-6)
        assertTrue("Pop: einfacher Typ (${p.type})", p.type in setOf(TransitionType.BLEND, TransitionType.FADE, TransitionType.CUT))
        assertTrue("fertig vor dem Ende", endOf(p, a, b) <= a.soundEnd + 0.01)
        assertTrue("so spät wie möglich: in den letzten 20 s (${p.mixSec} von ${a.soundEnd})", a.soundEnd - p.mixSec <= 20.0)
        assertFalse("A singt nach dem Start nicht mehr", a.vocalBetween(p.mixSec, a.soundEnd))
    }

    @Test
    fun vocalsUntilTheEnd_cutOrFadeRightAfter() {
        val a = song(vocal = listOf(10.0..185.5))
        val b = song(bpm = 124f)
        val p = TransitionPlanner.plan(a, 30.0, 1f, b, pop)
        assertTrue("Start ${p.mixSec}", p.mixSec >= 185.5 + TransitionPlanner.VOCAL_MARGIN - 1e-6)
        assertTrue(p.type == TransitionType.CUT || p.type == TransitionType.FADE)
    }

    @Test
    fun unsureBeatGrid_noBeatmatching_shortFade() {
        val a = song(conf = 0.3f, vocal = listOf(10.0..150.0))
        val b = song(bpm = 125f)
        val p = TransitionPlanner.plan(a, 30.0, 1f, b, pop)
        assertEquals(TransitionType.FADE, p.type)
        assertFalse(p.matchTempo)
        assertEquals(1f, p.tempoRatio, 0f)
        assertTrue(p.lengthSec in 1.0..8.0)
        assertTrue(p.mixSec >= 150.5)
        assertTrue(endOf(p, a, b) <= a.soundEnd + 0.6)
    }

    @Test
    fun electronic_longOutro_blendOnPhrase() {
        val a = song(bars = 160, outro = 48, family = GenreFamily.TECHNO, key = 21)
        val b = song(bars = 160, intro = 32, family = GenreFamily.TECHNO, key = 16)     // 8A → 9A passt
        val p = TransitionPlanner.plan(a, 30.0, 1f, b, pop)
        assertTrue(p.type == TransitionType.LONG_BLEND || p.type == TransitionType.BASS_SWAP)
        assertTrue(p.matchTempo)
        assertEquals("auf der Phrase", 0, p.mixBar % 8)
        assertTrue("fertig vor dem Ende", p.mixBar + p.lengthBars <= a.endBar)
        assertTrue("spät, im Outro: Takt ${p.mixBar}", p.mixBar >= a.endBar - 32)
    }

    @Test
    fun electronic_bigTempoGap_echoInTimeOfA() {
        val a = song(bars = 128, outro = 32, family = GenreFamily.TECHNO)
        val b = song(bpm = 140f, bars = 128, family = GenreFamily.TECHNO)
        val p = TransitionPlanner.plan(a, 30.0, 1f, b, pop)
        assertEquals(TransitionType.ECHO_OUT, p.type)
        assertFalse(p.matchTempo)
    }

    @Test
    fun pop_neverUsesEffects() {
        val settings = PlannerSettings(style = MixStyle.CLUB)
        for (bpm in listOf(90f, 118f, 124f, 126f, 140f)) for (conf in listOf(0.2f, 0.95f)) {
            val p = TransitionPlanner.plan(song(conf = conf, vocal = listOf(5.0..120.0)), 30.0, 1f, song(bpm = bpm), settings)
            assertTrue("$bpm/$conf → ${p.type}", p.type in setOf(TransitionType.BLEND, TransitionType.FADE, TransitionType.CUT))
        }
    }

    @Test
    fun fadeOut_mixesInsideTheFade() {
        val a = song(bars = 100, outro = 0, fadeBars = 8, vocal = listOf(5.0..170.0))
        val p = TransitionPlanner.plan(a, 30.0, 1f, song(bpm = 124f), pop)
        assertTrue("im Fade-out (${p.mixSec}, Fade ab ${a.fadeOutStart})", a.fadeOutStart > 0 && p.mixSec >= a.fadeOutStart)
    }

    @Test
    fun disabledTypes_respected() {
        val a = song(vocal = listOf(5.0..150.0))
        val b = song(bpm = 124f)
        val noBlend = TransitionPlanner.plan(a, 30.0, 1f, b, PlannerSettings(enabled = TransitionType.entries.toSet() - TransitionType.BLEND))
        assertTrue(noBlend.type != TransitionType.BLEND)
        val onlyCut = TransitionPlanner.plan(a, 30.0, 1f, b, PlannerSettings(enabled = setOf(TransitionType.CUT)))
        assertEquals(TransitionType.CUT, onlyCut.type)
        assertTrue(onlyCut.mixSec >= 150.5)
    }

    @Test
    fun earlyTransition_onlyWithLongInstrumentalTail_highConfidence() {
        val a = song(bars = 200, outro = 64, family = GenreFamily.HOUSE, vocal = listOf(20.0..120.0), key = 21)
        val b = song(bars = 160, intro = 32, family = GenreFamily.HOUSE, key = 16)
        val normal = TransitionPlanner.plan(a, 30.0, 1f, b, pop)
        val early = TransitionPlanner.earlyBar(a, 30.0, 1f, b, normal.mixBar)
        assertNotNull("Früh-Übergang möglich", early)
        assertTrue("nach dem Gesang", a.barTime(early!!) >= 120.0)
        assertTrue(early < normal.mixBar - 16)
        val forced = TransitionPlanner.plan(a, 30.0, 1f, b, pop, atBar = early)
        assertEquals(early, forced.mixBar)
        // Gesang bis kurz vor Schluss → kein Früh-Übergang
        val sung = song(bars = 200, family = GenreFamily.HOUSE, vocal = listOf(20.0..380.0), key = 21)
        assertNull(TransitionPlanner.earlyBar(sung, 30.0, 1f, b, 190))
        // unsicheres Raster → keiner
        assertNull(TransitionPlanner.earlyBar(a.copy(confidence = 0.7f), 30.0, 1f, b, normal.mixBar))
    }
}
