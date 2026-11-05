package travel.roame.takehome.domain

import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.SortOrder

/**
 * O1: [this] in [sort]'s order (D46). Best offer rates every flight first (B1–B4); the values aren't kept, so the cache
 * holds no more than it did for the users who never choose it.
 */
internal fun List<Flight>.sortedFor(sort: SortOrder): List<Flight> {
    val values = if (sort == SortOrder.BestOffer) DealIndex.rate(this).associate { it.flight.id to it.value } else emptyMap()
    return sortedWith(resultOrder(sort) { values[it.id] })
}

/**
 * O1: the results list in [sort]'s order (D46). Each ends with the date, then the id, so the order never depends on
 * the file. [value] is a flight's B4 value, or null when it has none (B2).
 */
internal fun resultOrder(sort: SortOrder, value: (Flight) -> Double?): Comparator<Flight> {
    val dateThenId = compareBy<Flight> { it.departureDate }.thenBy { it.id }
    return when (sort) {
        SortOrder.LowestPoints -> compareBy<Flight> { it.points }.then(dateThenId)
        SortOrder.HighestPoints -> compareByDescending<Flight> { it.points }.then(dateThenId)
        // Programs in F6's order, the cheapest first within each.
        SortOrder.ByProgram -> compareBy<Flight> { it.program }.thenBy { it.points }.then(dateThenId)
        // Most below typical first; a flight with no value goes last.
        SortOrder.BestOffer -> compareBy(nullsLast<Double>()) { flight: Flight -> value(flight) }.thenBy { it.points }.then(dateThenId)
    }
}

/** O2: a marker's sheet, so the "from" price on the marker is the first row. */
internal val MarkerSheetOrder: Comparator<Flight> =
    compareBy<Flight> { it.points }.thenBy { it.departureDate }.thenBy { it.id }

private val KNOWN_CABINS = listOf("Economy", "Premium Economy", "Business", "First")

/** DM1: known cabins by rank, then unknown ones alphabetically. */
internal val CabinOrder: Comparator<String> =
    compareBy<String> { cabin -> KNOWN_CABINS.indexOf(cabin).takeIf { it >= 0 } ?: KNOWN_CABINS.size }.thenBy { it }
