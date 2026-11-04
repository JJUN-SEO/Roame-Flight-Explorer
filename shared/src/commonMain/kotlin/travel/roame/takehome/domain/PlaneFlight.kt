package travel.roame.takehome.domain

import kotlin.math.min

/** M9: where the plane is along the selected route (0 at the origin, 1 at the destination), and how visible it is. */
data class PlaneFrame(val fraction: Double, val opacity: Double)

/**
 * M9: the plane's flight along the selected route, the same on both apps. It eases out of the origin as it fades in,
 * fades out before the destination, whose pill covers the end of the route, waits out of sight, and flies again.
 */
object PlaneFlight {
    const val FLIGHT_MILLIS = 3_600L
    const val PAUSE_MILLIS = 400L

    /** How far along the route the plane has fully faded in. */
    const val FADED_IN = 0.03

    /** How far along the route the plane starts fading out, and where it's gone. */
    const val FADING_OUT = 0.8
    const val GONE = 0.92

    /** Halfway and fully visible: the plane under Reduce Motion, which doesn't fly. */
    val resting = PlaneFrame(0.5, 1.0)

    /** The frame [elapsedMillis] after the first take-off. */
    fun at(elapsedMillis: Long): PlaneFrame {
        val time = elapsedMillis.coerceAtLeast(0L) % (FLIGHT_MILLIS + PAUSE_MILLIS)
        if (time >= FLIGHT_MILLIS) return PlaneFrame(1.0, 0.0)
        val progress = time.toDouble() / FLIGHT_MILLIS
        // Smoothstep: it eases out of the origin and into the destination.
        val fraction = progress * progress * (3 - 2 * progress)
        val opacity = min(fraction / FADED_IN, (GONE - fraction) / (GONE - FADING_OUT))
        return PlaneFrame(fraction, opacity.coerceIn(0.0, 1.0))
    }
}
