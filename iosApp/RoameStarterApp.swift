import SwiftUI
import Shared

@main
struct RoameStarterApp: App {
    // Step 3.6 only: proves SwiftUI can read the real store. Step 5.1 replaces this with the screens.
    @State private var model = ExplorerModel(store: ExplorerStoreIosKt.createExplorerStore(baseUrl: "http://localhost:8080"))

    var body: some Scene {
        WindowGroup {
            Text(model.state.data is DataStatus.Ready ? "\(model.state.resultCount) flights" : "Loading flights…")
        }
    }
}

/** Holds the latest state for SwiftUI (ARCHITECTURE §8). */
@MainActor @Observable
final class ExplorerModel {
    private(set) var state: ExplorerState
    let store: ExplorerStoreIos
    @ObservationIgnored private var subscription: Cancellable?

    init(store: ExplorerStoreIos) {
        self.store = store
        self.state = store.state
        subscription = store.observe { [weak self] newState in
            MainActor.assumeIsolated { self?.state = newState }   // Kotlin delivers on the main thread
        }
    }
}
