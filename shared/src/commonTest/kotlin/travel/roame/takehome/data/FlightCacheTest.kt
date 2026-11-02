package travel.roame.takehome.data

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import travel.roame.takehome.data.local.configure
import travel.roame.takehome.data.remote.FetchResult
import travel.roame.takehome.model.FetchError
import travel.roame.takehome.testing.FakeFlightsApi
import travel.roame.takehome.testing.TestClock
import travel.roame.takehome.testing.deleteDatabaseFiles
import travel.roame.takehome.testing.fileDatabaseBuilder
import travel.roame.takehome.testing.flight
import travel.roame.takehome.testing.newTempDatabasePath
import travel.roame.takehome.testing.testDb
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The cache and refresh, on a real database with a fake network: DOMAIN_RULES R1–R5, R8, R11, R12. */
class FlightCacheTest {

    private val db = testDb()
    private val api = FakeFlightsApi()
    private val clock = TestClock()
    private val repository = RoomFlightRepository(api, db.flightCacheDao(), clock)

    private val neverSynced = CacheSnapshot(emptyList(), null)

    @AfterTest
    fun closeDatabase() = db.close()

    private suspend fun cached() = repository.cache.first()

    private suspend fun cachedIds() = cached().flights.map { it.id }.toSet()

    /** Puts [ids] in the cache through a successful refresh. */
    private suspend fun seed(vararg ids: String) {
        api.enqueue(*ids.map { flight(id = it) }.toTypedArray())
        repository.refresh()
    }

    // Launch (R1)

    @Test
    fun r1_cachedDataIsAvailableWhileAFetchIsStillRunning() = runTest {
        seed("old")
        api.gate = CompletableDeferred()
        api.enqueue(flight(id = "new"))

        val refresh = launch { repository.refresh() }
        api.awaitFetchInFlight()

        assertEquals(setOf("old"), cachedIds())

        api.gate?.complete(Unit)
        refresh.join()
    }

    // One at a time (R2)

    @Test
    fun r2_refreshWhileOneIsRunning_isIgnored() = runTest {
        api.gate = CompletableDeferred()
        api.enqueue(flight(id = "a"))

        val first = launch { repository.refresh() }
        api.awaitFetchInFlight()
        repository.refresh()

        assertEquals(1, api.calls)
        assertTrue(repository.syncStatus.value.isRefreshing)

        api.gate?.complete(Unit)
        first.join()
        assertEquals(setOf("a"), cachedIds())
    }

    @Test
    fun r2_refreshAfterTheFirstFinishes_runsAgain() = runTest {
        seed("a")
        seed("b")
        assertEquals(2, api.calls)
        assertEquals(setOf("b"), cachedIds())
    }

    // Success (R3, R4)

    @Test
    fun r3_success_replacesEveryRowAndRecordsTheTime() = runTest {
        seed("old-1", "old-2")
        clock.current = Instant.fromEpochMilliseconds(5_000)
        seed("new")

        assertEquals(setOf("new"), cachedIds())
        assertEquals(clock.current, cached().lastSuccessAt)
        assertEquals(SyncStatus(), repository.syncStatus.value)
    }

    @Test
    fun r3_everyFieldSurvivesTheDatabase() = runTest {
        val expected = flight(
            id = "fx-JFK-HKG-20270212",
            origin = "JFK",
            destination = "HKG",
            date = "2027-02-12",
            points = 101_500,
            cabin = "Premium Economy",
            program = "Mileage Plan",
        )
        api.enqueue(expected)
        repository.refresh()
        assertEquals(listOf(expected), cached().flights)
    }

    @Test
    fun r3_failureHalfwayThroughTheInsert_leavesTheOldCacheIntact() = runTest {
        seed("old-1", "old-2")
        val before = cached()

        // The repeated id breaks the primary key after some rows have already gone in.
        clock.current = Instant.fromEpochMilliseconds(9_000)
        api.enqueue(flight(id = "new-1"), flight(id = "new-2"), flight(id = "new-1"))
        repository.refresh()

        assertEquals(before, cached())
        assertEquals(SyncStatus(error = FetchError.Unknown), repository.syncStatus.value)
    }

    @Test
    fun r3_aRefreshAfterAFailedWrite_works() = runTest {
        api.enqueue(flight(id = "dup"), flight(id = "dup"))
        repository.refresh()
        seed("good")
        assertEquals(setOf("good"), cachedIds())
        assertNull(repository.syncStatus.value.error)
    }

