package ch.hirschexpress.dj.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import ch.hirschexpress.dj.audio.NativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Liest die MP3s vom Handy ein und berechnet im Hintergrund die BPM. */
class LibraryRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).tracks()

    val tracks = dao.all()
    val unanalyzedCount = dao.unanalyzedCount()

    /** Musik vom Handy einlesen (neue Titel hinzufügen, gelöschte entfernen). */
    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        val found = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
        )
        // Nur Musik (keine Klingeltöne / Sprachnachrichten), mindestens 30 Sekunden
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            while (c.moveToNext()) {
                val mime = c.getString(mimeCol) ?: ""
                if (!(mime.contains("mpeg") || mime.contains("mp3") || mime.contains("mp4") ||
                        mime.contains("aac") || mime.contains("flac") || mime.contains("wav") ||
                        mime.contains("ogg"))
                ) continue
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val artist = c.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unbekannt"
                found += Track(
                    id = id,
                    uri = uri.toString(),
                    title = c.getString(titleCol) ?: "Ohne Titel",
                    artist = artist,
                    durationMs = c.getLong(durCol),
                )
            }
        }
        val existing = dao.allOnce().associateBy { it.id }
        dao.insertAll(found.filter { it.id !in existing })
        found.filter { it.id in existing }.forEach {
            dao.updateInfo(it.id, it.uri, it.title, it.artist, it.durationMs)
        }
        val foundIds = found.map { it.id }.toSet()
        val removed = existing.keys.filter { it !in foundIds }
        if (removed.isNotEmpty()) dao.deleteIds(removed)
        found.size
    }

    /** Analysiert den nächsten Titel ohne BPM. Gibt false zurück, wenn alle fertig sind. */
    suspend fun analyzeNext(): Boolean = withContext(Dispatchers.IO) {
        // Niedrige Priorität, damit die Musik nie stockt
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        val t = dao.nextUnanalyzed() ?: return@withContext false
        var bpm = 0f
        try {
            context.contentResolver.openFileDescriptor(Uri.parse(t.uri), "r")?.use { pfd ->
                val len = pfd.statSize
                val fd = pfd.detachFd() // C++ schliesst die Datei
                bpm = NativeEngine.analyzeFile(fd, len)[0]
            }
        } catch (e: Exception) {
            Log.e("HirschDJ", "BPM-Analyse fehlgeschlagen: ${t.title}", e)
        }
        dao.setBpm(t.id, bpm)
        true
    }

    suspend fun setBpm(id: Long, bpm: Float) = withContext(Dispatchers.IO) { dao.setBpm(id, bpm) }
}
