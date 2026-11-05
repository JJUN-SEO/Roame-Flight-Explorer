package travel.roame.takehome.domain

import travel.roame.takehome.domain.search.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** DOMAIN_RULES M7 and M9. The expected arcs come from a separate Python script (2026‑11‑04). */
class RouteArcTest {

    private val jfk = GeoPoint(40.63945, -73.77932)
    private val hkg = GeoPoint(22.31184, 113.91486)
    private val sea = GeoPoint(47.44794, -122.31028)
    private val syd = GeoPoint(-33.9461, 151.177)
    private val sfo = GeoPoint(37.61981, -122.37482)
    private val nrt = GeoPoint(35.76858, 140.38871)
    private val bos = GeoPoint(42.36197, -71.0079)
    private val cdg = GeoPoint(49.0097, 2.5479)

    private val routes = listOf(jfk to hkg, hkg to jfk, sea to syd, syd to sea, sfo to nrt, nrt to sfo, bos to cdg, cdg to bos)

    private val east = GeoPoint(0.0, 0.0) to GeoPoint(0.0, 40.0)

    /** The same longitude once any multiple of 360 is removed, in -180..180. */
    private fun normalized(longitude: Double) = longitude - 360.0 * floor((longitude + 180.0) / 360.0)

    /** Where a point sits on the flat, north-up map, in the units the arc is built in. */
    private fun mercator(point: GeoPoint) = point.longitude * PI / 180.0 to ln(tan(PI / 4 + point.latitude * PI / 360.0))

    @Test
    fun m7_givesOneMorePointThanSegments() {
        assertEquals(65, RouteArc.interpolate(jfk, hkg).size)
        assertEquals(2, RouteArc.interpolate(jfk, hkg, segments = 1).size)
        assertEquals(11, RouteArc.interpolate(jfk, hkg, segments = 10).size)
    }

    @Test
    fun m7_segmentsMustBePositive() {
        assertFailsWith<IllegalArgumentException> { RouteArc.interpolate(jfk, hkg, segments = 0) }
    }

    @Test
    fun m7_startsExactlyAtTheOriginAndEndsAtTheDestination() {
        for ((from, to) in routes) {
            val points = RouteArc.interpolate(from, to)
            assertEquals(from, points.first())
            assertEquals(to.latitude, points.last().latitude, 1e-9)
            assertEquals(to.longitude, normalized(points.last().longitude), 1e-9)
        }
    }

    @Test
    fun m7_noRouteJumpsMoreThan180DegreesBetweenPoints() {
        for ((from, to) in routes) {
            RouteArc.interpolate(from, to).zipWithNext { a, b ->
                assertTrue(abs(b.longitude - a.longitude) < 180.0, "$from to $to jumps from $a to $b")
            }
        }
    }

    @Test
    fun m7_routesTakeTheShortWayAroundTheWorld() {
        for ((from, to) in routes) {
            val points = RouteArc.interpolate(from, to)
            assertTrue(abs(points.last().longitude - points.first().longitude) <= 180.0, "$from to $to")
        }
    }

    @Test
    fun m7_aRouteAcrossTheAntimeridianLeavesTheNormalLongitudeRange() {
        assertEquals(151.177 - 360.0, RouteArc.interpolate(sea, syd).last().longitude, 1e-9)
    }

    @Test
    fun m7_aRouteThatDoesNotCrossStaysInTheNormalLongitudeRange() {
        assertEquals(cdg.longitude, RouteArc.interpolate(bos, cdg).last().longitude, 1e-9)
        assertTrue(RouteArc.interpolate(bos, cdg).all { it.longitude in -180.0..180.0 })
    }

    @Test
    fun m7_theArcBowsNorthByATenthOfItsLength() {
        // 40° along the equator: its middle sits 4° of map height north, at 3.997° of latitude.
        val middle = RouteArc.interpolate(east.first, east.second, segments = 2)[1]
        assertEquals(3.9967547, middle.latitude, 1e-6)
        assertEquals(20.0, middle.longitude, 1e-9)
        assertEquals(middle, RouteArc.interpolate(east.second, east.first, segments = 2)[1], "westbound bows north too")
    }

    @Test
    fun m7_aRouteDueNorthOrSouthBowsEast() {
        val north = RouteArc.interpolate(GeoPoint(10.0, 5.0), GeoPoint(50.0, 5.0), segments = 2)[1]
        assertEquals(32.1467014, north.latitude, 1e-6)
        assertEquals(9.7856721, north.longitude, 1e-6)
        val south = RouteArc.interpolate(GeoPoint(50.0, 5.0), GeoPoint(10.0, 5.0), segments = 2)[1]
        assertTrue(south.longitude > 5.0, "${south.longitude}")
    }

