import SwiftUI
import Shared

/**
 * Two tabs (UI_SPEC §1). The app only renders the shared state and sends intents.
 * Failed writes show as a toast with Retry, and a successful one gives a haptic (UI_SPEC §5).
 */
struct ExplorerApp: View {
    @Environment(ExplorerModel.self) private var model
    @State private var debugNote: String?
    @State private var pendingBefore: Set<String> = []
    @State private var successes = 0

    var body: some View {
        TabView {
            ExploreScreen(onDebugLongPress: debugHook)
                .tabItem { Label("Explore", systemImage: "airplane") }
            SavedScreen(onDebugLongPress: debugHook)
                .tabItem { Label("Saved", systemImage: "heart") }
                .badge(model.state.saved.count)
        }
        // At the top, so it never covers the selection card's Save or Remove button.
        .overlay(alignment: .top) { toasts }
        // A write succeeded when it leaves pendingWriteIds with no error message, because the store has no success event.
        .onChange(of: model.state.pendingWriteIds) { _, now in
            let finished = pendingBefore.subtracting(now)
            if finished.contains(where: { id in !model.state.messages.contains { $0.flightId == id } }) { successes += 1 }
            pendingBefore = now
        }
        .sensoryFeedback(.success, trigger: successes)
    }

    @ViewBuilder private var toasts: some View {
        VStack(spacing: 8) {
            if let message = model.state.messages.first {
                // V7: each failed write is shown once, then marked shown.
                Toast(text: message.kind == .savefailed ? "Couldn't save flight." : "Couldn't remove flight.", action: "Retry") {
                    model.store.messageShown(id: message.id)
                    if message.kind == .savefailed {
                        model.store.toggleSave(flightId: message.flightId)
                    } else {
                        model.store.removeSaved(flightId: message.flightId)
                    }
                }
                .task(id: message.id) {
                    try? await Task.sleep(for: .seconds(6))
                    model.store.messageShown(id: message.id)
                }
            }
            if let note = debugNote {
                Toast(text: note)
                    .task(id: note) {
                        try? await Task.sleep(for: .seconds(3))
                        debugNote = nil
                    }
            }
        }
        .padding(.horizontal, 16)
        .animation(.default, value: model.state.messages.first?.id)
    }

    /** TEST_PLAN §6: debug builds only. */
    private var debugHook: (() -> Void)? {
        #if DEBUG
        return {
            model.store.failNextSavedWrite()
            debugNote = "Debug: the next save or remove will fail."
        }
        #else
        return nil
        #endif
    }
}

private struct Toast: View {
    let text: String
    var action: String?
    var onAction: () -> Void = {}

    var body: some View {
        HStack {
            Text(text).foregroundStyle(.white)
            Spacer()
            if let action {
                Button(action, action: onAction).fontWeight(.semibold).tint(Color(red: 0.69, green: 0.78, blue: 1))
            }
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 48)
        .background(Color(white: 0.17), in: .rect(cornerRadius: 12))
        .shadow(radius: 6, y: 2)
        .transition(.move(edge: .top).combined(with: .opacity))
    }
}

private struct ExploreScreen: View {
    @Environment(ExplorerModel.self) private var model
    @State private var openSheet: FilterKind?
    let onDebugLongPress: (() -> Void)?

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
                        ResultsList(onDebugLongPress: onDebugLongPress)
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
    @Environment(ExplorerModel.self) private var model
    let onDebugLongPress: (() -> Void)?

    var body: some View {
        let state = model.state
        ScrollView {
            LazyVStack(spacing: 8) {
                ForEach(state.results, id: \.id) { flight in
                    FlightRow(
                        flight: flight,
                        isSelected: flight.id == state.selected?.id,
                        isPending: state.pendingWriteIds.contains(flight.id),
                        onTap: {
                            dismissKeyboard()
                            model.store.select(flightId: flight.id)
                        },
                        onToggleSave: { model.store.toggleSave(flightId: flight.id) }
                    )
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        // UI_SPEC §5: scrolling the list dismisses the keyboard.
        .scrollDismissesKeyboard(.immediately)
        // L6: the card sits over the bottom of the list, which scrolls its last rows clear of it.
        .safeAreaInset(edge: .bottom) {
            if let selected = state.selected {
                FlightCard(
                    flight: selected,
                    isPending: state.pendingWriteIds.contains(selected.id),
                    onClose: { model.store.select(flightId: nil) },
                    onToggleSave: { model.store.toggleSave(flightId: selected.id) },
                    onDebugLongPress: onDebugLongPress
                )
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
        }
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

/** V8, L7: every saved flight whatever the filters, with its own selection and card. */
private struct SavedScreen: View {
    @Environment(ExplorerModel.self) private var model
    let onDebugLongPress: (() -> Void)?

    var body: some View {
        let state = model.state
        if state.saved.isEmpty {
            ContentUnavailableView("No saved flights yet", systemImage: "heart", description: Text("Tap ♡ on any flight to keep it here — even offline."))
        } else {
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(state.saved, id: \.flight.id) { saved in
                        FlightRow(
                            flight: saved.flight,
                            isSelected: saved.flight.id == state.savedSelected?.flight.id,
                            isPending: state.pendingWriteIds.contains(saved.flight.id),
                            isListed: saved.isListed,
                            onTap: { model.store.selectSaved(flightId: saved.flight.id) },
                            onToggleSave: { model.store.removeSaved(flightId: saved.flight.id) }
                        )
                    }
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .safeAreaInset(edge: .bottom) {
                if let selected = state.savedSelected {
                    FlightCard(
                        flight: selected.flight,
                        isPending: state.pendingWriteIds.contains(selected.flight.id),
                        isListed: selected.isListed,
                        onClose: { model.store.selectSaved(flightId: nil) },
                        onToggleSave: { model.store.removeSaved(flightId: selected.flight.id) },
                        onDebugLongPress: onDebugLongPress
                    )
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
                }
            }
        }
    }
}

@MainActor private func dismissKeyboard() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
}

/** "1 flight", "1,000 flights". */
func flightCount(_ count: Int) -> String {
    "\(Format.count(count)) \(count == 1 ? "flight" : "flights")"
}
