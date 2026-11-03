import SwiftUI
import Shared

/** Two tabs (UI_SPEC §1). The app only renders the shared state and sends intents. */
struct ExplorerApp: View {
    var body: some View {
        TabView {
            ExploreScreen()
                .tabItem { Label("Explore", systemImage: "airplane") }
            SavedScreen()
                .tabItem { Label("Saved", systemImage: "heart") }
        }
    }
}

private struct ExploreScreen: View {
    @Environment(ExplorerModel.self) private var model
    @State private var openSheet: FilterKind?

    var body: some View {
        let state = model.state
        switch state.data {
        case is DataStatus.Loading:
            LoadingList()
        case let unavailable as DataStatus.Unavailable:
            UnavailableMessage(error: unavailable.error) { model.store.refresh() }
        default:
            if state.options.isEmpty {
                // UI_SPEC §4: a successful empty response is not an error (R4).
                ContentUnavailableView("No flights available right now", systemImage: "airplane", description: Text("The server returned no flights."))
            } else {
                VStack(spacing: 0) {
                    SearchHeader()
                    FilterBar { openSheet = $0 }
                    ResultSummary()
                    if state.results.isEmpty {
                        FilteredEmpty()
                    } else {
                        ResultsList(state: state)
                    }
                }
                .background(Color(.systemGroupedBackground))
                .sheet(isPresented: Binding(get: { openSheet != nil }, set: { if !$0 { openSheet = nil } })) {
                    if let kind = openSheet { FilterSheet(kind: kind) }
                }
            }
        }
    }
}

private struct ResultsList: View {
    let state: ExplorerState

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 8) {
                ForEach(state.results, id: \.id) { FlightRow(flight: $0) }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        // UI_SPEC §5: scrolling the list dismisses the keyboard.
        .scrollDismissesKeyboard(.immediately)
    }
}

/** UI_SPEC §4 first load: skeleton rows with a label. */
private struct LoadingList: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Finding flights…")
                .font(.headline)
                .foregroundStyle(.secondary)
            ForEach(0..<7, id: \.self) { _ in
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color(.secondarySystemFill))
                    .frame(height: 92)
            }
            Spacer()
        }
        .padding(16)
    }
}

/** R7, R9: the cause, in the words UI_SPEC §4 gives it, with Try again. */
private struct UnavailableMessage: View {
    let error: FetchError
    let onRetry: () -> Void

    var body: some View {
        let (title, body) = copy
        ContentUnavailableView {
            Label(title, systemImage: "exclamationmark.triangle")
        } description: {
            if let body { Text(body) }
        } actions: {
            Button("Try again", action: onRetry)
                .buttonStyle(.borderedProminent)
        }
    }

    private var copy: (String, String?) {
        switch error {
        case is FetchError.Offline:
            ("Can't reach Flight Explorer", "Make sure the local API is running and you're connected, then try again.")
        case is FetchError.Timeout:
            ("The server took too long to respond.", nil)
        case let server as FetchError.Server:
            ("The server had a problem (\(server.status)).", nil)
        case is FetchError.InvalidData:
            ("We received flight data we couldn't read.", nil)
        default:
            ("Something went wrong while loading flights.", nil)
        }
    }
}

/** V8: every saved flight, whatever the filters. */
private struct SavedScreen: View {
    @Environment(ExplorerModel.self) private var model

    var body: some View {
        let saved = model.state.saved
        if saved.isEmpty {
            ContentUnavailableView("No saved flights yet", systemImage: "heart", description: Text("Tap ♡ on any flight to keep it here — even offline."))
        } else {
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(saved, id: \.flight.id) { FlightRow(flight: $0.flight) }
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
        }
    }
}

/** "1 flight", "1,000 flights". */
func flightCount(_ count: Int) -> String {
    "\(Format.count(count)) \(count == 1 ? "flight" : "flights")"
}
