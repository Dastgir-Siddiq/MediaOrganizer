package com.mediaorganizer.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

data class SubtitleMovePair(
    val oldPath: String,
    val newPath: String
)

@Entity(tableName = "organize_batches")
data class OrganizeBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val totalFiles: Int,
    val movieCount: Int,
    val tvCount: Int,
    val isUndone: Boolean = false,
    val undoneAt: Long? = null
)

@Entity(
    tableName = "moved_file_records",
    foreignKeys = [
        ForeignKey(
            entity = OrganizeBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("batchId")]
)
data class MovedFileRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val oldPath: String,
    val newPath: String,
    val movedSubtitlePairsJson: String,
    val mediaType: String,
    val timestamp: Long
)

data class BatchWithRecords(
    @Embedded val batch: OrganizeBatchEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "batchId"
    )
    val records: List<MovedFileRecordEntity>
)

@Entity(tableName = "cleanup_batches")
data class CleanupBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val junkCount: Int,
    val emptyDirCount: Int,
    val trashDirPath: String,
    val isUndone: Boolean = false,
    val undoneAt: Long? = null
)

@Entity(
    tableName = "cleanup_records",
    foreignKeys = [
        ForeignKey(
            entity = CleanupBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("batchId")]
)
data class CleanupRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val recordType: String, // "JUNK" or "EMPTY_DIR"
    val originalPath: String,
    val backupPath: String? = null
)

data class CleanupBatchWithRecords(
    @Embedded val batch: CleanupBatchEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "batchId"
    )
    val records: List<CleanupRecordEntity>
)

@Entity(tableName = "cached_show_episodes")
data class CachedShowEpisodesEntity(
    @PrimaryKey val cacheKey: String,
    val showName: String,
    val season: Int,
    val episodesJson: String,
    val source: String,
    val fetchedAt: Long,
    val expiresAt: Long
)

@Entity(tableName = "cached_movies")
data class CachedMovieEntity(
    @PrimaryKey val lookupKey: String,
    val queryTitle: String,
    val resolvedTitle: String,
    val year: String,
    val source: String,
    val fetchedAt: Long,
    val expiresAt: Long
)

@Dao
interface TransactionDao {
    @Insert
    suspend fun insertBatch(batch: OrganizeBatchEntity): Long

    @Insert
    suspend fun insertRecords(records: List<MovedFileRecordEntity>)

    @Transaction
    suspend fun insertBatchWithRecords(
        batch: OrganizeBatchEntity,
        records: List<MovedFileRecordEntity>
    ): Long {
        val batchId = insertBatch(batch)
        val recordsWithId = records.map { it.copy(batchId = batchId) }
        insertRecords(recordsWithId)
        return batchId
    }

    @Transaction
    @Query("SELECT * FROM organize_batches ORDER BY timestamp DESC")
    fun getAllBatchesFlow(): Flow<List<BatchWithRecords>>

    @Transaction
    @Query("SELECT * FROM organize_batches ORDER BY timestamp DESC")
    suspend fun getAllBatches(): List<BatchWithRecords>

    @Transaction
    @Query("SELECT * FROM organize_batches WHERE isUndone = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestActiveBatch(): BatchWithRecords?

    @Transaction
    @Query("SELECT * FROM organize_batches WHERE id = :batchId LIMIT 1")
    suspend fun getBatchById(batchId: Long): BatchWithRecords?

    @Query("UPDATE organize_batches SET isUndone = 1, undoneAt = :undoneAt WHERE id = :batchId")
    suspend fun markBatchUndone(batchId: Long, undoneAt: Long)

    @Query("DELETE FROM organize_batches WHERE id = :batchId")
    suspend fun deleteBatch(batchId: Long)

    @Query("DELETE FROM organize_batches")
    suspend fun clearAllHistory()
}

@Dao
interface CleanupDao {
    @Insert
    suspend fun insertBatch(batch: CleanupBatchEntity): Long

    @Insert
    suspend fun insertRecords(records: List<CleanupRecordEntity>)

    @Transaction
    suspend fun insertBatchWithRecords(
        batch: CleanupBatchEntity,
        records: List<CleanupRecordEntity>
    ): Long {
        val batchId = insertBatch(batch)
        val recordsWithId = records.map { it.copy(batchId = batchId) }
        insertRecords(recordsWithId)
        return batchId
    }

