package app.fadelux

import app.fadelux.automix.QueueFlow
import app.fadelux.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Automatische Sortierung der Warteschlange (Genre, BPM, Energie) und Smart Shuffle. */
class QueueFlowTest {
    private fun t(id: Long, bpm: Float, energy: Float, genre: String = "") =
        Track(id, "", "Song $id", "Artist", 200000, bpm, energy, genre = genre)

    @Test
    fun insert_placesSongBetweenSimilarOnes() {
        val q = listOf(t(1, 100f, 0.4f, "Pop"), t(2, 102f, 0.45f, "Pop"), t(3, 126f, 0.8f, "House"), t(4, 128f, 0.85f, "House"))
        // Ein House-Track mit 127 BPM gehört zwischen 3 und 4 (oder ans Ende), nicht zu den Pop-Songs
        val res = QueueFlow.insertAll(q, null, listOf(t(5, 127f, 0.82f, "House")))
        val idx = res.indexOfFirst { it.id == 5L }
        assertTrue("index=$idx ${res.map { it.id }}", idx >= 3)
        // Ein ruhiger Pop-Song bei den Pop-Songs
        val res2 = QueueFlow.insertAll(q, null, listOf(t(6, 101f, 0.42f, "Pop")))
        assertTrue(res2.map { it.id }.toString(), res2.indexOfFirst { it.id == 6L } <= 2)
    }

    @Test
    fun insert_neverReordersExistingSongs() {
        val q = listOf(t(1, 128f, 0.9f), t(2, 90f, 0.2f), t(3, 140f, 0.5f))   // absichtlich "schlecht"
        val res = QueueFlow.insertAll(q, null, listOf(t(4, 120f, 0.6f), t(5, 95f, 0.3f)))
        assertEquals(listOf(1L, 2L, 3L), res.map { it.id }.filter { it <= 3 })
        assertEquals(5, res.size)
    }

    @Test
    fun insert_respectsPinnedHead() {
        val q = listOf(t(1, 128f, 0.9f), t(2, 128f, 0.9f))
        val prev = t(9, 100f, 0.3f)
        val res = QueueFlow.insertAll(q, prev, listOf(t(3, 100f, 0.3f)), headPinned = true)
        assertEquals(1L, res.first().id)          // "Als Nächstes" bleibt vorne
    }

    @Test
    fun noFit_stillBuildsBestOrder() {
        // Völlig unterschiedliche Songs: es gibt keine "passt nicht"-Meldung, nur die beste Reihenfolge
        val songs = listOf(t(1, 174f, 0.9f, "Drum & Bass"), t(2, 70f, 0.2f, "Ambient"), t(3, 124f, 0.7f, "House"),
            t(4, 95f, 0.5f, "Hip-Hop"), t(5, 128f, 0.8f, "House"), t(6, 100f, 0.4f, "Pop"))
        val res = QueueFlow.insertAll(emptyList(), null, songs)
        assertEquals(6, res.size)
        val arranged = QueueFlow.arrange(songs, null, emptySet())
        assertTrue(QueueFlow.totalCost(arranged) <= QueueFlow.totalCost(songs))
        // Die beiden House-Tracks stehen nebeneinander
        val i3 = arranged.indexOfFirst { it.id == 3L }
        val i5 = arranged.indexOfFirst { it.id == 5L }
        assertEquals(1, kotlin.math.abs(i3 - i5))
    }

    @Test
    fun arrange_keepsPinnedPositions() {
        val songs = (1L..8L).map { t(it, 100f + it * 4, it / 10f) }.shuffled(Random(1))
        val pinnedIds = setOf(songs[2].id, songs[5].id)
        val res = QueueFlow.arrange(songs, null, pinnedIds)
        assertEquals(songs[2].id, res[2].id)
        assertEquals(songs[5].id, res[5].id)
        assertEquals(songs.map { it.id }.toSet(), res.map { it.id }.toSet())
    }

    @Test
    fun smartShuffle_variesButKeepsGoodFlow() {
        val songs = (1L..12L).map { t(it, 118f + (it % 5) * 2, 0.3f + (it % 6) * 0.1f, if (it % 2 == 0L) "House" else "Disco") }
        val plain = QueueFlow.arrange(songs, null, emptySet())
        val a = QueueFlow.arrange(songs, null, emptySet(), Random(1))
        val b = QueueFlow.arrange(songs, null, emptySet(), Random(2))
        assertNotEquals(a.map { it.id }, b.map { it.id })          // zufällig
        val random = songs.shuffled(Random(3))
        // Smart Shuffle ist deutlich besser als reiner Zufall
        assertTrue("smart=${QueueFlow.totalCost(a)} random=${QueueFlow.totalCost(random)} best=${QueueFlow.totalCost(plain)}",
            QueueFlow.totalCost(a) < QueueFlow.totalCost(random))
    }

    @Test
    fun genreKey_simplifies() {
        assertEquals("house", QueueFlow.genreKey(" House/Dance "))
        assertEquals("", QueueFlow.genreKey(""))
    }
}
