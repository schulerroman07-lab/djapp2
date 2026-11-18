package app.fadelux

import app.fadelux.automix.GenreFamily
import app.fadelux.automix.MixStyle
import app.fadelux.automix.PlannerSettings
import app.fadelux.automix.TempoRamp
import app.fadelux.automix.TrackAnalysis
import app.fadelux.automix.TrackStructure
import app.fadelux.automix.TransitionPlanner
import app.fadelux.automix.TransitionType
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Viele zufällige Song-Paare (Tempo, Genre, Gesang bis zum Ende, unsicheres Raster, Fade-outs, kurze Songs):
 * jede Entscheidung muss die Regeln einhalten – und eine 2-Stunden-Party als Kette.
 */
class PlannerFuzzTest {
    private val gen = PlannerV2Test()

    private fun randomSong(r: Random): TrackStructure {
        val bpm = listOf(90f, 98f, 105f, 118f, 122f, 124f, 126f, 128f, 132f, 140f).random(r) + r.nextFloat() * 2 - 1
        val bars = r.nextInt(48, 160)
        val dur = bars * 4 * 60.0 / bpm
        val fam = listOf(GenreFamily.POP, GenreFamily.PARTY, GenreFamily.ROCK, GenreFamily.HOUSE, GenreFamily.TECHNO, null).random(r)
        val electronic = fam == GenreFamily.HOUSE || fam == GenreFamily.TECHNO
        val vocal = mutableListOf<ClosedFloatingPointRange<Double>>()
        if (!electronic || r.nextBoolean()) {
            var t = r.nextDouble(2.0, 30.0)
            val stop = if (r.nextInt(4) == 0) dur - r.nextDouble(0.0, 2.0) else dur * r.nextDouble(0.5, 0.97)
            while (t < stop - 2) { val e = minOf(stop, t + r.nextDouble(5.0, 40.0)); vocal += t..e; t = e + r.nextDouble(1.5, 20.0) }
        }
        return gen.song(
            bpm = bpm, bars = bars, intro = r.nextInt(0, minOf(32, bars / 3)), outro = r.nextInt(0, minOf(48, bars / 3)),
            conf = listOf(0.2f, 0.5f, 0.7f, 0.9f, 0.98f).random(r), vocal = vocal, family = fam,
            fadeBars = if (r.nextInt(4) == 0) r.nextInt(4, 12) else 0, key = r.nextInt(24),
        )
    }

