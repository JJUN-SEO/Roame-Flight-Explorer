package travel.roame.takehome.domain

import travel.roame.takehome.model.ActiveFilter
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.model.FlightFilters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** DOMAIN_RULES C2–C4. */
class ActiveFiltersTest {

    private val everything = FlightFilters(
        dateStart = "2027-02-10",
        dateEnd = "2027-02-12",
        cabin = "Business",
        maxPoints = 60_000,
        programs = setOf("Avios", "Aeroplan"),
    )

    @Test
    fun c2_defaultsShowNoChips() {
        assertEquals(emptyList(), FlightFilters().active())
    }

    @Test
    fun c2_eachFilterGivesOneChipInAFixedOrder() {
        assertEquals(
            listOf(
                ActiveFilter.Dates("2027-02-10", "2027-02-12"),
                ActiveFilter.Cabin("Business"),
                ActiveFilter.MaxPoints(60_000),
                ActiveFilter.Programs(listOf("Aeroplan", "Avios")),
            ),
            everything.active(),
        )
    }

    @Test
    fun c2_oneDateBoundIsStillOneDatesChip() {
        assertEquals(listOf(ActiveFilter.Dates("2027-02-10", null)), FlightFilters(dateStart = "2027-02-10").active())
        assertEquals(listOf(ActiveFilter.Dates(null, "2027-02-12")), FlightFilters(dateEnd = "2027-02-12").active())
    }

    @Test
    fun c2_chipsReportTheKindThatClearsThem() {
        assertEquals(FilterKind.entries, everything.active().map { it.kind })
    }

    @Test
    fun c3_clearingOneFilterLeavesTheOthers() {
        assertEquals(everything.copy(dateStart = null, dateEnd = null), everything.cleared(FilterKind.Dates))
        assertEquals(everything.copy(cabin = null), everything.cleared(FilterKind.Cabin))
        assertEquals(everything.copy(maxPoints = null), everything.cleared(FilterKind.MaxPoints))
        assertEquals(everything.copy(programs = emptySet()), everything.cleared(FilterKind.Programs))
    }

    @Test
    fun c3_clearingEveryChipReturnsToTheDefaults() {
        val cleared = everything.active().fold(everything) { filters, chip -> filters.cleared(chip.kind) }
        assertEquals(FlightFilters(), cleared)
    }

    @Test
    fun c2_nothingToResetAtTheDefaults() {
        assertFalse(hasAnythingToReset(FlightFilters(), originQuery = "", destinationQuery = ""))
    }

    @Test
    fun c2_searchTextIsNotAChipButCountsAsSomethingToReset() {
        assertTrue(hasAnythingToReset(FlightFilters(), originQuery = "bos", destinationQuery = ""))
        assertTrue(hasAnythingToReset(FlightFilters(), originQuery = "", destinationQuery = "tokyo"))
    }

    @Test
    fun c2_anyFilterCountsAsSomethingToReset() {
        for (kind in FilterKind.entries) {
            val onlyThatOne = FilterKind.entries.filter { it != kind }.fold(everything) { filters, other -> filters.cleared(other) }
            assertTrue(hasAnythingToReset(onlyThatOne, originQuery = "", destinationQuery = ""), "$kind")
        }
    }
}
