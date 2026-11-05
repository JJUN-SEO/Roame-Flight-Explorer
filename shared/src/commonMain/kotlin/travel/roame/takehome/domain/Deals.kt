package travel.roame.takehome.domain

import travel.roame.takehome.domain.search.GeoPoint
import travel.roame.takehome.domain.search.SearchText
import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.Flight
import kotlin.math.floor
import kotlin.math.roundToInt

/** A flight with its value (B4). Lower is better: 0.7 means 30% fewer points per kilometre than typical for its cabin. */
internal class RatedFlight(val flight: Flight, val value: Double) {
    /** B4: whole percent, rounded down; 0 when the flight isn't below typical. */
    val percentBelowTypical: Int get() = percentBelow(value)
}

/** B4: a value as whole percent below typical, rounded down; 0 when it isn't below. */
internal fun percentBelow(value: Double): Int = floor((1 - value) * 100).toInt().coerceAtLeast(0)

/** B7: one program's best-value flight. */
internal class ProgramDealResult(val program: String, val best: RatedFlight)

/** B8: one destination city's deals. */
internal class DestinationDealResult(val city: String, val codes: List<String>, val minPoints: Int, val best: RatedFlight)

/** What the landing page shows for one set of candidate flights (B5). */
internal class Deals(val offers: List<ProgramDealResult>, val destinations: List<DestinationDealResult>) {
    companion object {
        val None = Deals(emptyList(), emptyList())
    }
}

/**
 * The landing page's deals (DOMAIN_RULES B1–B8), prepared once per data change: for every flight, and for each origin
 * airport's flights. Choosing the user's home airport (B5) is then a lookup, so typing never recomputes them.
 */
internal class DealIndex private constructor(
    private val origins: List<Airport>,
    private val everywhere: Deals,
    private val byOrigin: Map<String, Deals>,
    /** B3: each cabin's typical rate. */
    private val typical: Map<String, Double>,
) {
    /** B5: the origin airport nearest the user, ties by code; null without a location. */
    fun homeAirport(location: GeoPoint?): Airport? = location?.let { here ->
        origins.minWithOrNull(compareBy<Airport> { here.distanceKmTo(it.point) }.thenBy { it.code })
    }

    /** B5: deals from the home airport, or from every airport without one. */
    fun dealsFrom(home: Airport?): Deals = if (home == null) everywhere else byOrigin[home.code] ?: Deals.None

    /**
     * DT1: how far below its cabin's typical rate (B3, B4) a flight of [points] over [km] is, for any flight, saved ones
     * included. 0 when it isn't below, has no rate (B2), or its cabin isn't in the cache.
     */
    fun percentBelowTypical(km: Double, points: Int, cabin: String): Int {
        val rate = rate(km, points) ?: return 0
        val cabinTypical = typical[cabin] ?: return 0
        return percentBelow(rate / cabinTypical)
    }

    companion object {
        /** B8: the deck stays short whatever the data's size. */
        const val MAX_DESTINATIONS = 30

        fun of(flights: List<Flight>): DealIndex {
            val rates = rates(flights)
            val typical = typical(rates)
            val rated = rated(rates, typical).sortedWith(DealOrder)
            return DealIndex(
                origins = flights.map { it.origin }.distinctBy { it.code },
                everywhere = deals(rated),
                byOrigin = rated.groupBy { it.flight.origin.code }.mapValues { (_, fromOrigin) -> deals(fromOrigin) },
                typical = typical,
            )
        }

        /** B1–B4. Flights with no rate (B2) are left out. */
        internal fun rate(flights: List<Flight>): List<RatedFlight> {
            val rates = rates(flights)
            return rated(rates, typical(rates))
        }

        private fun rates(flights: List<Flight>) = flights.mapNotNull { flight -> flight.rate()?.let { flight to it } }

        /** B3: each cabin's median rate. */
        private fun typical(rates: List<Pair<Flight, Double>>): Map<String, Double> =
            rates.groupBy({ it.first.cabin }, { it.second }).mapValues { (_, cabinRates) -> median(cabinRates) }

        private fun rated(rates: List<Pair<Flight, Double>>, typical: Map<String, Double>) =
            rates.map { (flight, rate) -> RatedFlight(flight, rate / typical.getValue(flight.cabin)) }

        /** B7, B8, from flights already in [DealOrder], so each program's and city's first flight is its best. */
        private fun deals(rated: List<RatedFlight>): Deals {
            val offers = rated.distinctBy { it.flight.program }
                .map { ProgramDealResult(it.flight.program, it) }
                .sortedWith(compareBy<ProgramDealResult> { it.best.value }.thenBy { it.program })
            val destinations = rated.groupBy { it.flight.destination.city }
                .map { (city, flights) ->
                    DestinationDealResult(
                        city = city,
                        codes = flights.map { it.flight.destination.code }.distinct().sorted(),
                        minPoints = flights.minOf { it.flight.points },
                        best = flights.first(),
                    )
                }
                .sortedWith(compareBy<DestinationDealResult> { it.best.value }.thenBy { it.city })
                .take(MAX_DESTINATIONS)
            return Deals(offers, destinations)
        }

        /** B3: the mean of the middle two for an even count. */
        internal fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
        }
    }
}

/** B7: best value first; ties by points, then date, then id. */
internal val DealOrder: Comparator<RatedFlight> =
    compareBy<RatedFlight> { it.value }.thenBy { it.flight.points }.thenBy { it.flight.departureDate }.thenBy { it.flight.id }

/** B1, B2: points per 1,000 km, or null for a flight shorter than 1 km or with no points. */
internal fun Flight.rate(): Double? = rate(origin.point.distanceKmTo(destination.point), points)

/** B2: points per 1,000 km over [km], or null under 1 km or with no points. */
internal fun rate(km: Double, points: Int): Double? = if (km < 1.0 || points <= 0) null else points / (km / 1000)

private val Airport.point get() = GeoPoint(latitude, longitude)

/** B6, A22: Roame's published cents per point for each cabin. Roame gives none for Premium Economy, so it uses Economy's. */
private val CENTS_PER_POINT = mapOf("Economy" to 1.80, "Premium Economy" to 1.80, "Business" to 4.10, "First" to 10.5)

/** B6: an estimate in whole US dollars, or null for a cabin with no published value. */
internal fun estimatedValueUsd(points: Int, cabin: String): Int? =
    CENTS_PER_POINT[cabin]?.let { cents -> (points * cents / 100).roundToInt() }

/** B8: the name each app's bundled photo has (D33), such as `dest_sao_paulo`. */
internal fun photoKey(city: String): String = "dest_" + SearchText.normalize(city).replace(' ', '_')
