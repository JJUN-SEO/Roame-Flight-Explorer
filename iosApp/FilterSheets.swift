import SwiftUI
import Shared

/**
 * One sheet per filter (UI_SPEC §2.4). Choices apply as they're made, so "Show N flights" shows the
 * real count and just closes the sheet.
 */
struct FilterSheet: View {
    @Environment(ExplorerModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let kind: FilterKind

    var body: some View {
        let state = model.state
        NavigationStack {
            Group {
                switch kind {
                case .dates: DatesBody(state: state, store: model.store)
                case .cabin: CabinBody(state: state, store: model.store)
                case .maxpoints: PointsBody(state: state, store: model.store)
                default: ProgramsBody(state: state, store: model.store)
                }
            }
            .navigationTitle(kindName(kind))
            .navigationBarTitleDisplayMode(.inline)
            // Always on screen, whatever the sheet's height.
            .safeAreaInset(edge: .bottom) {
                HStack {
                    Button(clearLabel) { model.store.clearFilter(kind: kind) }
                    Spacer()
                    Button("Show \(flightCount(Int(state.resultCount)))") { dismiss() }
                        .buttonStyle(.borderedProminent)
                }
                .padding(16)
                .background(.bar)
            }
        }
        .presentationDetents(kind == .dates ? [.large] : [.medium, .large])
    }

    private var clearLabel: String {
        switch kind {
        case .dates: "Any dates"
        case .cabin, .maxpoints: "Any"
        default: "Clear"
        }
    }
}

/**
 * Start and end pickers, limited to the data's dates and to each other. A picked day goes to the store as
 * year, month and day (UI_SPEC §9), so it can't move to another day.
 */
private struct DatesBody: View {
    let state: ExplorerState
    let store: ExplorerStoreIos

    var body: some View {
        if let minIso = state.options.minDate, let maxIso = state.options.maxDate,
           let min = Format.localDate(minIso), let max = Format.localDate(maxIso) {
            let start = Format.localDate(state.filters.dateStart ?? minIso) ?? min
            let end = Format.localDate(state.filters.dateEnd ?? maxIso) ?? max
            Form {
                DatePicker("Start", selection: Binding(get: { start }, set: { picked in
                    store.setDateRange(startIso: Format.iso(picked), endIso: state.filters.dateEnd)
                }), in: min...end, displayedComponents: .date)
                DatePicker("End", selection: Binding(get: { end }, set: { picked in
                    store.setDateRange(startIso: state.filters.dateStart, endIso: Format.iso(picked))
                }), in: start...max, displayedComponents: .date)
            }
        }
    }
}

/** Single choice (F5), in rank order from the data. */
private struct CabinBody: View {
    let state: ExplorerState
    let store: ExplorerStoreIos

    var body: some View {
        List {
            OptionRow(label: "Any", selected: state.filters.cabin == nil) { store.setCabin(cabin: nil) }
            ForEach(state.options.cabins, id: \.self) { cabin in
                OptionRow(label: cabin, selected: state.filters.cabin == cabin) { store.setCabin(cabin: cabin) }
            }
        }
    }
}

private struct OptionRow: View {
    let label: String
    let selected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                Text(label).foregroundStyle(.primary)
                Spacer()
                if selected { Image(systemName: "checkmark").foregroundStyle(.tint) }
            }
            .contentShape(.rect)
        }
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/** From the data's lowest to highest points in steps of 500; the top means no limit (F4). */
private struct PointsBody: View {
    let state: ExplorerState
    let store: ExplorerStoreIos
    // Kept here while dragging, because the store's copy arrives a moment later.
    @State private var value: Double?

    var body: some View {
        if let min = state.options.minPoints?.intValue, let max = state.options.maxPoints?.intValue {
            let current = value ?? Double(state.filters.maxPoints?.intValue ?? max)
            let points = Int(current)
            Form {
                Text(points >= max ? "Any" : "Up to \(PointsFormat.shared.full(points: Int32(points)))")
                    .font(.headline.monospacedDigit())
                Slider(value: Binding(get: { current }, set: { newValue in
                    value = newValue
                    store.setMaxPoints(points: Int32(newValue))
                }), in: Double(min)...Double(max), step: 500)
                .accessibilityLabel("Maximum points")
            }
            .onChange(of: state.filters.maxPoints == nil) { _, cleared in
                if cleared { value = Double(max) }
            }
        }
    }
}

/** Any of the ticked programs (F6); none ticked means all. */
private struct ProgramsBody: View {
    let state: ExplorerState
    let store: ExplorerStoreIos

    var body: some View {
        List {
            ForEach(state.options.programs, id: \.self) { program in
                OptionRow(label: program, selected: state.filters.programs.contains(program)) {
                    store.toggleProgram(name: program)
                }
            }
        }
    }
}
