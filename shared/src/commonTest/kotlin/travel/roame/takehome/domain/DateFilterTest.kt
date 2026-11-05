package travel.roame.takehome.domain

import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** DOMAIN_RULES F1–F3. */
class DateFilterTest {

    private val flights = listOf("2027-02-09", "2027-02-10", "2027-02-11", "2027-02-12", "2027-02-13")
        .map { flight(id = it, date = it) }

    private fun idsBetween(start: String?, end: String?) =
        flights.matching(FlightFilters(dateStart = start, dateEnd = end)).map { it.id }

    @Test
    fun f1_flightsOnBothBoundaryDatesAreIncluded() {
        assertEquals(listOf("2027-02-10", "2027-02-11", "2027-02-12"), idsBetween("2027-02-10", "2027-02-12"))
    }

    @Test
    fun f1_theDayBeforeStartAndTheDayAfterEndAreExcluded() {
        val ids = idsBetween("2027-02-10", "2027-02-12")
        assertFalse("2027-02-09" in ids)
        assertFalse("2027-02-13" in ids)
    }

    @Test
    fun f1_startEqualToEndMatchesThatOneDay() {
        assertEquals(listOf("2027-02-11"), idsBetween("2027-02-11", "2027-02-11"))
    }

    @Test
    fun f1_datesAreComparedAcrossMonthsAndNotAsAnInstant() {
        val flights = listOf("2027-01-31", "2027-02-01", "2027-03-01").map { flight(id = it, date = it) }
        val ids = flights.matching(FlightFilters(dateStart = "2027-02-01", dateEnd = "2027-02-28")).map { it.id }
        assertEquals(listOf("2027-02-01"), ids)
    }

    @Test
    fun f2_startOnlyHasNoUpperLimit() {
        assertEquals(listOf("2027-02-12", "2027-02-13"), idsBetween("2027-02-12", null))
    }

    @Test
    fun f2_endOnlyHasNoLowerLimit() {
        assertEquals(listOf("2027-02-09", "2027-02-10"), idsBetween(null, "2027-02-10"))
    }

    @Test
    fun f2_noBoundsMatchesEverything() {
        assertEquals(flights.map { it.id }, idsBetween(null, null))
    }

    @Test
    fun f3_startAfterEndMatchesNothingAndIsNotSwapped() {
        assertEquals(emptyList(), idsBetween("2027-02-12", "2027-02-10"))
    }
}
