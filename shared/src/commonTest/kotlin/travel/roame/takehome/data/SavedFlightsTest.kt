package travel.roame.takehome.data

import androidx.room.execSQL
import androidx.room.useWriterConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.data.local.SavedFlightDao
import travel.roame.takehome.data.local.SavedFlightEntity
import travel.roame.takehome.data.local.configure
import travel.roame.takehome.data.remote.FetchResult
import travel.roame.takehome.model.FetchError
import travel.roame.takehome.model.SavedFlight
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Saved flights on a real database: DOMAIN_RULES V1, V2, V4–V7, O3. */
class SavedFlightsTest {

    private val db = testDb()
    private val api = FakeFlightsApi()
    private val clock = TestClock(Instant.fromEpochMilliseconds(1_000))
    private val savedAtFirst = clock.current
    private val saved = RoomSavedFlightsRepository(db.savedFlightDao(), clock)
    private val cache = RoomFlightRepository(api, db.flightCacheDao(), clock)

    @AfterTest
    fun closeDatabase() = db.close()

    private suspend fun savedFlights() = saved.saved.first()

    private suspend fun savedIds() = savedFlights().map { it.flight.id }

    private fun later() {
        clock.current = Instant.fromEpochMilliseconds(clock.current.toEpochMilliseconds() + 1_000)
    }

    /** Makes SQLite itself refuse the write, so the failure takes the same path as a real one. */
    private suspend fun rejectWrites(table: String, operation: String) = db.useWriterConnection { connection ->
        connection.execSQL("CREATE TRIGGER reject_write BEFORE $operation ON $table BEGIN SELECT RAISE(ABORT, 'rejected'); END")
    }

    // Snapshots (V1)

    @Test
    fun v1_everyFieldAndTheSaveTimeSurviveTheDatabase() = runTest {
        val expected = flight(
            id = "fx-JFK-HKG-20270212",
            origin = "JFK",
            destination = "HKG",
            date = "2027-02-12",
            points = 101_500,
            cabin = "Premium Economy",
            program = "Mileage Plan",
        )
        assertTrue(saved.save(expected).isSuccess)
        assertEquals(listOf(SavedFlight(expected, savedAtFirst)), savedFlights())
    }

    @Test
    fun v1_savingNeedsNoCacheAndDoesNotChangeIt() = runTest {
        saved.save(flight(id = "a"))
        assertEquals(listOf("a"), savedIds())
        assertEquals(CacheSnapshot(emptyList(), null), cache.cache.first())
    }

    @Test
    fun v1_savedFlightsSurviveClosingAndReopeningTheFile() = runTest {
        val path = newTempDatabasePath()
        try {
            val expected = flight(id = "kept", program = "Flying Blue")
            val firstRun = fileDatabaseBuilder(path).configure()
            RoomSavedFlightsRepository(firstRun.savedFlightDao(), clock).save(expected)
            firstRun.close()

            val secondRun = fileDatabaseBuilder(path).configure()
            val reopened = RoomSavedFlightsRepository(secondRun.savedFlightDao(), clock).saved.first()
            secondRun.close()
            assertEquals(listOf(SavedFlight(expected, savedAtFirst)), reopened)
        } finally {
            deleteDatabaseFiles(path)
        }
    }

    // One row per id (V2)

    @Test
    fun v2_savingAgainLeavesOneRowWithNewDetailsAndTheOriginalTime() = runTest {
        saved.save(flight(id = "a", points = 25_000))
        later()
        assertTrue(saved.save(flight(id = "a", points = 30_000, cabin = "Business")).isSuccess)

        assertEquals(listOf(SavedFlight(flight(id = "a", points = 30_000, cabin = "Business"), savedAtFirst)), savedFlights())
    }

    @Test
    fun v2_eachFlightGetsItsOwnRowAndTime() = runTest {
        saved.save(flight(id = "a"))
        later()
        saved.save(flight(id = "b"))

        assertEquals(listOf(savedAtFirst, clock.current), savedFlights().map { it.savedAt })
    }

    // Missing from the API (V4)

    @Test
    fun v4_savedFlightSurvivesARefreshThatDropsIt() = runTest {
        val kept = flight(id = "dropped", points = 25_000)
        saved.save(kept)
        later()
        api.enqueue(flight(id = "other"))
        cache.refresh()

        assertEquals(listOf(SavedFlight(kept, savedAtFirst)), savedFlights())
    }

    @Test
    fun v4_savedFlightSurvivesAnEmptyRefresh() = runTest {
        saved.save(flight(id = "a"))
        api.enqueue(FetchResult.Success(emptyList()))
        cache.refresh()

        assertEquals(listOf("a"), savedIds())
    }

    // Kept up to date by a refresh (V5)

    @Test
    fun v5_refreshUpdatesTheDetailsOfASavedFlightAndKeepsItsTime() = runTest {
        saved.save(flight(id = "a", points = 25_000))
        later()
        val changed = flight(id = "a", points = 40_000, cabin = "Business", program = "Aeroplan", date = "2027-03-01")
        api.enqueue(changed)
        cache.refresh()

        assertEquals(listOf(SavedFlight(changed, savedAtFirst)), savedFlights())
    }

