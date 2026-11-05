import SwiftUI
import MapKit
import Shared

/** Where the results sheet rests (UI_SPEC §2.1): its header only, half the map, or the whole area. */
enum SheetStop { case peek, half, full }

/**
 * The results, like Airbnb's (Step 6.3d, D36, UI_SPEC §2.1), drawn the same as Android's ResultsScreen (D38): the search
 * summary with ← and Filters, the active filters, then the map with the flight list as a sheet over it. Selecting a
 * flight, from the list or a marker, drops the sheet so the map frames its route, with the selection card above it.
 */
struct ResultsScreen: View {
    @Environment(ExplorerModel.self) private var model
    let isActive: Bool
    /** Where the list rests. The app holds it, so a destination deal can open the list fully. */
    @Binding var stop: SheetStop
    let onBack: () -> Void
    let onEditSearch: () -> Void
    let onDebugLongPress: (() -> Void)?
    @State private var filtersOpen = false
    @State private var sortOpen = false
    @State private var openMarker: String?
    // Kept here, as the screen stays alive behind the other pages, so the camera survives (M8).
    @State private var camera: MapCameraPosition?

    var body: some View {
        let state = model.state
        VStack(spacing: 0) {
            ResultsHeader(onBack: onBack, onEditSearch: onEditSearch, onFilters: { filtersOpen = true })
            switch state.data {
            case let unavailable as DataStatus.Unavailable:
                UnavailableMessage(error: unavailable.error) { model.store.refresh() }
            case let data as DataStatus.Ready:
                StaleBanner(data: data)
                RefreshProgress(data: data)
                if state.options.isEmpty {
                    // UI_SPEC §4: a successful empty response is not an error (R4).
                    EmptyState(icon: "ms_flight", title: "No flights available right now", message: "The server returned no flights.")
                } else {
                    MapWithSheet(
                        stop: $stop,
                        camera: $camera,
                        isActive: isActive,
                        onMarkerTap: { openMarker = $0 },
                        onSort: { sortOpen = true },
                        onDebugLongPress: onDebugLongPress
                    )
                }
            default:
                // DataStatus.Loading, the only other case.
                LoadingList()
            }
        }
        .background(Color.background)
        .sheet(isPresented: $filtersOpen) { FiltersSheet() }
        .sheet(isPresented: $sortOpen) { SortSheet() }
        .sheet(isPresented: Binding(get: { openMarker != nil }, set: { if !$0 { openMarker = nil } })) {
            if let code = openMarker { MarkerSheet(code: code) }
        }
        // M5: the marker's sheet closes when its destination drops out of the results.
        .onChange(of: state.markers.map(\.code)) { _, codes in
            if let code = openMarker, !codes.contains(code) { openMarker = nil }
        }
        // A new selection, from the list, a marker or a deal, drops the sheet so the map shows the route.
        .onChange(of: state.selected?.id) { _, id in
            if id != nil { withAnimation(.spring(duration: 0.35)) { stop = .peek } }
        }
    }
}

/** ← to the landing page, the search summary (tap it to change the search), and Filters with its count. */
private struct ResultsHeader: View {
    @Environment(ExplorerModel.self) private var model
    let onBack: () -> Void
    let onEditSearch: () -> Void
    let onFilters: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Button(action: onBack) { MSIcon(name: "ms_arrow_back").frame(width: 48, height: 48).contentShape(.rect) }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Back")
                SearchPill(state: model.state, action: onEditSearch)
                FiltersButton(count: model.state.activeFilters.count, action: onFilters).padding(.leading, 8)
            }
            .padding(EdgeInsets(top: 8, leading: 4, bottom: 8, trailing: 8))
            ActiveFilterBar(onOpen: onFilters)
        }
        .background(Color.background)
    }
}

/** Airbnb's filter button: a round outlined button with the count of active filters on a badge. */
private struct FiltersButton: View {
    let count: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            MSIcon(name: "ms_tune")
                .frame(width: 48, height: 48)
                .overlay(Circle().strokeBorder(Color.outline))
                .contentShape(.circle)
        }
        .buttonStyle(.plain)
        .overlay(alignment: .topTrailing) {
            if count > 0 { CountBadge(count: count) }
        }
        .accessibilityLabel(count > 0 ? "Filters, \(count) active" : "Filters")
    }
}

/**
 * The map fills the area; the list is a sheet over it that rests at `SheetStop`s. Only the sheet's header drags it
 * while the list is fully open and scrolls; otherwise the whole sheet drags. The same rules as Android's.
 */
