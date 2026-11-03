package travel.roame.takehome.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import travel.roame.takehome.data.FlightRepository
import travel.roame.takehome.domain.cleared
import travel.roame.takehome.domain.normalizedTo
import travel.roame.takehome.model.FilterKind

/** The one source of truth for both apps (D2, D10). Every intent is a plain function, so it's easy to call from Swift. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExplorerStore internal constructor(
    private val flights: FlightRepository,
    private val scope: CoroutineScope,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    reducer: suspend (Snapshot) -> ExplorerState = ::reduce,
) {
    private val inputs = MutableStateFlow(ExplorerInputs())

    val state: StateFlow<ExplorerState> = combine(flights.cache.map(::index), flights.syncStatus, inputs, ::Snapshot)
        // Newer input cancels older work, so a slow result can't overwrite a newer one (PERF-3).
        .mapLatest(reducer)
        // F2, F4, F8: make what normalising dropped stay dropped, even if the data offers it again later.
        .onEach { state -> inputs.update { it.copy(filters = it.filters.normalizedTo(state.options)) } }
        .flowOn(defaultDispatcher)
        .stateIn(scope, SharingStarted.Eagerly, ExplorerState.Initial)

    init {
        // R1: the cache flow above shows cached data right away; this refreshes it in the background.
        refresh()
    }

    /** Pull to refresh and Retry. Ignored while a refresh is running (R2). */
    fun refresh() {
        scope.launch { flights.refresh() }
    }

    fun setOriginQuery(text: String) = inputs.update { it.copy(originQuery = text) }

    fun setDestinationQuery(text: String) = inputs.update { it.copy(destinationQuery = text) }

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

    /** C4. */
    fun resetAll() = inputs.update { ExplorerInputs() }

    private fun isIsoDateOrNull(text: String?): Boolean = try {
        text?.let(LocalDate::parse)
        true
    } catch (e: IllegalArgumentException) {
        false
    }
}
