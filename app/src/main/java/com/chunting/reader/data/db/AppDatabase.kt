package com.chunting.reader.data.db

import androidx.room.ColumnInfo
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

@Entity(tableName = "shelf_folders")
data class ShelfFolderEntity(
    @PrimaryKey val path: String,
    val name: String,
    val trackCount: Int,
    val totalDurationMs: Long,
    val addedAt: Long,
    val lastPlayedAt: Long = 0L,
)

@Entity(tableName = "track_progress")
data class TrackProgressEntity(
    @PrimaryKey val path: String,
    val folderPath: String,
    val fileName: String,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
    val updatedAt: Long,
    /** 该曲目在所属文件夹播放队列中的序号（0-based），用于按"实际进度点"算书架百分比 */
    @ColumnInfo(defaultValue = "-1") val trackIndex: Int = -1,
    /** 该次播放队列总集数 */
    @ColumnInfo(defaultValue = "0") val trackTotal: Int = 0,
)

@Dao
interface ShelfDao {
    @Query("SELECT * FROM shelf_folders ORDER BY lastPlayedAt DESC, addedAt DESC")
    fun observeAll(): Flow<List<ShelfFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: ShelfFolderEntity)

    @Query("DELETE FROM shelf_folders WHERE path = :path")
    suspend fun delete(path: String)

    /** 批量移除书架条目（书架多选删除） */
    @Query("DELETE FROM shelf_folders WHERE path IN (:paths)")
    suspend fun deleteAll(paths: List<String>)

    @Query("SELECT EXISTS(SELECT 1 FROM shelf_folders WHERE path = :path)")
    suspend fun exists(path: String): Boolean

    @Query("UPDATE shelf_folders SET lastPlayedAt = :ts WHERE path = :path")
    suspend fun touch(path: String, ts: Long)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM track_progress")
    fun observeAll(): Flow<List<TrackProgressEntity>>

    @Query("SELECT * FROM track_progress WHERE path = :path")
    suspend fun get(path: String): TrackProgressEntity?

    @Query("SELECT * FROM track_progress WHERE folderPath = :folder")
    suspend fun getForFolder(folder: String): List<TrackProgressEntity>

    /** 全局最近一次播放记录（用于冷启动续播） */
    @Query("SELECT * FROM track_progress ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(): TrackProgressEntity?

    /** 某文件夹（含子目录）内最近一次播放记录（用于切回文件夹续播） */
    @Query("SELECT * FROM track_progress WHERE path >= :prefix AND path < :prefixEnd ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestUnder(prefix: String, prefixEnd: String): TrackProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: TrackProgressEntity)

    @Query("DELETE FROM track_progress WHERE path = :path")
    suspend fun delete(path: String)

    /** 删除某文件夹（含子目录）下所有曲目的进度记录 */
    @Query("DELETE FROM track_progress WHERE path >= :prefix AND path < :prefixEnd")
    suspend fun deleteUnder(prefix: String, prefixEnd: String)
}

@Database(
    entities = [ShelfFolderEntity::class, TrackProgressEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shelfDao(): ShelfDao
    abstract fun progressDao(): ProgressDao

    companion object {
        /** 1→2：进度表补记"该曲在队列中的序号 / 队列集数"，供书架计算实际进度点 */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track_progress ADD COLUMN trackIndex INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE track_progress ADD COLUMN trackTotal INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: android.content.Context): AppDatabase {
            inst?.let { return it }
            return synchronized(AppDatabase::class.java) {
                inst ?: Room.databaseBuilder(
                    ctx.applicationContext, AppDatabase::class.java, "ting.db"
                ).addMigrations(MIGRATION_1_2).build().also { inst = it }
            }
        }
    }
}
