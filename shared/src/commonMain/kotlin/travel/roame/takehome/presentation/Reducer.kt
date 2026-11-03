package travel.roame.takehome.presentation

import travel.roame.takehome.data.CacheSnapshot
import travel.roame.takehome.data.SyncStatus
import travel.roame.takehome.domain.MarkerSheetOrder
import travel.roame.takehome.domain.ResultOrder
import travel.roame.takehome.domain.active
import travel.roame.takehome.domain.hasAnythingToReset
import travel.roame.takehome.domain.matching
import travel.roame.takehome.domain.normalizedTo
import travel.roame.takehome.domain.search.AIRPORT_PASSENGERS
import travel.roame.takehome.domain.search.AirportRanking
import travel.roame.takehome.domain.search.AirportSearchEngine
import travel.roame.takehome.domain.search.GeoPoint
import travel.roame.takehome.domain.search.RankingWeights
import travel.roame.takehome.domain.search.SearchableAirport
import travel.roame.takehome.domain.search.toHint
import travel.roame.takehome.domain.search.toSuggestions
import travel.roame.takehome.domain.toDestinationGroups
import travel.roame.takehome.domain.toFilterOptions
import kotlinx.datetime.LocalDate
import travel.roame.takehome.model.Airport
import travel.roame.takehome.model.AirportSuggestion
import travel.roame.takehome.model.FilterOptions
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightFilters
import travel.roame.takehome.model.SavedFlight

/** What the user has chosen, plus the writes in progress and the messages not yet shown. */
internal data class ExplorerInputs(
    val originQuery: String = "",
    val destinationQuery: String = "",
    val filters: FlightFilters = FlightFilters(),
    val selectedFlightId: String? = null,
    val savedSelectedId: String? = null,
    val pendingWriteIds: Set<String> = emptySet(),
    val messages: List<UserMessage> = emptyList(),
    /** The codes of a tapped suggestion (S14), or null while the field's text decides. */
    val originPick: List<String>? = null,
    val destinationPick: List<String>? = null,
    /** Only ever set when the user taps "Use my location" (D16). */
    val userLocation: GeoPoint? = null,
)

/** One side of the route's airports, indexed once per data change (S3, D18). */
internal class SideIndex(
    private val engine: AirportSearchEngine,
    private val airports: Map<String, SearchableAirport>,
    private val flightCounts: Map<String, Int>,
    private val weights: RankingWeights,
) {
    private val rankingWithoutLocation =
        AirportRanking.build(airports.values, AIRPORT_PASSENGERS, flightCounts, userLocation = null, weights)

    /** P2, P5: the location only counts where its weight does, so the destination field never uses it. */
    private fun ranking(location: GeoPoint?) =
        if (location == null || weights.proximity == 0.0) rankingWithoutLocation
        else AirportRanking.build(airports.values, AIRPORT_PASSENGERS, flightCounts, location, weights)

    fun resolve(query: String, pickCodes: List<String>?, location: GeoPoint?): FieldSearch {
        val pick = pickCodes?.takeIf { codes -> codes.all { it in airports } }?.let { codes ->
            AirportSuggestion(airports.getValue(codes.first()).city, codes, isCityRow = codes.size > 1)
        }
        if (pick != null) return FieldSearch(pick.codes.toSet(), suggestions = emptyList(), hint = emptyList(), pick)
        val result = engine.search(query, ranking(location))
        return FieldSearch(
            // S2: a blank field matches every airport on its side, so it narrows nothing. NoMatch narrows to nothing.
            codes = result.codes,
            suggestions = result.toSuggestions(),
            hint = result.toHint(),
            pick = null,
        )
    }

    companion object {
        fun of(airports: List<Airport>, weights: RankingWeights): SideIndex {
            val searchable = airports
                .distinctBy { it.code }
                .map { SearchableAirport(it.code, it.city, GeoPoint(it.latitude, it.longitude)) }
            return SideIndex(
                AirportSearchEngine(searchable),
                searchable.associateBy { it.code },
                airports.groupingBy { it.code }.eachCount(),
                weights,
            )
        }
    }
}

/** What one search field contributes: the airports a flight on that side must use. */
internal class FieldSearch(
    val codes: Set<String>,
    val suggestions: List<AirportSuggestion>,
    val hint: List<AirportSuggestion>,
    val pick: AirportSuggestion?,
)

/** The cache, prepared once per data change, so input changes filter without sorting (D6). */
internal class IndexedFlights(
    val resultOrder: List<Flight>,
    val sheetOrder: List<Flight>,
    val options: FilterOptions,
    val lastSuccessAt: Long?,
    val ids: Set<String>,
    val origins: SideIndex,
    val destinations: SideIndex,
)

