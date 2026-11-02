package travel.roame.takehome.domain

import travel.roame.takehome.model.FilterOptions
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightFilters

/** Pass the whole cache, not the current results, so options don't disappear while filtering (F8). */
internal fun List<Flight>.toFilterOptions() = FilterOptions(
    minDate = minOfOrNull { it.departureDate }?.toString(),
    maxDate = maxOfOrNull { it.departureDate }?.toString(),
    minPoints = minOfOrNull { it.points },
    maxPoints = maxOfOrNull { it.points },
    cabins = mapTo(HashSet()) { it.cabin }.sortedWith(CabinOrder),
    programs = mapTo(HashSet()) { it.program }.sorted(),
)

/**
 * Returns anything [options] no longer offers to its default (F8). So does a date or points limit at
 * or past the data's own limit, because it filters nothing, so the default follows the data (F2, F4).
 */
internal fun FlightFilters.normalizedTo(options: FilterOptions): FlightFilters {
    if (options.minDate == null || options.maxDate == null || options.maxPoints == null) return FlightFilters()
    return FlightFilters(
        // ISO dates compare correctly as text.
        dateStart = dateStart?.takeIf { it > options.minDate },
        dateEnd = dateEnd?.takeIf { it < options.maxDate },
        cabin = cabin?.takeIf { it in options.cabins },
        maxPoints = maxPoints?.takeIf { it < options.maxPoints },
        programs = programs.intersect(options.programs.toSet()),
    )
}
