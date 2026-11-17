package app.fadelux

import app.fadelux.automix.TransitionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Übergangs-Protokoll: speichern, bewerten, exportieren. */
class TransitionLogTest {
    private fun tmp() = File.createTempFile("log", ".jsonl").apply { delete(); deleteOnExit() }

    @Test
    fun addRateExport() {
        val f = tmp()
        val log = TransitionLog(f)
        val id1 = log.add(mapOf("type" to "FADE", "a" to mapOf("title" to "Song \"A\"", "bpm" to 124.5f, "conf" to 0.91f), "b" to mapOf("title" to "Süßes\nLied")), now = 1000)
        val id2 = log.add(mapOf("type" to "CUT", "matchTempo" to false), now = 1000)     // gleiche Millisekunde → andere ID
        assertTrue(id2 != id1)
        log.rate(id1, 1)
        log.rate(id2, -1)
        log.rate(id2, 1)                                                                   // letzte Bewertung zählt
        val e = log.entries()
        assertEquals(2, e.size)
        assertTrue(e[0].contains("\"rating\":1") && e[0].contains("\"type\":\"FADE\"") && e[0].contains("Song \\\"A\\\""))
        assertTrue(e[0].contains("\"bpm\":124.5000") && e[0].contains("Süßes\\nLied"))
        assertTrue(e[1].contains("\"rating\":1") && e[1].contains("\"matchTempo\":false"))
        assertEquals(2, log.ratedCount())

        val out = tmp()
        val n = TransitionLog(f).export(out, mapOf("app" to "Fadelux 2.1"))                // neu geöffnet: Daten bleiben
        assertEquals(2, n)
        val text = out.readText()
        assertTrue(text.startsWith("{\"app\":\"Fadelux 2.1\",\"count\":2,"))
        assertTrue(text.trimEnd().endsWith("]}"))
        // gültiges JSON: Klammern ausgeglichen
        assertEquals(text.count { it == '{' }, text.count { it == '}' })
    }

    @Test
    fun unratedStaysZero_andEmptyExportWorks() {
        val log = TransitionLog(tmp())
        val out = tmp()
        assertEquals(0, log.export(out))
        log.add(mapOf("type" to "FADE"))
        assertEquals(1, log.count())
        assertEquals(0, log.ratedCount())
    }
}
