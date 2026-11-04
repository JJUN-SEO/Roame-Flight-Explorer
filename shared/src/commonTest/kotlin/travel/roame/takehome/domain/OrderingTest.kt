package travel.roame.takehome.domain

import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.model.SortOrder
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals

/** DOMAIN_RULES O1 (each sort, D46), O2, and D6. The database sorts the Saved list, so `SavedFlightsTest` covers O3. */
class OrderingTest {

    /** The ids sort differently from the dates, points and programs, so skipping a step changes the result. */
    private val flights = listOf(
        flight(id = "a-later-cheap", date = "2027-02-02", points = 10_000, program = "MileagePlus"),
        flight(id = "b-tied", date = "2027-02-01", points = 20_000, program = "Aeroplan"),
        flight(id = "a-tied", date = "2027-02-01", points = 20_000, program = "Aeroplan"),
        flight(id = "z-early-cheap", date = "2027-02-01", points = 10_000, program = "Mileage Plan"),
        flight(id = "later-pricey", date = "2027-02-02", points = 30_000, program = "Aeroplan"),
    )

    /** B4 values; "z-early-cheap" has none (B2). */
    private val values = mapOf("a-later-cheap" to 0.9, "b-tied" to 0.5, "a-tied" to 0.5, "later-pricey" to 0.7)

    private fun order(sort: SortOrder) = resultOrder(sort) { values[it.id] }

    private fun ids(sort: SortOrder) = flights.sortedWith(order(sort)).map { it.id }

    @Test
    fun o1_lowestPointsSortsByPointsThenDateThenId() {
        assertEquals(listOf("z-early-cheap", "a-later-cheap", "a-tied", "b-tied", "later-pricey"), ids(SortOrder.LowestPoints))
    }

    @Test
    fun o1_highestPointsSortsByPointsDownThenDateThenId() {
        assertEquals(listOf("later-pricey", "a-tied", "b-tied", "z-early-cheap", "a-later-cheap"), ids(SortOrder.HighestPoints))
    }

    @Test
    fun o1_byProgramSortsProgramsAlphabetically_cheapestFirstWithinEach() {
        // "Mileage Plan" comes before "MileagePlus", as in the Programs filter (F6).
        assertEquals(listOf("a-tied", "b-tied", "later-pricey", "z-early-cheap", "a-later-cheap"), ids(SortOrder.ByProgram))
    }

    @Test
    fun o1_bestOfferSortsMostBelowTypicalFirst_withNoValueLast() {
        assertEquals(listOf("a-tied", "b-tied", "later-pricey", "a-later-cheap", "z-early-cheap"), ids(SortOrder.BestOffer))
    }

    @Test
    fun o1_bestOfferBreaksTiesByPoints() {
        val cheap = flight(id = "z-cheap", points = 10_000)
        val pricey = flight(id = "a-pricey", points = 20_000)
        assertEquals(listOf(cheap, pricey), listOf(pricey, cheap).sortedWith(resultOrder(SortOrder.BestOffer) { 0.5 }))
    }

    @Test
    fun o1_noSortDependsOnTheInputOrder() {
        for (sort in SortOrder.entries) {
            assertEquals(flights.sortedWith(order(sort)), flights.reversed().sortedWith(order(sort)), "$sort")
        }
        assertEquals(flights.sortedWith(MarkerSheetOrder), flights.reversed().sortedWith(MarkerSheetOrder))
    }

    @Test
    fun o1_filteringSortedFlightsKeepsThemSorted() {
        for (sort in SortOrder.entries) {
            val filtered = flights.sortedWith(order(sort)).matching(FlightFilters(maxPoints = 20_000))
            assertEquals(filtered.sortedWith(order(sort)), filtered, "$sort")
        }
    }

    @Test
    fun o2_markerSheetsSortByPointsThenDateThenId() {
        assertEquals(
            listOf("z-early-cheap", "a-later-cheap", "a-tied", "b-tied", "later-pricey"),
            flights.sortedWith(MarkerSheetOrder).map { it.id },
        )
    }

    @Test
    fun o2_filteringSortedFlightsKeepsThemSorted() {
        val sorted = flights.sortedWith(MarkerSheetOrder)
        val filtered = sorted.matching(FlightFilters(dateEnd = "2027-02-01"))
        assertEquals(filtered.sortedWith(MarkerSheetOrder), filtered)
    }
}