    /** On a file, where reads and writes use separate connections and really can overlap. */
    @Test
    fun r3_observersNeverSeeAHalfAppliedRefresh() = runTest {
        val path = newTempDatabasePath()
        val fileDb = fileDatabaseBuilder(path).configure()
        try {
            val repository = RoomFlightRepository(api, fileDb.flightCacheDao(), clock)
            val rounds = 100L
            withContext(Dispatchers.Default) {
                val observer = launch {
                    repository.cache.takeWhile { it.lastSuccessAt != Instant.fromEpochMilliseconds(rounds) }.collect { snapshot ->
                        val round = snapshot.lastSuccessAt?.toEpochMilliseconds()
                        assertTrue(snapshot.flights.all { it.id.startsWith("$round-") }, "flights from another refresh than $round")
                    }
                }
                for (round in 1L..rounds) {
                    clock.current = Instant.fromEpochMilliseconds(round)
                    api.enqueue(*List(200) { flight(id = "$round-$it") }.toTypedArray())
                    repository.refresh()
                }
                observer.join()
            }
        } finally {
            fileDb.close()
            deleteDatabaseFiles(path)
        }
    }

    @Test
    fun r3_aRefreshWithTheSameTimestamp_stillNotifiesObservers() = runTest {
        repository.cache.test {
            assertEquals(neverSynced, awaitItem())
            seed("a")
            assertEquals(setOf("a"), awaitItem().flights.map { it.id }.toSet())
            seed("b")
            assertEquals(setOf("b"), awaitItem().flights.map { it.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun r4_emptySuccess_emptiesTheCacheAndRecordsTheTime() = runTest {
        seed("old")
        clock.current = Instant.fromEpochMilliseconds(5_000)
        seed()

        assertEquals(CacheSnapshot(emptyList(), clock.current), cached())
        assertNull(repository.syncStatus.value.error)
    }

    // Failure (R5, R8, R11)

    @Test
    fun r5_everyFailedFetch_leavesTheCacheUntouchedAndReportsWhy() = runTest {
        seed("old")
        val before = cached()
        val errors = listOf(
            FetchError.Offline,
            FetchError.Timeout,
            FetchError.Server(503),
            FetchError.InvalidData,
            FetchError.Unknown,
        )
        for (error in errors) {
            clock.current = Instant.fromEpochMilliseconds(99_000)
            api.enqueue(FetchResult.Failure(error))
            repository.refresh()

            assertEquals(before, cached(), "$error")
            assertEquals(SyncStatus(error = error), repository.syncStatus.value)
        }
    }

    @Test
    fun r8_neverSyncedDiffersFromSyncedEmpty() = runTest {
        assertEquals(neverSynced, cached())

        api.enqueue(FetchResult.Failure(FetchError.Offline))
        repository.refresh()
        assertEquals(neverSynced, cached(), "a failed fetch is still never synced")

        seed()
        assertEquals(CacheSnapshot(emptyList(), clock.current), cached())
    }

    @Test
    fun r11_errorStaysDuringARetryAndClearsOnSuccess() = runTest {
        seed("old")
        api.enqueue(FetchResult.Failure(FetchError.Offline))
        repository.refresh()

        api.gate = CompletableDeferred()
        api.enqueue(flight(id = "new"))
        val retry = launch { repository.refresh() }
        api.awaitFetchInFlight()
        assertEquals(SyncStatus(isRefreshing = true, error = FetchError.Offline), repository.syncStatus.value)

        api.gate?.complete(Unit)
        retry.join()
        assertEquals(SyncStatus(), repository.syncStatus.value)
    }

    // Cancellation (R12)

    @Test
    fun r12_cancelledRefresh_isNotAnErrorAndDoesNotBlockTheNextOne() = runTest {
        api.gate = CompletableDeferred()
        val refresh = launch { repository.refresh() }
        api.awaitFetchInFlight()
        refresh.cancelAndJoin()

        assertEquals(SyncStatus(), repository.syncStatus.value)

        api.gate = null
        seed("after")
        assertEquals(setOf("after"), cachedIds())
    }

    // Surviving a restart (R1, DATA-4)

    @Test
    fun r1_cachedDataSurvivesClosingAndReopeningTheFile() = runTest {
        val path = newTempDatabasePath()
        try {
            val saved = flight(id = "kept", program = "Flying Blue")
            val firstRun = fileDatabaseBuilder(path).configure()
            api.enqueue(saved)
            RoomFlightRepository(api, firstRun.flightCacheDao(), clock).refresh()
            firstRun.close()

            val secondRun = fileDatabaseBuilder(path).configure()
            val reopened = RoomFlightRepository(FakeFlightsApi(), secondRun.flightCacheDao(), clock)
            assertEquals(CacheSnapshot(listOf(saved), clock.current), reopened.cache.first())
            assertFalse(reopened.syncStatus.value.isRefreshing)
            secondRun.close()
        } finally {
            deleteDatabaseFiles(path)
        }
    }
}
