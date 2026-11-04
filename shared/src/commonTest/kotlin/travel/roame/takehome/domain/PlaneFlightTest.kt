package travel.roame.takehome.domain

import travel.roame.takehome.domain.PlaneFlight.FADED_IN
import travel.roame.takehome.domain.PlaneFlight.FADING_OUT
import travel.roame.takehome.domain.PlaneFlight.FLIGHT_MILLIS
import travel.roame.takehome.domain.PlaneFlight.GONE
import travel.roame.takehome.domain.PlaneFlight.PAUSE_MILLIS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** DOMAIN_RULES M9: the plane's flight along the selected route. */
class PlaneFlightTest {

    /** A frame every 10 ms of one flight. */
    private val flight = (0..FLIGHT_MILLIS step 10).map(PlaneFlight::at)

    @Test
    fun m9_thePlaneTakesOffFromTheOrigin_outOfSight() {
        assertEquals(PlaneFrame(0.0, 0.0), PlaneFlight.at(0))
    }

    @Test
    fun m9_itFadesInJustAfterTheOrigin() {
        val fading = flight.filter { it.fraction > 0.0 && it.fraction < FADED_IN }
        assertTrue(fading.isNotEmpty() && fading.all { it.opacity > 0.0 && it.opacity < 1.0 }, "$fading")
        fading.zipWithNext { a, b -> assertTrue(b.opacity > a.opacity, "$a then $b") }
    }

    @Test
    fun m9_itIsFullyVisibleForMostOfTheRoute() {
        assertTrue(flight.filter { it.fraction in FADED_IN..FADING_OUT }.all { it.opacity == 1.0 })
    }

    @Test
    fun m9_itFadesOutBeforeTheDestinationsPill_andIsGoneAfter() {
        val fading = flight.filter { it.fraction > FADING_OUT && it.fraction < GONE }
        assertTrue(fading.isNotEmpty() && fading.all { it.opacity > 0.0 && it.opacity < 1.0 }, "$fading")
        fading.zipWithNext { a, b -> assertTrue(b.opacity < a.opacity, "$a then $b") }
        assertTrue(flight.filter { it.fraction >= GONE }.all { it.opacity == 0.0 })
    }

    @Test
    fun m9_halfwayThroughTheFlight_itIsHalfwayAlongTheRoute() {
        assertEquals(PlaneFrame(0.5, 1.0), PlaneFlight.at(FLIGHT_MILLIS / 2))
    }

    @Test
    fun m9_itNeverFliesBackwards() {
        flight.zipWithNext { a, b -> assertTrue(b.fraction >= a.fraction, "$a then $b") }
    }

    @Test
    fun m9_itEasesOutOfTheOriginAndIntoTheDestination() {
        val tenth = FLIGHT_MILLIS / 10
        assertTrue(PlaneFlight.at(tenth).fraction < 0.05, "slow after take-off")
        assertTrue(1 - PlaneFlight.at(FLIGHT_MILLIS - tenth).fraction < 0.05, "slow before landing")
        val middle = PlaneFlight.at(FLIGHT_MILLIS / 2 + tenth / 2).fraction - PlaneFlight.at(FLIGHT_MILLIS / 2 - tenth / 2).fraction
        assertTrue(middle > 0.1, "fastest in the middle: $middle")
    }

    @Test
    fun m9_itReachesTheDestination() {
        assertTrue(PlaneFlight.at(FLIGHT_MILLIS - 1).fraction > 0.999)
    }

    @Test
    fun m9_itWaitsOutOfSight_thenFliesAgain() {
        assertEquals(PlaneFrame(1.0, 0.0), PlaneFlight.at(FLIGHT_MILLIS + PAUSE_MILLIS / 2))
        assertEquals(PlaneFlight.at(0), PlaneFlight.at(FLIGHT_MILLIS + PAUSE_MILLIS))
        assertEquals(PlaneFlight.at(1_234), PlaneFlight.at(3 * (FLIGHT_MILLIS + PAUSE_MILLIS) + 1_234))
    }

    @Test
    fun m9_beforeTakeOffItWaitsAtTheOrigin() {
        assertEquals(PlaneFlight.at(0), PlaneFlight.at(-100))
    }

    @Test
    fun m9_underReduceMotionItRestsHalfway_fullyVisible() {
        assertEquals(PlaneFrame(0.5, 1.0), PlaneFlight.resting)
    }
}
