package travel.roame.takehome.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

internal fun appDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    return Room.databaseBuilder<AppDatabase>(appContext, appContext.getDatabasePath(DATABASE_NAME).absolutePath)
}
