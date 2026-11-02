package travel.roame.takehome.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import travel.roame.takehome.data.remote.FetchResult
import travel.roame.takehome.data.remote.FlightsApi
import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.Flight
import kotlin.time.Clock
import kotlin.time.Instant

/** The two airports' coordinates differ in every column, so a mixed-up mapping can't go unnoticed. */
internal fun flight(
    id: String = "x",
    origin: String = "BOS",
    destination: String = "CDG",
    date: String = "2027-02-01",
    points: Int = 25_000,
    cabin: String = "Economy",
    program: String = "Avios",
) = Flight(
    id = id,
    origin = Airport(origin, "$origin city", 42.36, -71.0),
    destination = Airport(destination, "$destination city", 49.0, 2.55),
    departureDate = LocalDate.parse(date),
    program = program,
    points = points,
    cabin = cabin,
)

/** Answers each fetch with the next enqueued result. */
internal class FakeFlightsApi : FlightsApi {
    private val results = ArrayDeque<FetchResult>()
    private val waiting = MutableStateFlow(0)

    var calls = 0
        private set

    /** While set, fetches wait for it, so a test can look at a refresh in flight. */
    var gate: CompletableDeferred<Unit>? = null

    fun enqueue(vararg results: FetchResult) {
        this.results.addAll(results)
    }

    fun enqueue(vararg flights: Flight) = enqueue(FetchResult.Success(flights.toList()))

    /** Suspends until a fetch is waiting at [gate]. */
    suspend fun awaitFetchInFlight() {
        waiting.first { it > 0 }
    }

    override suspend fun fetchFlights(): FetchResult {
        calls++
        gate?.let { gate ->
            waiting.update { it + 1 }
            try {
                gate.await()
            } finally {
                waiting.update { it - 1 }
            }
        }
        return results.removeFirst()
    }
}

internal class TestClock(var current: Instant = Instant.fromEpochMilliseconds(1_000)) : Clock {
    override fun now(): Instant = current
}
