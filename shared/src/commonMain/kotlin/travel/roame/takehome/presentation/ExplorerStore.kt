package travel.roame.takehome.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import travel.roame.takehome.data.FlightRepository
import travel.roame.takehome.data.SavedFlightsRepository
import travel.roame.takehome.domain.cleared
import travel.roame.takehome.domain.normalizedTo
import travel.roame.takehome.domain.search.GeoPoint
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.model.FlightFilters

/** The one source of truth for both apps (D2, D10). Every intent is a plain function, so it's easy to call from Swift. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStore internal constructor(
    private val flights: FlightRepository,
    private val savedFlights: SavedFlightsRepository,
    private val scope: CoroutineScope,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    reducer: suspend (Snapshot) -> ExplorerState = ::reduce,
) {
    private val inputs = MutableStateFlow(ExplorerInputs())
    private var lastMessageId = 0L

    val state: StateFlow<ExplorerState> =
        combine(flights.cache.map(::index), flights.syncStatus, savedFlights.saved, inputs, ::Snapshot)
            // Newer input cancels older work, so a slow result can't overwrite a newer one (PERF-3).
            .mapLatest { snapshot -> reducer(snapshot) to snapshot.inputs }
            .onEach { (state, used) -> keepDropped(state, used) }
            .map { (state) -> state }
            .flowOn(defaultDispatcher)
            .stateIn(scope, SharingStarted.Eagerly, ExplorerState.Initial)

    init {
        // R1: the cache flow above shows cached data right away; this refreshes it in the background.
        refresh()
    }

    /** Try again on the first-load error; there's no manual refresh (D29). Ignored while a refresh is running (R2). */
    fun refresh() {
        scope.launch { flights.refresh() }
    }

    /** S14: editing the text clears a pick and goes back to searching. */
    fun setOriginQuery(text: String) = inputs.update { it.copy(originQuery = text, originPick = null) }

    fun setDestinationQuery(text: String) = inputs.update { it.copy(destinationQuery = text, destinationPick = null) }

    /** S14: filters by exactly these codes until the text is edited. Pass a suggestion's `codes`. */
    fun pickOrigin(codes: List<String>) = inputs.update { it.copy(originPick = codes.takeIf(List<String>::isNotEmpty)) }

    fun pickDestination(codes: List<String>) = inputs.update { it.copy(destinationPick = codes.takeIf(List<String>::isNotEmpty)) }

    /** D16, D32: only call this once the user has allowed location. It ranks the origin field (P5) and picks the home airport (B5). */
    fun setUserLocation(latitude: Double, longitude: Double) =
        inputs.update { it.copy(userLocation = GeoPoint(latitude, longitude)) }

    /** Takes ISO `yyyy-MM-dd` dates. Ignores anything else, because filtering relies on them being valid. */
    fun setDateRange(startIso: String?, endIso: String?) {
        if (!isIsoDateOrNull(startIso) || !isIsoDateOrNull(endIso)) return
        inputs.update { it.copy(filters = it.filters.copy(dateStart = startIso, dateEnd = endIso)) }
    }

    fun setCabin(cabin: String?) = inputs.update { it.copy(filters = it.filters.copy(cabin = cabin)) }

    fun setMaxPoints(points: Int?) = inputs.update { it.copy(filters = it.filters.copy(maxPoints = points)) }

    fun toggleProgram(name: String) = inputs.update { input ->
        val programs = input.filters.programs
        input.copy(filters = input.filters.copy(programs = if (name in programs) programs - name else programs + name))
    }

    /** C3. */
    fun clearFilter(kind: FilterKind) = inputs.update { it.copy(filters = it.filters.cleared(kind)) }

    /** C4. The selections, writes and messages stay; after a reset the results include every flight anyway. */
    fun resetAll() = inputs.update {
        it.copy(originQuery = "", destinationQuery = "", originPick = null, destinationPick = null, filters = FlightFilters())
    }

    /**
     * B9: a destination deal starts a new search: everything resets (C4), To picks the deal's airports, and From picks
     * the home airport when there is one (B5). Pass a [DestinationDeal]'s `codes`.
     */
    fun openDestination(codes: List<String>) {
        val home = state.value.landing.homeAirport
        inputs.update { it.newSearch(originPick = home?.let { airport -> listOf(airport.code) }, destinationPick = codes) }
    }

    /** B9: an offer starts a new search for its route, with the offer selected. Pass a [ProgramOffer]'s flight id. */
    fun openOffer(flightId: String) {
        val flight = state.value.landing.offers.firstOrNull { it.flight.id == flightId }?.flight ?: return
        inputs.update {
            it.newSearch(originPick = listOf(flight.originCode), destinationPick = listOf(flight.destinationCode))
                .copy(selectedFlightId = flight.id)
        }
    }

    private fun ExplorerInputs.newSearch(originPick: List<String>?, destinationPick: List<String>) = copy(
        originQuery = "",
        destinationQuery = "",
        originPick = originPick,
        destinationPick = destinationPick.takeIf(List<String>::isNotEmpty),
        filters = FlightFilters(),
    )

    /** L1, L2: selecting the selected flight again, or passing null, deselects it. */
    fun select(flightId: String?) =
        inputs.update { it.copy(selectedFlightId = flightId.takeUnless { id -> id == it.selectedFlightId }) }

    /** L7: the same, for the Saved view's own selection. */
    fun selectSaved(flightId: String?) =
        inputs.update { it.copy(savedSelectedId = flightId.takeUnless { id -> id == it.savedSelectedId }) }

    /** V7: saves a flight from the results, or removes it if it's saved. Ignored while a write for it is running. */
    fun toggleSave(flightId: String) {
        val item = state.value.results.firstOrNull { it.id == flightId } ?: return
        if (item.isSaved) {
            write(flightId, UserMessageKind.RemoveFailed, saves = false) { savedFlights.remove(flightId) }
        } else {
            write(flightId, UserMessageKind.SaveFailed, saves = true) { savedFlights.save(item.toFlight()) }
        }
    }

    /** V6, V7. Ignored while a write for this flight is running. */
    fun removeSaved(flightId: String) =
        write(flightId, UserMessageKind.RemoveFailed, saves = false) { savedFlights.remove(flightId) }

    fun messageShown(id: Long) = inputs.update { input -> input.copy(messages = input.messages.filterNot { it.id == id }) }

    /**
     * V7: there's no optimistic UI. The flight stays pending until this store's own state shows the
     * change, so the button never looks finished while the heart still shows the old state.
     */
    private fun write(flightId: String, failure: UserMessageKind, saves: Boolean, operation: suspend () -> Result<Unit>) {
        var started = false
        inputs.update { input ->
            started = flightId !in input.pendingWriteIds
            if (started) input.copy(pendingWriteIds = input.pendingWriteIds + flightId) else input
        }
        if (!started) return
        scope.launch {
            val succeeded = operation().isSuccess
            if (succeeded) state.first { it.saved.any { saved -> saved.flight.id == flightId } == saves }
            inputs.update { input ->
                input.copy(
                    pendingWriteIds = input.pendingWriteIds - flightId,
                    messages = if (succeeded) input.messages else input.messages + UserMessage(++lastMessageId, failure, flightId),
                )
            }
        }
    }

    /**
     * F8: filter values the data no longer offers stay dropped, even if a later refresh offers them again.
     * L3, L7, S14: so does a selection or pick the state no longer shows, unless the user has chosen another since.
     */
    private fun keepDropped(state: ExplorerState, used: ExplorerInputs) = inputs.update { input ->
        input.copy(
            filters = input.filters.normalizedTo(state.options),
            selectedFlightId = input.selectedFlightId.takeUnless {
                it == used.selectedFlightId && state.selected == null
            },
            savedSelectedId = input.savedSelectedId.takeUnless {
                it == used.savedSelectedId && state.savedSelected == null
            },
            // S14: a pick whose airports a refresh removed goes back to searching the text.
            originPick = input.originPick.takeUnless { it == used.originPick && state.originPick == null },
            destinationPick = input.destinationPick.takeUnless { it == used.destinationPick && state.destinationPick == null },
        )
    }

    private fun isIsoDateOrNull(text: String?): Boolean = try {
        text?.let(LocalDate::parse)
        true
    } catch (e: IllegalArgumentException) {
        false
    }
}
