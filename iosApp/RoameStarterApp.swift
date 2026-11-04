import SwiftUI
import Shared

@main
struct RoameStarterApp: App {
    /** The one shared store (D10), pointed at the API in Info.plist: the simulator reaches the Mac at localhost (ARCHITECTURE §6.4). */
    @State private var model = ExplorerModel(store: ExplorerStoreIosKt.createExplorerStore(baseUrl: apiBaseUrl()))

    /** D39: the Account tab's appearance setting, saved between launches. */
    @AppStorage("appearance") private var appearance = Appearance.system.rawValue

    var body: some Scene {
        WindowGroup {
            ExplorerApp()
                .environment(model)
                .tint(.accent)
                .foregroundStyle(Color.text)
                .preferredColorScheme(Appearance(rawValue: appearance)?.colorScheme)
        }
    }
}

private func apiBaseUrl() -> String {
    guard let url = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String else {
        fatalError("API_BASE_URL is missing from Info.plist")
    }
    return url
}
