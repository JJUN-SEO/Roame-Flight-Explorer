package travel.roame.takehome.domain

import kotlinx.datetime.LocalDate
import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals

/** DOMAIN_RULES F7. */
class CombinedFilterTest {

    private val match = flight(
        id = "match",
        origin = "BOS",
        destination = "HND",
        date = "2027-02-10",
        points = 50_000,
        cabin = "Business",
        program = "Avios",
    )

    /** Each one fails exactly one condition. */
    private val nearMisses = listOf(
        match.copy(id = "wrong-origin", origin = match.origin.copy(code = "JFK")),
        match.copy(id = "wrong-destination", destination = match.destination.copy(code = "CDG")),
        match.copy(id = "wrong-date", departureDate = LocalDate(2027, 3, 1)),
        match.copy(id = "wrong-cabin", cabin = "Economy"),
        match.copy(id = "wrong-points", points = 60_500),
        match.copy(id = "wrong-program", program = "Flying Blue"),
    )

    private val flights = nearMisses + match

    private val filters = FlightFilters(
        dateStart = "2027-02-01",
        dateEnd = "2027-02-28",
        cabin = "Business",
        maxPoints = 60_000,
        programs = setOf("Avios", "Aeroplan"),
    )

    private val originCodes = setOf("BOS")
    private val destinationCodes = setOf("HND", "NRT")

    private fun ids(filters: FlightFilters = this.filters, origin: Set<String>? = originCodes, destination: Set<String>? = destinationCodes) =
        flights.matching(filters, origin, destination).map { it.id }

    @Test
    fun f7_aFlightMustSatisfyEveryFilterAndBothSearchFields() {
        assertEquals(listOf("match"), ids())
    }

    @Test
    fun f7_removingAnyOneConditionBringsBackOnlyItsNearMiss() {
        assertEquals(setOf("match", "wrong-origin"), ids(origin = null).toSet())
        assertEquals(setOf("match", "wrong-destination"), ids(destination = null).toSet())
        assertEquals(setOf("match", "wrong-date"), ids(filters.copy(dateStart = null, dateEnd = null)).toSet())
        assertEquals(setOf("match", "wrong-cabin"), ids(filters.copy(cabin = null)).toSet())
        assertEquals(setOf("match", "wrong-points"), ids(filters.copy(maxPoints = null)).toSet())
        assertEquals(setOf("match", "wrong-program"), ids(filters.copy(programs = emptySet())).toSet())
    }

    @Test
    fun f7_aSearchFieldThatMatchedNothingMatchesNoFlights() {
        assertEquals(emptyList(), ids(origin = emptySet()))
        assertEquals(emptyList(), ids(destination = emptySet()))
    }

    @Test
    fun f7_noFiltersAndNoSearchMatchesEverythingInTheSameOrder() {
        assertEquals(flights, flights.matching(FlightFilters()))
    }
}
