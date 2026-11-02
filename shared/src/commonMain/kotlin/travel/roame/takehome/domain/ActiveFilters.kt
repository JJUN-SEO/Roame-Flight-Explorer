package travel.roame.takehome.domain

import travel.roame.takehome.model.ActiveFilter
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.model.FlightFilters

/** C2: one entry per non-default filter, in a fixed order. Search text isn't a chip. */
internal fun FlightFilters.active(): List<ActiveFilter> = buildList {
    if (dateStart != null || dateEnd != null) add(ActiveFilter.Dates(dateStart, dateEnd))
    cabin?.let { add(ActiveFilter.Cabin(it)) }
    maxPoints?.let { add(ActiveFilter.MaxPoints(it)) }
    if (programs.isNotEmpty()) add(ActiveFilter.Programs(programs.sorted()))
}

/** C3. */
internal fun FlightFilters.cleared(kind: FilterKind): FlightFilters = when (kind) {
    FilterKind.Dates -> copy(dateStart = null, dateEnd = null)
    FilterKind.Cabin -> copy(cabin = null)
    FilterKind.MaxPoints -> copy(maxPoints = null)
    FilterKind.Programs -> copy(programs = emptySet())
}

/** C2: search text isn't a chip, but it does count, because Reset also empties the search fields (C4). */
internal fun hasAnythingToReset(filters: FlightFilters, originQuery: String, destinationQuery: String): Boolean =
    filters != FlightFilters() || originQuery.isNotEmpty() || destinationQuery.isNotEmpty()
