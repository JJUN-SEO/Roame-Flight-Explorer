package travel.roame.takehome.domain

import travel.roame.takehome.model.FilterOptions
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** DOMAIN_RULES F2, F4, F5, F6, F8. */
class FilterOptionsTest {

    private val flights = listOf(
        flight(id = "a", date = "2027-03-01", points = 30_000, cabin = "Business", program = "MileagePlus"),
        flight(id = "b", date = "2027-02-01", points = 9_000, cabin = "Premium Economy", program = "Aeroplan"),
        flight(id = "c", date = "2027-04-01", points = 133_000, cabin = "Economy", program = "Mileage Plan"),
    )

    private val options = flights.toFilterOptions()

    @Test
    fun f8_boundsComeFromTheData() {
        assertEquals("2027-02-01", options.minDate)
        assertEquals("2027-04-01", options.maxDate)
        assertEquals(9_000, options.minPoints)
        assertEquals(133_000, options.maxPoints)
    }

    @Test
    fun f5_cabinOptionsComeFromTheDataInRankOrder() {
        assertEquals(listOf("Economy", "Premium Economy", "Business"), options.cabins)
    }

    @Test
    fun f6_programOptionsComeFromTheDataAlphabetically() {
        assertEquals(listOf("Aeroplan", "Mileage Plan", "MileagePlus"), options.programs)
    }

    @Test
    fun f8_noFlightsMeansNoOptions() {
        val none = emptyList<Flight>().toFilterOptions()
        assertEquals(FilterOptions(), none)
        assertTrue(none.isEmpty)
        assertFalse(options.isEmpty)
    }

    // Normalising (F2, F4, F8)

    @Test
    fun f2_aDateBoundEqualToTheDatasetBoundBecomesNull() {
        val filters = FlightFilters(dateStart = "2027-02-01", dateEnd = "2027-04-01")
        assertEquals(FlightFilters(), filters.normalizedTo(options))
    }

    @Test
    fun f2_aDateBoundInsideTheDataIsKept() {
        val filters = FlightFilters(dateStart = "2027-02-02", dateEnd = "2027-03-31")
        assertEquals(filters, filters.normalizedTo(options))
    }

    @Test
    fun f2_aDateBoundPastTheDataAfterARefreshBecomesNull() {
        val filters = FlightFilters(dateStart = "2027-01-15", dateEnd = "2027-05-01")
        assertEquals(FlightFilters(), filters.normalizedTo(options))
    }

    @Test
    fun f3_normalisingNeverTurnsAnImpossibleRangeIntoAResult() {
        val impossible = FlightFilters(dateStart = "2027-05-01", dateEnd = "2027-04-01")
        assertEquals(emptyList(), flights.matching(impossible.normalizedTo(options)))
    }

    @Test
    fun f4_aPointsLimitAtTheTopBecomesNull() {
        assertEquals(FlightFilters(), FlightFilters(maxPoints = 133_000).normalizedTo(options))
    }

    @Test
    fun f4_aPointsLimitBelowTheTopIsKept() {
        val filters = FlightFilters(maxPoints = 132_500)
        assertEquals(filters, filters.normalizedTo(options))
    }

    @Test
    fun f8_aCabinThatNoLongerExistsIsDropped() {
        assertEquals(FlightFilters(), FlightFilters(cabin = "First").normalizedTo(options))
    }

    @Test
    fun f8_aProgramThatNoLongerExistsIsDroppedAndTheRestAreKept() {
        val filters = FlightFilters(programs = setOf("Aeroplan", "Avios"))
        assertEquals(FlightFilters(programs = setOf("Aeroplan")), filters.normalizedTo(options))
    }

    @Test
    fun f8_anEmptyDatasetResetsEveryFilter() {
        val filters = FlightFilters("2027-02-02", "2027-03-01", "Business", 60_000, setOf("Avios"))
        assertEquals(FlightFilters(), filters.normalizedTo(FilterOptions()))
    }

    @Test
    fun f8_normalisingDoesNotChangeWhichFlightsMatch() {
        val cases = listOf(
            FlightFilters(dateStart = "2027-02-01"),
            FlightFilters(dateEnd = "2027-04-01"),
            FlightFilters(maxPoints = 133_000),
            FlightFilters(dateStart = "2027-02-15", maxPoints = 40_000, programs = setOf("MileagePlus")),
        )
        for (filters in cases) {
            assertEquals(flights.matching(filters), flights.matching(filters.normalizedTo(options)), "$filters")
        }
    }
}