    @Test
    fun v5_refreshOnlyTouchesFlightsThatAreBothSavedAndInTheResponse() = runTest {
        val dropped = flight(id = "dropped", points = 10_000)
        saved.save(dropped)
        saved.save(flight(id = "listed", points = 10_000))

        api.enqueue(flight(id = "listed", points = 20_000), flight(id = "never-saved"))
        cache.refresh()

        assertEquals(
            listOf(flight(id = "dropped", points = 10_000), flight(id = "listed", points = 20_000)),
            savedFlights().map { it.flight },
        )
    }

    @Test
    fun v5_failedFetchLeavesSavedFlightsUntouched() = runTest {
        saved.save(flight(id = "a", points = 25_000))
        val before = savedFlights()
        api.enqueue(FetchResult.Failure(FetchError.Offline))
        cache.refresh()

        assertEquals(before, savedFlights())
    }

    @Test
    fun v5_failureWhileUpdatingSavedFlightsRollsBackTheCacheToo() = runTest {
        api.enqueue(flight(id = "old"))
        cache.refresh()
        val cacheBefore = cache.cache.first()
        saved.save(flight(id = "a", points = 25_000))
        val savedBefore = savedFlights()

        rejectWrites("saved_flight", "INSERT")
        later()
        api.enqueue(flight(id = "a", points = 40_000))
        cache.refresh()

        assertEquals(FetchError.Unknown, cache.syncStatus.value.error)
        assertEquals(cacheBefore, cache.cache.first())
        assertEquals(savedBefore, savedFlights())
    }

    @Test
    fun v5_failureAfterUpdatingSavedFlightsRollsThemBack() = runTest {
        saved.save(flight(id = "a", points = 25_000))
        val before = savedFlights()

        rejectWrites("sync_meta", "INSERT")
        api.enqueue(flight(id = "a", points = 40_000))
        cache.refresh()

        assertEquals(FetchError.Unknown, cache.syncStatus.value.error)
        assertEquals(before, savedFlights())
    }

    @Test
    fun v5_failureHalfwayThroughARefreshLeavesSavedFlightsUntouched() = runTest {
        saved.save(flight(id = "a", points = 25_000))
        val before = savedFlights()
        // The repeated id breaks the primary key, so the whole transaction rolls back.
        api.enqueue(flight(id = "a", points = 40_000), flight(id = "a", points = 50_000))
        cache.refresh()

        assertEquals(FetchError.Unknown, cache.syncStatus.value.error)
        assertEquals(before, savedFlights())
    }

    // Removal (V6)

    @Test
    fun v6_removeDeletesOnlyThatFlight() = runTest {
        saved.save(flight(id = "a"))
        saved.save(flight(id = "b"))
        assertTrue(saved.remove("a").isSuccess)
        assertEquals(listOf("b"), savedIds())
    }

    @Test
    fun v6_removingAFlightThatIsNotSavedIsNotAnError() = runTest {
        saved.save(flight(id = "a"))
        assertTrue(saved.remove("missing").isSuccess)
        assertEquals(listOf("a"), savedIds())
    }

    @Test
    fun v6_removeLeavesTheCacheAlone() = runTest {
        api.enqueue(flight(id = "a"))
        cache.refresh()
        saved.save(flight(id = "a"))
        saved.remove("a")

        assertEquals(listOf("a"), cache.cache.first().flights.map { it.id })
    }

    // Write failures (V7, R12)

    @Test
    fun v7_failedSaveComesBackAsAFailedResultAndChangesNothing() = runTest {
        rejectWrites("saved_flight", "INSERT")
        assertTrue(saved.save(flight(id = "a")).isFailure)
        assertEquals(emptyList(), savedIds())
    }

    @Test
    fun v7_failedRemoveComesBackAsAFailedResultAndChangesNothing() = runTest {
        saved.save(flight(id = "a"))
        rejectWrites("saved_flight", "DELETE")
        assertTrue(saved.remove("a").isFailure)
        assertEquals(listOf("a"), savedIds())
    }

    @Test
    fun r12_cancelledWritesAreRethrownNotReportedAsFailures() = runTest {
        val cancelled = RoomSavedFlightsRepository(CancellingDao(), clock)
        assertFailsWith<CancellationException> { cancelled.save(flight()) }
        assertFailsWith<CancellationException> { cancelled.remove("a") }
    }

    private class CancellingDao : SavedFlightDao() {
        override fun observeAll(): Flow<List<SavedFlightEntity>> = emptyFlow()

        override suspend fun save(saved: SavedFlightEntity) = throw CancellationException("stop")

        override suspend fun remove(id: String) = throw CancellationException("stop")

        override suspend fun findById(id: String): SavedFlightEntity? = null

        override suspend fun upsert(saved: SavedFlightEntity) = Unit
    }

    // Order (O3)

    @Test
    fun o3_savedFlightsAreSortedByDateThenId() = runTest {
        saved.save(flight(id = "b", date = "2027-02-02"))
        saved.save(flight(id = "c", date = "2027-02-01"))
        saved.save(flight(id = "a", date = "2027-02-02"))

        assertEquals(listOf("c", "a", "b"), savedIds())
    }
}
