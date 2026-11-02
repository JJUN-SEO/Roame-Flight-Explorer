package travel.roame.takehome.domain

import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.testing.flight
import kotlin.test.Test
import kotlin.test.assertEquals

/** DOMAIN_RULES F4. */
class PointsFilterTest {

    private val flights = listOf(9_000, 59_500, 60_000, 60_500, 133_000).map { flight(id = "$it", points = it) }

    private fun pointsUpTo(limit: Int?) = flights.matching(FlightFilters(maxPoints = limit)).map { it.points }

    @Test
    fun f4_aFlightExactlyAtTheLimitIsIncluded() {
        assertEquals(listOf(9_000, 59_500, 60_000), pointsUpTo(60_000))
    }

    @Test
    fun f4_oneStepBelowTheLimitExcludesTheFlightAtIt() {
        assertEquals(listOf(9_000, 59_500), pointsUpTo(59_500))
    }

    @Test
    fun f4_oneStepAboveTheLimitIncludesTheNextFlight() {
        assertEquals(listOf(9_000, 59_500, 60_000, 60_500), pointsUpTo(60_500))
    }

    @Test
    fun f4_noLimitMatchesEverything() {
        assertEquals(flights.map { it.points }, pointsUpTo(null))
    }

    @Test
    fun f4_aLimitBelowEveryFlightMatchesNothing() {
        assertEquals(emptyList(), pointsUpTo(8_500))
    }
}
