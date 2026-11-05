package app.fadelux

import app.fadelux.automix.FlowCurve
import app.fadelux.automix.FlowSettings
import app.fadelux.automix.GenreFamily
import app.fadelux.automix.Genres
import app.fadelux.automix.Keys
import app.fadelux.automix.QueueFlow
import app.fadelux.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Reihenfolge nach Stimmung (Energie-Kurve, Party-Bogen, Genre-Blöcke), Tonarten, Genres. */
class MoodFlowTest {
    private fun t(id: Long, energy: Float, bpm: Float = 120f, genre: String = "", key: Int? = null) =
        Track(id, "", "Song $id", "Artist", 200000, bpm, energy, genre = genre, musicalKey = key)

    private val songs = (1L..20L).map { t(it, it / 21f, 100f + it * 2) }.shuffled(Random(7))

    /** Rangkorrelation (Spearman) zwischen Position und Energie. */
    private fun spearman(list: List<Track>): Double {
        val n = list.size
        val energyRank = list.sortedBy { it.energy }.mapIndexed { i, tr -> tr.id to i }.toMap()
        val d2 = list.mapIndexed { i, tr -> val d = i - energyRank[tr.id]!!; (d * d).toDouble() }.sum()
        return 1 - 6 * d2 / (n * (n * n - 1.0))
    }

    @Test
    fun rising_energyGoesUp() {
        val res = QueueFlow.arrangeByMood(songs, null, emptySet(), FlowSettings(FlowCurve.RISING))
        assertEquals(songs.toSet(), res.toSet())
        assertTrue("spearman=${spearman(res)}", spearman(res) > 0.9)
        assertTrue(res.first().energy!! < 0.2f && res.last().energy!! > 0.8f)
    }

    @Test
    fun arc_peaksLateThenCoolsDown() {
        val res = QueueFlow.arrangeByMood(songs, null, emptySet(), FlowSettings(FlowCurve.ARC))
        val peak = res.indices.maxBy { res[it].energy!! }
        assertTrue("peak at $peak", peak in 10..16)                    // Höhepunkt bei ca. 70 %
        assertTrue(res.first().energy!! < 0.3f)                          // ruhiger Start
        assertTrue(res.last().energy!! < res[peak].energy!! - 0.3f)      // Ausklang
    }

    @Test
    fun genreBlocks_stayTogether_calmBlockFirst() {
        val mixed = (1L..6L).map { t(it, 0.5f + it * 0.02f, 125f, "Schlager") } +
            (7L..12L).map { t(it, 0.85f + it * 0.005f, 140f, "Techno") } +
            (13L..18L).map { t(it, 0.3f + it * 0.01f, 110f, "Rock") }
        val res = QueueFlow.arrangeByMood(mixed.shuffled(Random(3)), null, emptySet(), FlowSettings(FlowCurve.RISING, genreBlocks = true))
        val fams = res.map { Genres.family(it) }
        // jede Familie zusammenhängend
        val changes = fams.zipWithNext().count { (a, b) -> a != b }
        assertEquals(2, changes)
        assertEquals(listOf(GenreFamily.ROCK, GenreFamily.PARTY, GenreFamily.TECHNO), fams.distinct())
    }

    @Test
    fun pinnedSongsStay_and_originalKeepsOrder() {
        val pinned = setOf(songs[3].id, songs[9].id)
        val res = QueueFlow.arrangeByMood(songs, null, pinned, FlowSettings(FlowCurve.RISING))
        assertEquals(songs[3].id, res[3].id)
        assertEquals(songs[9].id, res[9].id)
        assertEquals(songs, QueueFlow.arrangeByMood(songs, null, emptySet(), FlowSettings(FlowCurve.ORIGINAL)))
    }

    @Test
    fun unanalyzedSongs_neverFail() {
        val raw = (1L..10L).map { Track(it, "", "S$it", "A", 1000) }   // noch keine Analyse
        for (c in FlowCurve.entries) for (b in listOf(false, true)) {
            assertEquals(10, QueueFlow.arrangeByMood(raw, null, emptySet(), FlowSettings(c, b)).size)
        }
    }

    @Test
    fun camelot() {
        assertEquals("8B", Keys.label(0))     // C-Dur
        assertEquals("8A", Keys.label(21))    // a-Moll
        assertEquals("9B", Keys.label(7))     // G-Dur
        assertEquals("9A", Keys.label(16))    // e-Moll
        assertEquals(1f, Keys.compatibility(0, 0))
        assertEquals(0.9f, Keys.compatibility(0, 21))     // C ↔ a
        assertEquals(0.85f, Keys.compatibility(0, 7))     // C ↔ G
        assertTrue(Keys.compatibility(0, 6) < 0.3f)       // C ↔ F# reibt
        assertEquals(0.5f, Keys.compatibility(null, 3))
    }

    @Test
    fun genres() {
        assertEquals(GenreFamily.PARTY, Genres.family(t(1, 0.6f, genre = "Deutscher Schlager")))
        assertEquals(GenreFamily.TECHNO, Genres.family(t(1, 0.6f, genre = "Hardstyle")))
        assertEquals(GenreFamily.OLDIES, Genres.family(t(1, 0.6f, genre = "Disco")))
        assertEquals(GenreFamily.ROCK, Genres.family(t(1, 0.6f, genre = "Alternative Rock")))
        assertEquals(GenreFamily.TECHNO, Genres.family(t(1, 0.8f, bpm = 140f)))   // geschätzt
        assertEquals(GenreFamily.CHILL, Genres.family(t(1, 0.2f, bpm = 90f)))
    }
}
