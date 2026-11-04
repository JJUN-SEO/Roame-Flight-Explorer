package travel.roame.takehome.domain

import travel.roame.takehome.domain.search.GeoPoint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** DOMAIN_RULES M7. The midpoints come from a separate Python script using a different formula. */
class GreatCircleTest {

    private val jfk = GeoPoint(40.63945, -73.77932)
    private val hkg = GeoPoint(22.31184, 113.91486)
    private val sea = GeoPoint(47.44794, -122.31028)
    private val syd = GeoPoint(-33.9461, 151.177)
    private val sfo = GeoPoint(37.61981, -122.37482)
    private val nrt = GeoPoint(35.76858, 140.38871)
    private val bos = GeoPoint(42.36197, -71.0079)
    private val cdg = GeoPoint(49.0097, 2.5479)

    private val routes = listOf(jfk to hkg, hkg to jfk, sea to syd, syd to sea, sfo to nrt, nrt to sfo, bos to cdg, cdg to bos)

    /** The same longitude once any multiple of 360 is removed, in -180..180. */
    private fun normalized(longitude: Double) = longitude - 360.0 * floor((longitude + 180.0) / 360.0)

    @Test
    fun m7_givesOneMorePointThanSegments() {
        assertEquals(65, GreatCircle.interpolate(jfk, hkg).size)
        assertEquals(2, GreatCircle.interpolate(jfk, hkg, segments = 1).size)
        assertEquals(11, GreatCircle.interpolate(jfk, hkg, segments = 10).size)
    }

    @Test
    fun m7_segmentsMustBePositive() {
        assertFailsWith<IllegalArgumentException> { GreatCircle.interpolate(jfk, hkg, segments = 0) }
    }

    @Test
    fun m7_startsExactlyAtTheOriginAndEndsAtTheDestination() {
        for ((from, to) in routes) {
            val points = GreatCircle.interpolate(from, to)
            assertEquals(from, points.first())
            assertEquals(to.latitude, points.last().latitude)
            assertEquals(to.longitude, normalized(points.last().longitude), 1e-9)
        }
    }

    @Test
    fun m7_noRouteJumpsMoreThan180DegreesBetweenPoints() {
        for ((from, to) in routes) {
            GreatCircle.interpolate(from, to).zipWithNext { a, b ->
                assertTrue(abs(b.longitude - a.longitude) < 180.0, "$from to $to jumps from $a to $b")
            }
        }
    }

    @Test
    fun m7_routesTakeTheShortWayAroundTheWorld() {
        for ((from, to) in routes) {
            val points = GreatCircle.interpolate(from, to)
            assertTrue(abs(points.last().longitude - points.first().longitude) <= 180.0, "$from to $to")
        }
    }

    @Test
    fun m7_aRouteAcrossTheAntimeridianLeavesTheNormalLongitudeRange() {
        assertEquals(151.177 - 360.0, GreatCircle.interpolate(sea, syd).last().longitude, 1e-9)
    }

    @Test
    fun m7_aRouteThatDoesNotCrossStaysInTheNormalLongitudeRange() {
        assertEquals(cdg, GreatCircle.interpolate(bos, cdg).last())
        assertTrue(GreatCircle.interpolate(bos, cdg).all { it.longitude in -180.0..180.0 })
    }

    @Test
    fun m7_midpointsMatchAnIndependentCalculation() {
        val cases = listOf(
            Triple(jfk to hkg, 78.981197, 144.318490),
            Triple(sea to syd, 9.190544, -171.038412),
            Triple(sfo to nrt, 48.421588, -171.775986),
        )
        for ((route, latitude, longitude) in cases) {
            val middle = GreatCircle.interpolate(route.first, route.second, segments = 2)[1]
            assertEquals(latitude, middle.latitude, 1e-6, "$route")
            assertEquals(longitude, normalized(middle.longitude), 1e-6, "$route")
        }
    }

    @Test
    fun m7_thePathIsTheShortestOne() {
        for ((from, to) in routes) {
            val path = GreatCircle.interpolate(from, to).zipWithNext { a, b -> a.distanceKmTo(b) }.sum()
            assertEquals(from.distanceKmTo(to), path, 1e-3, "$from to $to")
        }
    }

    @Test
    fun m7_pointsAreEvenlySpaced() {
        for ((from, to) in routes) {
            val expected = from.distanceKmTo(to) / 64
            GreatCircle.interpolate(from, to).zipWithNext { a, b -> assertEquals(expected, a.distanceKmTo(b), 1e-3, "$from to $to") }
        }
    }

