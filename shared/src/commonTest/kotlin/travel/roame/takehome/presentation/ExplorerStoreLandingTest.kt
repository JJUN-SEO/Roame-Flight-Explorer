package travel.roame.takehome.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.SavedFlight
import travel.roame.takehome.testing.FakeFlightRepository
import travel.roame.takehome.testing.FakeSavedFlightsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** DOMAIN_RULES B5–B9, through the store. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStoreLandingTest {

    private val toronto = Airport("YYZ", "Toronto", 43.68, -79.63)
    private val seattle = Airport("SEA", "Seattle", 47.45, -122.31)
    private val paris = Airport("CDG", "Paris", 49.01, 2.55)
    private val haneda = Airport("HND", "Tokyo", 35.55, 139.78)
    private val narita = Airport("NRT", "Tokyo", 35.77, 140.39)

    private fun flight(id: String, origin: Airport, destination: Airport, points: Int, program: String, cabin: String = "Economy") =
        Flight(id, origin, destination, LocalDate.parse("2027-02-01"), program, points, cabin)

    private val torontoParis = flight("yyz-cdg", toronto, paris, 30_000, "Aeroplan")
    private val torontoTokyo = flight("yyz-nrt", toronto, narita, 50_000, "Avios")
    private val seattleTokyo = flight("sea-hnd", seattle, haneda, 35_000, "Avios", cabin = "Business")
    private val seattleParis = flight("sea-cdg", seattle, paris, 20_000, "Aeroplan")
    private val flights = listOf(torontoParis, torontoTokyo, seattleTokyo, seattleParis)

    private val saved = FakeSavedFlightsRepository(SavedFlight(seattleParis, Instant.fromEpochMilliseconds(500)))

    private fun TestScope.newStore(): ExplorerStore {
        val store = ExplorerStore(FakeFlightRepository(flights), saved, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        return store
    }

    private fun TestScope.stateAfter(store: ExplorerStore, intent: ExplorerStore.() -> Unit): ExplorerState {
        store.intent()
        runCurrent()
        return store.state.value
    }

    private val ottawa = 45.42 to -75.70

    @Test
    fun b5_withoutALocationTheDealsComeFromEveryAirport() = runTest {
        val landing = newStore().state.value.landing
        assertNull(landing.homeAirport)
        assertEquals(listOf("HND", "NRT"), landing.destinations.single { it.city == "Tokyo" }.codes, "Haneda from Seattle, Narita from Toronto")
        assertEquals(setOf("SEA"), landing.offers.map { it.flight.originCode }.toSet(), "Seattle has each program's best value here")
    }

    @Test
    fun b5_sharingALocationMakesTheNearestOriginHome_andEveryDealComesFromIt() = runTest {
        val landing = stateAfter(newStore()) { setUserLocation(ottawa.first, ottawa.second) }.landing
        assertEquals(HomeAirport("YYZ", "Toronto"), landing.homeAirport)
        assertEquals(listOf("YYZ", "YYZ"), landing.offers.map { it.flight.originCode })
        assertEquals(setOf("Paris", "Tokyo"), landing.destinations.map { it.city }.toSet())
        assertEquals(listOf("NRT"), landing.destinations.single { it.city == "Tokyo" }.codes, "Toronto flies to Narita only")
    }

    @Test
    fun b5_theDealsIgnoreTheSearchAndFilters() = runTest {
        val store = newStore()
        val before = store.state.value.landing
        val after = stateAfter(store) {
            setDestinationQuery("tokyo")
            setCabin("Business")
            setMaxPoints(25_000)
        }.landing
        assertEquals(before, after)
    }

    @Test
    fun b7_anOfferCarriesHowFarBelowTypicalItIs() = runTest {
        val offers = newStore().state.value.landing.offers
        val avios = offers.single { it.program == "Avios" }
        assertEquals("sea-hnd", avios.flight.id)
        assertEquals(0, avios.percentBelowTypical, "the only Business flight is the Business typical")
        val aeroplan = offers.single { it.program == "Aeroplan" }
        assertTrue(aeroplan.percentBelowTypical > 0)
    }

    @Test
    fun b7_anOffersFlightShowsWhetherItIsSaved() = runTest {
        val aeroplan = newStore().state.value.landing.offers.single { it.program == "Aeroplan" }
        assertEquals("sea-cdg", aeroplan.flight.id)
        assertTrue(aeroplan.flight.isSaved)
    }

    @Test
    fun b8_eachDestinationHasItsPhotoKey() = runTest {
        val tokyo = newStore().state.value.landing.destinations.single { it.city == "Tokyo" }
        assertEquals("dest_tokyo", tokyo.photoKey)
        assertEquals(listOf("HND", "NRT"), tokyo.codes)
        assertEquals(35_000, tokyo.minPoints)
    }

    // Opening a deal (B9)

    @Test
    fun b9_openingADestinationStartsANewSearchForThatCity() = runTest {
        val store = newStore()
        stateAfter(store) {
            setOriginQuery("seattle")
            setCabin("Business")
        }
        val state = stateAfter(store) { openDestination(listOf("HND", "NRT")) }

        assertEquals("", state.originQuery)
        assertNull(state.originPick, "no home airport without a location")
        assertEquals(listOf("HND", "NRT"), state.destinationPick?.codes)
        assertTrue(state.destinationPick!!.isCityRow)
        assertTrue(state.activeFilters.isEmpty(), "filters reset (C4)")
        assertEquals(setOf("yyz-nrt", "sea-hnd"), state.results.map { it.id }.toSet())
    }

    @Test
    fun b9_withAHomeAirportOpeningADestinationAlsoPicksItAsFrom() = runTest {
        val store = newStore()
        stateAfter(store) { setUserLocation(ottawa.first, ottawa.second) }
        val state = stateAfter(store) { openDestination(listOf("NRT")) }

        assertEquals(listOf("YYZ"), state.originPick?.codes)
        assertEquals(listOf("yyz-nrt"), state.results.map { it.id })
    }

    @Test
    fun b9_openingAnOfferSearchesItsRoute_andSelectsIt() = runTest {
        val store = newStore()
        stateAfter(store) { setMaxPoints(10_000) }
        val state = stateAfter(store) { openOffer("sea-hnd") }

        assertEquals(listOf("SEA"), state.originPick?.codes)
        assertEquals(listOf("HND"), state.destinationPick?.codes)
        assertEquals(listOf("sea-hnd"), state.results.map { it.id })
        assertEquals("sea-hnd", state.selected?.id)
        assertTrue(state.activeFilters.isEmpty())
    }

    @Test
    fun b9_aFlightThatIsNotAnOfferOpensNothing() = runTest {
        val store = newStore()
        val before = stateAfter(store) { setOriginQuery("toronto") }
        val after = stateAfter(store) { openOffer("yyz-cdg") }
        assertEquals(before, after)
    }
}
