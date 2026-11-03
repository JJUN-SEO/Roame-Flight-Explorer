package travel.roame.takehome.ios

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore
import travel.roame.takehome.testing.FakeFlightRepository
import travel.roame.takehome.testing.FakeSavedFlightsRepository
import travel.roame.takehome.testing.flight
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Swift bridge passes every intent through and delivers state until cancelled (ARCHITECTURE §8, D7). */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStoreIosTest {

    private val flights = listOf(
        flight(id = "cheap", points = 10_000, cabin = "Economy", program = "Avios"),
        flight(id = "pricey", points = 90_000, cabin = "Business", program = "Aeroplan"),
    )

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun TestScope.bridge(): ExplorerStoreIos {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val bridge = ExplorerStoreIos(ExplorerStore(FakeFlightRepository(flights), FakeSavedFlightsRepository(), backgroundScope, dispatcher))
        runCurrent()
        return bridge
    }

    @Test
    fun d7_observeDeliversTheCurrentStateAndEveryChangeUntilCancelled() = runTest {
        val bridge = bridge()
        val seen = mutableListOf<ExplorerState>()
        val subscription = bridge.observe { seen += it }
        runCurrent()
        assertEquals(listOf(2), seen.map { it.resultCount })

        bridge.setMaxPoints(10_000)
        runCurrent()
        assertEquals(listOf(2, 1), seen.map { it.resultCount })

        subscription.cancel()
        bridge.resetAll()
        runCurrent()
        assertEquals(listOf(2, 1), seen.map { it.resultCount }, "nothing arrives after cancel")
        assertEquals(2, bridge.state.resultCount, "the state itself still updates")
    }

    @Test
    fun d7_everyIntentReachesTheStore() = runTest {
        val bridge = bridge()
        bridge.setOriginQuery("bos")
        bridge.setDestinationQuery("cdg")
        bridge.setDateRange("2027-02-01", null)
        bridge.setCabin("Economy")
        bridge.toggleProgram("Avios")
        bridge.setMaxPoints(50_000)
        bridge.select("cheap")
        runCurrent()

        val state = bridge.state
        assertEquals("bos", state.originQuery)
        assertEquals("cdg", state.destinationQuery)
        // Every test flight leaves on Feb 1, so that start date is the data's own limit and is stored as no limit (F2).
        assertEquals(FlightFilters(cabin = "Economy", maxPoints = 50_000, programs = setOf("Avios")), state.filters)
        assertEquals("cheap", state.selected?.id)

        bridge.clearFilter(FilterKind.Cabin)
        bridge.toggleSave("cheap")
        runCurrent()
        assertEquals(null, bridge.state.filters.cabin)
        assertEquals(listOf("cheap"), bridge.state.saved.map { it.flight.id })

        bridge.selectSaved("cheap")
        bridge.removeSaved("cheap")
        bridge.pickOrigin(listOf("BOS"))
        bridge.pickDestination(listOf("CDG"))
        bridge.setUserLocation(42.36, -71.0)
        runCurrent()
        assertEquals(emptyList(), bridge.state.saved)
        assertEquals("BOS", bridge.state.originPick?.codes?.single())
        assertEquals("CDG", bridge.state.destinationPick?.codes?.single())
        assertEquals(true, bridge.state.isLocationShared)
    }
}
