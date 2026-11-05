package travel.roame.takehome.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.presentation.ExplorerStore
import travel.roame.takehome.presentation.UserMessageKind
import travel.roame.takehome.testing.FakeFlightRepository
import travel.roame.takehome.testing.FakeSavedFlightsRepository
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The "Fail next DB write" debug hook (TEST_PLAN §6), which shows V7 on screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class FailNextWriteSavedFlightsTest {

    private val paris = flight(id = "paris")
    private val database = FakeSavedFlightsRepository()
    private val saved = FailNextWriteSavedFlights(database)

    @Test
    fun v7_hook_writesNormallyUntilArmed() = runTest {
        assertTrue(saved.save(paris).isSuccess)
        assertTrue(saved.remove("paris").isSuccess)
        assertEquals(2, database.writes)
    }

    @Test
    fun v7_hook_failsTheNextSaveOnceWithoutWriting() = runTest {
        saved.failNextWrite()
        assertTrue(saved.save(paris).isFailure)
        assertEquals(0, database.writes)
        assertEquals(emptyList(), database.saved.value)

        assertTrue(saved.save(paris).isSuccess, "only one write fails")
        assertEquals(listOf("paris"), database.saved.value.map { it.flight.id })
    }

    @Test
    fun v7_hook_failsTheNextRemoveOnce() = runTest {
        saved.save(paris)
        saved.failNextWrite()
        assertTrue(saved.remove("paris").isFailure)
        assertEquals(listOf("paris"), database.saved.value.map { it.flight.id })

        assertTrue(saved.remove("paris").isSuccess)
        assertEquals(emptyList(), database.saved.value)
    }

    @Test
    fun v7_hook_armingTwiceStillFailsOnlyOnce() = runTest {
        saved.failNextWrite()
        saved.failNextWrite()
        assertTrue(saved.save(paris).isFailure)
        assertTrue(saved.save(paris).isSuccess)
    }

    @Test
    fun v7_hook_theStoreReportsTheFailureAndKeepsTheHeart() = runTest {
        val store = ExplorerStore(FakeFlightRepository(listOf(paris)), saved, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        saved.failNextWrite()

        store.toggleSave("paris")
        runCurrent()
        val state = store.state.value
        assertEquals(listOf(UserMessageKind.SaveFailed), state.messages.map { it.kind })
        assertEquals(false, state.results.single().isSaved)
        assertEquals(emptySet(), state.pendingWriteIds)
    }
}
