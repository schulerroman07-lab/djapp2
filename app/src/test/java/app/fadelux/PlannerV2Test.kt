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
        fadeBars: Int = 0, key: Int = 21, gridDev: ((Int) -> Int)? = null, silentBars: Int = 0,
    ): TrackStructure {
        val beatSec = 60.0 / bpm
        val beats = bars * 4
        val f = ByteArray(beats * 5)
        for (i in 0 until beats) {
            val bar = i / 4
            val loud = bar >= intro && bar < bars - outro
            var e = if (loud) 0.9f else 0.45f
            if (fadeBars > 0 && bar >= bars - fadeBars) e = 0.9f * (bars - bar).toFloat() / (fadeBars + 1)
            if (bar < silentBars) e = 0.02f
            f[i * 5] = (e * 255).toInt().toByte()
            f[i * 5 + 1] = ((if (i % 4 == 0) 0.95f else 0.6f) * 255).toInt().toByte()
            f[i * 5 + 2] = 80; f[i * 5 + 3] = 100
        }
        val dur = beats * beatSec + 0.6
        val v = ByteArray((dur / 0.25).toInt()) { k -> if (vocal.any { (k * 0.25 + 0.125) in it }) 230.toByte() else 10 }
        val g = gridDev?.let { fn -> ByteArray(beats) { fn(it).toByte() } } ?: ByteArray(0)
        val d = AnalysisData(bpm, 0.3, 0, conf, conf >= 0.6f, f, v, dur, g)
        return StructureAnalyzer.analyze(d, beats * beatSec + 0.3, family)!!.copy(musicalKey = key)
    }

    private val pop = PlannerSettings()

    private fun endOf(p: app.fadelux.automix.TransitionPlan, a: TrackStructure, b: TrackStructure): Double =
        p.mixSec + if (p.lengthSec > 0) p.lengthSec else p.lengthBars * b.barSec / p.tempoRatio

    @Test
    fun vocalSegments_fromProbability() {
        val s = song(vocal = listOf(20.0..60.0, 60.6..90.0, 120.0..140.0))
        assertTrue(s.vocalKnown)
        assertEquals("kurze Lücke geschlossen", 2, s.vocalSegments.size)
        assertEquals(140.0, s.lastVocalEnd, 0.3)
        assertTrue(s.vocalBetween(135.0, 136.0))
        assertFalse(s.vocalBetween(95.0, 115.0))
    }

    @Test
    fun vocalNearTheEnd_countsUntilTheEnd() {
        // Erkennung verpasst leise Reste am Schluss: Gesang < 4 s vor dem Ende gilt bis zum Ende …
        val pop = song(vocal = listOf(20.0..183.0))                          // Ende ca. 186 s
        assertEquals(pop.soundEnd, pop.lastVocalEnd, 0.01)
        // … weiter vor dem Ende bleibt der Gesang, wie er ist – der instrumentale Schluss bleibt nutzbar
        assertEquals(170.0, song(vocal = listOf(20.0..170.0)).lastVocalEnd, 0.3)
        assertEquals(170.0, song(vocal = listOf(20.0..170.0), family = GenreFamily.TECHNO).lastVocalEnd, 0.3)
    }

    @Test
    fun pop_neverBeforeLastVocal_andLate() {
        val a = song(vocal = listOf(10.0..120.0, 130.0..150.0))            // 96 Takte à 1,94 s = 186 s
        val b = song(bpm = 126f, vocal = listOf(20.0..100.0))
        val p = TransitionPlanner.plan(a, 30.0, 1f, b, pop)
        assertTrue("Start ${p.mixSec} nach dem letzten Gesang", p.mixSec >= 150.0 + TransitionPlanner.RELEASE_MARGIN - 1e-6)
        assertTrue("Pop: einfacher Typ (${p.type})", p.type in setOf(TransitionType.BLEND, TransitionType.FADE, TransitionType.CUT))
        assertTrue("fertig vor dem Ende", endOf(p, a, b) <= a.soundEnd + 0.01)
        assertTrue("so spät wie möglich: in den letzten 20 s (${p.mixSec} von ${a.soundEnd})", a.soundEnd - p.mixSec <= 20.0)
        assertFalse("A singt nach dem Start nicht mehr", a.vocalBetween(p.mixSec, a.soundEnd))
    }

    @Test
    fun pop_instrumentalEnding_usedForTheTransition() {
        // Gesang bis 16 s vor dem Ende: der instrumentale Schluss wird genutzt (nicht erst ganz am Ende gemischt)
        val a = song(vocal = listOf(10.0..120.0, 130.0..170.0))
        val b = song(bpm = 126f, vocal = listOf(20.0..100.0))
        val p = TransitionPlanner.plan(a, 30.0, 1f, b, pop)
        assertTrue("Start ${p.mixSec} nach dem Gesang", p.mixSec >= 170.0 + TransitionPlanner.RELEASE_MARGIN - 1e-6)
        assertTrue("Start ${p.mixSec} im instrumentalen Schluss, vor dem Ende (${a.soundEnd})", p.mixSec <= a.soundEnd - 2 * a.barSec)
        assertFalse("A singt nach dem Start nicht mehr", a.vocalBetween(p.mixSec, a.soundEnd))
        assertTrue(p.type != TransitionType.FADE)
    }

    @Test
    fun vocalsUntilTheEnd_echoOnTheLastWord() {
        // A singt bis zum Schluss: kein Fade, sondern das letzte Wort klingt im Echo aus, B setzt auf der 1 ein
        val a = song(vocal = listOf(10.0..185.5))
        val b = song(bpm = 124f)
        val p = TransitionPlanner.plan(a, 30.0, 1f, b, pop)
        assertTrue("Start ${p.mixSec} nach dem Gesang", p.mixSec >= 185.5 - 1e-6)
        assertEquals(p.reason, TransitionType.ECHO_OUT, p.type)
        // B auf einem Downbeat: hier nach dem 8-Takte-Intro (leise), wo B "voll" wird – nie mitten in einer Phrase
        assertEquals("B auf dem Downbeat nach dem leisen Intro", b.barTime(8), p.bStartSec, 1e-6)
        assertFalse(b.midVocal(p.bStartSec))
        // im Takt von A: Einsatz genau auf einem Beat von A, B im Tempo von A (das Echo bleibt im Takt)
        assertTrue(p.reason, p.matchTempo)
        val k = (p.mixSec - a.firstDownbeat) / a.beatSec
        assertEquals("auf einem Beat von A", Math.round(k).toDouble(), k, 1e-6)
    }

    @Test
    fun unsureBeatGrid_noBeatmatching_echoOut() {
        // Raster von A unsicher: kein Beatmatching, aber trotzdem kein Fade – A klingt mit Echo aus, B setzt auf der 1 ein
        val a = song(conf = 0.3f, vocal = listOf(10.0..150.0))
        val b = song(bpm = 125f)
        val p = TransitionPlanner.plan(a, 30.0, 1f, b, pop)
        assertEquals(p.reason, TransitionType.ECHO_OUT, p.type)
        assertFalse(p.matchTempo)
        assertEquals(1f, p.tempoRatio, 0f)
        assertTrue(p.mixSec >= 150.5)
        assertTrue(endOf(p, a, b) <= a.soundEnd + 2 * b.barSec)
    }

    @Test
    fun gridSteady_localDeviation() {
        val s = song(bars = 32, gridDev = { i -> if (i < 32) 30 else if (i in 64..71) 255 else 6 })
        assertFalse("Intro verschoben", s.gridSteady(s.gridFirstBeat, s.gridFirstBeat + 10.0))
        assertTrue("Hauptteil fest", s.gridSteady(s.gridFirstBeat + 40 * s.beatSec, s.gridFirstBeat + 60 * s.beatSec))
        assertFalse("unbekannt (Break) zählt als unsicher", s.gridSteady(s.gridFirstBeat + 60 * s.beatSec, s.gridFirstBeat + 66 * s.beatSec))
        assertTrue("ohne Angabe wie bisher", song(bars = 32).gridSteady(0.0, 30.0))
    }

    @Test
    fun electronic_gridShiftWhereBStarts_noBeatmatching() {
        val a = song(bars = 160, outro = 48, family = GenreFamily.TECHNO, key = 21, gridDev = { 5 })
        // B verschiebt sein Timing im Intro (die ersten 32 Takte um 30 ms): dort würde B einsetzen
        val b = song(bars = 160, intro = 32, family = GenreFamily.TECHNO, key = 16, gridDev = { i -> if (i < 128) 30 else 5 })
        val p = TransitionPlanner.plan(a, 30.0, 1f, b, pop)
        assertFalse("ohne Beatmatching: ${p.reason}", p.matchTempo)
        assertTrue(p.reason, p.reason.contains("Mix-Stelle"))
        assertTrue("nach dem letzten Gesang, fertig am Ende", p.mixSec + p.lengthSec <= a.soundEnd + 1.0)
        // gleiches Paar mit festem Raster: Blend im Takt wie bisher
        val b2 = song(bars = 160, intro = 32, family = GenreFamily.TECHNO, key = 16, gridDev = { 5 })
        assertTrue(TransitionPlanner.plan(a, 30.0, 1f, b2, pop).matchTempo)
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
    fun pop_shortMusicalTransitions_noFade() {
        // Pop/Schlager/Rock: kurze, musikalische Übergänge (Blend, Bass-Swap, Echo-Out, Filter, Schnitt, Loop) – kein Fade
        val settings = PlannerSettings(style = MixStyle.CLUB)
        for (bpm in listOf(90f, 118f, 124f, 126f, 140f)) for (conf in listOf(0.2f, 0.95f)) {
            val p = TransitionPlanner.plan(song(conf = conf, vocal = listOf(5.0..120.0)), 30.0, 1f, song(bpm = bpm), settings)
            assertTrue("$bpm/$conf → ${p.type}", p.type in setOf(TransitionType.BLEND, TransitionType.BASS_SWAP, TransitionType.ECHO_OUT,
                TransitionType.FILTER, TransitionType.CUT, TransitionType.LOOP))
            assertTrue("$bpm/$conf: höchstens 8 Takte", p.lengthBars <= 8)
        }
    }

    @Test
    fun variety_exitTypesAlternate() {
        // gleich gute Ausstiege wechseln sich ab (Echo-Out, Filter-Sweep, Schnitt) statt immer derselbe
        val a = song(conf = 0.95f, vocal = listOf(5.0..150.0))
        val b = song(bpm = 90f)                                  // Tempo zu verschieden: kein Blend
        val seen = mutableSetOf<TransitionType>()
        val recent = mutableListOf<TransitionType>()
        repeat(6) {
            val p = TransitionPlanner.plan(a, 30.0, 1f, b, pop, recent)
            seen += p.type; recent += p.type
        }
        assertTrue("Arten: $seen", seen.size >= 3)
        assertFalse(TransitionType.FADE in seen)
    }

    @Test
    fun bEntry_skipsNearSilentStart() {
        // B blendet die ersten 2 Takte fast lautlos ein: nach einem Echo-Out wäre das ein Loch – B startet beim 3. Takt
        val b = song(bars = 96, intro = 0, silentBars = 2)
        assertEquals(b.barTime(2), TransitionPlanner.bEntryHit(b), 1e-6)
    }

    @Test
    fun bEntry_skipsLongQuietIntro_neverMidPhrase() {
        // B: 16 Takte leises Intro, danach voll – bei einem Ausstieg (Echo/Schnitt) startet B dort, wo es voll wird
        val b = song(bars = 96, intro = 16, vocal = listOf(60.0..90.0))
        val t = TransitionPlanner.bEntryHit(b)
        assertEquals(b.barTime(16), t, 1e-6)
        // Gesang direkt am Ende des Intros: B startet auf dem Phrasenanfang, nie mitten in der Phrase
        val b2 = song(bars = 96, intro = 16, vocal = listOf(28.0..60.0))
        assertFalse(b2.midVocal(TransitionPlanner.bEntryHit(b2)))
    }

    @Test
    fun fadeOut_mixesInsideTheFade() {
        val a = song(bars = 100, outro = 0, fadeBars = 8, vocal = listOf(5.0..170.0))
        val p = TransitionPlanner.plan(a, 30.0, 1f, song(bpm = 124f), pop)
        // der Übergang reicht in den Fade-out hinein (B kommt, während A leiser wird) und ist am Ende fertig
        val end = p.mixSec + p.lengthBars * a.barSec
        assertTrue("im Fade-out (${p.mixSec}..$end, Fade ab ${a.fadeOutStart})", a.fadeOutStart > 0 && end > a.fadeOutStart && end <= a.soundEnd + 0.5)
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
