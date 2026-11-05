package travel.roame.takehome.domain

import travel.roame.takehome.domain.search.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.tan

/** M9: the selected route's plane. [headingDegrees] is clockwise from north, as the route is drawn on a north-up map. */
data class RoutePlane(val latitude: Double, val longitude: Double, val headingDegrees: Double)

/**
 * M7: the selected route as a gentle arc, the same on both maps (D50). It's drawn on the flat map, not along the great
 * circle, which bows far towards the pole on a Web Mercator map and can leave the screen: the straight line from origin
 * to destination, bent so its middle sits a tenth of its length to the north (east for a route due north or south).
 */
object RouteArc {
    /** The curve's control point is twice as far off the line as its middle. */
    private const val BEND = 0.2

    private const val NEARLY_ZERO = 1e-12

    /**
     * Returns [segments] + 1 points along the arc, evenly spread along the curve's parameter. Longitudes are unwrapped,
     * so a route across the antimeridian stays one continuous line, and they can leave the range -180..180.
     */
    fun interpolate(from: GeoPoint, to: GeoPoint, segments: Int = 64): List<GeoPoint> {
        require(segments > 0) { "segments must be positive, was $segments" }
        val arc = Arc(from, to)
        return List(segments + 1) { i -> arc.at(i.toDouble() / segments) }
    }

    /**
     * M9: the plane [fraction] of the way along the arc, 0 at the origin and 1 at the destination, pointing along it as
     * a north-up Web Mercator map draws it, which is how both apps' maps draw it. Halfway, it's the middle of the
     * [interpolate] points. Its longitude is brought back into -180..180.
     */
    fun plane(from: GeoPoint, to: GeoPoint, fraction: Double = 0.5): RoutePlane {
        val arc = Arc(from, to)
        val at = fraction.coerceIn(0.0, 1.0)
        val position = arc.at(at)
        return RoutePlane(position.latitude, position.longitude - 360.0 * round(position.longitude / 360.0), arc.heading(at))
    }

    /**
     * A quadratic curve in Web Mercator coordinates, where x is the unwrapped longitude in radians: both maps are flat
     * and north-up, so it draws as the same smooth arc at any zoom.
     */
    private class Arc(private val from: GeoPoint, to: GeoPoint) {
        // A shortest path never spans more than 180° of longitude, so the destination is moved within 180° of the origin.
        private val x0 = radians(from.longitude)
        private val y0 = mercatorY(from.latitude)
        private val x2 = radians(to.longitude + 360.0 * round((from.longitude - to.longitude) / 360.0))
        private val y2 = mercatorY(to.latitude)
        private val length = hypot(x2 - x0, y2 - y0)
        private val cx: Double
        private val cy: Double

        init {
            // The perpendicular on the north side, or the east side for a line due north or south.
            var nx = if (length < NEARLY_ZERO) 0.0 else -(y2 - y0) / length
            var ny = if (length < NEARLY_ZERO) 0.0 else (x2 - x0) / length
            if (ny < -NEARLY_ZERO || (abs(ny) <= NEARLY_ZERO && nx < 0)) {
                nx = -nx
                ny = -ny
            }
            cx = (x0 + x2) / 2 + nx * BEND * length
            cy = (y0 + y2) / 2 + ny * BEND * length
        }

        fun at(t: Double): GeoPoint {
            if (t <= 0.0 || length < NEARLY_ZERO) return from
            val u = 1 - t
            val x = u * u * x0 + 2 * u * t * cx + t * t * x2
            val y = u * u * y0 + 2 * u * t * cy + t * t * y2
            return GeoPoint(degrees(2 * atan(exp(y)) - PI / 2), degrees(x))
        }

        /** The curve's direction at [t], as the map draws it, in degrees clockwise from north; 0 for a route that goes nowhere. */
        fun heading(t: Double): Double {
            val east = 2 * (1 - t) * (cx - x0) + 2 * t * (x2 - cx)
            val north = 2 * (1 - t) * (cy - y0) + 2 * t * (y2 - cy)
            if (hypot(east, north) < NEARLY_ZERO) return 0.0
            return (degrees(atan2(east, north)) + 360.0) % 360.0
        }
    }

    private fun mercatorY(latitude: Double) = ln(tan(PI / 4 + radians(latitude) / 2))

    private fun radians(degrees: Double) = degrees * PI / 180.0

    private fun degrees(radians: Double) = radians * 180.0 / PI
}
