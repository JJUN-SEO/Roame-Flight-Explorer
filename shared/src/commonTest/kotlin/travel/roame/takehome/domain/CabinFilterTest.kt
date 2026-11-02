package travel.roame.takehome.domain

import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals

/** DOMAIN_RULES F5, DM1. */
class CabinFilterTest {

    private val flights = listOf("Economy", "Premium Economy", "Business").map { flight(id = it, cabin = it) }

    @Test
    fun f5_aCabinMatchesOnlyThatCabin() {
        assertEquals(listOf("Premium Economy"), flights.matching(FlightFilters(cabin = "Premium Economy")).map { it.cabin })
    }

    @Test
    fun f5_noCabinMatchesEveryCabin() {
        assertEquals(flights, flights.matching(FlightFilters(cabin = null)))
    }

    @Test
    fun dm1_anUnknownCabinCanStillBeFiltered() {
        val all = flights + flight(id = "new", cabin = "Lie Flat Plus")
        assertEquals(listOf("new"), all.matching(FlightFilters(cabin = "Lie Flat Plus")).map { it.id })
    }

    @Test
    fun dm1_knownCabinsSortByRankAndUnknownOnesAfterThemAlphabetically() {
        val shuffled = listOf("Zed Class", "First", "Business", "Alpha Class", "Economy", "Premium Economy")
        assertEquals(
            listOf("Economy", "Premium Economy", "Business", "First", "Alpha Class", "Zed Class"),
            shuffled.sortedWith(CabinOrder),
        )
    }
}
