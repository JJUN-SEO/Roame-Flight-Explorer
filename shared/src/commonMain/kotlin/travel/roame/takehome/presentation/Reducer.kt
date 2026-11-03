package travel.roame.takehome.presentation

import travel.roame.takehome.data.CacheSnapshot
import travel.roame.takehome.data.SyncStatus
import travel.roame.takehome.domain.MarkerSheetOrder
import travel.roame.takehome.domain.ResultOrder
import travel.roame.takehome.domain.active
import travel.roame.takehome.domain.hasAnythingToReset
import travel.roame.takehome.domain.matching
import travel.roame.takehome.domain.normalizedTo
import travel.roame.takehome.domain.toDestinationGroups
import travel.roame.takehome.domain.toFilterOptions
import kotlinx.datetime.LocalDate
import travel.roame.takehome.model.Airport
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
)

/** The cache, prepared once per data change, so input changes filter without sorting (D6). */
internal class IndexedFlights(
    val resultOrder: List<Flight>,
    val sheetOrder: List<Flight>,
    val options: FilterOptions,
    val lastSuccessAt: Long?,
    val ids: Set<String>,
)

internal fun index(cache: CacheSnapshot) = IndexedFlights(
    resultOrder = cache.flights.sortedWith(ResultOrder),
    sheetOrder = cache.flights.sortedWith(MarkerSheetOrder),
    options = cache.flights.toFilterOptions(),
    lastSuccessAt = cache.lastSuccessAt?.toEpochMilliseconds(),
    ids = cache.flights.mapTo(HashSet()) { it.id },
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
    val savedIds = savedFlights.mapTo(HashSet()) { it.flight.id }
    val item = { flight: Flight -> flight.toItem(isSaved = flight.id in savedIds) }
    val results = index.resultOrder.matching(filters).map(item)
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
        hasAnythingToReset = hasAnythingToReset(filters, inputs.originQuery, inputs.destinationQuery),
        results = results,
        markers = index.sheetOrder.matching(filters).toDestinationGroups().map { group ->
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
