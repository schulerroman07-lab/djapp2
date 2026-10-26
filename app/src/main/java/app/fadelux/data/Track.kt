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

    @Query("SELECT * FROM tracks WHERE bpm IS NULL OR energy IS NULL LIMIT 1")
    suspend fun nextUnanalyzed(): Track?

    @Query("SELECT COUNT(*) FROM tracks WHERE bpm IS NULL OR energy IS NULL")
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

// Version 2: neue Spalten "energy" und "folder" (bestehende Daten bleiben erhalten)
private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN energy REAL")
        db.execSQL("ALTER TABLE tracks ADD COLUMN folder TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [Track::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "fadelux.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