    @Transaction
    @Query("SELECT * FROM cleanup_batches ORDER BY timestamp DESC")
    fun getAllBatchesFlow(): Flow<List<CleanupBatchWithRecords>>

    @Transaction
    @Query("SELECT * FROM cleanup_batches ORDER BY timestamp DESC")
    suspend fun getAllBatches(): List<CleanupBatchWithRecords>

    @Transaction
    @Query("SELECT * FROM cleanup_batches WHERE isUndone = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestActiveBatch(): CleanupBatchWithRecords?

    @Transaction
    @Query("SELECT * FROM cleanup_batches WHERE id = :batchId LIMIT 1")
    suspend fun getBatchById(batchId: Long): CleanupBatchWithRecords?

    @Query("UPDATE cleanup_batches SET isUndone = 1, undoneAt = :undoneAt WHERE id = :batchId")
    suspend fun markBatchUndone(batchId: Long, undoneAt: Long)

    @Query("DELETE FROM cleanup_batches")
    suspend fun clearAllHistory()
}

@Dao
interface MetadataCacheDao {
    @Query("SELECT * FROM cached_show_episodes WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getShowEpisodes(cacheKey: String): CachedShowEpisodesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShowEpisodes(entity: CachedShowEpisodesEntity)

    @Query("SELECT * FROM cached_movies WHERE lookupKey = :lookupKey LIMIT 1")
    suspend fun getMovie(lookupKey: String): CachedMovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovie(entity: CachedMovieEntity)

    @Query("SELECT COUNT(*) FROM cached_show_episodes")
    suspend fun getCachedShowCount(): Int

    @Query("SELECT COUNT(*) FROM cached_movies")
    suspend fun getCachedMovieCount(): Int

    @Query("DELETE FROM cached_show_episodes WHERE expiresAt < :now")
    suspend fun deleteExpiredShows(now: Long)

    @Query("DELETE FROM cached_movies WHERE expiresAt < :now")
    suspend fun deleteExpiredMovies(now: Long)

    @Query("DELETE FROM cached_show_episodes")
    suspend fun clearAllShows()

    @Query("DELETE FROM cached_movies")
    suspend fun clearAllMovies()
}

object RoomJsonConverters {
    private val gson = Gson()

    fun encodeSubtitlePairs(pairs: List<SubtitleMovePair>): String {
        return gson.toJson(pairs)
    }

    fun decodeSubtitlePairs(json: String): List<SubtitleMovePair> {
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<SubtitleMovePair>>() {}.type
            gson.fromJson<List<SubtitleMovePair>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun encodeEpisodesMap(episodes: Map<Int, String>): String {
        val stringKeyMap = episodes.entries.associate { it.key.toString() to it.value }
        return gson.toJson(stringKeyMap)
    }

    fun decodeEpisodesMap(json: String): Map<Int, String> {
        if (json.isBlank()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val raw: Map<String, String> = gson.fromJson(json, type) ?: emptyMap()
            raw.entries.mapNotNull { (k, v) ->
                k.toIntOrNull()?.let { epNum -> epNum to v }
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

@Database(
    entities = [
        OrganizeBatchEntity::class,
        MovedFileRecordEntity::class,
        CleanupBatchEntity::class,
        CleanupRecordEntity::class,
        CachedShowEpisodesEntity::class,
        CachedMovieEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun cleanupDao(): CleanupDao
    abstract fun metadataCacheDao(): MetadataCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cleanup_batches` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `junkCount` INTEGER NOT NULL,
                        `emptyDirCount` INTEGER NOT NULL,
                        `trashDirPath` TEXT NOT NULL,
                        `isUndone` INTEGER NOT NULL,
                        `undoneAt` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cleanup_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `batchId` INTEGER NOT NULL,
                        `recordType` TEXT NOT NULL,
                        `originalPath` TEXT NOT NULL,
                        `backupPath` TEXT,
                        FOREIGN KEY(`batchId`) REFERENCES `cleanup_batches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cleanup_records_batchId` ON `cleanup_records` (`batchId`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Invalidate partial/older episode caches so full-season maps are fetched cleanly.
                db.execSQL("DELETE FROM `cached_show_episodes`")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "media_organizer.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
