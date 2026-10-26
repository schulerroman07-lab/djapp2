package app.fadelux.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Ein Musiktitel in der Bibliothek. */
@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: Long,      // ID aus dem Android-Medienspeicher
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val bpm: Float? = null,        // null = noch nicht analysiert, 0 = nicht erkannt
    val energy: Float? = null,     // 0..1 Lautheit/Intensität, null = noch nicht analysiert
    val folder: String = "",       // Ordner auf dem Handy (z.B. "Music/Party/")
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY artist COLLATE NOCASE, title COLLATE NOCASE")
    fun all(): Flow<List<Track>>

    @Query("SELECT * FROM tracks")
    suspend fun allOnce(): List<Track>

    @Query("SELECT * FROM tracks WHERE bpm IS NULL OR energy IS NULL OR id NOT IN (SELECT trackId FROM track_analysis) LIMIT 1")
    suspend fun nextUnanalyzed(): Track?

    @Query("SELECT COUNT(*) FROM tracks WHERE bpm IS NULL OR energy IS NULL OR id NOT IN (SELECT trackId FROM track_analysis)")
    fun unanalyzedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tracks: List<Track>)

    @Query("UPDATE tracks SET title = :title, artist = :artist, durationMs = :durationMs, uri = :uri, folder = :folder WHERE id = :id")
    suspend fun updateInfo(id: Long, uri: String, title: String, artist: String, durationMs: Long, folder: String)

    @Query("UPDATE tracks SET bpm = :bpm, energy = :energy WHERE id = :id")
    suspend fun setAnalysis(id: Long, bpm: Float, energy: Float)

    @Query("UPDATE tracks SET bpm = :bpm WHERE id = :id")
    suspend fun setBpm(id: Long, bpm: Float)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)
}

/** Detail-Analyse eines Titels (für Auto-Mix): Beat-Raster + Merkmale pro Beat. */
@Entity(tableName = "track_analysis")
data class TrackAnalysis(
    @PrimaryKey val trackId: Long,
    val firstBeat: Float,
    val features: ByteArray,     // je Beat 4 Bytes: Energie, Bass, Mitten (Gesang), Höhen
)

@Dao
interface AnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(a: TrackAnalysis)

    @Query("SELECT * FROM track_analysis WHERE trackId = :id")
    suspend fun get(id: Long): TrackAnalysis?
}

// Version 2: neue Spalten "energy" und "folder" (bestehende Daten bleiben erhalten)
private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN energy REAL")
        db.execSQL("ALTER TABLE tracks ADD COLUMN folder TEXT NOT NULL DEFAULT ''")
    }
}

// Version 3: Analyse-Tabelle und Playlists (bestehende Daten bleiben erhalten)
private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `track_analysis` (`trackId` INTEGER NOT NULL, `firstBeat` REAL NOT NULL, `features` BLOB NOT NULL, PRIMARY KEY(`trackId`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_tracks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` INTEGER NOT NULL, `trackId` INTEGER NOT NULL, `position` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId` ON `playlist_tracks` (`playlistId`)")
    }
}

@Database(entities = [Track::class, TrackAnalysis::class, Playlist::class, PlaylistTrack::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun analysis(): AnalysisDao
    abstract fun playlists(): PlaylistDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "fadelux.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
