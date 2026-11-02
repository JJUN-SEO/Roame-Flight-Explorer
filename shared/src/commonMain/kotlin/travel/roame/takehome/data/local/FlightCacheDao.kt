package travel.roame.takehome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

internal data class CachedRows(val flights: List<CachedFlightEntity>, val lastSuccessAt: Long?)

@Dao
internal abstract class FlightCacheDao {
    /** Emits at the start and after every commit to `sync_meta`, which every [replaceAll] writes. */
    @Query("SELECT lastSuccessAt FROM sync_meta WHERE id = 0")
    abstract fun observeLastSuccessAt(): Flow<Long?>

    /** One transaction, so the flights and the timestamp always come from the same refresh. */
    @Transaction
    open suspend fun read(): CachedRows = CachedRows(getAll(), getLastSuccessAt())

    /** R3 and V5: one transaction, so a failure partway leaves the cache and saved flights as they were. */
    @Transaction
    open suspend fun replaceAll(flights: List<CachedFlightEntity>, now: Long) {
        deleteAll()
        insertAll(flights)
        refreshSavedDetails()
        upsertMeta(SyncMetaEntity(lastSuccessAt = now))
    }

    @Query("SELECT * FROM cached_flight")
    protected abstract suspend fun getAll(): List<CachedFlightEntity>

    @Query("SELECT lastSuccessAt FROM sync_meta WHERE id = 0")
    protected abstract suspend fun getLastSuccessAt(): Long?

    @Query("DELETE FROM cached_flight")
    protected abstract suspend fun deleteAll()

    @Insert
    protected abstract suspend fun insertAll(flights: List<CachedFlightEntity>)

    /** V5: only flights that are both saved and in the new cache change, and they keep their `savedAt`. */
    @Query(
        """
        INSERT OR REPLACE INTO saved_flight (
            id, origin, originCity, originLat, originLon,
            destination, destinationCity, destinationLat, destinationLon,
            departureDate, program, points, cabin, savedAt
        )
        SELECT
            c.id, c.origin, c.originCity, c.originLat, c.originLon,
            c.destination, c.destinationCity, c.destinationLat, c.destinationLon,
            c.departureDate, c.program, c.points, c.cabin, s.savedAt
        FROM saved_flight AS s
        JOIN cached_flight AS c ON c.id = s.id
        """,
    )
    protected abstract suspend fun refreshSavedDetails()

    @Upsert
    protected abstract suspend fun upsertMeta(meta: SyncMetaEntity)
}
