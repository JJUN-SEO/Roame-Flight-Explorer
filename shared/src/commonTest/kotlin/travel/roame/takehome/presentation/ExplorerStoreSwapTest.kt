package travel.roame.takehome.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.Flight
import travel.roame.takehome.testing.FakeFlightRepository
import travel.roame.takehome.testing.FakeSavedFlightsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DOMAIN_RULES S15: swapping From and To. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStoreSwapTest {

    private val toronto = Airport("YYZ", "Toronto", 43.68, -79.63)
    private val london = Airport("LHR", "London", 51.47, -0.45)
    private val paris = Airport("CDG", "Paris", 49.01, 2.55)

    private fun flight(id: String, origin: Airport, destination: Airport) =
        Flight(id, origin, destination, LocalDate.parse("2027-02-01"), "Avios", 30_000, "Economy")

    // London is on both sides; Toronto only leaves, and Paris is only flown to.
    private val flights = listOf(flight("yyz-lhr", toronto, london), flight("lhr-cdg", london, paris), flight("yyz-cdg", toronto, paris))

    private fun TestScope.newStore(): ExplorerStore {
        val store = ExplorerStore(FakeFlightRepository(flights), FakeSavedFlightsRepository(), backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        return store
    }

    private fun TestScope.stateAfter(store: ExplorerStore, intent: ExplorerStore.() -> Unit): ExplorerState {
        store.intent()
        runCurrent()
        return store.state.value
    }

    @Test
    fun s15_swappingTradesTheTextOfTheTwoFields() = runTest {
        val store = newStore()
        stateAfter(store) {
            setOriginQuery("lon")
            setDestinationQuery("par")
        }
        val state = stateAfter(store) { swapSides() }
        assertEquals("par", state.originQuery)
        assertEquals("lon", state.destinationQuery)
        assertTrue(state.results.isEmpty(), "nothing leaves Paris")
    }

    @Test
    fun s15_aPickTheOtherSideHasMovesWithItsAirports() = runTest {
        val store = newStore()
        stateAfter(store) {
            setOriginQuery("lon")
            pickOrigin(listOf("LHR"))
        }
        val state = stateAfter(store) { swapSides() }
        assertNull(state.originPick)
        assertEquals(listOf("LHR"), state.destinationPick?.codes)
        assertEquals(listOf("yyz-lhr"), state.results.map { it.id })
    }

    @Test
    fun s15_aPickTheOtherSideLacksSearchesItsCityName() = runTest {
        val store = newStore()
        stateAfter(store) {
            setOriginQuery("tor")
            pickOrigin(listOf("YYZ"))
            pickDestination(listOf("CDG"))
        }
        val state = stateAfter(store) { swapSides() }
        assertEquals("Paris", state.originQuery)
        assertEquals("Toronto", state.destinationQuery)
        assertNull(state.originPick)
        assertNull(state.destinationPick)
        assertTrue(state.results.isEmpty(), "nothing flies from Paris to Toronto")
    }

    @Test
    fun s15_swappingTwiceGivesTheSameRouteBack() = runTest {
        val store = newStore()
        stateAfter(store) {
            pickOrigin(listOf("YYZ"))
            pickDestination(listOf("CDG"))
        }
        stateAfter(store) { swapSides() }
        val state = stateAfter(store) { swapSides() }
        assertEquals(listOf("yyz-cdg"), state.results.map { it.id }, "by the city names the first swap left")
    }

    @Test
    fun s15_swappingLeavesTheFiltersAlone() = runTest {
        val store = newStore()
        stateAfter(store) { setCabin("Economy") }
        assertEquals("Economy", stateAfter(store) { swapSides() }.filters.cabin)
    }
}
