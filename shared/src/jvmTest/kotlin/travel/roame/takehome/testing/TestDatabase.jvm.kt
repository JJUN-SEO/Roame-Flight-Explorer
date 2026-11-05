package travel.roame.takehome.testing

import androidx.room.Room
import androidx.room.RoomDatabase
import travel.roame.takehome.data.local.AppDatabase
import java.io.File

internal actual fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> =
    Room.inMemoryDatabaseBuilder<AppDatabase>()

internal actual fun fileDatabaseBuilder(path: String): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(name = path)

internal actual fun newTempDatabasePath(): String =
    File.createTempFile("roame-test", ".db").apply { delete() }.absolutePath

internal actual fun deleteDatabaseFiles(path: String) {
    listOf("", "-wal", "-shm").forEach { File(path + it).delete() }
}
