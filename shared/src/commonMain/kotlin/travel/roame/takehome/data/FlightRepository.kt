package travel.roame.takehome.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import travel.roame.takehome.data.local.CachedFlightEntity
import travel.roame.takehome.data.local.FlightCacheDao
import travel.roame.takehome.data.local.toDomain
import travel.roame.takehome.data.local.toEntity
import travel.roame.takehome.data.remote.FetchResult
import travel.roame.takehome.data.remote.FlightsApi
import travel.roame.takehome.model.FetchError
import travel.roame.takehome.model.Flight
import kotlin.time.Clock
import kotlin.time.Instant

/** What the database holds. A null [lastSuccessAt] means never synced; an empty list doesn't (R8). */
internal data class CacheSnapshot(val flights: List<Flight>, val lastSuccessAt: Instant?)

/** [error] is the latest refresh's failure. It stays during a retry, so the stale banner only clears on success (R11). */
internal data class SyncStatus(val isRefreshing: Boolean = false, val error: FetchError? = null)

internal interface FlightRepository {
    val cache: Flow<CacheSnapshot>
    val syncStatus: StateFlow<SyncStatus>

    /** Does nothing if a refresh is already running (R2). Reports failures through [syncStatus]. */
    suspend fun refresh()
}

internal class RoomFlightRepository(
    private val api: FlightsApi,
    private val dao: FlightCacheDao,
    private val clock: Clock,
) : FlightRepository {

    private val refreshLock = Mutex()
    private val status = MutableStateFlow(SyncStatus())

    override val syncStatus: StateFlow<SyncStatus> = status.asStateFlow()

    // Reads both tables together on each change. Observing them separately would briefly show
    // the new flights with the old timestamp.
    override val cache: Flow<CacheSnapshot> = dao.observeLastSuccessAt().map {
        val rows = dao.read()
        CacheSnapshot(rows.flights.map(CachedFlightEntity::toDomain), rows.lastSuccessAt?.let(Instant::fromEpochMilliseconds))
    }

    override suspend fun refresh() {
        if (!refreshLock.tryLock()) return
        try {
            status.update { it.copy(isRefreshing = true) }
            val error = when (val result = api.fetchFlights()) {
                is FetchResult.Success -> store(result.flights)
                is FetchResult.Failure -> result.error
            }
            status.value = SyncStatus(error = error)
        } catch (e: CancellationException) {
            status.update { it.copy(isRefreshing = false) }
            throw e
        } finally {
            refreshLock.unlock()
        }
    }

    /** Returns the error if the write failed, in which case the transaction has rolled back. */
    private suspend fun store(flights: List<Flight>): FetchError? = try {
        dao.replaceAll(flights.map(Flight::toEntity), clock.now().toEpochMilliseconds())
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FetchError.Unknown
    }
}
