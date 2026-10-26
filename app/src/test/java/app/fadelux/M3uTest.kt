package app.fadelux

import app.fadelux.io.LibFile
import app.fadelux.io.M3u
import app.fadelux.io.M3uEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests für M3U lesen/schreiben und die Zuordnung zu Songs der Bibliothek. */
class M3uTest {
    private val lib = listOf(
        LibFile(1, "Music/Party/Daft Punk - One More Time.mp3", "Daft Punk - One More Time.mp3", "One More Time", "Daft Punk"),
        LibFile(2, "Music/Chill/sunset.mp3", "sunset.mp3", "Sunset Groove", "The Waves"),
        LibFile(3, "Download/Mädchen.mp3", "Mädchen.mp3", "Mädchen", "Lucilectric"),
        LibFile(4, "Music/Rock/song.mp3", "song.mp3", "Song", "Band A"),
    )

    @Test
    fun writeAndParse_roundTrip() {
        val text = M3u.write(listOf(M3uEntry("Music/Party/Daft Punk - One More Time.mp3", "Daft Punk", "One More Time", 320)))
        assertTrue(text.startsWith("#EXTM3U\n#EXTINF:320,Daft Punk - One More Time\n"))
        val back = M3u.parse(text)
        assertEquals(1, back.size)
        assertEquals("Daft Punk", back[0].artist)
        assertEquals("One More Time", back[0].title)
        assertEquals(320, back[0].durationSec)
    }

    @Test
    fun parse_handlesBomCrlfFileUrlsAndBackslashes() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:200,X\r\nC:\\Music\\Chill\\sunset.mp3\r\nfile:///storage/emulated/0/Download/M%C3%A4dchen.mp3\r\n"
        val e = M3u.parse(text)
        assertEquals(2, e.size)
        assertEquals("C:/Music/Chill/sunset.mp3", e[0].path)
        assertEquals("/storage/emulated/0/Download/Mädchen.mp3", e[1].path)
    }

    @Test
    fun match_byPathFilenameAndTitle() {
        val entries = listOf(
            M3uEntry("Music/Party/Daft Punk - One More Time.mp3"),                 // exakter relativer Pfad
            M3uEntry("/storage/emulated/0/Download/Mädchen.mp3"),                  // absoluter Pfad
            M3uEntry("../Chill/sunset.mp3"),                                        // relativer Pfad mit ../
            M3uEntry("D:/Old/Other Name.mp3", "The Waves", "Sunset Groove"),       // über #EXTINF Interpret + Titel
            M3uEntry("Daft Punk - One More Time.flac"),                             // über Dateiname "Interpret - Titel"
            M3uEntry("missing/unknown.mp3", "Nobody", "Nothing"),                   // nicht vorhanden
        )
        val r = M3u.match(entries, lib)
        assertEquals(listOf(1L, 3L, 2L, 2L, 1L), r.trackIds)
        assertEquals(1, r.missing.size)
        assertEquals("missing/unknown.mp3", r.missing[0].path)
    }

    @Test
    fun exportFileNames() {
        assertEquals("03 - a.mp3", M3u.exportFileName(2, 12, "a.mp3", true))
        assertEquals("003 - a.mp3", M3u.exportFileName(2, 120, "a.mp3", true))
        assertEquals("a_b.mp3", M3u.exportFileName(0, 1, "a/b.mp3", false))
        assertEquals("My_Set", M3u.safeName("My/Set"))
    }
}
