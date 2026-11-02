package travel.roame.takehome.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal const val DATABASE_NAME = "roame.db"

@Database(entities = [CachedFlightEntity::class, SavedFlightEntity::class, SyncMetaEntity::class], version = 1)
@ConstructedBy(AppDatabaseConstructor::class)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun flightCacheDao(): FlightCacheDao

    abstract fun savedFlightDao(): SavedFlightDao
}

/** Room's generator writes the `actual` for every target. */
@Suppress("KotlinNoActualForExpect")
internal expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** The setup every platform shares; only the file location differs (ARCHITECTURE §5.3). */
internal fun RoomDatabase.Builder<AppDatabase>.configure(): AppDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
