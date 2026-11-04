import SwiftUI
import Shared

/**
 * The active filters as chips, each with ✕ to clear it on its own (C2, C3), then "Clear all", which clears the filters
 * but keeps the search; the same as Android's. Tapping a chip opens the Filters sheet. Nothing shows while no filter
 * is active.
 */
struct ActiveFilterBar: View {
    @Environment(ExplorerModel.self) private var model
    let onOpen: () -> Void

    var body: some View {
        let state = model.state
        if !state.activeFilters.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(state.activeFilters, id: \.self) { filter in
                        let name = kindName(filter.kind)
                        let label = chipValue(filter, state)
                        AppChip(
                            label: label,
                            isActive: true,
                            description: "\(name): \(label), active",
                            onTap: onOpen,
                            onClear: { model.store.clearFilter(kind: filter.kind) },
                            clearDescription: "Clear \(name)"
                        )
                    }
                    TextLink(text: "Clear all") {
                        for filter in state.activeFilters { model.store.clearFilter(kind: filter.kind) }
                    }
                }
                .padding(EdgeInsets(top: 4, leading: 16, bottom: 8, trailing: 8))
            }
        }
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

private func dateRange(_ start: String?, _ end: String?) -> String {
    guard let start, let end else { return kindName(.dates) }
    return "\(Format.chipDate(start)) – \(Format.chipDate(end))"
}
