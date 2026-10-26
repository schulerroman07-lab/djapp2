package app.fadelux.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Zugriff auf die Playlists in der lokalen Datenbank (Room). */
class PlaylistRepository(context: Context) {
    private val dao = AppDatabase.get(context).playlists()

    val summaries = dao.summaries()
    fun entries(playlistId: Long) = dao.entries(playlistId)

    suspend fun create(name: String): Long = withContext(Dispatchers.IO) {
        dao.insert(Playlist(name = name.trim().ifBlank { "Playlist" }.take(80), createdAt = System.currentTimeMillis()))
    }

    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) { dao.rename(id, name.trim().take(80)) }
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }
    suspend fun addTracks(id: Long, trackIds: List<Long>) = withContext(Dispatchers.IO) { dao.addTracks(id, trackIds) }
    suspend fun removeEntry(entryId: Long) = withContext(Dispatchers.IO) { dao.removeEntry(entryId) }
    suspend fun reorder(entryIds: List<Long>) = withContext(Dispatchers.IO) { dao.reorder(entryIds) }
    suspend fun tracks(id: Long): List<Track> = withContext(Dispatchers.IO) { dao.tracksOnce(id) }
    suspend fun all(): List<Playlist> = withContext(Dispatchers.IO) { dao.allOnce() }
}
