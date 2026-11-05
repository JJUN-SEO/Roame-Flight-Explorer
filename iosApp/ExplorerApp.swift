import SwiftUI
import MapKit
import Shared

/**
 * Three tabs, Explore, Saved and Account, with Roame's tab bar drawn the same as on Android (UI_SPEC §1, D38). The app
 * only renders the shared state and sends intents. Failed writes show as a toast with Retry, and a successful one gives
 * a haptic (UI_SPEC §5).
 */
struct ExplorerApp: View {
    @Environment(ExplorerModel.self) private var model
    @State private var tab = AppTab.explore
    /** A short message of the app's own, such as the debug hook's confirmation, shown like a failed write's. */
    @State private var note: String?
    @State private var pendingBefore: Set<String> = []
    @State private var successes = 0
    @State private var location: UserLocation?
    /**
     * D36: Explore opens on the landing page. The search bar and the banner open the search page, whose button shows the
     * results; a deal shows its results straight away.
     */
    @State private var page = ExplorePage.landing
    /** Which selection's details page is open (Step 6.3f). */
    @State private var detailsSource: DetailsSource?
    /** Where the results' list rests, kept here so a destination deal can open it fully. */
    @State private var resultsStop = SheetStop.half

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                // The results stay alive behind the other pages and tabs, as on Android (D31), so the list, map and
                // camera survive. The landing page stays too, so it keeps its scroll position.
                let results = tab == .explore && page == .results
                ResultsScreen(
                    isActive: results,
                    stop: $resultsStop,
                    onBack: { page = .landing },
                    onEditSearch: { page = .search },
                    onDetails: { openDetails(.explore) },
                    onDebugLongPress: debugHook
                )
                .opacity(results ? 1 : 0)
                .allowsHitTesting(results)
                .accessibilityHidden(!results)
                let landing = tab == .explore && page == .landing
                LandingScreen(
                    onSearch: { page = .search },
                    onDestination: { deal in
                        model.store.openDestination(codes: deal.codes)
                        // The destination's flights as a list straight away (the owner's review of Step 6.3e).
                        resultsStop = .full
                        page = .results
                    },
                    onOffer: { offer in
                        model.store.openOffer(flightId: offer.flight.id)
                        page = .results
                    }
                )
                .background(Color.background)
                .opacity(landing ? 1 : 0)
                .allowsHitTesting(landing)
                .accessibilityHidden(!landing)
                // Made fresh each time, so it opens on its form.
                if tab == .explore && page == .search {
                    SearchScreen(
                        onClose: {
                            dismissKeyboard()
                            page = .landing
                        },
                        onSearch: {
                            dismissKeyboard()
                            page = .results
                        }
                    )
                    .background(Color.background)
                }
                switch tab {
                case .saved: SavedScreen(onDebugLongPress: debugHook, onDetails: { openDetails(.saved) })
                case .account: AccountScreen(onToast: { note = $0 })
                case .explore: EmptyView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            AppTabBar(selected: tab, savedCount: model.state.saved.count) { selected in
                if selected != tab { dismissKeyboard() }
                tab = selected
            }
        }
        .background(Color.background)
        // Over the tabs, like Roame's. It follows the live selection, so it closes if its flight goes, such as a saved
        // flight removed from the page itself.
        .overlay {
            if let details = shownDetails {
                FlightDetailsScreen(
                    details: details,
                    isPending: model.state.pendingWriteIds.contains(details.flight.id),
                    onClose: { openDetails(nil) },
                    onToggleSave: {
                        if detailsSource == .saved {
                            model.store.removeSaved(flightId: details.flight.id)
                        } else {
                            model.store.toggleSave(flightId: details.flight.id)
                        }
                    }
                )
                .accessibilityAddTraits(.isModal)
                .transition(.move(edge: .bottom))
            }
        }
        .onChange(of: shownDetails == nil) { _, gone in if gone { detailsSource = nil } }
        // At the top, so it never covers the selection card's Save or Remove button.
        .overlay(alignment: .top) { toasts }
        // A write succeeded when it leaves pendingWriteIds with no error message, because the store has no success event.
        .onChange(of: model.state.pendingWriteIds) { _, now in
            let finished = pendingBefore.subtracting(now)
            if finished.contains(where: { id in !model.state.messages.contains { $0.flightId == id } }) { successes += 1 }
            pendingBefore = now
        }
        .sensoryFeedback(.success, trigger: successes)
        // D32: made at launch, so it asks the first time and reads a permission granted on an earlier launch.
        .task {
            location = UserLocation { latitude, longitude in
                model.store.setUserLocation(latitude: latitude, longitude: longitude)
            }
        }
    }

    private var shownDetails: FlightDetails? {
        switch detailsSource {
        case .explore: model.state.selectedDetails
        case .saved: model.state.savedSelectedDetails
        case nil: nil
        }
    }

    private func openDetails(_ source: DetailsSource?) {
        withAnimation(.easeInOut(duration: 0.3)) { detailsSource = source }
    }

    /** V7: each failed write is shown once, with Retry, for 6 s; the app's own notes for 3 s. */
    @ViewBuilder private var toasts: some View {
        VStack(spacing: 8) {
            if let message = model.state.messages.first {
                AppToast(text: message.kind == .savefailed ? "Couldn't save flight." : "Couldn't remove flight.", action: "Retry") {
                    model.store.messageShown(id: message.id)
                    if message.kind == .savefailed {
                        model.store.toggleSave(flightId: message.flightId)
                    } else {
                        model.store.removeSaved(flightId: message.flightId)
                    }
                }
                .transition(.move(edge: .top).combined(with: .opacity))
                .task(id: message.id) {
                    try? await Task.sleep(for: .seconds(6))
                    model.store.messageShown(id: message.id)
                }
            }
            if let note {
                AppToast(text: note)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .task(id: note) {
                        try? await Task.sleep(for: .seconds(3))
                        self.note = nil
                    }
            }
        }
        .padding(16)
        .animation(.default, value: model.state.messages.first?.id)
        .animation(.default, value: note)
    }

    /** TEST_PLAN §6: debug builds only. */
    private var debugHook: (() -> Void)? {
        #if DEBUG
        return {
            model.store.failNextSavedWrite()
            note = "Debug: the next save or remove will fail."
        }
        #else
        return nil
        #endif
    }
}

