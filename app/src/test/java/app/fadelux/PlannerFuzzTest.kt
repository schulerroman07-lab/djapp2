package app.fadelux

import app.fadelux.automix.MixStyle
import app.fadelux.automix.PlannerSettings
import app.fadelux.automix.StructureAnalyzer
import app.fadelux.automix.TempoRamp
import app.fadelux.automix.TrackStructure
import app.fadelux.automix.TransitionPlanner
import app.fadelux.automix.TransitionType
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Viele zufällige Song-Paare (Tempo, Tonart, Gesang, Breaks, Intro/Outro, Stille, kurze Songs):
 * jede Entscheidung muss gültig sein – und es soll abwechslungsreich gemischt werden.
 * Simuliert ausserdem eine 2-Stunden-Party (Kette von Songs) wie der Auto-DJ sie plant.
 */
class PlannerFuzzTest {

    private fun randomSong(r: Random): TrackStructure {
        val bpm = listOf(90f, 98f, 105f, 118f, 122f, 124f, 126f, 128f, 132f, 140f, 174f).random(r) + r.nextFloat() * 2 - 1
        val bars = r.nextInt(40, 140)
        val intro = r.nextInt(0, minOf(32, bars / 3))
        val outro = r.nextInt(0, minOf(32, bars / 3))
        val silent = if (r.nextInt(4) == 0) r.nextInt(1, 6) else 0
        val vocalStart = r.nextInt(0, bars)
        val vocal = (vocalStart until minOf(bars, vocalStart + r.nextInt(0, 48))).toSet()
        val brk = if (r.nextBoolean()) r.nextInt(intro + 1, maxOf(intro + 2, bars - outro - 4)).let { (it until it + 4).toSet() } else emptySet()
        val beats = bars * 4
        val f = ByteArray(beats * 4)
        for (i in 0 until beats) {
            val bar = i / 4
            val loud = bar >= intro && bar < bars - outro
            var e = if (loud) 0.85f else 0.4f
            var b = if (i % 4 == 0) 0.95f else 0.55f
            var m = if (bar in vocal) 0.9f else 0.3f
            if (bar in brk) { b = 0.05f; e = 0.5f }
            if (bar >= bars - silent) { e = 0f; b = 0f; m = 0f }
            f[i * 4] = (e * 255).toInt().toByte(); f[i * 4 + 1] = (b * 255).toInt().toByte()
            f[i * 4 + 2] = (m * 255).toInt().toByte(); f[i * 4 + 3] = 100
        }
        val dur = beats * 60.0 / bpm + 0.5
        return StructureAnalyzer.analyze(bpm, r.nextFloat(), f, dur)!!
            .copy(musicalKey = if (r.nextInt(5) == 0) -1 else r.nextInt(24), trackEnergy = r.nextFloat())
    }

