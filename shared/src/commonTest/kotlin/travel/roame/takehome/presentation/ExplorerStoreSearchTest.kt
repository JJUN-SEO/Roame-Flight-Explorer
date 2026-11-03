package travel.roame.takehome.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import travel.roame.takehome.data.CacheSnapshot
import travel.roame.takehome.domain.search.SearchableAirport
import travel.roame.takehome.domain.search.TestAirports
import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.AirportSuggestion
import travel.roame.takehome.model.Flight
import travel.roame.takehome.testing.FakeFlightRepository
import travel.roame.takehome.testing.FakeSavedFlightsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Both search fields in the store, with real airport names and positions: DOMAIN_RULES S1–S3, S13, S14, P2, P5, P7. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStoreSearchTest {

    private fun code(code: String): SearchableAirport =
        (TestAirports.origins + TestAirports.destinations).first { it.code == code }

    private fun route(from: String, to: String, points: Int, day: Int = 1): Flight {
        val (origin, destination) = code(from) to code(to)
        return Flight(
            id = "$from-$to-$points",
            origin = Airport(origin.code, origin.city, origin.location.latitude, origin.location.longitude),
            destination = Airport(destination.code, destination.city, destination.location.latitude, destination.location.longitude),
            departureDate = LocalDate(2027, 2, day),
            program = "Avios",
            points = points,
            cabin = "Economy",
        )
    }

    private val flights = listOf(
        route("BOS", "HND", 30_000),
        route("BOS", "NRT", 40_000),
        route("BOS", "DUB", 20_000),
        route("BOS", "DXB", 60_000),
        route("SEA", "HND", 50_000),
        route("SFO", "NRT", 70_000),
        route("JFK", "SYD", 80_000),
    )

    private val cache = FakeFlightRepository(flights)

    private fun TestScope.newStore(): ExplorerStore {
        val store = ExplorerStore(cache, FakeSavedFlightsRepository(), backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        return store
    }

    private fun TestScope.stateAfter(store: ExplorerStore, intent: ExplorerStore.() -> Unit): ExplorerState {
        store.intent()
        runCurrent()
        return store.state.value
    }

    private fun routes(state: ExplorerState) = state.results.map { "${it.originCode}-${it.destinationCode}" }.toSet()

    private fun airport(city: String, code: String) = AirportSuggestion(city, listOf(code), isCityRow = false)

    // Matching (S1–S3)

    @Test
    fun s2_blankFieldsShowEveryFlight() = runTest {
        assertEquals(7, newStore().state.value.resultCount)
    }

    @Test
    fun s1_caseAndSurroundingSpacesDoNotMatter() = runTest {
        val store = newStore()
        assertEquals(routes(stateAfter(store) { setOriginQuery("boston") }), routes(stateAfter(store) { setOriginQuery("  BoStOn ") }))
    }

    @Test
    fun s3_theOriginFieldOnlyMatchesOriginAirports() = runTest {
        val store = newStore()
        assertEquals(setOf("BOS-HND", "BOS-NRT", "BOS-DUB", "BOS-DXB"), routes(stateAfter(store) { setOriginQuery("bos") }))

        val tokyoAsOrigin = stateAfter(store) { setOriginQuery("tokyo") }
        assertEquals(emptyList(), tokyoAsOrigin.results)
        assertEquals(emptyList(), tokyoAsOrigin.markers)
    }

    @Test
    fun s3_bothFieldsMustMatch() = runTest {
        val store = newStore()
        store.setOriginQuery("bos")
        assertEquals(setOf("BOS-HND", "BOS-NRT"), routes(stateAfter(store) { setDestinationQuery("tokyo") }))
    }

    @Test
    fun f7_searchCombinesWithTheFilters() = runTest {
        val store = newStore()
        store.setDestinationQuery("tokyo")
        assertEquals(setOf("BOS-HND", "SEA-HND", "BOS-NRT"), routes(stateAfter(store) { setMaxPoints(50_000) }))
    }

    @Test
    fun m1_markersFollowTheSearch() = runTest {
        val state = stateAfter(newStore()) { setDestinationQuery("tokyo") }
        assertEquals(listOf("HND", "NRT"), state.markers.map { it.code })
        assertEquals(state.resultCount, state.markers.sumOf { it.flightCount })
    }

    @Test
    fun l3_aSearchThatExcludesTheSelectionClearsIt() = runTest {
        val store = newStore()
        stateAfter(store) { select("JFK-SYD-80000") }
        assertNull(stateAfter(store) { setDestinationQuery("tokyo") }.selected)
    }

    // Suggestions and picks (S13, S14)

    @Test
    fun s14_tokyoOffersTheCityAndEachAirport() = runTest {
        val state = stateAfter(newStore()) { setDestinationQuery("tokyo") }
        assertEquals(
            listOf(AirportSuggestion("Tokyo", listOf("HND", "NRT"), isCityRow = true), airport("Tokyo", "HND"), airport("Tokyo", "NRT")),
            state.destinationSuggestions,
        )
        assertEquals(emptyList(), state.originSuggestions)
    }

    @Test
    fun s14_pickingOneTokyoAirportExcludesTheOther() = runTest {
        val store = newStore()
        store.setDestinationQuery("tokyo")
        val hnd = stateAfter(store) { pickDestination(listOf("HND")) }
        assertEquals(setOf("BOS-HND", "SEA-HND"), routes(hnd))
        assertEquals(airport("Tokyo", "HND"), hnd.destinationPick)

        store.setDestinationQuery("tokyo")
        assertEquals(setOf("BOS-NRT", "SFO-NRT"), routes(stateAfter(store) { pickDestination(listOf("NRT")) }))
    }

    @Test
    fun s14_pickingTheCityRowKeepsBothAirports() = runTest {
        val store = newStore()
        store.setDestinationQuery("tokyo")
        val city = stateAfter(store) { pickDestination(listOf("HND", "NRT")) }
        assertEquals(setOf("BOS-HND", "SEA-HND", "BOS-NRT", "SFO-NRT"), routes(city))
        assertTrue(city.destinationPick!!.isCityRow)
    }

    @Test
    fun s14_pickingDublinExcludesDubai() = runTest {
        val store = newStore()
        assertEquals(setOf("BOS-DUB", "BOS-DXB"), routes(stateAfter(store) { setDestinationQuery("dub") }))
        assertEquals(setOf("BOS-DUB"), routes(stateAfter(store) { pickDestination(listOf("DUB")) }))
    }

    @Test
    fun s14_aPickClosesTheSuggestionsAndHidesTheHint() = runTest {
        val store = newStore()
        val typed = stateAfter(store) { setDestinationQuery("sydeny") }
        assertEquals(listOf(airport("Sydney", "SYD")), typed.destinationHint)
        assertEquals(listOf(airport("Sydney", "SYD")), typed.destinationSuggestions)

        val picked = stateAfter(store) { pickDestination(listOf("SYD")) }
        assertEquals(emptyList(), picked.destinationHint)
        assertEquals(emptyList(), picked.destinationSuggestions)
    }

    @Test
    fun s14_editingTheTextClearsThePick() = runTest {
        val store = newStore()
        store.setDestinationQuery("tokyo")
        stateAfter(store) { pickDestination(listOf("HND")) }

        val edited = stateAfter(store) { setDestinationQuery("toky") }
        assertNull(edited.destinationPick)
        assertEquals(setOf("BOS-HND", "SEA-HND", "BOS-NRT", "SFO-NRT"), routes(edited))

        store.setOriginQuery("s")
        stateAfter(store) { pickOrigin(listOf("SEA")) }
        val origin = stateAfter(store) { setOriginQuery("se") }
        assertNull(origin.originPick)
        assertEquals(setOf("SEA-HND"), routes(origin))
    }

    @Test
    fun s14_aPickCountsAsSomethingToResetAndResetClearsIt() = runTest {
        val store = newStore()
        val picked = stateAfter(store) { pickOrigin(listOf("BOS")) }
        assertEquals("", picked.originQuery)
        assertTrue(picked.hasAnythingToReset)

        val reset = stateAfter(store) { resetAll() }
        assertNull(reset.originPick)
        assertFalse(reset.hasAnythingToReset)
        assertEquals(7, reset.resultCount)
    }

    @Test
    fun s14_aPickOfAirportsThatAreNotOnThatSideIsIgnored() = runTest {
        val store = newStore()
        for (codes in listOf(listOf("HND"), listOf("BOS", "HND"))) {
            val state = stateAfter(store) { pickOrigin(codes) }
            assertNull(state.originPick, "$codes")
            assertEquals(7, state.resultCount, "$codes")
        }
    }

    @Test
    fun s14_aPickARefreshRemovedGoesBackToSearchingForGood() = runTest {
        val store = newStore()
        stateAfter(store) { pickDestination(listOf("SYD")) }

        cache.cache.value = CacheSnapshot(flights.filterNot { it.destination.code == "SYD" }, Instant.fromEpochMilliseconds(2_000))
        runCurrent()
        assertNull(store.state.value.destinationPick)
        assertEquals(6, store.state.value.resultCount)

        cache.cache.value = CacheSnapshot(flights, Instant.fromEpochMilliseconds(3_000))
        runCurrent()
        assertNull(store.state.value.destinationPick, "it doesn't come back with the data")
    }

    // Ranking (P2, P3, P5, P7, D16)

    @Test
    fun p3_theStoreRanksByTheShippedPassengerFigures() = runTest {
        // Both have one flight here, so flight counts would tie and put DUB first by code. Dubai has more passengers.
        assertEquals(listOf("DXB", "DUB"), stateAfter(newStore()) { setDestinationQuery("du") }.destinationSuggestions.flatMap { it.codes })
    }

    private val nearSanFrancisco = 37.7749 to -122.4194

    @Test
    fun p2_locationPromotesNearbyOriginsInTheSuggestions() = runTest {
        val store = newStore()
        // Seattle has slightly more passengers, so it leads without a location.
        assertEquals(listOf("SEA", "SFO"), stateAfter(store) { setOriginQuery("s") }.originSuggestions.flatMap { it.codes })

        val located = stateAfter(store) { setUserLocation(nearSanFrancisco.first, nearSanFrancisco.second) }
        assertEquals(listOf("SFO", "SEA"), located.originSuggestions.flatMap { it.codes })
        assertTrue(located.isLocationShared)
    }

    @Test
    fun p5_locationNeverChangesTheDestinationSuggestions() = runTest {
        val store = newStore()
        val before = stateAfter(store) { setDestinationQuery("d") }.destinationSuggestions
        val after = stateAfter(store) { setUserLocation(53.4, -6.3) }.destinationSuggestions
        assertEquals(before, after)
    }

    @Test
    fun p7_locationNeverChangesTheFlightList() = runTest {
        val store = newStore()
        stateAfter(store) { setOriginQuery("s") }
        val before = store.state.value.results
        val after = stateAfter(store) { setUserLocation(nearSanFrancisco.first, nearSanFrancisco.second) }.results
        assertEquals(before, after)
    }

    @Test
    fun d16_noLocationIsSharedUntilTheAppSendsOne() = runTest {
        assertFalse(newStore().state.value.isLocationShared)
    }
}
