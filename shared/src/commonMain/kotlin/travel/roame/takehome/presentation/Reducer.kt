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
import travel.roame.takehome.model.FilterOptions
import travel.roame.takehome.model.Flight
import travel.roame.takehome.model.FlightFilters

/** What the user has chosen. */
internal data class ExplorerInputs(
    val originQuery: String = "",
    val destinationQuery: String = "",
    val filters: FlightFilters = FlightFilters(),
)

/** The cache, prepared once per data change, so input changes filter without sorting (D6). */
internal class IndexedFlights(
    val resultOrder: List<Flight>,
    val sheetOrder: List<Flight>,
    val options: FilterOptions,
    val lastSuccessAt: Long?,
)

internal fun index(cache: CacheSnapshot) = IndexedFlights(
    resultOrder = cache.flights.sortedWith(ResultOrder),
    sheetOrder = cache.flights.sortedWith(MarkerSheetOrder),
    options = cache.flights.toFilterOptions(),
    lastSuccessAt = cache.lastSuccessAt?.toEpochMilliseconds(),
)

internal data class Snapshot(val index: IndexedFlights, val sync: SyncStatus, val inputs: ExplorerInputs)

/** Pure, so it can be tested without coroutines. */
internal fun reduce(snapshot: Snapshot): ExplorerState {
    val (index, sync, inputs) = snapshot
    val filters = inputs.filters.normalizedTo(index.options)
    return ExplorerState(
        data = dataStatus(index.lastSuccessAt, sync),
        originQuery = inputs.originQuery,
        destinationQuery = inputs.destinationQuery,
        filters = filters,
        options = index.options,
        activeFilters = filters.active(),
        hasAnythingToReset = hasAnythingToReset(filters, inputs.originQuery, inputs.destinationQuery),
        results = index.resultOrder.matching(filters).map(Flight::toItem),
        markers = index.sheetOrder.matching(filters).toDestinationGroups().map { group ->
            DestinationMarker(
                code = group.destination.code,
                city = group.destination.city,
                latitude = group.destination.latitude,
                longitude = group.destination.longitude,
                minPoints = group.minPoints,
                flights = group.flights.map(Flight::toItem),
            )
        },
    )
}

private fun dataStatus(lastSuccessAt: Long?, sync: SyncStatus): DataStatus = when {
    lastSuccessAt != null -> DataStatus.Ready(lastSuccessAt, sync.isRefreshing, sync.error)
    sync.error != null && !sync.isRefreshing -> DataStatus.Unavailable(sync.error)
    else -> DataStatus.Loading
}

private fun Flight.toItem() = FlightItem(
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
)