    private fun check(a: TrackStructure, b: TrackStructure, pos: Double, aTempo: Float, style: MixStyle, tag: String,
                      recent: List<TransitionType> = emptyList()): TransitionType {
        val p = TransitionPlanner.plan(a, pos, aTempo, b, PlannerSettings(style = style), recent)
        val end = p.mixSec + if (p.lengthSec > 0) p.lengthSec else p.lengthBars * a.barSec
        if (p.mixSec < pos) fail("$tag: Start in der Vergangenheit: $p")
        // A hört beim Einsatz auf (Schnitt, Echo, Hall, Filter-Sweep): frühestens am Ende des Gesangs – singt A bis zum
        // Schluss, klingt das letzte Wort im Echo aus (erlaubt). Spielt A weiter: mit Abstand nach dem Gesang.
        val aStops = p.type in setOf(TransitionType.CUT, TransitionType.ECHO_OUT, TransitionType.REVERB_WASH) ||
            (p.type == TransitionType.FILTER && p.lengthBars <= 2)
        val earliest = if (aStops) minOf(a.lastVocalEnd + TransitionPlanner.RELEASE_MARGIN, maxOf(a.soundEnd, pos + 2.0))
            else a.lastVocalEnd + TransitionPlanner.RELEASE_MARGIN
        if (a.lastVocalEnd > 0 && p.mixSec < earliest - 1e-6 && p.mixSec >= pos + 4.0 + a.barSec * 2)
            fail("$tag: Start vor dem Ende des letzten Gesangs (${a.lastVocalEnd}): $p")
        if (a.vocalBetween(p.mixSec + 0.01, a.soundEnd) && p.mixSec >= a.lastVocalEnd) fail("$tag: Gesang in A während des Übergangs: $p")
        if (end > a.soundEnd + 2 * a.barSec + 8.0 && a.soundEnd > pos + 30) fail("$tag: endet nach dem Song (${a.soundEnd}): $p")
        if (p.matchTempo) {
            if (!(a.beatReliable && b.beatReliable)) fail("$tag: Beatmatching mit unsicherem Raster: $p")
            val ratio = TrackAnalysis.tempoRatio(a.bpm * aTempo, b.bpm)
            if (ratio == null || abs(ratio - 1f) > 0.08f) fail("$tag: Tempo zu weit für Beatmatching: $p")
        }
        val elec = TransitionPlanner.electronic(a) && TransitionPlanner.electronic(b)
        // Pop/Schlager/Rock: kurze, musikalische Übergänge (kein langer Blend, kein Hall), höchstens 8 Takte
        if (!elec && (p.type == TransitionType.LONG_BLEND || (p.lengthSec <= 0 && p.lengthBars > 8)))
            fail("$tag: zu langer/zu starker Übergang bei Pop/Schlager/Rock: $p")
        // harte Regel: nie zwei Gesänge voll übereinander. Spielt A weiter, singt B frühestens in der zweiten Hälfte
        // (A ist dann deutlich leiser und instrumental); hört A auf, darf B sofort singen. Nie mitten in einer Phrase.
        val lenB = if (p.lengthSec > 0) p.lengthSec else p.lengthBars * b.barSec
        if (!aStops && lenB > 0.3 && b.vocalBetween(p.bStartSec, p.bStartSec + lenB * 0.5))
            fail("$tag: B singt schon in der ersten Hälfte der Überblendung (B-Gesang ab ${b.nextVocalStart(p.bStartSec)}): $p")
        if (b.midVocal(p.bStartSec)) fail("$tag: B setzt mitten in einer Gesangsphrase ein: $p")
        if (p.reason.isBlank()) fail("$tag: ohne Begründung")
        return p.type
    }

    @Test
    fun manyRandomPairs_followTheRules() {
        val r = Random(42)
        val counts = HashMap<TransitionType, Int>()
        val recent = ArrayList<TransitionType>()           // wie in der App: die letzten Übergänge (für Abwechslung)
        repeat(3000) { n ->
            val a = randomSong(r)
            val b = randomSong(r)
            val pos = r.nextDouble(5.0, maxOf(6.0, a.duration * 0.6))
            val t = check(a, b, pos, 1f, MixStyle.entries.random(r), "Paar $n", recent.takeLast(10))
            counts.merge(t, 1, Int::plus)
            recent += t
        }
        println("Typen: $counts")
        // Fade nur als letzter Ausweg (Ziel: höchstens 20 % Fades), echte Übergänge in allen Arten
        assertTrue("Fades höchstens 20 %: $counts", (counts[TransitionType.FADE] ?: 0) <= 3000 * 0.2)
        for (t in listOf(TransitionType.BLEND, TransitionType.ECHO_OUT, TransitionType.CUT, TransitionType.FILTER))
            assertTrue("$t kommt vor: $counts", (counts[t] ?: 0) > 0)
        assertTrue("Blends bei Elektronik", (counts[TransitionType.LONG_BLEND] ?: 0) + (counts[TransitionType.BASS_SWAP] ?: 0) > 0)
    }

    @Test
    fun twoHourParty_chain() {
        val r = Random(7)
        var a = randomSong(r)
        var aTempo = 1f
        var t = 0.0
        var n = 0
        while (t < 7200) {
            val b = randomSong(r)
            val type = check(a, b, 20.0, aTempo, MixStyle.BALANCED, "Kette $n")
            val p = TransitionPlanner.plan(a, 20.0, aTempo, b, PlannerSettings())
            t += p.mixSec
            aTempo = TempoRamp.value(p.tempoRatio, 1f)
            a = b
            n++
            if (type == TransitionType.CUT) Unit
        }
        assertTrue(n > 20)
    }
}