private struct MapWithSheet: View {
    @Environment(ExplorerModel.self) private var model
    @Binding var stop: SheetStop
    @Binding var camera: MapCameraPosition?
    let isActive: Bool
    let onMarkerTap: (String) -> Void
    let onSort: () -> Void
    let onDebugLongPress: (() -> Void)?
    @State private var drag: CGFloat = 0
    @State private var dragging = false
    @State private var cardHeight: CGFloat = 0
    /** Large text makes the header taller than its usual 64 pt, and the peek shows all of it. */
    @State private var headerHeight: CGFloat = 0

    var body: some View {
        let state = model.state
        GeometryReader { geometry in
            let height = geometry.size.height
            let offset = min(max(anchor(stop, height) + drag, anchor(.full, height)), anchor(.peek, height))
            let showCard = state.selected != nil && stop == .peek
            let covered = height - anchor(stop, height) + (showCard && cardHeight > 0 ? cardHeight + 12 : 0)
            let full = stop == .full && !dragging
            let sheetDrag = DragGesture(minimumDistance: 8, coordinateSpace: .global)
                .onChanged { value in
                    dragging = true
                    drag = value.translation.height
                }
                .onEnded { value in
                    let at = min(max(anchor(stop, height) + value.translation.height, 0), anchor(.peek, height))
                    // How much further the finger was heading: a flick goes one stop in its direction, as on Android.
                    let flick = value.predictedEndTranslation.height - value.translation.height
                    let stops = [SheetStop.full, .half, .peek]
                    let target: SheetStop = if flick < -flickDistance {
                        stops.last { anchor($0, height) < at - 1 } ?? .full
                    } else if flick > flickDistance {
                        stops.first { anchor($0, height) > at + 1 } ?? .peek
                    } else {
                        stops.min { abs(anchor($0, height) - at) < abs(anchor($1, height) - at) } ?? stop
                    }
                    withAnimation(.spring(duration: 0.35)) {
                        stop = target
                        drag = 0
                    }
                    dragging = false
                }

            ZStack(alignment: .top) {
                ExplorerMap(
                    camera: $camera,
                    isActive: isActive,
                    bottomInset: covered,
                    routeToFit: showCard && cardHeight > 0 ? state.selected?.id : nil,
                    onMarkerTap: onMarkerTap
                )

                if showCard, let selected = state.selected {
                    FlightCard(
                        flight: selected,
                        isPending: state.pendingWriteIds.contains(selected.id),
                        onClose: { model.store.select(flightId: nil) },
                        onToggleSave: { model.store.toggleSave(flightId: selected.id) },
                        onDebugLongPress: onDebugLongPress
                    )
                    .padding(.horizontal, 16)
                    // Measured before the cap below, which would otherwise report its own, taller frame: the map then
                    // took most of itself as covered and framed routes at its top (found in Step 6.3e).
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { cardHeight = $0 }
                    // No taller than the room above the peeking sheet, so its ✕ stays on screen; its details scroll.
                    .frame(maxHeight: max(anchor(.peek, height) - 20, 160), alignment: .top)
                    .offset(y: offset - cardHeight - 12)
                }

                VStack(spacing: 0) {
                    SheetHeader(state: state, onSort: onSort)
                        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { headerHeight = $0 }
                        .gesture(sheetDrag)
                    Group {
                        if state.results.isEmpty {
                            FilteredEmpty()
                        } else {
                            ZStack(alignment: .bottom) {
                                ResultsList(scrollEnabled: full)
                                if full {
                                    // Airbnb's way back to the map from the full list.
                                    MapButton { withAnimation(.spring(duration: 0.35)) { stop = .peek } }.padding(.bottom, 24)
                                }
                            }
                        }
                    }
                    .frame(maxHeight: .infinity, alignment: .top)
                    .contentShape(.rect)
                    .gesture(sheetDrag, including: full ? .subviews : .all)
                }
                .frame(height: height, alignment: .top)
                .background(Color.background, in: UnevenRoundedRectangle(topLeadingRadius: 20, topTrailingRadius: 20))
                .shadow(color: .black.opacity(0.18), radius: 10, y: -2)
                .offset(y: offset)
            }
        }
        .clipped()
    }

    private func anchor(_ stop: SheetStop, _ height: CGFloat) -> CGFloat {
        switch stop {
        case .full: 0
        case .half: height * 0.5
        case .peek: max(height - max(peekHeight, headerHeight), 0)
        }
    }
}

/**
 * The handle, the count (C1) and the sort (O1, D46). The count is spoken with a note that the list can be dragged;
 * the sort is its own button.
 */
