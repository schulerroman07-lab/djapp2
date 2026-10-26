package app.fadelux.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Eine Playlist (eigene Song-Liste). */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** Ein Eintrag einer Playlist (derselbe Song darf mehrmals vorkommen). */
@Entity(tableName = "playlist_tracks", indices = [Index("playlistId")])
data class PlaylistTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

/** Übersicht für die Playlist-Seite: Name, Anzahl Songs, Gesamtdauer. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val count: Int,
    val durationMs: Long,
)

/** Ein Song in einer Playlist (mit Eintrags-ID für Sortieren/Entfernen). */
data class PlaylistEntry(
    val entryId: Long,
    val position: Int,
    @Embedded val track: Track,
)

@Dao
abstract class PlaylistDao {
    @Query(
        """SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
           COUNT(t.id) AS count, COALESCE(SUM(t.durationMs), 0) AS durationMs
           FROM playlists p
           LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id
           LEFT JOIN tracks t ON t.id = pt.trackId
           GROUP BY p.id ORDER BY p.name COLLATE NOCASE""",
    )
    abstract fun summaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    abstract suspend fun allOnce(): List<Playlist>

    @Query(
        """SELECT pt.id AS entryId, pt.position AS position, t.*
           FROM playlist_tracks pt JOIN tracks t ON t.id = pt.trackId
           WHERE pt.playlistId = :playlistId ORDER BY pt.position""",
    )
    abstract fun entries(playlistId: Long): Flow<List<PlaylistEntry>>

    @Query(
        """SELECT t.* FROM playlist_tracks pt JOIN tracks t ON t.id = pt.trackId
           WHERE pt.playlistId = :playlistId ORDER BY pt.position""",
    )
    abstract suspend fun tracksOnce(playlistId: Long): List<Track>

    @Insert
    abstract suspend fun insert(p: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    abstract suspend fun deleteEntriesOf(id: Long)

    @Transaction
    open suspend fun delete(id: Long) {
        deleteEntriesOf(id)
        deletePlaylist(id)
    }

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :id")
    abstract suspend fun maxPosition(id: Long): Int

    @Insert
    abstract suspend fun insertEntries(entries: List<PlaylistTrack>)

    /** Songs hinten anhängen. */
    @Transaction
    open suspend fun addTracks(playlistId: Long, trackIds: List<Long>) {
        var pos = maxPosition(playlistId)
        insertEntries(trackIds.map { PlaylistTrack(playlistId = playlistId, trackId = it, position = ++pos) })
    }

    @Query("DELETE FROM playlist_tracks WHERE id = :entryId")
    abstract suspend fun removeEntry(entryId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE id = :entryId")
    abstract suspend fun setPosition(entryId: Long, position: Int)

    /** Neue Reihenfolge speichern (Liste der Eintrags-IDs von oben nach unten). */
    @Transaction
    open suspend fun reorder(entryIds: List<Long>) {
        entryIds.forEachIndexed { i, id -> setPosition(id, i) }
    }
}
