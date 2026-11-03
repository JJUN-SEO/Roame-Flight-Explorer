import SwiftUI
import Shared

/** The chips, in the order C2 gives them. Kotlin/Native lowercases the whole enum name: `MaxPoints` is `maxpoints`. */
var filterKinds: [FilterKind] { [.dates, .cabin, .maxpoints, .programs] }

/** One chip per filter, filled when active, with ✕ to clear it on its own (C2, C3, UI_SPEC §1). */
struct FilterBar: View {
    @Environment(ExplorerModel.self) private var model
    let onOpen: (FilterKind) -> Void

    var body: some View {
        let state = model.state
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(filterKinds, id: \.self) { kind in
                    let active = state.activeFilters.first { $0.kind == kind }
                    FilterChip(
                        name: kindName(kind),
                        label: active.map { chipValue($0, state) } ?? defaultChipLabel(kind, state),
                        isActive: active != nil,
                        onOpen: { onOpen(kind) },
                        onClear: { model.store.clearFilter(kind: kind) }
                    )
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
    }
}

private struct FilterChip: View {
    let name: String
    let label: String
    let isActive: Bool
    let onOpen: () -> Void
    let onClear: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Button(action: onOpen) {
                Text(label).font(.subheadline.weight(.medium))
            }
            .accessibilityLabel(isActive ? "\(name): \(label), active" : name)
            if isActive {
                Button(action: onClear) {
                    Image(systemName: "xmark").font(.caption.weight(.semibold))
                }
                .accessibilityLabel("Clear \(name)")
            }
        }
        .buttonStyle(.plain)
        .foregroundStyle(isActive ? Color.white : Color.primary)
        .padding(.horizontal, 14)
        .frame(minHeight: 36)
        .background(isActive ? Color.accent : Color.clear, in: .capsule)
        .overlay(Capsule().strokeBorder(isActive ? Color.clear : Color(.separator)))
        .contentShape(.capsule)
    }
}

/** "238 flights · 2 filters" and Reset, which is hidden when there's nothing to reset (C1, C2, C4). */
struct ResultSummary: View {
    @Environment(ExplorerModel.self) private var model

    var body: some View {
        let state = model.state
        let filters = state.activeFilters.count
        HStack {
            Text(filters == 0 ? flightCount(Int(state.resultCount)) : "\(flightCount(Int(state.resultCount))) · \(filters) \(filters == 1 ? "filter" : "filters")")
                .font(.headline)
            Spacer()
            if state.hasAnythingToReset {
                Button("Reset") { model.store.resetAll() }
            }
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 36)
    }
}

/** The active filters, listed under the filtered-empty message (UI_SPEC §4). */
struct ActiveFilterList: View {
    let state: ExplorerState

    var body: some View {
        VStack(spacing: 2) {
            ForEach(state.activeFilters, id: \.self) { filter in
                Text("\(kindName(filter.kind)): \(chipValue(filter, state))")
            }
        }
    }
}

func kindName(_ kind: FilterKind) -> String {
    switch kind {
    case .dates: "Dates"
    case .cabin: "Cabin"
    case .maxpoints: "Points"
    default: "Programs"
    }
}

/** A null date bound shows as the data's own limit (F2). */
private func chipValue(_ filter: ActiveFilter, _ state: ExplorerState) -> String {
    switch filter {
    case let dates as ActiveFilter.Dates:
        dateRange(dates.start ?? state.options.minDate, dates.end ?? state.options.maxDate)
    case let cabin as ActiveFilter.Cabin:
        cabin.cabin
    case let points as ActiveFilter.MaxPoints:
        "≤ \(PointsFormat.shared.compact(points: points.points))"
    case let programs as ActiveFilter.Programs:
        programs.programs.count == 1 ? programs.programs[0] : "\(programs.programs[0]) +\(programs.programs.count - 1)"
    default:
        ""
    }
}

/** Dates show the data's range by default, so the starting state is visible (F2, FILT-10). */
private func defaultChipLabel(_ kind: FilterKind, _ state: ExplorerState) -> String {
    kind == .dates ? dateRange(state.options.minDate, state.options.maxDate) : kindName(kind)
}

private func dateRange(_ start: String?, _ end: String?) -> String {
    guard let start, let end else { return kindName(.dates) }
    return "\(Format.chipDate(start)) – \(Format.chipDate(end))"
}
