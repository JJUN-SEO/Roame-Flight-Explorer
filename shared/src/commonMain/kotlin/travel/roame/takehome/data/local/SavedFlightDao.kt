package travel.roame.takehome.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class SavedFlightDao {
    /** O3. */
    @Query("SELECT * FROM saved_flight ORDER BY departureDate, id")
    abstract fun observeAll(): Flow<List<SavedFlightEntity>>

    /** V2: one row per id. Saving again updates the details and keeps the original `savedAt`. */
    @Transaction
    open suspend fun save(saved: SavedFlightEntity) {
        val existing = findById(saved.flight.id)
        upsert(saved.copy(savedAt = existing?.savedAt ?: saved.savedAt))
    }

    @Query("DELETE FROM saved_flight WHERE id = :id")
    abstract suspend fun remove(id: String)

    @Query("SELECT * FROM saved_flight WHERE id = :id")
    protected abstract suspend fun findById(id: String): SavedFlightEntity?

    @Upsert
    protected abstract suspend fun upsert(saved: SavedFlightEntity)
}
