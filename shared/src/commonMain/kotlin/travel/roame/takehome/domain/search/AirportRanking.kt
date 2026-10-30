package travel.roame.takehome.domain.search

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

internal data class GeoPoint(val latitude: Double, val longitude: Double) {

    /** Great-circle (haversine) distance in kilometres. */
    fun distanceKmTo(other: GeoPoint): Double {
        val lat1 = latitude.toRadians()
        val lat2 = other.latitude.toRadians()
        val dLat = lat2 - lat1
        val dLon = (other.longitude - longitude).toRadians()
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun Double.toRadians() = this * kotlin.math.PI / 180.0

    private companion object {
        const val EARTH_RADIUS_KM = 6371.0
    }
}

/** How much proximity and traffic volume count toward an airport's priority (DOMAIN_RULES P1, P5). */
internal data class RankingWeights(val proximity: Double, val volume: Double) {
    companion object {
        /** You usually fly from somewhere near you. */
        val ORIGIN = RankingWeights(proximity = 0.7, volume = 0.3)

        /** Where you fly to is mostly about popularity, not distance. */
        val DESTINATION = RankingWeights(proximity = 0.2, volume = 0.8)
    }
}

/** Where the volume signal came from (DOMAIN_RULES P3, P4). */
internal enum class VolumeSource {
    /** Every ranked airport has a passenger figure from `fixtures/airports.json`. */
    PassengerStats,

    /** `airports.json` was missing: volume is each airport's flight count in `flights.json`. */
    FlightCountFallback,

    /** Some airports had passenger figures and the rest fell back to flight counts. */
    Mixed,

    /** No volume data at all (only [AirportRanking.NONE]). */
    None,
}

/**
 * Prioritisation of airports by the user's location and airport traffic (DOMAIN_RULES P1–P6).
 *
 * Priority only orders airports that already matched; it never changes which airports match.
 */
internal class AirportRanking private constructor(
    private val priorities: Map<String, Double>,
    val volumeSource: VolumeSource,
    val usesLocation: Boolean,
) {

    /** A value in [0, 1]; higher ranks first. Unknown codes get 0. */
    fun priorityOf(code: String): Double = priorities[code] ?: 0.0

    companion object {
        val NONE = AirportRanking(emptyMap(), VolumeSource.None, usesLocation = false)

        /** Distance at which proximity has decayed to 1/e (≈ 0.37). */
        const val PROXIMITY_SCALE_KM = 1000.0

        /**
         * @param annualPassengers passenger figures by airport code from `fixtures/airports.json`;
         *   empty when that file is missing or failed to load.
         * @param flightCounts flights per airport code in `flights.json` — the fallback volume
         *   signal, so ranking still works with only the original data (P4).
         * @param userLocation the device's location, or null when unknown or not permitted (P2).
         */
        fun build(
            airports: Collection<SearchableAirport>,
            annualPassengers: Map<String, Long>,
            flightCounts: Map<String, Int>,
            userLocation: GeoPoint?,
            weights: RankingWeights,
        ): AirportRanking {
            val maxPassengers = airports.maxOfOrNull { annualPassengers[it.code] ?: 0L } ?: 0L
            val maxFlights = airports.maxOfOrNull { flightCounts[it.code] ?: 0 } ?: 0
            var fromStats = 0
            var fromFallback = 0

            val priorities = airports.associate { airport ->
                val passengers = annualPassengers[airport.code]
                val volume = when {
                    passengers != null && maxPassengers > 0 -> {
                        fromStats++
                        passengers.toDouble() / maxPassengers
                    }
                    maxFlights > 0 -> {
                        fromFallback++
                        (flightCounts[airport.code] ?: 0).toDouble() / maxFlights
                    }
                    else -> 0.0
                }
                val priority = if (userLocation == null) {
                    volume
                } else {
                    val proximity = exp(-userLocation.distanceKmTo(airport.location) / PROXIMITY_SCALE_KM)
                    (weights.proximity * proximity + weights.volume * volume) / (weights.proximity + weights.volume)
                }
                airport.code to priority
            }

            val source = when {
                fromStats > 0 && fromFallback > 0 -> VolumeSource.Mixed
                fromStats > 0 -> VolumeSource.PassengerStats
                fromFallback > 0 -> VolumeSource.FlightCountFallback
                else -> VolumeSource.None
            }
            return AirportRanking(priorities, source, usesLocation = userLocation != null)
        }
    }
}
