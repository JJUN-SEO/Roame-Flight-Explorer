package travel.roame.takehome.domain

import travel.roame.takehome.domain.search.GeoPoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** M9: the selected route's plane. [headingDegrees] is clockwise from north, as the route is drawn on a north-up map. */
data class RoutePlane(val latitude: Double, val longitude: Double, val headingDegrees: Double)

/** Route lines for map SDKs that can't draw geodesics themselves (M7). */
object GreatCircle {
    private const val NEARLY_ZERO = 1e-12

    /**
     * Returns [segments] + 1 evenly spaced points along the shortest path. Longitudes are unwrapped, so
     * a route across the antimeridian stays one continuous line, and they can leave the range -180..180.
     */
    fun interpolate(from: GeoPoint, to: GeoPoint, segments: Int = 64): List<GeoPoint> {
        require(segments > 0) { "segments must be positive, was $segments" }
        val arc = Arc(from, to)
        return List(segments + 1) { i -> arc.at(i.toDouble() / segments) }
    }

    /**
     * M9: the plane [fraction] of the way along the route, 0 at the origin and 1 at the destination, pointing along
     * the line as a north-up Web Mercator map draws it, which is how both apps' maps draw it. Halfway, it's the middle
     * of the [interpolate] points. Its longitude is brought back into -180..180.
     */
    fun plane(from: GeoPoint, to: GeoPoint, fraction: Double = 0.5): RoutePlane {
        val arc = Arc(from, to)
        val at = fraction.coerceIn(0.0, 1.0)
        val before = arc.at((at - HEADING_SPAN).coerceAtLeast(0.0))
        val after = arc.at((at + HEADING_SPAN).coerceAtMost(1.0))
        // The longitudes are unwrapped, so their difference never jumps across the antimeridian.
        val east = (after.longitude - before.longitude) * PI / 180.0
        val north = mercatorY(after.latitude) - mercatorY(before.latitude)
        val heading = (atan2(east, north) * 180.0 / PI + 360.0) % 360.0
        val position = arc.at(at)
        return RoutePlane(position.latitude, position.longitude - 360.0 * round(position.longitude / 360.0), heading)
    }

    /** The heading is taken across a 64th of the route either side of the plane, as the routes are drawn in 64 segments. */
    private const val HEADING_SPAN = 1.0 / 64

    private fun mercatorY(latitude: Double) = ln(tan(PI / 4 + latitude * PI / 360.0))

    /** The shortest path from [from] to [to]. */
    private class Arc(private val from: GeoPoint, private val to: GeoPoint) {
        private val start = from.toVector()
        private val end = to.toVector()
        private val angle = atan2((start cross end).length, start dot end)
        private val sinAngle = sin(angle)

        /**
         * The point [fraction] of the way along. Its longitude is unwrapped: a shortest path spans at most 180° of
         * longitude, so every point stays within 180° of the start and a route across the antimeridian stays continuous.
         */
        fun at(fraction: Double): GeoPoint {
            val point = when {
                fraction <= 0.0 -> from
                fraction >= 1.0 -> to
                // The same point, or exact opposites, where no single shortest path exists.
                sinAngle < NEARLY_ZERO -> GeoPoint(
                    from.latitude + (to.latitude - from.latitude) * fraction,
                    from.longitude + (to.longitude - from.longitude) * fraction,
                )
                else -> (start * (sin((1 - fraction) * angle) / sinAngle) + end * (sin(fraction * angle) / sinAngle)).toGeoPoint()
            }
            return point.copy(longitude = point.longitude + 360.0 * round((from.longitude - point.longitude) / 360.0))
        }
    }

    private data class Vector(val x: Double, val y: Double, val z: Double) {
        val length: Double get() = sqrt(x * x + y * y + z * z)

        infix fun dot(other: Vector) = x * other.x + y * other.y + z * other.z

        infix fun cross(other: Vector) = Vector(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x,
        )

        operator fun plus(other: Vector) = Vector(x + other.x, y + other.y, z + other.z)

        operator fun times(scale: Double) = Vector(x * scale, y * scale, z * scale)

        fun toGeoPoint() = GeoPoint(
            atan2(z, sqrt(x * x + y * y)) * 180.0 / PI,
            atan2(y, x) * 180.0 / PI,
        )
    }

    private fun GeoPoint.toVector(): Vector {
        val lat = latitude * PI / 180.0
        val lon = longitude * PI / 180.0
        return Vector(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))
    }
}
