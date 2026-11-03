package travel.roame.takehome.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.data.FlightRepository
import travel.roame.takehome.data.RoomFlightRepository
import travel.roame.takehome.data.remote.FetchResult
import travel.roame.takehome.model.FetchError
import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.testing.FakeFlightsApi
import travel.roame.takehome.testing.TestClock
import travel.roame.takehome.testing.awaitState
import travel.roame.takehome.testing.flight
import travel.roame.takehome.testing.testDb
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The store on a real database with a fake network: DOMAIN_RULES R1, R2, R4–R8, R11. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStoreOfflineTest {

    private val db = testDb()
    private val api = FakeFlightsApi()
    private val clock = TestClock(Instant.fromEpochMilliseconds(1_000))
    private val repository = RoomFlightRepository(api, db.flightCacheDao(), clock)
    private val storeJob = SupervisorJob()

    @AfterTest
    fun closeDatabase() = db.close()

    /** A finished, successful sync. The cache updates a moment before the refreshing flag clears, so wait for this. */
    private val synced = DataStatus.Ready(lastSuccessAt = 1_000, isRefreshing = false, refreshError = null)

    private fun TestScope.newStore(flights: FlightRepository = repository) =
        ExplorerStore(flights, CoroutineScope(storeJob + StandardTestDispatcher(testScheduler)))

    /** Stops every store before the database closes. Otherwise a read still running on a real thread hits a closed database. */
    private fun runStoreTest(test: suspend TestScope.() -> Unit) = runTest {
        try {
            test()
        } finally {
            storeJob.cancelAndJoin()
        }
    }

    private fun ids(state: ExplorerState) = state.results.map { it.id }

    /** Puts flights in the cache before the store exists, as a previous run of the app would have. */
    private suspend fun cacheFromAnEarlierRun(vararg ids: String) {
        api.enqueue(*ids.map { flight(id = it) }.toTypedArray())
        repository.refresh()
    }

    private suspend fun ExplorerStore.awaitIds(vararg ids: String) = awaitState { ids(it) == ids.toList() }

    @Test
    fun r1_cachedDataAppearsBeforeTheNetworkResponds() = runStoreTest {
        cacheFromAnEarlierRun("cached")
        api.gate = CompletableDeferred()
        api.enqueue(flight(id = "fresh"))

        val store = newStore()
        api.awaitFetchInFlight()
        val state = store.awaitState { it.data is DataStatus.Ready }

        assertEquals(listOf("cached"), ids(state))
        assertEquals(2, api.calls, "the earlier run's fetch, plus the one the store started")

        api.gate?.complete(Unit)
        store.awaitIds("fresh")
    }

    @Test
    fun r2_refreshesWhileOneIsRunningAreIgnored() = runStoreTest {
        api.gate = CompletableDeferred()
        api.enqueue(flight(id = "a"))

        val store = newStore()
        api.awaitFetchInFlight()
        store.refresh()
        store.refresh()
        runCurrent()

        api.gate?.complete(Unit)
        store.awaitIds("a")
        assertEquals(1, api.calls)
    }

    @Test
    fun r2_refreshRunsAgainOnceTheFirstHasFinished() = runStoreTest {
        api.enqueue(flight(id = "a"))
        val store = newStore()
        store.awaitIds("a")

        api.enqueue(flight(id = "b"))
        store.refresh()
        store.awaitIds("b")
    }

    @Test
    fun r3_aSuccessfulFetchIsReadyWithItsTime() = runStoreTest {
        api.enqueue(flight(id = "a"))
        val state = newStore().awaitState { it.data == synced }

        assertEquals(listOf("a"), ids(state))
    }

    @Test
    fun r4_anEmptyResponseIsReadyAndNotAnError() = runStoreTest {
        api.enqueue(FetchResult.Success(emptyList()))
        val state = newStore().awaitState { it.data == synced }

        assertEquals(emptyList(), state.results)
        assertTrue(state.options.isEmpty, "the apps use this to tell synced empty from filtered empty")
    }

    @Test
    fun r6_aFailedRefreshKeepsTheDataAndReportsIt() = runStoreTest {
        cacheFromAnEarlierRun("cached")
        for (error in listOf(FetchError.Offline, FetchError.Timeout, FetchError.Server(503), FetchError.InvalidData, FetchError.Unknown)) {
            api.enqueue(FetchResult.Failure(error))
            val store = newStore(RoomFlightRepository(api, db.flightCacheDao(), clock))
            val state = store.awaitState { (it.data as? DataStatus.Ready)?.refreshError == error }

            assertEquals(listOf("cached"), ids(state), "$error")
            assertEquals(1_000, (state.data as DataStatus.Ready).lastSuccessAt)
        }
    }

    @Test
    fun r6_filtersKeepWorkingAfterAFailedRefresh() = runStoreTest {
        api.enqueue(flight(id = "cheap", points = 10_000), flight(id = "pricey", points = 90_000))
        repository.refresh()
        api.enqueue(FetchResult.Failure(FetchError.Offline))

        val store = newStore()
        store.awaitState { (it.data as? DataStatus.Ready)?.refreshError == FetchError.Offline }
        store.setMaxPoints(20_000)

        val state = store.awaitState { it.filters == FlightFilters(maxPoints = 20_000) }
        assertEquals(listOf("cheap"), ids(state))
        assertEquals(FetchError.Offline, (state.data as DataStatus.Ready).refreshError)
    }

    @Test
    fun r7_aFailureWithNothingCachedIsUnavailableWithItsCause() = runStoreTest {
        for (error in listOf(FetchError.Offline, FetchError.Timeout, FetchError.Server(503), FetchError.InvalidData, FetchError.Unknown)) {
            api.enqueue(FetchResult.Failure(error))
            val store = newStore(RoomFlightRepository(api, db.flightCacheDao(), clock))
            val state = store.awaitState { it.data == DataStatus.Unavailable(error) }
            assertEquals(emptyList(), state.results)
        }
    }

    @Test
    fun r7_retryingShowsLoadingAndThenTheData() = runStoreTest {
        api.enqueue(FetchResult.Failure(FetchError.Offline))
        val store = newStore()
        store.awaitState { it.data == DataStatus.Unavailable(FetchError.Offline) }

        api.gate = CompletableDeferred()
        api.enqueue(flight(id = "a"))
        store.refresh()
        api.awaitFetchInFlight()
        store.awaitState { it.data == DataStatus.Loading }

        api.gate?.complete(Unit)
        store.awaitIds("a")
    }

    @Test
    fun r8_aFailedFetchAfterAnEmptySyncIsStaleAndNotUnavailable() = runStoreTest {
        api.enqueue(FetchResult.Success(emptyList()))
        repository.refresh()
        api.enqueue(FetchResult.Failure(FetchError.Offline))

        val state = newStore().awaitState { (it.data as? DataStatus.Ready)?.refreshError == FetchError.Offline }
        assertEquals(emptyList(), state.results)
    }

    @Test
    fun r11_aRefreshOverDataShowsProgressAndNotALoader() = runStoreTest {
        cacheFromAnEarlierRun("cached")
        api.gate = CompletableDeferred()
        api.enqueue(flight(id = "fresh"))

        val store = newStore()
        api.awaitFetchInFlight()
        val state = store.awaitState { (it.data as? DataStatus.Ready)?.isRefreshing == true }
        assertEquals(listOf("cached"), ids(state))

        api.gate?.complete(Unit)
        store.awaitIds("fresh")
    }

    @Test
    fun r11_theStaleErrorStaysDuringARetryAndClearsOnSuccess() = runStoreTest {
        cacheFromAnEarlierRun("cached")
        api.enqueue(FetchResult.Failure(FetchError.Offline))
        val store = newStore()
        store.awaitState { (it.data as? DataStatus.Ready)?.refreshError == FetchError.Offline }

        api.gate = CompletableDeferred()
        clock.current = Instant.fromEpochMilliseconds(9_000)
        api.enqueue(flight(id = "fresh"))
        store.refresh()
        api.awaitFetchInFlight()
        store.awaitState {
            val ready = it.data as? DataStatus.Ready
            ready?.isRefreshing == true && ready.refreshError == FetchError.Offline
        }

        api.gate?.complete(Unit)
        val state = store.awaitState { it.data == DataStatus.Ready(9_000, isRefreshing = false, refreshError = null) }
        assertEquals(listOf("fresh"), ids(state))
    }
}
