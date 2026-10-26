package app.fadelux.io

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import app.fadelux.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Ergebnis eines Imports (für die Anzeige). */
data class ImportResult(val playlistName: String, val found: Int, val missing: List<String>)

/**
 * Import/Export von Playlists über das "Dateien"-System von Android (Storage Access Framework):
 * der Benutzer wählt Datei oder Ordner, die App braucht dafür keine zusätzlichen Berechtigungen.
 */
object PlaylistIO {

    /** Alle Songs der Bibliothek mit Pfad und Dateiname (für die Zuordnung beim Import). */
    suspend fun libraryFiles(context: Context, tracks: List<Track>): List<LibFile> = withContext(Dispatchers.IO) {
        val byId = tracks.associateBy { it.id }
        val out = mutableListOf<LibFile>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.DISPLAY_NAME)
        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (c.moveToNext()) {
                val t = byId[c.getLong(idCol)] ?: continue
                val name = c.getString(nameCol) ?: continue
                out += LibFile(t.id, (c.getString(pathCol) ?: "") + name, name, t.title, t.artist)
            }
        }
        out
    }

    /** Playlist als .m3u8 in die gewählte Datei schreiben (Pfade relativ zum Speicher, UTF-8). */
    suspend fun exportM3u(context: Context, target: Uri, tracks: List<Track>) = withContext(Dispatchers.IO) {
        val files = libraryFiles(context, tracks).associateBy { it.trackId }
        val entries = tracks.map { t ->
            M3uEntry(files[t.id]?.relPath ?: t.title, t.artist, t.title, (t.durationMs / 1000).toInt())
        }
        context.contentResolver.openOutputStream(target, "wt")?.use { it.write(M3u.write(entries).toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("cannot write")
    }

    /** .m3u/.m3u8 lesen und den Songs der Bibliothek zuordnen. */
    suspend fun readM3u(context: Context, source: Uri, tracks: List<Track>): Pair<String, MatchResult> = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)?.use { String(it.readBytes(), Charsets.UTF_8) }
            ?: throw IllegalStateException("cannot read")
        val name = displayName(context, source)?.substringBeforeLast('.') ?: "Import"
        name to M3u.match(M3u.parse(text), libraryFiles(context, tracks))
    }

    /**
     * Alle Songs einer Playlist in einen Unterordner des gewählten Ordners kopieren + .m3u8 dazu.
     * progress(fertig, total) wird laufend aufgerufen; Abbrechen über die Coroutine.
     */
    suspend fun exportFolder(
        context: Context,
        tree: Uri,
        playlistName: String,
        tracks: List<Track>,
        numbered: Boolean,
        progress: (Int, Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, tree) ?: throw IllegalStateException("no folder")
        // Eindeutigen Unterordner anlegen
        val base = M3u.safeName(playlistName)
        var dirName = base
        var n = 2
        while (root.findFile(dirName) != null) dirName = "$base ($n)".also { n++ }
        val dir = root.createDirectory(dirName) ?: throw IllegalStateException("cannot create folder")

        val files = libraryFiles(context, tracks).associateBy { it.trackId }
        val names = mutableListOf<M3uEntry>()
        var copied = 0
        progress(0, tracks.size)
        tracks.forEachIndexed { i, t ->
            coroutineContext.ensureActive()   // Abbrechen möglich
            val original = files[t.id]?.fileName ?: (M3u.safeName("${t.artist} - ${t.title}") + ".mp3")
            val fileName = M3u.exportFileName(i, tracks.size, original, numbered)
            val mime = context.contentResolver.getType(Uri.parse(t.uri)) ?: "audio/mpeg"
            val out = dir.createFile(mime, fileName)
            if (out != null) {
                context.contentResolver.openInputStream(Uri.parse(t.uri))?.use { input ->
                    context.contentResolver.openOutputStream(out.uri)?.use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val r = input.read(buf)
                            if (r < 0) break
                            output.write(buf, 0, r)
                        }
                    }
                }
                // createFile kann den Namen anpassen → tatsächlichen Namen in die M3U schreiben
                names += M3uEntry(out.name ?: fileName, t.artist, t.title, (t.durationMs / 1000).toInt())
                copied++
            }
            progress(i + 1, tracks.size)
        }
        dir.createFile("audio/x-mpegurl", "$base.m3u8")?.let { m3u ->
            context.contentResolver.openOutputStream(m3u.uri)?.use { it.write(M3u.write(names).toByteArray(Charsets.UTF_8)) }
        }
        copied
    }

    /** Musikdateien eines gewählten Ordners (sortiert nach Name) den Songs der Bibliothek zuordnen. */
    suspend fun readFolder(context: Context, tree: Uri, tracks: List<Track>): Pair<String, MatchResult> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, tree) ?: throw IllegalStateException("no folder")
        val audioExt = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
        val files = root.listFiles()
            .filter { it.isFile && (it.type?.startsWith("audio/") == true || it.name?.substringAfterLast('.')?.lowercase() in audioExt) }
            .sortedBy { it.name?.lowercase() }
        // Führende Nummern ("03 - Song.mp3") für die Zuordnung entfernen
        val entries = files.map { f ->
            val name = f.name ?: ""
            M3uEntry(name.replace(Regex("^\\d{1,4}\\s*[-._)]\\s*"), ""))
        }
        (root.name ?: "Import") to M3u.match(entries, libraryFiles(context, tracks))
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** (wird nicht direkt gebraucht – hilft beim Debuggen) */
    @Suppress("unused")
    fun trackUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}
