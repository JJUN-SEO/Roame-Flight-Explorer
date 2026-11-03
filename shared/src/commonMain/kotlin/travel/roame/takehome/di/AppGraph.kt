package travel.roame.takehome.di

import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import travel.roame.takehome.data.RoomFlightRepository
import travel.roame.takehome.data.RoomSavedFlightsRepository
import travel.roame.takehome.data.local.AppDatabase
import travel.roame.takehome.data.remote.KtorFlightsApi
import travel.roame.takehome.presentation.ExplorerStore
import kotlin.time.Clock

/** The single composition root. Each app creates one per process (D10). The platform supplies the database file, the HTTP engine and the host. */
class AppGraph internal constructor(
    database: AppDatabase,
    engine: HttpClientEngine,
    baseUrl: String,
    clock: Clock = Clock.System,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    val store: ExplorerStore = ExplorerStore(
        flights = RoomFlightRepository(KtorFlightsApi(engine, baseUrl), database.flightCacheDao(), clock),
        savedFlights = RoomSavedFlightsRepository(database.savedFlightDao(), clock),
        scope = scope,
    )
}
