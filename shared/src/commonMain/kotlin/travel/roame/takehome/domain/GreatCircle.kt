package travel.roame.takehome.domain

import travel.roame.takehome.domain.search.GeoPoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** Route lines for map SDKs that can't draw geodesics themselves (M7). */
object GreatCircle {
    private const val NEARLY_ZERO = 1e-12

    /**
     * Returns [segments] + 1 evenly spaced points along the shortest path. Longitudes are unwrapped, so
     * a route across the antimeridian stays one continuous line, and they can leave the range -180..180.
     */
    fun interpolate(from: GeoPoint, to: GeoPoint, segments: Int = 64): List<GeoPoint> {
        require(segments > 0) { "segments must be positive, was $segments" }
        val start = from.toVector()
        val end = to.toVector()
        val angle = atan2((start cross end).length, start dot end)
        val sinAngle = sin(angle)

        val points = List(segments + 1) { i ->
            val fraction = i.toDouble() / segments
            when {
                i == 0 -> from
                i == segments -> to
                // The same point, or exact opposites, where no single shortest path exists.
                sinAngle < NEARLY_ZERO -> GeoPoint(
                    from.latitude + (to.latitude - from.latitude) * fraction,
                    from.longitude + (to.longitude - from.longitude) * fraction,
                )
                else -> (start * (sin((1 - fraction) * angle) / sinAngle) + end * (sin(fraction * angle) / sinAngle)).toGeoPoint()
            }
        }
        return points.unwrapped()
    }

    /** A shortest path spans at most 180° of longitude, so every point can stay within 180° of the start. */
    private fun List<GeoPoint>.unwrapped(): List<GeoPoint> {
        val start = first().longitude
        return map { it.copy(longitude = it.longitude + 360.0 * round((start - it.longitude) / 360.0)) }
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
