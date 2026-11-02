package travel.roame.takehome.domain

import travel.roame.takehome.model.Flight

/** O1: the results list. */
internal val ResultOrder: Comparator<Flight> =
    compareBy<Flight> { it.departureDate }.thenBy { it.points }.thenBy { it.id }

/** O2: a marker's sheet, so the "from" price on the marker is the first row. */
internal val MarkerSheetOrder: Comparator<Flight> =
    compareBy<Flight> { it.points }.thenBy { it.departureDate }.thenBy { it.id }

private val KNOWN_CABINS = listOf("Economy", "Premium Economy", "Business", "First")

/** DM1: known cabins by rank, then unknown ones alphabetically. */
internal val CabinOrder: Comparator<String> =
    compareBy<String> { cabin -> KNOWN_CABINS.indexOf(cabin).takeIf { it >= 0 } ?: KNOWN_CABINS.size }.thenBy { it }
