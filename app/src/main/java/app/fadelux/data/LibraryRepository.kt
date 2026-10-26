package app.fadelux.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import app.fadelux.audio.NativeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Liest die Musik vom Handy ein und analysiert im Hintergrund BPM und Energie. */
class LibraryRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val dao = db.tracks()
    private val analysisDao = db.analysis()

    val tracks = dao.all()
    val unanalyzedCount = dao.unanalyzedCount()

    /**
     * Musik einlesen. folder = nur dieser Ordner (leer = alle).
     * Gibt die Anzahl Titel und die Liste aller gefundenen Ordner zurück.
     */
    suspend fun scan(folder: String): Pair<Int, List<String>> = withContext(Dispatchers.IO) {
        val found = mutableListOf<Track>()
        val folders = sortedSetOf<String>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.RELATIVE_PATH,
        )
        // Nur Musik (keine Klingeltöne / Sprachnachrichten), mindestens 30 Sekunden
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            while (c.moveToNext()) {
                val mime = c.getString(mimeCol) ?: ""
                if (!(mime.contains("mpeg") || mime.contains("mp3") || mime.contains("mp4") ||
                        mime.contains("aac") || mime.contains("flac") || mime.contains("wav") ||
                        mime.contains("ogg"))
                ) continue
                val path = c.getString(pathCol) ?: ""
                folders += path
                if (folder.isNotEmpty() && !path.startsWith(folder)) continue
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val artist = c.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "?"
                found += Track(
                    id = id,
                    uri = uri.toString(),
                    title = c.getString(titleCol) ?: "?",
                    artist = artist,
                    durationMs = c.getLong(durCol),
                    folder = path,
                )
            }
        }
        val existing = dao.allOnce().associateBy { it.id }
        dao.insertAll(found.filter { it.id !in existing })
        found.filter { it.id in existing }.forEach {
            dao.updateInfo(it.id, it.uri, it.title, it.artist, it.durationMs, it.folder)
        }
        val foundIds = found.map { it.id }.toSet()
        val removed = existing.keys.filter { it !in foundIds }
        if (removed.isNotEmpty()) dao.deleteIds(removed)
        found.size to folders.toList()
    }

    /** Analysiert den nächsten Titel (BPM + Energie). false = alle fertig. */
    suspend fun analyzeNext(): Boolean = withContext(Dispatchers.IO) {
        // Niedrige Priorität, damit die Musik nie stockt
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        val t = dao.nextUnanalyzed() ?: return@withContext false
        var bpm = 0f
        var energy = 0.5f
        var firstBeat = 0f
        var features = ByteArray(0)
        try {
            context.contentResolver.openFileDescriptor(Uri.parse(t.uri), "r")?.use { pfd ->
                val len = pfd.statSize
                val fd = pfd.detachFd() // C++ schliesst die Datei
                val r = NativeEngine.analyzeFile(fd, len)
                bpm = r[0]
                firstBeat = r.getOrElse(1) { 0f }
                energy = r.getOrElse(2) { 0.5f }
                val n = r.getOrElse(3) { 0f }.toInt()
                if (n > 0 && r.size >= 4 + n * 4) features = ByteArray(n * 4) { r[4 + it].toInt().toByte() }
            }
        } catch (e: Exception) {
            Log.e("Fadelux", "Analyse fehlgeschlagen: ${t.title}", e)
        }
        dao.setAnalysis(t.id, bpm, energy)
        // Auch bei Fehler speichern (leere Merkmale), sonst würde der Titel endlos neu analysiert
        analysisDao.put(TrackAnalysis(t.id, firstBeat, features))
        true
    }

    suspend fun setBpm(id: Long, bpm: Float) = withContext(Dispatchers.IO) { dao.setBpm(id, bpm) }

    suspend fun analysis(id: Long): TrackAnalysis? = withContext(Dispatchers.IO) { analysisDao.get(id) }
}