internal fun index(cache: CacheSnapshot) = IndexedFlights(
    resultOrder = cache.flights.sortedWith(ResultOrder),
    sheetOrder = cache.flights.sortedWith(MarkerSheetOrder),
    options = cache.flights.toFilterOptions(),
    lastSuccessAt = cache.lastSuccessAt?.toEpochMilliseconds(),
    ids = cache.flights.mapTo(HashSet()) { it.id },
    origins = SideIndex.of(cache.flights.map { it.origin }, RankingWeights.ORIGIN),
    destinations = SideIndex.of(cache.flights.map { it.destination }, RankingWeights.DESTINATION),
)

internal data class Snapshot(
    val index: IndexedFlights,
    val sync: SyncStatus,
    val saved: List<SavedFlight>,
    val inputs: ExplorerInputs,
)

/** Pure, so it can be tested without coroutines. */
internal fun reduce(snapshot: Snapshot): ExplorerState {
    val (index, sync, savedFlights, inputs) = snapshot
    val filters = inputs.filters.normalizedTo(index.options)
    val origin = index.origins.resolve(inputs.originQuery, inputs.originPick, inputs.userLocation)
    val destination = index.destinations.resolve(inputs.destinationQuery, inputs.destinationPick, inputs.userLocation)
    val savedIds = savedFlights.mapTo(HashSet()) { it.flight.id }
    val item = { flight: Flight -> flight.toItem(isSaved = flight.id in savedIds) }
    // P7: ranking only orders the suggestions; the flights keep O1.
    val results = index.resultOrder.matching(filters, origin.codes, destination.codes).map(item)
    val saved = savedFlights.map { savedFlight ->
        SavedFlightItem(
            flight = savedFlight.flight.toItem(isSaved = true),
            savedAt = savedFlight.savedAt.toEpochMilliseconds(),
            // V4: only a successful refresh can say a flight is no longer listed.
            isListed = index.lastSuccessAt == null || savedFlight.flight.id in index.ids,
        )
    }
    return ExplorerState(
        data = dataStatus(index.lastSuccessAt, sync),
        originQuery = inputs.originQuery,
        destinationQuery = inputs.destinationQuery,
        filters = filters,
        options = index.options,
        activeFilters = filters.active(),
        // S14: a pick counts as search text.
        hasAnythingToReset = hasAnythingToReset(filters, inputs.originQuery, inputs.destinationQuery) ||
            origin.pick != null || destination.pick != null,
        results = results,
        markers = index.sheetOrder.matching(filters, origin.codes, destination.codes).toDestinationGroups().map { group ->
            DestinationMarker(
                code = group.destination.code,
                city = group.destination.city,
                latitude = group.destination.latitude,
                longitude = group.destination.longitude,
                minPoints = group.minPoints,
                flights = group.flights.map(item),
            )
        },
        // L3: a selection the results no longer have shows as nothing; the store then clears it for good.
        selected = results.firstOrNull { it.id == inputs.selectedFlightId },
        saved = saved,
        savedSelected = saved.firstOrNull { it.flight.id == inputs.savedSelectedId },
        pendingWriteIds = inputs.pendingWriteIds,
        messages = inputs.messages,
        originSuggestions = origin.suggestions,
        destinationSuggestions = destination.suggestions,
        originHint = origin.hint,
        destinationHint = destination.hint,
        originPick = origin.pick,
        destinationPick = destination.pick,
        isLocationShared = inputs.userLocation != null,
    )
}

private fun dataStatus(lastSuccessAt: Long?, sync: SyncStatus): DataStatus = when {
    lastSuccessAt != null -> DataStatus.Ready(lastSuccessAt, sync.isRefreshing, sync.error)
    sync.error != null && !sync.isRefreshing -> DataStatus.Unavailable(sync.error)
    else -> DataStatus.Loading
}

private fun Flight.toItem(isSaved: Boolean) = FlightItem(
    id = id,
    originCode = origin.code,
    originCity = origin.city,
    originLatitude = origin.latitude,
    originLongitude = origin.longitude,
    destinationCode = destination.code,
    destinationCity = destination.city,
    destinationLatitude = destination.latitude,
    destinationLongitude = destination.longitude,
    departureDate = departureDate.toString(),
    program = program,
    points = points,
    cabin = cabin,
    isSaved = isSaved,
)

/** Back to the domain model, to save a flight the user tapped. */
internal fun FlightItem.toFlight() = Flight(
    id = id,
    origin = Airport(originCode, originCity, originLatitude, originLongitude),
    destination = Airport(destinationCode, destinationCity, destinationLatitude, destinationLongitude),
    departureDate = LocalDate.parse(departureDate),
    program = program,
    points = points,
    cabin = cabin,
)
