package travel.roame.takehome.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import travel.roame.takehome.data.local.SavedFlightDao
import travel.roame.takehome.data.local.SavedFlightEntity
import travel.roame.takehome.data.local.toDomain
import travel.roame.takehome.data.local.toSavedEntity
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.SavedFlight
import kotlin.time.Clock

internal interface SavedFlightsRepository {
    /** The only source of saved state. It changes when the database does, never before (V7). */
    val saved: Flow<List<SavedFlight>>

    /** A failed write comes back as a failed [Result] (V7). Only cancellation is thrown (R12). */
    suspend fun save(flight: Flight): Result<Unit>

    /** A failed write comes back as a failed [Result] (V7). Only cancellation is thrown (R12). */
    suspend fun remove(id: String): Result<Unit>
}

internal class RoomSavedFlightsRepository(
    private val dao: SavedFlightDao,
    private val clock: Clock,
) : SavedFlightsRepository {

    override val saved: Flow<List<SavedFlight>> = dao.observeAll().map { rows -> rows.map(SavedFlightEntity::toDomain) }

    override suspend fun save(flight: Flight) = write { dao.save(flight.toSavedEntity(clock.now().toEpochMilliseconds())) }

    override suspend fun remove(id: String) = write { dao.remove(id) }

    private suspend fun write(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
