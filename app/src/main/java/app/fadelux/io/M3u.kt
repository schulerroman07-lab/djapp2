package app.fadelux.io

import java.net.URLDecoder
import java.text.Normalizer

/** Ein Eintrag aus einer M3U-Datei. */
data class M3uEntry(
    val path: String,
    val artist: String? = null,
    val title: String? = null,
    val durationSec: Int? = null,
)

/** Ein Song der Bibliothek, wie er für die Zuordnung gebraucht wird. */
data class LibFile(
    val trackId: Long,
    val relPath: String,      // z.B. "Music/Party/Song.mp3"
    val fileName: String,     // z.B. "Song.mp3"
    val title: String,
    val artist: String,
)

/** Ergebnis eines Imports: gefundene Titel (in Reihenfolge) und nicht gefundene Einträge. */
data class MatchResult(val trackIds: List<Long>, val missing: List<M3uEntry>)

/**
 * M3U/M3U8-Playlists lesen und schreiben.
 * Zuordnung beim Import: 1. über den Pfad, 2. über den Dateinamen, 3. über Interpret + Titel.
 */
object M3u {

    /** Playlist als M3U8-Text (UTF-8, mit #EXTINF-Infos). */
    fun write(items: List<M3uEntry>): String = buildString {
        append("#EXTM3U\n")
        for (e in items) {
            val info = listOfNotNull(e.artist?.takeIf { it.isNotBlank() }, e.title?.takeIf { it.isNotBlank() }).joinToString(" - ")
            append("#EXTINF:").append(e.durationSec ?: -1).append(',').append(info.replace('\n', ' ')).append('\n')
            append(e.path).append('\n')
        }
    }

    /** M3U-Text lesen (auch mit BOM, Windows-Zeilenenden, file://-Adressen und Backslashes). */
    fun parse(text: String): List<M3uEntry> {
        val out = mutableListOf<M3uEntry>()
        var pendingArtist: String? = null
        var pendingTitle: String? = null
        var pendingDur: Int? = null
        for (raw in text.removePrefix("\uFEFF").lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val rest = line.substring(8)
                val comma = rest.indexOf(',')
                pendingDur = rest.substring(0, if (comma >= 0) comma else rest.length).trim().substringBefore(' ').toIntOrNull()?.takeIf { it >= 0 }
                val info = if (comma >= 0) rest.substring(comma + 1).trim() else ""
                val dash = info.indexOf(" - ")
                if (dash > 0) {
                    pendingArtist = info.substring(0, dash).trim()
                    pendingTitle = info.substring(dash + 3).trim()
                } else {
                    pendingArtist = null
                    pendingTitle = info.ifBlank { null }
                }
                continue
            }
            if (line.startsWith("#")) continue
            var path = line.replace('\\', '/')
            if (path.startsWith("file://", ignoreCase = true)) {
                path = runCatching { URLDecoder.decode(path.substring(7).replace("+", "%2B"), "UTF-8") }.getOrDefault(path.substring(7))
            }
            out += M3uEntry(path, pendingArtist, pendingTitle, pendingDur)
            pendingArtist = null
            pendingTitle = null
            pendingDur = null
        }
        return out
    }

    /** Einträge den Songs der Bibliothek zuordnen. */
    fun match(entries: List<M3uEntry>, lib: List<LibFile>): MatchResult {
        val byRelPath = lib.associateBy { normPath(it.relPath) }
        val byName = lib.groupBy { it.fileName.lowercase() }
        val byArtistTitle = lib.groupBy { key("${it.artist} - ${it.title}") }
        val byTitle = lib.groupBy { key(it.title) }
        val ids = mutableListOf<Long>()
        val missing = mutableListOf<M3uEntry>()
        for (e in entries) {
            val p = normPath(e.path)
            val fileName = p.substringAfterLast('/')
            // 1) Pfad: exakt oder als Endstück (absolute Pfade, ../ usw.)
            val hit = byRelPath[p]
                ?: lib.firstOrNull { p.endsWith("/" + normPath(it.relPath)) || normPath(it.relPath).endsWith("/$p") }
                // 2) Dateiname
                ?: byName[fileName]?.singleOrNull()
                ?: byName[fileName]?.firstOrNull()
                // 3) Interpret + Titel (aus #EXTINF oder aus dem Dateinamen "Interpret - Titel.mp3")
                ?: e.artist?.let { a -> e.title?.let { t -> byArtistTitle[key("$a - $t")]?.firstOrNull() } }
                ?: byArtistTitle[key(fileName.substringBeforeLast('.'))]?.firstOrNull()
                ?: e.title?.let { byTitle[key(it)]?.singleOrNull() }
                ?: byTitle[key(fileName.substringBeforeLast('.'))]?.singleOrNull()
            if (hit != null) ids += hit.trackId else missing += e
        }
        return MatchResult(ids, missing)
    }

    /** Dateiname für Ordner-Export, optional mit Nummer davor ("03 - Song.mp3"). */
    fun exportFileName(index: Int, total: Int, original: String, numbered: Boolean): String {
        val clean = original.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (!numbered) return clean
        val digits = maxOf(2, total.toString().length)
        return (index + 1).toString().padStart(digits, '0') + " - " + clean
    }

    /** Gültiger Datei-/Ordnername aus einem Playlist-Namen. */
    fun safeName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Playlist" }.take(80)

    private fun normPath(p: String): String {
        var s = p.replace('\\', '/').trim()
        while (s.startsWith("./")) s = s.substring(2)
        while (s.startsWith("../")) s = s.substring(3)
        s = s.removePrefix("/storage/emulated/0/").removePrefix("/sdcard/")
        return s.trimStart('/').lowercase()
    }

    /** Vergleichs-Schlüssel: Kleinbuchstaben, ohne Akzente und Sonderzeichen. */
    private fun key(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]"), "")
}
