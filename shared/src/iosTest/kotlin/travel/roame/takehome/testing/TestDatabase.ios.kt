package travel.roame.takehome.testing

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import travel.roame.takehome.data.local.AppDatabase

internal actual fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> =
    Room.inMemoryDatabaseBuilder<AppDatabase>()

internal actual fun fileDatabaseBuilder(path: String): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(name = path)

internal actual fun newTempDatabasePath(): String =
    "${NSTemporaryDirectory()}roame-test-${NSUUID().UUIDString}.db"

@OptIn(ExperimentalForeignApi::class)
internal actual fun deleteDatabaseFiles(path: String) {
    listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, null) }
}
