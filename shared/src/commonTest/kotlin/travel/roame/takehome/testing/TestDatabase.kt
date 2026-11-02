package travel.roame.takehome.testing

import androidx.room.RoomDatabase
import travel.roame.takehome.data.local.AppDatabase
import travel.roame.takehome.data.local.configure

internal expect fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

internal expect fun fileDatabaseBuilder(path: String): RoomDatabase.Builder<AppDatabase>

internal expect fun newTempDatabasePath(): String

/** Also removes SQLite's `-wal` and `-shm` files. */
internal expect fun deleteDatabaseFiles(path: String)

internal fun testDb(): AppDatabase = inMemoryDatabaseBuilder().configure()
