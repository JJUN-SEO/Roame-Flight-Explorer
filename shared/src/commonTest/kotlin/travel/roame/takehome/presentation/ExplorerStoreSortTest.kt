package travel.roame.takehome.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.model.SortOrder
import travel.roame.takehome.testing.FakeFlightRepository
import travel.roame.takehome.testing.FakeSavedFlightsRepository
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The sort (D46): DOMAIN_RULES O1, O2 and O4 through the store. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStoreSortTest {

    // Every test airport has the same coordinates, so each value (B4) is the points over the cabin's median points:
    // Economy 9,500, so Hong Kong 0.95 and cheap Paris 1.05; Business 111,500, so pricey Paris 0.81 and Tokyo 1.19.
    private val cheapParis = flight(id = "cheap-paris", destination = "CDG", date = "2027-02-10", points = 10_000, cabin = "Economy", program = "Avios")
    private val priceyParis = flight(id = "pricey-paris", destination = "CDG", date = "2027-02-05", points = 90_000, cabin = "Business", program = "Aeroplan")
    private val tokyo = flight(id = "tokyo", destination = "NRT", date = "2027-04-01", points = 133_000, cabin = "Business", program = "Avios")
    private val hongKong = flight(id = "hong-kong", destination = "HKG", date = "2027-02-01", points = 9_000, cabin = "Economy", program = "Flying Blue")

    private fun TestScope.store(): ExplorerStore {
        val store = ExplorerStore(
            FakeFlightRepository(listOf(tokyo, cheapParis, hongKong, priceyParis)),
            FakeSavedFlightsRepository(),
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        runCurrent()
        return store
    }

    private fun TestScope.after(store: ExplorerStore, intent: ExplorerStore.() -> Unit): ExplorerState {
        store.intent()
        runCurrent()
        return store.state.value
    }

    private fun ids(state: ExplorerState) = state.results.map { it.id }

    @Test
    fun o1_theResultsStartWithTheLowestPoints() = runTest {
        val state = store().state.value
        assertEquals(SortOrder.LowestPoints, state.sort)
        assertEquals(listOf("hong-kong", "cheap-paris", "pricey-paris", "tokyo"), ids(state))
    }

    @Test
    fun o1_highestPointsPutsThePriciestFirst() = runTest {
        val store = store()
        assertEquals(listOf("tokyo", "pricey-paris", "cheap-paris", "hong-kong"), ids(after(store) { setSort(SortOrder.HighestPoints) }))
    }

    @Test
    fun o1_bestOfferPutsTheMostBelowTypicalFirst_acrossCabins() = runTest {
        val store = store()
        assertEquals(listOf("pricey-paris", "hong-kong", "cheap-paris", "tokyo"), ids(after(store) { setSort(SortOrder.BestOffer) }))
    }

    @Test
    fun o1_byProgramGivesASectionPerProgram_cheapestFirstWithinEach() = runTest {
        val store = store()
        val state = after(store) { setSort(SortOrder.ByProgram) }
        assertEquals(
            listOf("Aeroplan" to listOf("pricey-paris"), "Avios" to listOf("cheap-paris", "tokyo"), "Flying Blue" to listOf("hong-kong")),
            state.sections.map { section -> section.program to section.flights.map { it.id } },
        )
        assertEquals(ids(state), state.sections.flatMap { it.flights }.map { it.id })
        assertEquals(listOf(1, 2, 1), state.sections.map { it.flightCount })
    }

    @Test
    fun o1_everyOtherSortGivesOneUntitledSection() = runTest {
        val store = store()
        for (sort in SortOrder.entries - SortOrder.ByProgram) {
            val state = after(store) { setSort(sort) }
            assertEquals(listOf(ResultSection(program = null, flights = state.results)), state.sections, "$sort")
        }
    }

    @Test
    fun o1_noResultsGiveNoSections() = runTest {
        val store = store()
        store.setSort(SortOrder.ByProgram)
        assertEquals(emptyList(), after(store) { setMaxPoints(1) }.sections)
    }

    @Test
    fun o1_filtersKeepTheChosenSort() = runTest {
        val store = store()
        store.setSort(SortOrder.HighestPoints)
        assertEquals(listOf("tokyo", "pricey-paris"), ids(after(store) { setCabin("Business") }))
    }

    @Test
    fun o2_markerSheetsStayCheapestFirst_whateverTheSort() = runTest {
        val store = store()
        val paris = after(store) { setSort(SortOrder.HighestPoints) }.markers.single { it.code == "CDG" }
        assertEquals(listOf("cheap-paris", "pricey-paris"), paris.flights.map { it.id })
    }

    @Test
    fun o4_theSortIsNotAFilter() = runTest {
        val store = store()
        val state = after(store) { setSort(SortOrder.ByProgram) }
        assertTrue(state.activeFilters.isEmpty())
        assertFalse(state.hasAnythingToReset)
    }

    @Test
    fun o4_clearingAFilterResettingAndANewSearchKeepTheSort() = runTest {
        val store = store()
        store.setSort(SortOrder.BestOffer)
        store.setCabin("Business")
        assertEquals(SortOrder.BestOffer, after(store) { clearFilter(FilterKind.Cabin) }.sort)
        store.setOriginQuery("BOS")
        assertEquals(SortOrder.BestOffer, after(store) { resetAll() }.sort)
        assertEquals(SortOrder.BestOffer, after(store) { openDestination(listOf("NRT")) }.sort)
    }

    @Test
    fun o4_changingTheSortKeepsTheSelection() = runTest {
        val store = store()
        store.select("tokyo")
        assertEquals("tokyo", after(store) { setSort(SortOrder.ByProgram) }.selected?.id)
    }
}