    @Test
    fun m7_theSamePointTwiceStaysPut() {
        assertEquals(List(65) { jfk }, GreatCircle.interpolate(jfk, jfk))
    }

    @Test
    fun m7_oppositeSidesOfTheEarthStillGiveValidCoordinates() {
        val points = GreatCircle.interpolate(GeoPoint(10.0, 20.0), GeoPoint(-10.0, -160.0))
        assertTrue(points.all { !it.latitude.isNaN() && !it.longitude.isNaN() })
        assertTrue(points.all { it.latitude in -90.0..90.0 })
    }

    // The plane (M9)

    @Test
    fun m9_thePlaneSitsHalfwayAlongTheRoute() {
        val plane = GreatCircle.plane(GeoPoint(0.0, 0.0), GeoPoint(0.0, 40.0))
        assertEquals(0.0, plane.latitude, 1e-9)
        assertEquals(20.0, plane.longitude, 1e-9)
    }

    @Test
    fun m9_thePlanePointsTheWayTheRouteGoes() {
        assertEquals(90.0, GreatCircle.plane(GeoPoint(0.0, 0.0), GeoPoint(0.0, 40.0)).headingDegrees, 1e-6, "east")
        assertEquals(270.0, GreatCircle.plane(GeoPoint(0.0, 40.0), GeoPoint(0.0, 0.0)).headingDegrees, 1e-6, "west")
        assertEquals(0.0, GreatCircle.plane(GeoPoint(10.0, 5.0), GeoPoint(50.0, 5.0)).headingDegrees, 1e-6, "north")
        assertEquals(180.0, GreatCircle.plane(GeoPoint(50.0, 5.0), GeoPoint(10.0, 5.0)).headingDegrees, 1e-6, "south")
    }

    @Test
    fun m9_theHeadingIsAsTheMapDrawsTheLine_notTheCompassBearing() {
        // On Mercator a north-east line at high latitude looks steeper than its compass bearing.
        val plane = GreatCircle.plane(GeoPoint(59.0, 0.0), GeoPoint(61.0, 2.0))
        assertTrue(plane.headingDegrees in 25.0..35.0, "about 30°, where the compass bearing is about 45°: ${plane.headingDegrees}")
    }

    @Test
    fun m9_acrossTheAntimeridianThePlaneStillPointsEast_andItsLongitudeIsNormal() {
        val tokyoToSeattle = GreatCircle.plane(GeoPoint(35.77, 140.39), GeoPoint(47.45, -122.31))
        assertTrue(tokyoToSeattle.headingDegrees in 30.0..150.0, "eastward: ${tokyoToSeattle.headingDegrees}")
        assertTrue(tokyoToSeattle.longitude in -180.0..180.0, "${tokyoToSeattle.longitude}")
        val middle = GreatCircle.interpolate(GeoPoint(35.77, 140.39), GeoPoint(47.45, -122.31))[32]
        assertEquals(middle.latitude, tokyoToSeattle.latitude, 1e-9)
        assertEquals((middle.longitude - tokyoToSeattle.longitude + 720.0) % 360.0, 0.0, 1e-9)
    }

    @Test
    fun m9_thePlaneFliesAlongTheDrawnRoute() {
        for ((from, to) in routes) {
            val points = GreatCircle.interpolate(from, to)
            for (k in 0..64 step 4) {
                val plane = GreatCircle.plane(from, to, fraction = k / 64.0)
                assertEquals(points[k].latitude, plane.latitude, 1e-9, "$from to $to at $k/64")
                assertEquals(normalized(points[k].longitude), plane.longitude, 1e-9, "$from to $to at $k/64")
            }
        }
    }

    @Test
    fun m9_atTheOriginAndTheDestinationThePlaneStillPointsAlongTheRoute() {
        val east = GeoPoint(0.0, 0.0) to GeoPoint(0.0, 40.0)
        assertEquals(90.0, GreatCircle.plane(east.first, east.second, fraction = 0.0).headingDegrees, 1e-6)
        assertEquals(90.0, GreatCircle.plane(east.first, east.second, fraction = 1.0).headingDegrees, 1e-6)
    }

    @Test
    fun m9_thePlaneNeverLeavesTheRoute() {
        assertEquals(GreatCircle.plane(bos, cdg, fraction = 0.0), GreatCircle.plane(bos, cdg, fraction = -0.5))
        assertEquals(GreatCircle.plane(bos, cdg, fraction = 1.0), GreatCircle.plane(bos, cdg, fraction = 1.5))
    }
}
