package travel.roame.takehome.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import travel.roame.takehome.data.CacheSnapshot
import travel.roame.takehome.data.SyncStatus
import travel.roame.takehome.model.ActiveFilter
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.testing.FakeFlightRepository
import travel.roame.takehome.testing.FakeSavedFlightsRepository
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Filter intents and what they do to the state: DOMAIN_RULES F2, F4–F8, C1–C4, M1, M5, O1, O2. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStoreFiltersTest {

    private val cheapParis = flight(id = "cheap-paris", destination = "CDG", date = "2027-02-10", points = 10_000, cabin = "Economy", program = "Avios")
    private val priceyParis = flight(id = "pricey-paris", destination = "CDG", date = "2027-02-05", points = 90_000, cabin = "Business", program = "Aeroplan")
    private val tokyo = flight(id = "tokyo", destination = "NRT", date = "2027-04-01", points = 133_000, cabin = "Business", program = "Avios")
    private val hongKong = flight(id = "hong-kong", destination = "HKG", date = "2027-02-01", points = 9_000, cabin = "Economy", program = "Flying Blue")

    private val flights = listOf(cheapParis, priceyParis, tokyo, hongKong)

    private fun TestScope.storeWith(flights: List<Flight> = this@ExplorerStoreFiltersTest.flights): Pair<ExplorerStore, FakeFlightRepository> {
        val repository = FakeFlightRepository(flights)
        val store = ExplorerStore(repository, FakeSavedFlightsRepository(), backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        return store to repository
    }

    private fun TestScope.stateAfter(store: ExplorerStore, intent: ExplorerStore.() -> Unit): ExplorerState {
        store.intent()
        runCurrent()
        return store.state.value
    }

    private fun ids(state: ExplorerState) = state.results.map { it.id }

    // Results (C1, M1, O1, O2)

    @Test
    fun o1_resultsAreSortedWhateverOrderTheCacheIsIn() = runTest {
        val (store) = storeWith()
        assertEquals(listOf("hong-kong", "pricey-paris", "cheap-paris", "tokyo"), ids(store.state.value))
    }

    @Test
    fun o2_markerFlightsAreSortedByPoints() = runTest {
        val (store) = storeWith(listOf(priceyParis, cheapParis))
        val paris = store.state.value.markers.single()
        assertEquals(listOf("cheap-paris", "pricey-paris"), paris.flights.map { it.id })
    }

    @Test
    fun c1_theCountEqualsTheRowsAndTheMarkerCounts() = runTest {
        val (store) = storeWith()
        val filtersToTry: List<ExplorerStore.() -> Unit> = listOf(
            { setMaxPoints(10_000) },
            { setCabin("Business") },
            { setDateRange("2027-02-02", "2027-02-20") },
            { toggleProgram("Avios") },
            { setMaxPoints(1) },
        )
        for (intent in filtersToTry) {
            val state = stateAfter(store, intent)
            assertEquals(state.results.size, state.resultCount)
            assertEquals(state.resultCount, state.markers.sumOf { it.flightCount })
        }
    }

    @Test
    fun m1_markersFollowTheSameFiltersAsTheList() = runTest {
        val (store) = storeWith()
        val state = stateAfter(store) { setMaxPoints(10_000) }

        assertEquals(setOf("cheap-paris", "hong-kong"), ids(state).toSet())
        assertEquals(listOf("CDG", "HKG"), state.markers.map { it.code })
        assertEquals(listOf(1, 1), state.markers.map { it.flightCount })
        assertEquals(listOf(10_000, 9_000), state.markers.map { it.minPoints })
    }

    @Test
    fun m3_aMarkerCarriesItsCityAndPositionFromTheData() = runTest {
        val (store) = storeWith()
        val paris = store.state.value.markers.first { it.code == "CDG" }
        assertEquals(cheapParis.destination.city, paris.city)
        assertEquals(cheapParis.destination.latitude, paris.latitude)
        assertEquals(cheapParis.destination.longitude, paris.longitude)
        assertEquals(10_000, paris.minPoints)
    }

    @Test
    fun t3_aFlightItemCarriesItsDateAsIsoText() = runTest {
        val (store) = storeWith()
        val item = store.state.value.results.first { it.id == "cheap-paris" }
        assertEquals("2027-02-10", item.departureDate)
        assertEquals(10_000, item.points)
        assertEquals("BOS", item.originCode)
        assertEquals("CDG", item.destinationCode)
        assertEquals("Avios", item.program)
        assertEquals("Economy", item.cabin)
    }

    // Each filter intent (F2, F4–F6)

    @Test
    fun f1_aDateRangeFiltersResultsAndMarkers() = runTest {
        val (store) = storeWith()
        val state = stateAfter(store) { setDateRange("2027-02-05", "2027-02-10") }
        assertEquals(listOf("pricey-paris", "cheap-paris"), ids(state))
        assertEquals(listOf("CDG"), state.markers.map { it.code })
    }

    @Test
    fun f2_datesEqualToTheDataBoundsAreStoredAsNoLimit() = runTest {
        val (store) = storeWith()
        val state = stateAfter(store) { setDateRange("2027-02-01", "2027-04-01") }
        assertEquals(FlightFilters(), state.filters)
        assertEquals(emptyList(), state.activeFilters)
        assertFalse(state.hasAnythingToReset)
    }

    @Test
    fun f2_aMalformedDateIsIgnored() = runTest {
        val (store) = storeWith()
        stateAfter(store) { setDateRange("2027-02-05", "2027-02-10") }
        for (bad in listOf("not a date", "2027-2-5", "02/05/2027", "2027-02-30", "")) {
            assertEquals(FlightFilters(dateStart = "2027-02-05", dateEnd = "2027-02-10"), stateAfter(store) { setDateRange(bad, null) }.filters, bad)
            assertEquals(FlightFilters(dateStart = "2027-02-05", dateEnd = "2027-02-10"), stateAfter(store) { setDateRange(null, bad) }.filters, bad)
        }
    }

    @Test
    fun f3_startAfterEndGivesNoResultsAndIsNotSwapped() = runTest {
        val (store) = storeWith()
        val state = stateAfter(store) { setDateRange("2027-02-10", "2027-02-05") }
        assertEquals(emptyList(), state.results)
        assertEquals(emptyList(), state.markers)
        assertEquals(FlightFilters(dateStart = "2027-02-10", dateEnd = "2027-02-05"), state.filters)
    }

    @Test
    fun f4_thePointsLimitIsInclusive() = runTest {
        val (store) = storeWith()
        assertEquals(setOf("cheap-paris", "hong-kong"), ids(stateAfter(store) { setMaxPoints(10_000) }).toSet())
        assertEquals(setOf("hong-kong"), ids(stateAfter(store) { setMaxPoints(9_500) }).toSet())
    }

    @Test
    fun f4_thePointsLimitAtTheTopIsStoredAsNoLimit() = runTest {
        val (store) = storeWith()
        assertEquals(FlightFilters(), stateAfter(store) { setMaxPoints(133_000) }.filters)
    }

    @Test
    fun f5_aCabinFiltersAndNullClearsIt() = runTest {
        val (store) = storeWith()
        assertEquals(setOf("pricey-paris", "tokyo"), ids(stateAfter(store) { setCabin("Business") }).toSet())
        assertEquals(4, stateAfter(store) { setCabin(null) }.resultCount)
    }

    @Test
    fun f6_programsToggleOnAndOff() = runTest {
        val (store) = storeWith()
        assertEquals(setOf("cheap-paris", "tokyo"), ids(stateAfter(store) { toggleProgram("Avios") }).toSet())
        assertEquals(setOf("cheap-paris", "tokyo", "pricey-paris"), ids(stateAfter(store) { toggleProgram("Aeroplan") }).toSet())
        assertEquals(setOf("pricey-paris"), ids(stateAfter(store) { toggleProgram("Avios") }).toSet())
        assertEquals(4, stateAfter(store) { toggleProgram("Aeroplan") }.resultCount)
    }

    @Test
    fun f7_filtersCombine() = runTest {
        val (store) = storeWith()
        store.setCabin("Business")
        val state = stateAfter(store) { toggleProgram("Avios") }
        assertEquals(listOf("tokyo"), ids(state))
    }

    // Options (F8)

    @Test
    fun f8_optionsComeFromTheWholeCacheAndDoNotShrinkWhileFiltering() = runTest {
        val (store) = storeWith()
        val before = store.state.value.options
        assertEquals(listOf("Economy", "Business"), before.cabins)
        assertEquals(listOf("Aeroplan", "Avios", "Flying Blue"), before.programs)

        assertEquals(before, stateAfter(store) { setCabin("Economy") }.options)
        assertEquals(before, stateAfter(store) { setMaxPoints(1) }.options)
    }

    @Test
    fun f8_aProgramGoneAfterARefreshIsDroppedAndStaysDropped() = runTest {
        val (store, repository) = storeWith()
        stateAfter(store) { toggleProgram("Aeroplan") }
        stateAfter(store) { toggleProgram("Avios") }

        repository.cache.value = CacheSnapshot(listOf(cheapParis, tokyo), Instant.fromEpochMilliseconds(2_000))
        runCurrent()
        assertEquals(setOf("Avios"), store.state.value.filters.programs)

        repository.cache.value = CacheSnapshot(flights, Instant.fromEpochMilliseconds(3_000))
        runCurrent()
        assertEquals(setOf("Avios"), store.state.value.filters.programs, "it doesn't come back with the data")
    }

    @Test
    fun f8_aCabinGoneAfterARefreshIsDropped() = runTest {
        val (store, repository) = storeWith()
        stateAfter(store) { setCabin("Business") }

        repository.cache.value = CacheSnapshot(listOf(cheapParis, hongKong), Instant.fromEpochMilliseconds(2_000))
        runCurrent()

        assertEquals(FlightFilters(), store.state.value.filters)
        assertEquals(2, store.state.value.resultCount)
    }

    /** Pure, because the store corrects its own inputs right after this, which would hide a slip here. */
    @Test
    fun f8_aFilterTheDataNoLongerOffersNeverReachesTheState() {
        val cache = CacheSnapshot(listOf(cheapParis, hongKong), Instant.fromEpochMilliseconds(2_000))
        val inputs = ExplorerInputs(filters = FlightFilters(cabin = "Business", programs = setOf("Aeroplan")))

        val state = reduce(Snapshot(index(cache), SyncStatus(), emptyList(), inputs))

        assertEquals(FlightFilters(), state.filters)
        assertEquals(emptyList(), state.activeFilters)
        assertEquals(2, state.resultCount)
        assertFalse(state.hasAnythingToReset)
    }

    @Test
    fun f2_theDefaultDateRangeFollowsTheDataAfterARefresh() = runTest {
        val (store, repository) = storeWith()
        assertEquals("2027-04-01", store.state.value.options.maxDate)

        val later = flight(id = "later", date = "2027-06-01")
        repository.cache.value = CacheSnapshot(flights + later, Instant.fromEpochMilliseconds(2_000))
        runCurrent()

        assertEquals("2027-06-01", store.state.value.options.maxDate)
        assertEquals(5, store.state.value.resultCount)
    }

    // Chips and reset (C2–C4)

    @Test
    fun c2_activeFiltersFollowTheFilters() = runTest {
        val (store) = storeWith()
        assertEquals(emptyList(), store.state.value.activeFilters)

        store.setDateRange("2027-02-05", null)
        store.toggleProgram("Avios")
        val state = stateAfter(store) { setCabin("Business") }

        assertEquals(
            listOf(ActiveFilter.Dates("2027-02-05", null), ActiveFilter.Cabin("Business"), ActiveFilter.Programs(listOf("Avios"))),
            state.activeFilters,
        )
    }

    @Test
    fun c2_searchTextCountsAsSomethingToResetButIsNotAChip() = runTest {
        val (store) = storeWith()
        assertFalse(store.state.value.hasAnythingToReset)

        val origin = stateAfter(store) { setOriginQuery("bos") }
        assertEquals("bos", origin.originQuery)
        assertTrue(origin.hasAnythingToReset)
        assertEquals(emptyList(), origin.activeFilters)

        assertFalse(stateAfter(store) { setOriginQuery("") }.hasAnythingToReset)
        val destination = stateAfter(store) { setDestinationQuery("tokyo") }
        assertEquals("tokyo", destination.destinationQuery)
        assertTrue(destination.hasAnythingToReset)
    }

    @Test
    fun c3_clearingOneFilterLeavesTheOthers() = runTest {
        val (store) = storeWith()
        store.setCabin("Business")
        store.setMaxPoints(100_000)
        val state = stateAfter(store) { clearFilter(FilterKind.Cabin) }

        assertEquals(FlightFilters(maxPoints = 100_000), state.filters)
        assertEquals(listOf(ActiveFilter.MaxPoints(100_000)), state.activeFilters)
    }

    @Test
    fun c4_resetRestoresEveryDefaultAndAllTheResults() = runTest {
        val (store) = storeWith()
        store.setOriginQuery("bos")
        store.setDestinationQuery("tokyo")
        store.setDateRange("2027-02-05", "2027-02-10")
        store.setCabin("Business")
        store.setMaxPoints(100_000)
        store.toggleProgram("Avios")
        val state = stateAfter(store) { resetAll() }

        assertEquals("", state.originQuery)
        assertEquals("", state.destinationQuery)
        assertEquals(FlightFilters(), state.filters)
        assertEquals(emptyList(), state.activeFilters)
        assertFalse(state.hasAnythingToReset)
        assertEquals(4, state.resultCount)
    }

    @Test
    fun m6_noResultsMeansNoMarkers() = runTest {
        val (store) = storeWith()
        val state = stateAfter(store) { setMaxPoints(1) }
        assertEquals(emptyList(), state.results)
        assertEquals(emptyList(), state.markers)
        assertTrue(state.hasAnythingToReset)
    }

    // Starting up (R1, R2)

    @Test
    fun r1_theFirstStateIsLoadingAndEmpty() = runTest {
        val store = ExplorerStore(FakeFlightRepository(flights), FakeSavedFlightsRepository(), backgroundScope, StandardTestDispatcher(testScheduler))
        assertEquals(ExplorerState.Initial, store.state.value)
        assertEquals(DataStatus.Loading, store.state.value.data)
    }

    @Test
    fun r2_theStoreRefreshesOnLaunchAndOnRequest() = runTest {
        val (store, repository) = storeWith()
        assertEquals(1, repository.refreshes)
        stateAfter(store) { refresh() }
        assertEquals(2, repository.refreshes)
    }
}
