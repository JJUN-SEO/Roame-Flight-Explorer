import SwiftUI
import UIKit
import Shared

@main
struct RoameStarterApp: App {
    /** The one shared store (D10), pointed at the API in Info.plist: the simulator reaches the Mac at localhost (ARCHITECTURE §6.4). */
    @State private var model = ExplorerModel(store: ExplorerStoreIosKt.createExplorerStore(baseUrl: apiBaseUrl()))

    init() {
        // Roame's segmented control: the chosen side filled in pink. SwiftUI's segmented Picker has no style for this.
        UISegmentedControl.appearance().selectedSegmentTintColor = UIColor(Color.accent)
        UISegmentedControl.appearance().setTitleTextAttributes([.foregroundColor: UIColor(Color.onAccent)], for: .selected)
    }

    var body: some Scene {
        WindowGroup {
            ExplorerApp()
                .environment(model)
                .tint(.accent)
        }
    }
}

private func apiBaseUrl() -> String {
    guard let url = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String else {
        fatalError("API_BASE_URL is missing from Info.plist")
    }
    return url
}
