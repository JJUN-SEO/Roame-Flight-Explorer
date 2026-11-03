package travel.roame.takehome.ios

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import travel.roame.takehome.di.createAppGraph
import travel.roame.takehome.model.FilterKind
import travel.roame.takehome.presentation.ExplorerState
import travel.roame.takehome.presentation.ExplorerStore

/**
 * The only store API Swift uses (ARCHITECTURE §8): one plain method per intent, and state delivered
 * on the main thread. Swift can't use `StateFlow` or `suspend` well, so this hides them.
 */
class ExplorerStoreIos internal constructor(private val store: ExplorerStore) {

    val state: ExplorerState get() = store.state.value

    /** Calls [onChange] on the main thread with the current state and then every new one, until cancelled. */
    fun observe(onChange: (ExplorerState) -> Unit): Cancellable {
        val scope = MainScope()
        scope.launch { store.state.collect { onChange(it) } }
        return Cancellable { scope.cancel() }
    }

    fun refresh() = store.refresh()

    fun setOriginQuery(text: String) = store.setOriginQuery(text)

    fun setDestinationQuery(text: String) = store.setDestinationQuery(text)

    fun pickOrigin(codes: List<String>) = store.pickOrigin(codes)

    fun pickDestination(codes: List<String>) = store.pickDestination(codes)

    fun setUserLocation(latitude: Double, longitude: Double) = store.setUserLocation(latitude, longitude)

    fun setDateRange(startIso: String?, endIso: String?) = store.setDateRange(startIso, endIso)

    fun setCabin(cabin: String?) = store.setCabin(cabin)

    /** Not nullable, so Swift passes a plain Int. The slider's top value means no limit anyway (F4); a chip uses [clearFilter]. */
    fun setMaxPoints(points: Int) = store.setMaxPoints(points)

    fun toggleProgram(name: String) = store.toggleProgram(name)

    fun clearFilter(kind: FilterKind) = store.clearFilter(kind)

    fun resetAll() = store.resetAll()

    fun select(flightId: String?) = store.select(flightId)

    fun selectSaved(flightId: String?) = store.selectSaved(flightId)

    fun toggleSave(flightId: String) = store.toggleSave(flightId)

    fun removeSaved(flightId: String) = store.removeSaved(flightId)

    fun messageShown(id: Long) = store.messageShown(id)
}

fun interface Cancellable {
    fun cancel()
}

/** Creates the app's one store (D10). Call it once, when the app starts. */
fun createExplorerStore(baseUrl: String): ExplorerStoreIos = ExplorerStoreIos(createAppGraph(baseUrl).store)
