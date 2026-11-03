package travel.roame.takehome.presentation

import travel.roame.takehome.model.ActiveFilter
import travel.roame.takehome.model.AirportSuggestion
import travel.roame.takehome.model.FetchError
import travel.roame.takehome.model.FilterOptions
import travel.roame.takehome.model.FlightFilters

/** Everything both apps show (ARCHITECTURE §4.1). They render this and never filter, sort or group flights themselves. */
data class ExplorerState(
    val data: DataStatus,
    val originQuery: String,
    val destinationQuery: String,
    val filters: FlightFilters,
    /** From the whole cache, so options don't disappear while filtering (F8). */
    val options: FilterOptions,
    val activeFilters: List<ActiveFilter>,
    val hasAnythingToReset: Boolean,
    /** After search and filters, in O1 order. */
    val results: List<FlightItem>,
    /** From exactly the same flights as [results] (M1), ordered by code. */
    val markers: List<DestinationMarker>,
    /** The discovery selection, shared by the list and the map (L1). Always one of [results]. */
    val selected: FlightItem?,
    /** Every saved flight, whatever the filters (V8), in O3 order. */
    val saved: List<SavedFlightItem>,
    /** The Saved view's own selection (L7). */
    val savedSelected: SavedFlightItem?,
    /** Flights with a save or remove still running. Their buttons show progress and are disabled (V7). */
    val pendingWriteIds: Set<String>,
    /** One-shot errors. The app shows each once, then calls `messageShown(id)`. */
    val messages: List<UserMessage>,
    /** S14: at most 5 rows. Empty for a blank field, no match, or while a pick is active. */
    val originSuggestions: List<AirportSuggestion>,
    val destinationSuggestions: List<AirportSuggestion>,
    /** S13: every fuzzy match, in ranked order. Empty unless the field's tier is `Fuzzy`. */
    val originHint: List<AirportSuggestion>,
    val destinationHint: List<AirportSuggestion>,
    /** S14: the tapped suggestion. While set, the field shows it instead of its text. */
    val originPick: AirportSuggestion?,
    val destinationPick: AirportSuggestion?,
    /** D16: the user shared a location, so the ⌖ button is filled. */
    val isLocationShared: Boolean,
) {
    /** C1. */
    val resultCount: Int get() = results.size

    companion object {
        val Initial = ExplorerState(
            data = DataStatus.Loading,
            originQuery = "",
            destinationQuery = "",
            filters = FlightFilters(),
            options = FilterOptions(),
            activeFilters = emptyList(),
            hasAnythingToReset = false,
            results = emptyList(),
            markers = emptyList(),
            selected = null,
            saved = emptyList(),
            savedSelected = null,
            pendingWriteIds = emptySet(),
            messages = emptyList(),
            originSuggestions = emptyList(),
            destinationSuggestions = emptyList(),
            originHint = emptyList(),
            destinationHint = emptyList(),
            originPick = null,
            destinationPick = null,
            isLocationShared = false,
        )
    }
}

/** The offline rules (R6–R8). Whether a sync ever succeeded decides this, not how many flights are cached. */
sealed class DataStatus {
    /** Nothing has ever synced, and no failure to report yet. */
    data object Loading : DataStatus()

    /** Nothing has ever synced and the fetch failed (R7). */
    data class Unavailable(val error: FetchError) : DataStatus()

    /** At least one sync succeeded, so there's data to show, even if the latest refresh failed (R6). */
    data class Ready(
        /** Epoch milliseconds (T4). */
        val lastSuccessAt: Long,
        val isRefreshing: Boolean,
        val refreshError: FetchError?,
    ) : DataStatus()
}

data class FlightItem(
    val id: String,
    val originCode: String,
    val originCity: String,
    val originLatitude: Double,
    val originLongitude: Double,
    val destinationCode: String,
    val destinationCity: String,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    /** ISO `yyyy-MM-dd`: a calendar date, never an instant (T3). */
    val departureDate: String,
    val program: String,
    val points: Int,
    val cabin: String,
    /** The same answer everywhere this flight appears (V3). */
    val isSaved: Boolean,
)

data class SavedFlightItem(
    val flight: FlightItem,
    /** Epoch milliseconds. */
    val savedAt: Long,
    /** False when the latest successful refresh no longer has this flight: "No longer listed" (V4). */
    val isListed: Boolean,
)

/** Retry calls `toggleSave(flightId)` after [UserMessageKind.SaveFailed] and `removeSaved(flightId)` after [UserMessageKind.RemoveFailed]. */
data class UserMessage(val id: Long, val kind: UserMessageKind, val flightId: String)

enum class UserMessageKind { SaveFailed, RemoveFailed }

data class DestinationMarker(
    val code: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val minPoints: Int,
    /** In O2 order (M5). */
    val flights: List<FlightItem>,
) {
    val flightCount: Int get() = flights.size
}
