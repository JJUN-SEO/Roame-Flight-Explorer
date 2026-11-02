package travel.roame.takehome.domain

import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals

/** DOMAIN_RULES O1, O2, and D6. The database sorts the Saved list, so `SavedFlightsTest` covers O3. */
class OrderingTest {

    /** The ids sort differently from the dates and points, so skipping a step changes the result. */
    private val flights = listOf(
        flight(id = "a-later-cheap", date = "2027-02-02", points = 10_000),
        flight(id = "b-tied", date = "2027-02-01", points = 20_000),
        flight(id = "a-tied", date = "2027-02-01", points = 20_000),
        flight(id = "z-early-cheap", date = "2027-02-01", points = 10_000),
        flight(id = "later-pricey", date = "2027-02-02", points = 30_000),
    )

    @Test
    fun o1_resultsSortByDateThenPointsThenId() {
        assertEquals(
            listOf("z-early-cheap", "a-tied", "b-tied", "a-later-cheap", "later-pricey"),
            flights.sortedWith(ResultOrder).map { it.id },
        )
    }

    @Test
    fun o2_markerSheetsSortByPointsThenDateThenId() {
        assertEquals(
            listOf("z-early-cheap", "a-later-cheap", "a-tied", "b-tied", "later-pricey"),
            flights.sortedWith(MarkerSheetOrder).map { it.id },
        )
    }

    @Test
    fun o1_theOrderDoesNotDependOnTheInputOrder() {
        assertEquals(flights.sortedWith(ResultOrder), flights.reversed().sortedWith(ResultOrder))
        assertEquals(flights.sortedWith(MarkerSheetOrder), flights.reversed().sortedWith(MarkerSheetOrder))
    }

    @Test
    fun o1_filteringSortedFlightsKeepsThemSorted() {
        val sorted = flights.sortedWith(ResultOrder)
        val filtered = sorted.matching(FlightFilters(maxPoints = 20_000))
        assertEquals(filtered.sortedWith(ResultOrder), filtered)
    }

    @Test
    fun o2_filteringSortedFlightsKeepsThemSorted() {
        val sorted = flights.sortedWith(MarkerSheetOrder)
        val filtered = sorted.matching(FlightFilters(dateEnd = "2027-02-01"))
        assertEquals(filtered.sortedWith(MarkerSheetOrder), filtered)
    }
}
