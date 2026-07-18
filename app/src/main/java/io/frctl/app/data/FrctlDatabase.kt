package io.frctl.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "catalog_cache")
data class CatalogCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payload: String,
    val savedAt: Long,
)

@Entity(tableName = "library")
data class LibraryEntity(
    @PrimaryKey val entryKey: String,
    val entryId: String,
    val kind: String,
    val name: String,
    val owner: String,
    val description: String,
    val repoUrl: String,
    val source: String,
    val iconUrl: String?,
    val fallbackIconUrl: String?,
    val stars: Int,
    val downloads: Int,
    val pipelineTag: String,
    val category: String,
    val latestUpdatedAt: String,
    val favorite: Boolean,
    val installed: Boolean,
    val checkedAt: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

@Dao
interface FrctlDao {
    @Query("SELECT * FROM catalog_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun cache(key: String): CatalogCacheEntity?

    @Upsert
    suspend fun upsertCache(value: CatalogCacheEntity)

    @Query("SELECT * FROM library ORDER BY name COLLATE NOCASE")
    fun observeLibrary(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM library WHERE entryKey = :key LIMIT 1")
    suspend fun libraryEntry(key: String): LibraryEntity?

    @Query("SELECT * FROM library WHERE favorite = 1 OR installed = 1")
    suspend fun trackedEntries(): List<LibraryEntity>

    @Upsert
    suspend fun upsertLibrary(value: LibraryEntity)

    @Query("DELETE FROM library WHERE entryKey = :key AND favorite = 0 AND installed = 0")
    suspend fun deleteUnusedLibraryEntry(key: String)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 8")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun upsertSearch(value: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query NOT IN (SELECT query FROM search_history ORDER BY searchedAt DESC LIMIT 8)")
    suspend fun trimSearchHistory()
}

@Database(
    entities = [CatalogCacheEntity::class, LibraryEntity::class, SearchHistoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class FrctlDatabase : RoomDatabase() {
    abstract fun dao(): FrctlDao

    companion object {
        @Volatile private var instance: FrctlDatabase? = null

        fun get(context: Context): FrctlDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FrctlDatabase::class.java,
                "frctl-catalog.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library ADD COLUMN fallbackIconUrl TEXT")
            }
        }
    }
}