    @Test
    fun manyRandomPairs_allPlansValid_andVaried() {
        val r = Random(42)
        val counts = HashMap<TransitionType, Int>()
        var early = 0
        repeat(3000) { n ->
            val a = randomSong(r)
            val b = randomSong(r)
            val style = MixStyle.entries.random(r)
            val settings = PlannerSettings(style = style)
            val pos = a.barTime(r.nextInt(0, maxOf(1, a.barCount / 2)))
            val recent = List(r.nextInt(0, 3)) { TransitionType.entries.random(r) }
            for (mode in 0..2) {   // normal, jetzt, Weiter/Zurück
                val p = try {
                    TransitionPlanner.plan(a, pos, 1f, b, settings, recent, now = mode == 1, quick = mode == 2)
                } catch (e: Exception) {
                    fail("Paar $n Modus $mode: ${e.javaClass.simpleName} ${e.message}"); return
                }
                val ctx = "Paar $n Modus $mode: $p"
                assertTrue(ctx, p.lengthBars >= 1)
                assertTrue(ctx, p.mixBar >= a.barAt(pos))
                assertTrue(ctx, p.mixSec >= pos && p.mixSec < a.duration)
                assertTrue(ctx, p.bStartSec >= 0 && p.bStartSec < b.duration)
                assertTrue(ctx, abs(p.tempoRatio - 1f) <= 0.101f)
                if (!p.matchTempo) assertTrue(ctx, p.type !in TransitionPlanner.blendTypes)
                if (mode == 0) {
                    // nie mitten im Song: Start in den letzten 32 Takten (bzw. Länge), fertig vor dem Ende
                    val lateStart = a.barAt(pos) + 2 > a.endBar - p.lengthBars
                    if (!lateStart) {
                        assertTrue(ctx + " end=${a.endBar}", p.mixBar >= a.endBar - maxOf(32, 40) )
                        if (p.type in TransitionPlanner.blendTypes) assertTrue(ctx + " end=${a.endBar}", p.mixBar + p.lengthBars <= a.endBar)
                    }
                    counts[p.type] = (counts[p.type] ?: 0) + 1
                    TransitionPlanner.earlyBar(a, pos, 1f, b, p.mixBar)?.let { eb ->
                        early++
                        assertTrue(ctx, eb < p.mixBar && eb >= a.barAt(pos))
                    }
                }
            }
        }
        println("Übergangs-Typen bei 3000 zufälligen Paaren: " + counts.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" })
        println("Früh-Übergang möglich bei $early Paaren")
    }

    /**
     * Realistische Party (nach Stimmung geordnet → ähnliche Tempi folgen aufeinander):
     * alle Übergangs-Typen kommen vor, keiner dominiert, nie zweimal derselbe direkt hintereinander.
     */
    @Test
    fun moodOrderedParty_isVaried() {
        val r = Random(11)
        val types = mutableListOf<TransitionType>()
        var a = randomSong(r)
        repeat(80) {
            var b = randomSong(r)
            // Tempo nahe am vorherigen (wie nach der Stimmungs-Ordnung), ab und zu ein grösserer Sprung
            if (r.nextInt(5) != 0) b = b.copy(bpm = a.bpm * (1f + (r.nextFloat() - 0.5f) * 0.06f))
            val p = TransitionPlanner.plan(a, 0.0, 1f, b, PlannerSettings(), types.takeLast(10))
            types += p.type
            a = b
        }
        val counts = types.groupingBy { it }.eachCount()
        println("Party mit 80 Übergängen: " + counts.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" })
        assertTrue("Wiederholung", types.zipWithNext().none { (x, y) -> x == y })
        val core = TransitionType.entries - TransitionType.LONG_BLEND   // langer Blend nur bei passender Tonart + viel Platz
        assertTrue("fehlende Typen: $counts", core.all { (counts[it] ?: 0) > 0 })
        assertTrue("ein Typ dominiert: $counts", counts.values.max() <= 80 * 0.4)
    }

    /** 2-Stunden-Party: Kette von Songs, Tempo kehrt nach jedem Übergang zurück, Typen wechseln. */
    @Test
    fun twoHourParty_chain() {
        val r = Random(7)
        var a = randomSong(r)
        var t = 0.0
        val types = mutableListOf<TransitionType>()
        var songs = 0
        while (t < 2 * 3600) {
            val b = randomSong(r)
            val p = TransitionPlanner.plan(a, 0.0, 1f, b, PlannerSettings(), types.takeLast(10))
            if (types.isNotEmpty()) assertTrue("gleicher Typ zweimal: ${types.last()} → ${p.type}", p.type != types.last() || p.type == TransitionType.CUT && !p.matchTempo)
            types += p.type
            // Tempo nach dem Übergang zurück auf 100 %
            assertTrue(TempoRamp.value(p.tempoRatio, 1f) == 1f)
            t += p.mixSec - p.bStartSec + 0.0
            t += 0.01
            a = b
            songs++
        }
        val repeats = types.zipWithNext().count { (x, y) -> x == y }
        println("2-h-Party: $songs Songs, Wiederholungen direkt hintereinander: $repeats, Typen: " +
            types.groupingBy { it }.eachCount().entries.joinToString { "${it.key}=${it.value}" })
        assertTrue("zu viele Wiederholungen: $repeats", repeats <= songs / 10)
    }
}
