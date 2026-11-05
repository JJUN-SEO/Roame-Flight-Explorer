package travel.roame.takehome.presentation

import travel.roame.takehome.model.FlightFilters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** DOMAIN_RULES K1–K5. */
class DateCalendarTest {

    private val min = "2027-02-01"
    private val max = "2027-04-01"
    private val none = DateSelection(startIso = null, endIso = null, awaitingEnd = false)

    private fun months(selection: DateSelection = none, from: String = min, to: String = max) = DateCalendar.months(from, to, selection)

    private fun List<CalendarMonth>.day(iso: String) = flatMap { it.weeks }.flatMap { it.days }.first { it.inMonth && it.iso == iso }

    private fun tap(selection: DateSelection, iso: String) = DateCalendar.tap(selection, iso, min, max)

    // Months and weeks (K1)

    @Test
    fun k1_theMonthsRunFromTheFirstDatesMonthToTheLastDatesMonth() {
        assertEquals(listOf(2027 to 2, 2027 to 3, 2027 to 4), months().map { it.year to it.month })
    }

    @Test
    fun k1_theMonthsCrossAYearEnd() {
        assertEquals(listOf(2026 to 12, 2027 to 1), months(from = "2026-12-15", to = "2027-01-10").map { it.year to it.month })
    }

    @Test
    fun k1_weeksStartOnSunday_andTheNeighbouringMonthsFillTheEdges() {
        val february = months().first()
        // 1 February 2027 is a Monday, and the 28th a Sunday.
        assertEquals(listOf("2027-01-31", "2027-02-01", "2027-02-06"), february.weeks.first().days.let { listOf(it[0].iso, it[1].iso, it[6].iso) })
        assertEquals(listOf(false, true), february.weeks.first().days.take(2).map { it.inMonth })
        assertEquals(5, february.weeks.size)
        assertEquals(listOf("2027-02-28", "2027-03-06"), february.weeks.last().days.let { listOf(it.first().iso, it.last().iso) })
        assertEquals(1, february.weeks.last().days.count { it.inMonth })
        assertTrue(february.weeks.all { it.days.size == 7 })
    }

    @Test
    fun k1_aMonthStartingOnSundayHasNoLeadingDays() {
        val august = months(from = "2027-08-01", to = "2027-08-31").single()
        assertEquals("2027-08-01", august.weeks.first().days.first().iso)
        assertTrue(august.weeks.first().days.all { it.inMonth })
    }

    @Test
    fun k1_aLeapYearsFebruaryHas29Days() {
        val february = months(from = "2028-02-01", to = "2028-02-29").single()
        assertEquals(29, february.weeks.flatMap { it.days }.count { it.inMonth })
    }

    @Test
    fun k1_withoutDataThereAreNoMonths() {
        assertTrue(DateCalendar.months(null, null, none).isEmpty())
    }

    // What can be picked (K2)

    @Test
    fun k2_onlyTheDatasDatesCanBePicked() {
        val calendar = months()
        assertTrue(calendar.day("2027-02-01").isSelectable)
        assertTrue(calendar.day("2027-04-01").isSelectable)
        assertFalse(calendar.day("2027-04-02").isSelectable)
        assertFalse(calendar.first().weeks.first().days.first().isSelectable, "31 January, outside the month and the data")
    }

    @Test
    fun k2_tappingADayOutsideTheDataChangesNothing() {
        val selection = tap(none, "2027-02-10")
        assertEquals(selection, tap(selection, "2027-04-02"))
        assertEquals(selection, tap(selection, "2027-01-31"))
    }

    // Taps (K3)

    @Test
    fun k3_theFirstTapStartsARange_andAwaitsItsEnd() {
        assertEquals(DateSelection("2027-02-10", null, awaitingEnd = true), tap(none, "2027-02-10"))
    }

    @Test
    fun k3_aDayOnOrAfterTheStartEndsTheRange() {
        val started = tap(none, "2027-02-10")
        assertEquals(DateSelection("2027-02-10", "2027-03-05", awaitingEnd = false), tap(started, "2027-03-05"))
        assertEquals(DateSelection("2027-02-10", "2027-02-10", awaitingEnd = false), tap(started, "2027-02-10"), "one day")
    }

    @Test
    fun k3_aDayBeforeTheStartStartsAgain() {
        val started = tap(none, "2027-02-10")
        assertEquals(DateSelection("2027-02-05", null, awaitingEnd = true), tap(started, "2027-02-05"))
    }

    @Test
    fun k3_aTapAfterACompleteRangeStartsANewOne() {
        val range = tap(tap(none, "2027-02-10"), "2027-02-20")
        assertEquals(DateSelection("2027-03-01", null, awaitingEnd = true), tap(range, "2027-03-01"))
    }

    @Test
    fun k3_aSavedRangeOpensWithNoEndAwaited_soTheNextTapStartsAgain() {
        val opened = DateCalendar.selectionOf(FlightFilters(dateStart = "2027-02-10", dateEnd = "2027-02-20"))
        assertEquals(DateSelection("2027-02-12", null, awaitingEnd = true), tap(opened, "2027-02-12"))
    }

    // Marks (K4)

    @Test
    fun k4_withNoDateFilterTheWholeRangeIsMarked() {
        val calendar = months()
        assertEquals(DayMark.Start, calendar.day("2027-02-01").mark)
        assertEquals(DayMark.Between, calendar.day("2027-03-15").mark)
        assertEquals(DayMark.End, calendar.day("2027-04-01").mark)
    }

    @Test
    fun k4_aMissingBoundIsTheDatasOwn() {
        val calendar = months(DateSelection("2027-03-01", null, awaitingEnd = false))
        assertEquals(DayMark.Unmarked, calendar.day("2027-02-28").mark)
        assertEquals(DayMark.Start, calendar.day("2027-03-01").mark)
        assertEquals(DayMark.End, calendar.day("2027-04-01").mark)
    }

    @Test
    fun k4_whileTheEndIsAwaitedOnlyTheStartIsMarked() {
        val calendar = months(DateSelection("2027-02-10", null, awaitingEnd = true))
        assertEquals(DayMark.Single, calendar.day("2027-02-10").mark)
        assertEquals(DayMark.Unmarked, calendar.day("2027-02-11").mark)
        assertEquals(DayMark.Unmarked, calendar.day("2027-04-01").mark)
    }

    @Test
    fun k4_aOneDayRangeIsASingleMark() {
        assertEquals(DayMark.Single, months(DateSelection("2027-02-10", "2027-02-10", awaitingEnd = false)).day("2027-02-10").mark)
    }

    @Test
    fun k4_daysFromTheNeighbouringMonthsAreNeverMarked() {
        val march = months().first { it.month == 3 }
        assertTrue(march.weeks.flatMap { it.days }.filterNot { it.inMonth }.all { it.mark == DayMark.Unmarked })
    }

    // Opening (K5)

    @Test
    fun k5_theCalendarOpensOnTheCurrentFilter() {
        assertEquals(DateSelection("2027-02-10", null, awaitingEnd = false), DateCalendar.selectionOf(FlightFilters(dateStart = "2027-02-10")))
        assertEquals(none, DateCalendar.selectionOf(FlightFilters()))
    }
}
