package travel.roame.takehome.domain

import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals

/** DOMAIN_RULES F6. */
class ProgramFilterTest {

    private val flights = listOf("Avios", "Aeroplan", "Flying Blue", "Mileage Plan", "MileagePlus")
        .map { flight(id = it, program = it) }

    private fun programsMatching(vararg programs: String) =
        flights.matching(FlightFilters(programs = programs.toSet())).map { it.program }

    @Test
    fun f6_oneProgramMatchesOnlyThatProgram() {
        assertEquals(listOf("Avios"), programsMatching("Avios"))
    }

    @Test
    fun f6_twoProgramsMatchEitherOne() {
        assertEquals(listOf("Avios", "Aeroplan"), programsMatching("Avios", "Aeroplan"))
    }

    @Test
    fun f6_noProgramsMatchesEveryProgram() {
        assertEquals(flights.map { it.program }, programsMatching())
    }

    @Test
    fun f6_mileagePlanAndMileagePlusAreDifferentPrograms() {
        assertEquals(listOf("Mileage Plan"), programsMatching("Mileage Plan"))
        assertEquals(listOf("MileagePlus"), programsMatching("MileagePlus"))
    }
}
