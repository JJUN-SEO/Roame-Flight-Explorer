package travel.roame.takehome.domain

import kotlinx.datetime.LocalDate
import travel.roame.takehome.domain.search.GeoPoint
import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.Flight
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DOMAIN_RULES B1–B8. */
class DealsTest {

    /** On the equator, so a flight's distance is exactly the arc between the two longitudes. */
    private fun equator(code: String, km: Double, city: String = "$code city") =
        Airport(code, city, 0.0, km / 6371.0 * 180 / PI)

    private val home = equator("AAA", 0.0)

    private fun flight(
        id: String,
        destination: Airport,
        points: Int,
        cabin: String = "Economy",
        program: String = "Avios",
        origin: Airport = home,
        date: String = "2027-02-01",
    ) = Flight(id, origin, destination, LocalDate.parse(date), program, points, cabin)

    private fun valueOf(id: String, flights: List<Flight>) = DealIndex.rate(flights).single { it.flight.id == id }

    // Rate (B1, B2)

    @Test
    fun b1_b2_rateIsPointsPerThousandKilometresOfGreatCircle() {
        val rate = flight("a", equator("BBB", 2_000.0), points = 30_000).rate()!!
        assertEquals(15_000.0, rate, 1e-6)
    }

    @Test
    fun b1_distanceIsTheGreatCircleNotAStraightLineOnTheMap() {
        // From the equator to the pole is a quarter of the Earth's circumference, whatever the longitude.
        val pole = Airport("NPL", "North Pole", 90.0, 120.0)
        val rate = flight("a", pole, points = 10_007).rate()!!
        assertEquals(1_000.0, rate, 0.1)
    }

    @Test
    fun b2_aFlightWithNoDistanceHasNoRateAndIsNeverADeal() {
        val sameAirport = flight("a", home, points = 20_000)
        assertNull(sameAirport.rate())
        assertTrue(DealIndex.of(listOf(sameAirport)).dealsFrom(null).offers.isEmpty())
    }

    @Test
    fun b2_aFlightWithNoPointsHasNoRate() {
        assertNull(flight("a", equator("BBB", 1_000.0), points = 0).rate())
    }

    // Typical rate and value (B3, B4)

    @Test
    fun b3_theTypicalRateIsTheMedian() {
        assertEquals(2.0, DealIndex.median(listOf(3.0, 1.0, 2.0)))
        assertEquals(2.5, DealIndex.median(listOf(10.0, 1.0, 3.0, 2.0)), "the mean of the middle two")
    }

    @Test
    fun b3_b4_eachCabinIsComparedWithItsOwnTypicalRate() {
        val far = equator("BBB", 1_000.0)
        val flights = listOf(
            flight("e1", far, 10_000), flight("e2", far, 20_000), flight("e3", far, 30_000),
            flight("b1", far, 50_000, cabin = "Business"), flight("b2", far, 60_000, cabin = "Business"),
            flight("b3", far, 70_000, cabin = "Business"),
        )
        assertEquals(1.0, valueOf("e2", flights).value, 1e-9)
        assertEquals(1.0, valueOf("b2", flights).value, 1e-9, "a typical Business fare is typical, though it costs more than any Economy one")
        assertEquals(0.5, valueOf("e1", flights).value, 1e-9)
    }

    @Test
    fun b4_percentBelowTypicalRoundsDown_andIsZeroAtOrAboveTypical() {
        val far = equator("BBB", 1_000.0)
        val flights = listOf(flight("cheap", far, 20_000), flight("typical", far, 30_000), flight("dear", far, 45_000))
        assertEquals(33, valueOf("cheap", flights).percentBelowTypical, "a third below is 33%, not 34%")
        assertEquals(0, valueOf("typical", flights).percentBelowTypical)
        assertEquals(0, valueOf("dear", flights).percentBelowTypical)
    }

    @Test
    fun b4_aLongCheapFlightBeatsAShortCheaperOne() {
        val near = equator("NEA", 1_000.0)
        val far = equator("FAR", 10_000.0)
        val flights = listOf(flight("short", near, 15_000), flight("long", far, 60_000))
        val best = DealIndex.of(flights).dealsFrom(null).offers.single().best.flight.id
        assertEquals("long", best, "6,000 points per 1,000 km beats 15,000, though it costs more points")
    }

