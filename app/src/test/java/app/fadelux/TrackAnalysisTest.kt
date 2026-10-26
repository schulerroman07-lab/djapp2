package app.fadelux

import app.fadelux.automix.Suggestions
import app.fadelux.automix.TrackAnalysis
import app.fadelux.data.Track
import app.fadelux.wishes.LocalWishServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** Automatische Tests für Auto-Mix-Logik, Vorschläge und den lokalen Wunsch-Server. */
class TrackAnalysisTest {
    private fun t(id: Long, bpm: Float?, e: Float?, title: String = "Song $id") =
        Track(id, "", title, "Artist", 200000, bpm, e)

    @Test
    fun tempoRatio_matchesHalfAndDouble() {
        assertEquals(1.0f, TrackAnalysis.tempoRatio(128f, 128f)!!, 0.001f)
        assertEquals(1.0f, TrackAnalysis.tempoRatio(128f, 64f)!!, 0.001f)   // halbes Tempo passt
        assertEquals(124f / 120f, TrackAnalysis.tempoRatio(124f, 120f)!!, 0.001f)
        assertNull(TrackAnalysis.tempoRatio(128f, 90f))                      // zu weit weg
    }

    @Test
    fun introOutro_findsLoudPart() {
        // 20 s leise, 60 s laut, 20 s leise (100 Werte pro Sekunde)
        val peaks = FloatArray(10000) { i -> if (i in 2000 until 8000) 0.9f else 0.1f }
        val (intro, outro) = TrackAnalysis.introOutro(peaks)
        assertTrue("intro=$intro", intro in 19.0..23.0)
        assertTrue("outro=$outro", outro in 79.0..83.0)
    }

    @Test
    fun optimizeOrder_keepsAllTracksAndStartsCalm() {
        val list = listOf(t(1, 128f, 0.9f), t(2, 100f, 0.2f), t(3, 126f, 0.6f), t(4, 101f, 0.3f))
        val sorted = TrackAnalysis.optimizeOrder(list)
        assertEquals(list.map { it.id }.toSet(), sorted.map { it.id }.toSet())
        assertEquals(2L, sorted.first().id)
        assertEquals(4L, sorted[1].id)   // ähnliches Tempo wie Titel 2
    }

    @Test
    fun suggestions_wishesFirstThenSimilarTempo() {
        val all = listOf(t(1, 128f, 0.5f), t(2, 130f, 0.5f), t(3, 90f, 0.5f), t(4, 70f, 0.5f, title = "Dancing Queen"))
        val s = Suggestions.suggest(all[0], 128f, all, listOf("bitte dancing queen!"), emptySet())
        assertEquals(4L, s.first().track.id)
        assertTrue(s.first().isWish)
        assertTrue(s.any { it.track.id == 2L })
        assertTrue(s.none { it.track.id == 3L })   // 90 BPM passt nicht zu 128
    }

    @Test
    fun localServer_acceptsWishAndLimitsSpam() {
        val received = mutableListOf<String>()
        val server = LocalWishServer({ listOf("A – B") }, mapOf("title" to "T")) { received += it.song }
        assertTrue(server.start())
        try {
            fun post(): Int {
                val c = URL("http://127.0.0.1:${server.port}/wish").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.outputStream.use { it.write("song=A+%E2%80%93+B&text=".toByteArray()) }
                return c.responseCode.also { c.disconnect() }
            }
            assertEquals(200, post())
            assertEquals(429, post())   // zweiter Wunsch sofort danach → abgelehnt
            assertEquals(listOf("A – B"), received)
            val songs = URL("http://127.0.0.1:${server.port}/songs").readText()
            assertTrue(songs.contains("A – B"))
            assertNotNull(URL("http://127.0.0.1:${server.port}/").readText())
        } finally {
            server.stop()
        }
    }
}