private struct SheetHeader: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    let state: ExplorerState
    let onSort: () -> Void

    var body: some View {
        let count = flightCount(Int(state.resultCount))
        // At the accessibility sizes the sort goes under the count, or its name broke mid-word (Step 6.3e).
        let layout = typeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(HStackLayout(spacing: 8))
        VStack(spacing: 0) {
            Capsule().fill(Color.outline).frame(width: 36, height: 4).padding(.top, 10)
            layout {
                Text(state.results.isEmpty ? "No flights match your search" : count)
                    .font(.appTitle)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityLabel("\(count). Flight list. Drag to show more or less of the map")
                    .accessibilityAddTraits(.isHeader)
                if !state.results.isEmpty { SortButton(sort: state.sort, action: onSort) }
            }
            .padding(EdgeInsets(top: 6, leading: 20, bottom: 8, trailing: 12))
        }
        .frame(maxWidth: .infinity)
        .frame(minHeight: peekHeight, alignment: .top)
        .contentShape(.rect)
    }
}

/** The current sort, as an outlined pill with ▾, which opens the sort sheet. */
private struct SortButton: View {
    let sort: Shared.SortOrder
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text(sortName(sort)).font(.appLabel)
                MSIcon(name: "ms_arrow_down", size: 18)
            }
            .padding(EdgeInsets(top: 4, leading: 14, bottom: 4, trailing: 10))
            .frame(minHeight: 36)
            .overlay(Capsule().strokeBorder(Color.outline))
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Sort, \(sortName(sort))")
    }
}

/** The four sorts (O1, D46), drawn the same as Android's SortSheet. Choosing one applies it and closes the sheet. */
private struct SortSheet: View {
    @Environment(ExplorerModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let current = model.state.sort
        VStack(spacing: 0) {
            Text("Sort by")
                .font(.appTitle)
                .frame(maxWidth: .infinity)
                .padding(.top, 20)
                .padding(.bottom, 12)
                .accessibilityAddTraits(.isHeader)
            ForEach(Shared.SortOrder.entries, id: \.self) { sort in
                OptionRow(label: sortName(sort), isSelected: sort == current) {
                    model.store.setSort(sort: sort)
                    dismiss()
                }
            }
            Spacer(minLength: 16)
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.background)
        .foregroundStyle(Color.text)
    }
}

private func sortName(_ sort: Shared.SortOrder) -> String {
    switch sort {
    case .lowestpoints: "Lowest points"
    case .highestpoints: "Highest points"
    case .byprogram: "By program"
    default: "Best offer"
    }
}

/** The results in the shared sections: under By program, each program's flights follow its heading (O1). */
private struct ResultsList: View {
    @Environment(ExplorerModel.self) private var model
    let scrollEnabled: Bool

    var body: some View {
        let state = model.state
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 8) {
                    Color.clear.frame(height: 0).id(listTop)
                    ForEach(state.sections, id: \.program) { section in
                        if let program = section.program {
                            ProgramHeading(program: program, count: Int(section.flightCount))
                        }
                        ForEach(section.flights, id: \.id) { flight in
                            FlightRow(
                                flight: flight,
                                isSelected: flight.id == state.selected?.id,
                                isPending: state.pendingWriteIds.contains(flight.id),
                                onTap: { model.store.select(flightId: flight.id) },
                                onToggleSave: { model.store.toggleSave(flightId: flight.id) }
                            )
                        }
                    }
                }
                .padding(EdgeInsets(top: 4, leading: 16, bottom: 96, trailing: 16))
            }
            .scrollDisabled(!scrollEnabled)
            // A new sort or a new search starts the list from its top, as on Android.
            .onChange(of: state.sort) { proxy.scrollTo(listTop, anchor: .top) }
            .onChange(of: searchKey(state)) { proxy.scrollTo(listTop, anchor: .top) }
        }
    }
}

private let listTop = "list-top"

/** What the search is: the fields, their picks and the filters. */
private func searchKey(_ state: ExplorerState) -> [AnyHashable] {
    [state.originQuery, state.destinationQuery, AnyHashable(state.originPick), AnyHashable(state.destinationPick), state.filters]
}

/** "Aeroplan · 212 flights", spoken as a heading. */
private struct ProgramHeading: View {
    let program: String
    let count: Int

    var body: some View {
        Text("\(program) · \(flightCount(count))")
            .font(.appTitleSmall)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(EdgeInsets(top: 8, leading: 4, bottom: 0, trailing: 0))
            .accessibilityLabel("\(program), \(flightCount(count))")
            .accessibilityAddTraits(.isHeader)
    }
}

/** A dark pill with a map icon, over the full list. */
private struct MapButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                MSIcon(name: "ms_map", size: 20)
                Text("Map").font(.appLabel)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 20)
            .frame(height: 44)
            .background(Color.toast, in: .capsule)
            .shadow(color: .black.opacity(0.25), radius: 6, y: 2)
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Map")
    }
}

/** The sheet's header: the handle and the count, which is all that shows at `SheetStop.peek`. */
private let peekHeight: CGFloat = 64

/** How far past the release point the finger was heading for the release to count as a flick. */
private let flickDistance: CGFloat = 60
