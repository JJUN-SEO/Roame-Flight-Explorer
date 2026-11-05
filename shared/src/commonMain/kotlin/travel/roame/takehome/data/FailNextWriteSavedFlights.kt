package travel.roame.takehome.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import travel.roame.takehome.model.Flight

/**
 * The "Fail next DB write" debug hook (TEST_PLAN §6): once armed, the next save or remove fails
 * without touching the database, so QA can see V7 on screen (Q20, OFF-8). Only debug builds arm it.
 */
internal class FailNextWriteSavedFlights(private val delegate: SavedFlightsRepository) : SavedFlightsRepository by delegate {
    private val armed = MutableStateFlow(false)

    fun failNextWrite() {
        armed.value = true
    }

    override suspend fun save(flight: Flight) = if (disarm()) injectedFailure() else delegate.save(flight)

    override suspend fun remove(id: String) = if (disarm()) injectedFailure() else delegate.remove(id)

    /** Atomic, so two writes racing can't both fail. */
    private fun disarm() = armed.getAndUpdate { false }

    private fun injectedFailure() = Result.failure<Unit>(IllegalStateException("Failed by the debug hook"))
}
