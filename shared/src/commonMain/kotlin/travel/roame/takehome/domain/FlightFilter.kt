package travel.roame.takehome.domain

import kotlinx.datetime.LocalDate
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightFilters

/**
 * Applies every filter and both search fields with AND (F7), keeping this list's order (D6).
 *
 * [originCodes] and [destinationCodes] are the airports each search field matched. Null means the
 * field isn't narrowing anything; an empty set means it matched nothing, so no flight qualifies.
 */
internal fun List<Flight>.matching(
    filters: FlightFilters,
    originCodes: Set<String>? = null,
    destinationCodes: Set<String>? = null,
): List<Flight> {
    val start = filters.dateStart?.let(LocalDate::parse)
    val end = filters.dateEnd?.let(LocalDate::parse)
    return filter { flight ->
        (start == null || flight.departureDate >= start) &&
            (end == null || flight.departureDate <= end) &&
            (filters.cabin == null || flight.cabin == filters.cabin) &&
            (filters.maxPoints == null || flight.points <= filters.maxPoints) &&
            (filters.programs.isEmpty() || flight.program in filters.programs) &&
            (originCodes == null || flight.origin.code in originCodes) &&
            (destinationCodes == null || flight.destination.code in destinationCodes)
    }
}
