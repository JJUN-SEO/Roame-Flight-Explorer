package travel.roame.takehome.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.model.Flight
import travel.roame.takehome.testing.FakeFlightRepository
import travel.roame.takehome.testing.FakeSavedFlightsRepository
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The details page's figures (DOMAIN_RULES DT1) and how they follow the selections (DT3), through the store. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStoreDetailsTest {

    // Every test flight flies the same 5,535 km (Python's haversine, 2026‑11‑04), so a flight's B4 value is its points
    // over its cabin's median points: Economy's median is 20,000.
    private val cheap = flight(id = "cheap", points = 10_000, cabin = "Economy")
    private val typical = flight(id = "typical", points = 20_000, cabin = "Economy")
    private val pricey = flight(id = "pricey", points = 30_000, cabin = "Economy")
    private val unknownCabin = flight(id = "royal", points = 50_000, cabin = "Royal")

    private fun TestScope.store(
        flights: List<Flight> = listOf(cheap, typical, pricey, unknownCabin),
        saved: FakeSavedFlightsRepository = FakeSavedFlightsRepository(),
    ): ExplorerStore {
        val store = ExplorerStore(FakeFlightRepository(flights), saved, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        return store
    }

    private fun TestScope.after(store: ExplorerStore, intent: ExplorerStore.() -> Unit): ExplorerState {
        store.intent()
        runCurrent()
        return store.state.value
    }

    @Test
    fun dt1_noSelectionHasNoDetails() = runTest {
        val state = store().state.value
        assertNull(state.selectedDetails)
        assertNull(state.savedSelectedDetails)
    }

    @Test
    fun dt1_theSelectedFlightsDetails() = runTest {
        val details = after(store()) { select("cheap") }.selectedDetails!!
        assertEquals("cheap", details.flight.id)
        assertEquals(5_535, details.distanceKm)
        assertEquals(50, details.percentBelowTypical, "10,000 against Economy's typical 20,000")
        assertEquals("dest_cdg_city", details.photoKey)
    }

    @Test
    fun dt1_aFlightAtOrAboveTypicalIsNotBelowIt() = runTest {
        val store = store()
        assertEquals(0, after(store) { select("typical") }.selectedDetails!!.percentBelowTypical)
        assertEquals(0, after(store) { select("pricey") }.selectedDetails!!.percentBelowTypical)
    }

    @Test
    fun dt1_aCabinsOnlyFlightIsItsTypical() = runTest {
        val details = after(store()) { select("royal") }.selectedDetails!!
        assertEquals(0, details.percentBelowTypical, "the only Royal flight is its cabin's typical")
    }

    @Test
    fun dt1_theSavedSelectionHasItsOwnDetails() = runTest {
        val store = store()
        store.toggleSave("pricey")
        runCurrent()
        val state = after(store) { selectSaved("pricey") }
        assertEquals("pricey", state.savedSelectedDetails!!.flight.id)
        assertEquals(0, state.savedSelectedDetails!!.percentBelowTypical, "30,000 is above Economy's typical")
        assertNull(state.selectedDetails)
    }

    @Test
    fun dt1_aSavedFlightTheCacheNoLongerHasStillGetsDetails() = runTest {
        // Saved, then gone from the data: its cabin isn't in the cache, so it can't be compared with a typical rate.
        val saved = FakeSavedFlightsRepository()
        val first = store(listOf(cheap, unknownCabin), saved)
        first.toggleSave("royal")
        runCurrent()
        val store = store(listOf(cheap), saved)
        val details = after(store) { selectSaved("royal") }.savedSelectedDetails!!
        assertEquals(5_535, details.distanceKm)
        assertEquals(0, details.percentBelowTypical)
    }

    @Test
    fun dt3_detailsFollowTheFlightsSavedState() = runTest {
        val store = store()
        store.select("cheap")
        assertEquals(true, after(store) { toggleSave("cheap") }.selectedDetails!!.flight.isSaved)
    }

    @Test
    fun dt3_removingTheSavedSelectionClosesItsDetails() = runTest {
        val store = store()
        store.toggleSave("pricey")
        runCurrent()
        after(store) { selectSaved("pricey") }
        assertNull(after(store) { removeSaved("pricey") }.savedSelectedDetails)
    }

    @Test
    fun dt3_aSelectionTheFiltersDropLosesItsDetails() = runTest {
        val store = store()
        after(store) { select("cheap") }
        assertNull(after(store) { setCabin("Royal") }.selectedDetails)
    }
}
