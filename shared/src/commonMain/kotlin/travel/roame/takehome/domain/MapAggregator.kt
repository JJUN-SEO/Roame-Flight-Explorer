package travel.roame.takehome.domain

import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.Flight

/** One destination's flights (M2, M3), which become one map marker. */
internal data class DestinationGroup(
    val destination: Airport,
    val minPoints: Int,
    /** In the order they were passed in, so pass them in O2 order for the marker sheet (M5). */
    val flights: List<Flight>,
) {
    val flightCount: Int get() = flights.size
}

/** One group per destination code, ordered by code (M2, M4). Pass exactly the flights the list shows (M1). */
internal fun List<Flight>.toDestinationGroups(): List<DestinationGroup> =
    groupBy { it.destination.code }
        .values
        .map { flights -> DestinationGroup(flights.first().destination, flights.minOf { it.points }, flights) }
        .sortedBy { it.destination.code }