    // Home airport (B5)

    private val toronto = Airport("YYZ", "Toronto", 43.68, -79.63)
    private val seattle = Airport("SEA", "Seattle", 47.45, -122.31)
    private val paris = Airport("CDG", "Paris", 49.01, 2.55)

    @Test
    fun b5_theHomeAirportIsTheOriginNearestTheUser() {
        val index = DealIndex.of(listOf(flight("t", paris, 30_000, origin = toronto), flight("s", paris, 30_000, origin = seattle)))
        assertEquals("YYZ", index.homeAirport(GeoPoint(45.42, -75.70))?.code, "Ottawa is nearest Toronto")
        assertEquals("SEA", index.homeAirport(GeoPoint(49.28, -123.12))?.code, "Vancouver is nearest Seattle")
    }

    @Test
    fun b5_theNearestOriginIsHomeHoweverFarItIs() {
        val index = DealIndex.of(listOf(flight("t", paris, 30_000, origin = toronto), flight("s", paris, 30_000, origin = seattle)))
        assertEquals("SEA", index.homeAirport(GeoPoint(37.57, 126.98))?.code, "Seoul")
    }

    @Test
    fun b5_equallyNearOriginsTieByCode() {
        val west = equator("WWW", 1_000.0).copy(longitude = -10.0)
        val east = equator("EEE", 1_000.0).copy(longitude = 10.0)
        val index = DealIndex.of(listOf(flight("w", paris, 30_000, origin = west), flight("e", paris, 30_000, origin = east)))
        assertEquals("EEE", index.homeAirport(GeoPoint(0.0, 0.0))?.code)
    }

    @Test
    fun b5_withoutALocationThereIsNoHomeAirport() {
        assertNull(DealIndex.of(listOf(flight("t", paris, 30_000, origin = toronto))).homeAirport(null))
    }

    @Test
    fun b5_withAHomeAirportTheDealsAreOnlyFromIt_andWithoutOneFromEverywhere() {
        val flights = listOf(
            flight("fromToronto", paris, 40_000, origin = toronto),
            flight("fromSeattle", paris, 20_000, origin = seattle),
        )
        val index = DealIndex.of(flights)
        assertEquals("fromToronto", index.dealsFrom(toronto).offers.single().best.flight.id)
        assertEquals("fromSeattle", index.dealsFrom(null).offers.single().best.flight.id)
    }

    // Estimated value (B6, A22)

    @Test
    fun b6_theEstimateUsesRoamesCentsPerPointForTheCabin() {
        assertEquals(504, estimatedValueUsd(28_000, "Economy"), "1.80¢")
        assertEquals(702, estimatedValueUsd(39_000, "Premium Economy"), "Economy's 1.80¢: Roame gives none for Premium Economy")
        assertEquals(1_640, estimatedValueUsd(40_000, "Business"), "4.10¢")
        assertEquals(10_500, estimatedValueUsd(100_000, "First"), "10.5¢")
    }

    @Test
    fun b6_theEstimateRoundsToWholeDollars() {
        assertEquals(22, estimatedValueUsd(1_234, "Economy"), "$22.21")
        assertEquals(51, estimatedValueUsd(1_250, "Business"), "$51.25")
    }

    @Test
    fun b6_aCabinWithNoPublishedValueHasNoEstimate() {
        assertNull(estimatedValueUsd(30_000, "Basic Economy"))
    }

    // Best offer per program (B7)

    @Test
    fun b7_eachProgramShowsItsBestValueFlight() {
        val far = equator("BBB", 1_000.0)
        val flights = listOf(
            flight("avios-dear", far, 30_000, program = "Avios"),
            flight("avios-cheap", far, 20_000, program = "Avios"),
            flight("aeroplan", far, 25_000, program = "Aeroplan"),
        )
        val offers = DealIndex.of(flights).dealsFrom(null).offers
        assertEquals(mapOf("Avios" to "avios-cheap", "Aeroplan" to "aeroplan"), offers.associate { it.program to it.best.flight.id })
    }

