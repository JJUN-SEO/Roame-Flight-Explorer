package travel.roame.takehome.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.data.CacheSnapshot
import travel.roame.takehome.model.SavedFlight
import travel.roame.takehome.testing.FakeFlightRepository
import travel.roame.takehome.testing.FakeSavedFlightsRepository
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** DOMAIN_RULES L1–L5, L7 and C4. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStoreSelectionTest {

    private val paris = flight(id = "paris", destination = "CDG", date = "2027-02-10", points = 30_000)
    private val tokyo = flight(id = "tokyo", destination = "NRT", date = "2027-02-11", points = 90_000)
    private val flights = listOf(paris, tokyo)

    private val cache = FakeFlightRepository(flights)
    private val saved = FakeSavedFlightsRepository(SavedFlight(paris, Instant.fromEpochMilliseconds(500)))

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

    // One shared selection (L1, L2, L4)

    @Test
    fun l1_theSelectionIsTheSameFlightInTheListAndOnTheMap() = runTest {
        val state = stateAfter(newStore()) { select("tokyo") }

        assertEquals(state.results.first { it.id == "tokyo" }, state.selected)
        assertEquals(state.selected, state.markers.first { it.code == "NRT" }.flights.single())
    }

    @Test
    fun l2_selectingTheSelectedFlightAgainDeselectsIt() = runTest {
        val store = newStore()
        stateAfter(store) { select("tokyo") }
        assertNull(stateAfter(store) { select("tokyo") }.selected)
    }

    @Test
    fun l2_selectingNothingDeselects() = runTest {
        val store = newStore()
        stateAfter(store) { select("tokyo") }
        assertNull(stateAfter(store) { select(null) }.selected)
    }

    @Test
    fun l2_selectingAnotherFlightMovesTheSelection() = runTest {
        val store = newStore()
        stateAfter(store) { select("tokyo") }
        assertEquals("paris", stateAfter(store) { select("paris") }.selected?.id)
    }

    // Leaving the results (L3)

    @Test
    fun l3_aFilterThatExcludesTheSelectionClearsItForGood() = runTest {
        val store = newStore()
        stateAfter(store) { select("tokyo") }

        assertNull(stateAfter(store) { setMaxPoints(50_000) }.selected)
        assertNull(stateAfter(store) { setMaxPoints(null) }.selected, "undoing the filter doesn't bring it back")
    }

    @Test
    fun l3_aFilterThatKeepsTheSelectionKeepsIt() = runTest {
        val store = newStore()
        stateAfter(store) { select("tokyo") }
        assertEquals("tokyo", stateAfter(store) { setMaxPoints(100_000) }.selected?.id)
    }

    @Test
    fun l3_aRefreshThatDropsTheSelectedFlightClearsIt() = runTest {
        val store = newStore()
        stateAfter(store) { select("tokyo") }

        cache.cache.value = CacheSnapshot(listOf(paris), Instant.fromEpochMilliseconds(2_000))
        runCurrent()
        assertNull(store.state.value.selected)

        cache.cache.value = CacheSnapshot(flights, Instant.fromEpochMilliseconds(3_000))
        runCurrent()
        assertNull(store.state.value.selected, "it doesn't come back with the data")
    }

    @Test
    fun l3_aSelectionMadeJustAsOlderResultsFinishIsKept() = runTest {
        lateinit var store: ExplorerStore
        var userTapsDuringThisUpdate = false
        store = ExplorerStore(cache, saved, backgroundScope, StandardTestDispatcher(testScheduler), reducer = { snapshot ->
            val state = reduce(snapshot)
            if (userTapsDuringThisUpdate) {
                userTapsDuringThisUpdate = false
                store.select("paris")
            }
            state
        })
        runCurrent()
        stateAfter(store) { select("tokyo") }

        // These results drop tokyo, and the user picks paris before the store has caught up with them.
        userTapsDuringThisUpdate = true
        stateAfter(store) { setMaxPoints(50_000) }

        assertEquals("paris", store.state.value.selected?.id)
    }

    @Test
    fun l3_anIdThatIsNotInTheResultsSelectsNothing() = runTest {
        assertNull(stateAfter(newStore()) { select("nowhere") }.selected)
    }

    @Test
    fun c4_resetKeepsTheSelectionBecauseEveryFlightIsBackInTheResults() = runTest {
        val store = newStore()
        stateAfter(store) { setMaxPoints(100_000) }
        stateAfter(store) { select("tokyo") }
        assertEquals("tokyo", stateAfter(store) { resetAll() }.selected?.id)
    }

    @Test
    fun l5_aNewStoreStartsWithNoSelection() = runTest {
        stateAfter(newStore()) { select("tokyo") }
        assertNull(newStore().state.value.selected)
    }

    // The Saved view's own selection (L7)

    @Test
    fun l7_theSavedSelectionIgnoresTheFilters() = runTest {
        val store = newStore()
        stateAfter(store) { selectSaved("paris") }
        val state = stateAfter(store) { setMaxPoints(1) }

        assertEquals(emptyList(), state.results)
        assertEquals("paris", state.savedSelected?.flight?.id)
    }

    @Test
    fun l7_selectingTheSavedSelectionAgainDeselectsIt() = runTest {
        val store = newStore()
        stateAfter(store) { selectSaved("paris") }
        assertNull(stateAfter(store) { selectSaved("paris") }.savedSelected)
    }

    @Test
    fun l7_removingTheSavedSelectionClearsItForGood() = runTest {
        val store = newStore()
        stateAfter(store) { selectSaved("paris") }

        assertNull(stateAfter(store) { removeSaved("paris") }.savedSelected)
        stateAfter(store) { toggleSave("paris") }
        assertEquals(listOf("paris"), store.state.value.saved.map { it.flight.id })
        assertNull(store.state.value.savedSelected, "saving it again doesn't bring the selection back")
    }

    @Test
    fun l7_theTwoSelectionsAreIndependent() = runTest {
        val store = newStore()
        stateAfter(store) { select("tokyo") }
        val both = stateAfter(store) { selectSaved("paris") }
        assertEquals("tokyo", both.selected?.id)
        assertEquals("paris", both.savedSelected?.flight?.id)

        val onlySaved = stateAfter(store) { select(null) }
        assertNull(onlySaved.selected)
        assertEquals("paris", onlySaved.savedSelected?.flight?.id)
    }
}