/**
 * R11: the refresh on launch shows a thin bar over the data, never a full-screen loader. On iOS, SwiftUI's
 * linear `ProgressView` with no value is a still grey line, so this slides a short bar along a track, as
 * Android's does. With Reduce Motion on, the bar fades in and out instead of moving.
 */
struct RefreshProgress: View {
    let data: DataStatus.Ready
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        if data.isRefreshing {
            TimelineView(.animation) { context in
                let phase = context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.2) / 1.2
                GeometryReader { geometry in
                    let width = geometry.size.width
                    if reduceMotion {
                        Rectangle().fill(Color.accent).opacity(0.3 + 0.7 * abs(sin(phase * .pi)))
                    } else {
                        Capsule().fill(Color.accent)
                            .frame(width: width / 3)
                            .offset(x: -width / 3 + phase * width * 4 / 3)
                    }
                }
            }
            .frame(height: 4)
            .background(Color.accent.opacity(0.2))
            .clipped()
            .accessibilityElement()
            .accessibilityLabel("Refreshing flights")
        }
    }
}

/**
 * R6, T4: the last refresh failed, so say how old the data is. There's no Retry (D29): the app refreshes
 * when it opens, and the next success clears the banner (R11).
 */
struct StaleBanner: View {
    let data: DataStatus.Ready

    var body: some View {
        if data.refreshError != nil {
            Text("Couldn't refresh · showing results from \(Format.syncTime(data.lastSuccessAt))")
                .font(.appLabelSmall)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color.orange.opacity(0.15), in: .rect(cornerRadius: 12))
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
        }
    }
}

struct LoadingList: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Finding flights…")
                .font(.appTitle)
                .foregroundStyle(Color.secondaryText)
            ForEach(0..<7, id: \.self) { _ in
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.track)
                    .frame(height: 92)
            }
        }
        .padding(16)
        // The rows are taller than most screens give the list. Without this frame they stretched the whole app past
        // the screen while loading, pushing the title under the clock and the tab labels off the bottom (Step 6.4).
        // Both bounds, so the frame takes the height it's offered, not its rows' height.
        .frame(minWidth: 0, maxWidth: .infinity, minHeight: 0, maxHeight: .infinity, alignment: .topLeading)
        .clipped()
    }
}

/** R7, R9: the cause, in the words UI_SPEC §4 gives it, with Try again. */
struct UnavailableMessage: View {
    let error: FetchError
    let onRetry: () -> Void

    var body: some View {
        let (title, body) = copy
        EmptyState(icon: "ms_cloud_off", title: title, message: body) {
            PrimaryButton(text: "Try again", action: onRetry)
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
    let onDetails: () -> Void

    var body: some View {
        let state = model.state
        if state.saved.isEmpty {
            VStack(spacing: 0) {
                PageTitle(text: "Saved")
                EmptyState(icon: "ms_favorite", title: "No saved flights yet", message: "Tap ♡ on any flight to keep it here — even offline.")
            }
        } else {
            ScrollView {
                // Roame's large title, as on Account (Step 6.3e).
                PageTitle(text: "Saved")
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
                .padding(EdgeInsets(top: 8, leading: 16, bottom: 16, trailing: 16))
            }
            .background(Color.background)
            .safeAreaInset(edge: .bottom) {
                if let selected = state.savedSelected {
                    FlightCard(
                        flight: selected.flight,
                        isPending: state.pendingWriteIds.contains(selected.flight.id),
                        isListed: selected.isListed,
                        onClose: { model.store.selectSaved(flightId: nil) },
                        onToggleSave: { model.store.removeSaved(flightId: selected.flight.id) },
                        onDebugLongPress: onDebugLongPress,
                        onDetails: onDetails
                    )
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
                }
            }
        }
    }
}

/** Where a details page was opened from: Explore's selection or Saved's own (L7). */
private enum DetailsSource { case explore, saved }

/** Explore's three pages (D36): the landing page, the search page and the results. */
private enum ExplorePage { case landing, search, results }

@MainActor private func dismissKeyboard() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
}

/** "1 flight", "1,000 flights". */
func flightCount(_ count: Int) -> String {
    "\(Format.count(count)) \(count == 1 ? "flight" : "flights")"
}