    @Test
    fun b7_offersAreBestValueFirst_thenByProgram() {
        val far = equator("BBB", 1_000.0)
        val flights = listOf(
            flight("z", far, 20_000, program = "Zeta"),
            flight("b", far, 30_000, program = "Beta"),
            flight("a", far, 30_000, program = "Alpha"),
        )
        assertEquals(listOf("Zeta", "Alpha", "Beta"), DealIndex.of(flights).dealsFrom(null).offers.map { it.program })
    }

    @Test
    fun b7_equalValueTiesByPointsThenDateThenId() {
        val near = equator("NEA", 1_000.0)
        // Different distances rarely give exactly equal values in floating point, so this one uses the order directly.
        val fewerPoints = RatedFlight(flight("b", equator("FAR", 2_000.0), 40_000), value = 0.5)
        val morePoints = RatedFlight(flight("a", near, 50_000), value = 0.5)
        assertEquals(listOf(fewerPoints, morePoints), listOf(morePoints, fewerPoints).sortedWith(DealOrder), "fewer points first")

        val sameDay = listOf(flight("b", near, 20_000), flight("a", near, 20_000))
        assertEquals("a", DealIndex.of(sameDay).dealsFrom(null).offers.single().best.flight.id, "then id")

        val dates = listOf(flight("a", near, 20_000, date = "2027-03-01"), flight("b", near, 20_000, date = "2027-02-01"))
        assertEquals("b", DealIndex.of(dates).dealsFrom(null).offers.single().best.flight.id, "then date, before id")
    }

    // Destination deals (B8)

    private val haneda = Airport("HND", "Tokyo", 35.55, 139.78)
    private val narita = Airport("NRT", "Tokyo", 35.77, 140.39)

    @Test
    fun b8_aCityIsOneDeal_withAllItsAirports() {
        val flights = listOf(flight("h", haneda, 60_000, origin = toronto), flight("n", narita, 50_000, origin = toronto))
        val tokyo = DealIndex.of(flights).dealsFrom(null).destinations.single()
        assertEquals("Tokyo", tokyo.city)
        assertEquals(listOf("HND", "NRT"), tokyo.codes)
        assertEquals(50_000, tokyo.minPoints)
    }

    @Test
    fun b8_theLowestPointsAndTheBestValueCanBeDifferentFlights() {
        val far = equator("FAR", 10_000.0, city = "Faraway")
        val nearSameCity = equator("NEA", 1_000.0, city = "Faraway")
        val flights = listOf(flight("value", far, 50_000), flight("cheap", nearSameCity, 30_000))
        val deal = DealIndex.of(flights).dealsFrom(null).destinations.single()
        assertEquals(30_000, deal.minPoints)
        assertEquals("value", deal.best.flight.id)
    }

    @Test
    fun b8_destinationsAreBestValueFirst_thenByCity() {
        val flights = listOf(
            flight("b", equator("BBB", 1_000.0, city = "Bravo"), 30_000),
            flight("c", equator("CCC", 1_000.0, city = "Charlie"), 20_000),
            flight("a", equator("AAB", 1_000.0, city = "Alpha"), 30_000),
        )
        assertEquals(listOf("Charlie", "Alpha", "Bravo"), DealIndex.of(flights).dealsFrom(null).destinations.map { it.city })
    }

    @Test
    fun b8_theDeckHoldsAtMostThirtyDestinations() {
        val flights = (1..40).map { flight("f$it", equator("D$it", 1_000.0 + it, city = "City $it"), 20_000) }
        assertEquals(DealIndex.MAX_DESTINATIONS, DealIndex.of(flights).dealsFrom(null).destinations.size)
        assertEquals(30, DealIndex.MAX_DESTINATIONS)
    }

    @Test
    fun b8_thePhotoKeyIsTheCityInLowercaseAscii() {
        assertEquals("dest_sao_paulo", photoKey("São Paulo"))
        assertEquals("dest_sao_paulo", photoKey("Sao Paulo"))
        assertEquals("dest_mexico_city", photoKey("Mexico City"))
        assertEquals("dest_hong_kong", photoKey(" Hong  Kong "))
    }
}
