package travel.roame.takehome.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.data.CacheSnapshot
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.SavedFlight
import travel.roame.takehome.testing.FakeFlightRepository
import travel.roame.takehome.testing.FakeSavedFlightsRepository
import travel.roame.takehome.testing.FakeSavedFlightsRepository.Op
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** DOMAIN_RULES V3, V4, V6–V8. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStoreSavedTest {

    private val paris = flight(id = "paris", destination = "CDG", date = "2027-02-10", points = 30_000)
    private val tokyo = flight(id = "tokyo", destination = "NRT", date = "2027-02-11", points = 90_000)

    private val cache = FakeFlightRepository(listOf(paris, tokyo))
    private val saved = FakeSavedFlightsRepository()

    private fun TestScope.newStore(): ExplorerStore {
        val store = ExplorerStore(cache, saved, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        return store
    }

    private fun TestScope.stateAfter(store: ExplorerStore, intent: ExplorerStore.() -> Unit): ExplorerState {
        store.intent()
        runCurrent()
        return store.state.value
    }

    private fun ExplorerState.isSaved(id: String) = results.first { it.id == id }.isSaved

    private fun savedEarlier(vararg flights: Flight) {
        saved.saved.value = flights.map { SavedFlight(it, Instant.fromEpochMilliseconds(500)) }
    }

    // One saved state everywhere (V3)

    @Test
    fun v3_savingShowsTheFlightSavedInTheListOnTheMapInTheCardAndInSaved() = runTest {
        val store = newStore()
        stateAfter(store) { select("paris") }
        val state = stateAfter(store) { toggleSave("paris") }

        assertTrue(state.isSaved("paris"))
        assertTrue(state.markers.first { it.code == "CDG" }.flights.single().isSaved)
        assertTrue(state.selected!!.isSaved)
        assertEquals(listOf("paris"), state.saved.map { it.flight.id })
        assertTrue(state.saved.single().flight.isSaved, "the Saved card shows Remove")
        assertFalse(state.isSaved("tokyo"))
    }

    @Test
    fun v3_removingShowsTheFlightUnsavedEverywhere() = runTest {
        savedEarlier(paris)
        val store = newStore()
        assertTrue(store.state.value.isSaved("paris"))

        val state = stateAfter(store) { toggleSave("paris") }
        assertFalse(state.isSaved("paris"))
        assertFalse(state.markers.first { it.code == "CDG" }.flights.single().isSaved)
        assertEquals(emptyList(), state.saved)
    }

    @Test
    fun v3_aFlightThatIsNotShownCanNotBeToggled() = runTest {
        val store = newStore()
        stateAfter(store) { setMaxPoints(50_000) }
        stateAfter(store) { toggleSave("tokyo") }
        assertEquals(0, saved.writes)
    }

    // No optimistic UI (V7)

    @Test
    fun v7_whileAWriteRunsTheFlightIsPendingAndUnchanged() = runTest {
        saved.gate = CompletableDeferred()
        val store = newStore()

        val during = stateAfter(store) { toggleSave("paris") }
        assertEquals(setOf("paris"), during.pendingWriteIds)
        assertFalse(during.isSaved("paris"))

        saved.gate?.complete(Unit)
        runCurrent()
        assertEquals(emptySet(), store.state.value.pendingWriteIds)
        assertTrue(store.state.value.isSaved("paris"))
    }

    @Test
    fun v7_theFlightStaysPendingUntilTheSavedStateHasChanged() = runTest {
        saved.holdChanges = true
        val store = newStore()

        val written = stateAfter(store) { toggleSave("paris") }
        assertEquals(1, saved.writes)
        assertEquals(setOf("paris"), written.pendingWriteIds, "the write returned, but the saved state hasn't changed yet")
        assertFalse(written.isSaved("paris"))

        saved.publish()
        runCurrent()
        assertTrue(store.state.value.isSaved("paris"))
        assertEquals(emptySet(), store.state.value.pendingWriteIds)
    }

    @Test
    fun v7_noStateEverShowsAFinishedWriteWithTheOldSavedState() = runTest {
        saved.holdChanges = true
        val store = newStore()
        val seen = mutableListOf<ExplorerState>()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) { store.state.collect { seen += it } }
        runCurrent()

        stateAfter(store) { toggleSave("paris") }
        saved.publish()
        runCurrent()

        val afterTap = seen.dropWhile { "paris" !in it.pendingWriteIds }
        assertTrue(afterTap.isNotEmpty())
        assertTrue(afterTap.all { "paris" in it.pendingWriteIds || it.isSaved("paris") })
        assertTrue(seen.last().isSaved("paris"))
    }

    @Test
    fun v7_aSecondTapWhileAWriteRunsIsIgnored() = runTest {
        saved.gate = CompletableDeferred()
        val store = newStore()
        stateAfter(store) { toggleSave("paris") }
        stateAfter(store) { toggleSave("paris") }
        stateAfter(store) { removeSaved("paris") }

        saved.gate?.complete(Unit)
        runCurrent()
        assertEquals(1, saved.writes)
        assertTrue(store.state.value.isSaved("paris"))
    }

    @Test
    fun v7_aFailedSaveLeavesTheFlightUnsavedAndReportsIt() = runTest {
        saved.failing += Op.Save
        val state = stateAfter(newStore()) { toggleSave("paris") }

        assertFalse(state.isSaved("paris"))
        assertEquals(emptySet(), state.pendingWriteIds)
        assertEquals(listOf(UserMessageKind.SaveFailed to "paris"), state.messages.map { it.kind to it.flightId })
    }

    @Test
    fun v7_aFailedRemoveLeavesTheFlightSavedAndReportsIt() = runTest {
        savedEarlier(paris)
        saved.failing += Op.Remove
        val store = newStore()

        val fromTheList = stateAfter(store) { toggleSave("paris") }
        val fromSaved = stateAfter(store) { removeSaved("paris") }

        assertTrue(fromSaved.isSaved("paris"))
        assertEquals(listOf("paris"), fromSaved.saved.map { it.flight.id })
        assertEquals(emptySet(), fromSaved.pendingWriteIds)
        assertEquals(listOf(UserMessageKind.RemoveFailed, UserMessageKind.RemoveFailed), fromSaved.messages.map { it.kind })
        assertTrue(fromTheList.isSaved("paris"))
    }

    @Test
    fun v7_eachMessageHasItsOwnIdAndGoesOnceShown() = runTest {
        saved.failing += Op.Save
        val store = newStore()
        stateAfter(store) { toggleSave("paris") }
        val two = stateAfter(store) { toggleSave("tokyo") }
        assertNotEquals(two.messages[0].id, two.messages[1].id)

        val one = stateAfter(store) { messageShown(two.messages[0].id) }
        assertEquals(listOf("tokyo"), one.messages.map { it.flightId })
    }

    @Test
    fun v7_retryingAfterAFailureCanSucceed() = runTest {
        saved.failing += Op.Save
        val store = newStore()
        val failed = stateAfter(store) { toggleSave("paris") }

        saved.failing.clear()
        stateAfter(store) { messageShown(failed.messages.single().id) }
        val retried = stateAfter(store) { toggleSave(failed.messages.single().flightId) }

        assertTrue(retried.isSaved("paris"))
        assertEquals(emptyList(), retried.messages)
    }

    // Removal from the Saved view (V6)

    @Test
    fun v6_aSavedFlightNoLongerInTheResultsCanStillBeRemoved() = runTest {
        val gone = flight(id = "gone", destination = "SYD")
        savedEarlier(gone, paris)
        val store = newStore()
        val state = stateAfter(store) { removeSaved("gone") }
        assertEquals(listOf("paris"), state.saved.map { it.flight.id })
    }

    // The Saved list (V4, V8)

    @Test
    fun v8_theSavedListIgnoresTheFiltersAndTheSearch() = runTest {
        savedEarlier(paris, tokyo)
        val store = newStore()
        stateAfter(store) { setOriginQuery("nowhere") }
        val state = stateAfter(store) { setMaxPoints(1) }

        assertEquals(emptyList(), state.results)
        assertEquals(listOf("paris", "tokyo"), state.saved.map { it.flight.id })
    }

    @Test
    fun v4_aSavedFlightMissingFromTheLatestRefreshIsNoLongerListed() = runTest {
        val gone = flight(id = "gone", destination = "SYD")
        savedEarlier(paris, gone)
        val state = newStore().state.value

        assertEquals(mapOf("paris" to true, "gone" to false), state.saved.associate { it.flight.id to it.isListed })
        assertEquals(500, state.saved.first().savedAt)
    }

    @Test
    fun v4_beforeAnySyncNothingIsLabelledNoLongerListed() = runTest {
        cache.cache.value = CacheSnapshot(emptyList(), null)
        savedEarlier(paris)
        assertTrue(newStore().state.value.saved.single().isListed)
    }

    @Test
    fun v4_afterAnEmptySyncEverySavedFlightIsNoLongerListed() = runTest {
        cache.cache.value = CacheSnapshot(emptyList(), Instant.fromEpochMilliseconds(2_000))
        savedEarlier(paris)
        assertFalse(newStore().state.value.saved.single().isListed)
    }
}
