package travel.roame.takehome.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/** Passes only the file name, because resolving the full path touches the disk and this runs on the main thread. */
internal fun appDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(context.applicationContext, DATABASE_NAME)