    @Test
    fun m7_middlesMatchAnIndependentCalculation() {
        val cases = listOf(
            Triple(jfk to hkg, 45.3213592, -162.0974379),
            Triple(sea to syd, 17.3364128, -174.5830890),
        )
        for ((route, latitude, longitude) in cases) {
            val middle = RouteArc.interpolate(route.first, route.second, segments = 2)[1]
            assertEquals(latitude, middle.latitude, 1e-6, "$route")
            assertEquals(longitude, normalized(middle.longitude), 1e-6, "$route")
        }
    }

    @Test
    fun m7_theArcNeverStraysMoreThanATenthOfItsLengthFromTheStraightLine() {
        for ((from, to) in routes) {
            val points = RouteArc.interpolate(from, to).map(::mercator)
            val (x0, y0) = points.first()
            val (x2, y2) = points.last()
            val length = hypot(x2 - x0, y2 - y0)
            for ((x, y) in points) {
                val off = abs((x2 - x0) * (y - y0) - (y2 - y0) * (x - x0)) / length
                assertTrue(off <= 0.1 * length + 1e-9, "$from to $to: ${off / length} of its length")
            }
        }
    }

    @Test
    fun m7_theSamePointTwiceStaysPut() {
        assertEquals(List(65) { jfk }, RouteArc.interpolate(jfk, jfk))
    }

    // The plane (M9)

    @Test
    fun m9_thePlaneSitsHalfwayAlongTheRoute() {
        val plane = RouteArc.plane(east.first, east.second)
        assertEquals(3.9967547, plane.latitude, 1e-6)
        assertEquals(20.0, plane.longitude, 1e-9)
    }

    @Test
    fun m9_halfwayThePlanePointsTheWayTheRouteGoes() {
        assertEquals(90.0, RouteArc.plane(east.first, east.second).headingDegrees, 1e-6, "east")
        assertEquals(270.0, RouteArc.plane(east.second, east.first).headingDegrees, 1e-6, "west")
        assertEquals(0.0, RouteArc.plane(GeoPoint(10.0, 5.0), GeoPoint(50.0, 5.0)).headingDegrees, 1e-6, "north")
        assertEquals(180.0, RouteArc.plane(GeoPoint(50.0, 5.0), GeoPoint(10.0, 5.0)).headingDegrees, 1e-6, "south")
    }

    @Test
    fun m9_thePlaneClimbsAwayFromTheOriginAndDescendsIntoTheDestination() {
        assertEquals(68.1985905, RouteArc.plane(east.first, east.second, fraction = 0.0).headingDegrees, 1e-6)
        assertEquals(111.8014095, RouteArc.plane(east.first, east.second, fraction = 1.0).headingDegrees, 1e-6)
    }

    @Test
    fun m9_theHeadingIsAsTheMapDrawsTheLine_notTheCompassBearing() {
        // On Mercator a north-east line at high latitude looks steeper than its compass bearing.
        assertEquals(26.5569032, RouteArc.plane(GeoPoint(59.0, 0.0), GeoPoint(61.0, 2.0)).headingDegrees, 1e-6)
    }

    @Test
    fun m9_acrossTheAntimeridianThePlaneStillPointsEast_andItsLongitudeIsNormal() {
        val tokyoToSeattle = RouteArc.plane(nrt, sea)
        assertTrue(tokyoToSeattle.headingDegrees in 30.0..150.0, "eastward: ${tokyoToSeattle.headingDegrees}")
        assertTrue(tokyoToSeattle.longitude in -180.0..180.0, "${tokyoToSeattle.longitude}")
        val middle = RouteArc.interpolate(nrt, sea)[32]
        assertEquals(middle.latitude, tokyoToSeattle.latitude, 1e-9)
        assertEquals((middle.longitude - tokyoToSeattle.longitude + 720.0) % 360.0, 0.0, 1e-9)
    }

    @Test
    fun m9_thePlaneFliesAlongTheDrawnRoute() {
        for ((from, to) in routes) {
            val points = RouteArc.interpolate(from, to)
            for (k in 0..64 step 4) {
                val plane = RouteArc.plane(from, to, fraction = k / 64.0)
                assertEquals(points[k].latitude, plane.latitude, 1e-9, "$from to $to at $k/64")
                assertEquals(normalized(points[k].longitude), plane.longitude, 1e-9, "$from to $to at $k/64")
            }
        }
    }

    @Test
    fun m9_thePlaneNeverLeavesTheRoute() {
        assertEquals(RouteArc.plane(bos, cdg, fraction = 0.0), RouteArc.plane(bos, cdg, fraction = -0.5))
        assertEquals(RouteArc.plane(bos, cdg, fraction = 1.0), RouteArc.plane(bos, cdg, fraction = 1.5))
    }

    @Test
    fun m9_aRouteThatGoesNowhereHasAPlaneThatStaysPut() {
        assertEquals(RoutePlane(jfk.latitude, jfk.longitude, 0.0), RouteArc.plane(jfk, jfk))
    }
}
