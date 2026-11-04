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
    /** K3: a start whose end hasn't been picked yet. "Any dates" clears it with the filter. */
    @State private var awaitingStart: String?

    var body: some View {
        let state = model.state
        // The same layout as Android's sheet (D38): the filter's name centred, the choices, then the buttons.
        VStack(spacing: 0) {
            Text(kindName(kind))
                .font(.appTitle)
                .frame(maxWidth: .infinity)
                .padding(.top, 20)
                .padding(.bottom, 12)
                .accessibilityAddTraits(.isHeader)
            if kind == .dates {
                // The calendar scrolls by itself.
                DateRangeCalendar(awaitingStart: $awaitingStart)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        switch kind {
                        case .cabin: CabinBody(state: state, store: model.store)
                        case .maxpoints: PointsBody(state: state, store: model.store)
                        default: ProgramsBody(state: state, store: model.store)
                        }
                    }
                }
            }
            HStack {
                TextLink(text: clearLabel) {
                    model.store.clearFilter(kind: kind)
                    awaitingStart = nil
                }
                Spacer()
                PrimaryButton(text: "Show \(flightCount(Int(state.resultCount)))") { dismiss() }
            }
            .padding(EdgeInsets(top: 16, leading: 8, bottom: 16, trailing: 16))
        }
        .presentationDetents(kind == .dates ? [.large] : [.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.background)
        .foregroundStyle(Color.text)
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
 * Every filter in one sheet (Step 6.3d, UI_SPEC §2.4), drawn the same as Android's FiltersSheet: the dates as a row that
 * opens the calendar inside the sheet, then the cabin, points and programs. Choices apply as they're made, so "Show N
 * flights" shows the real count and just closes the sheet; "Clear all" clears the filters and keeps the search.
 */
struct FiltersSheet: View {
    @Environment(ExplorerModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var datesOpen = false
    /** K3: a start whose end hasn't been picked yet. "Any dates" clears it with the filter. */
    @State private var awaitingStart: String?

    var body: some View {
        let state = model.state
        VStack(spacing: 0) {
            if datesOpen {
                SubpageTopBar(title: "Dates") { datesOpen = false }
                DateRangeCalendar(awaitingStart: $awaitingStart)
                HStack {
                    TextLink(text: "Any dates") {
                        model.store.clearFilter(kind: .dates)
                        awaitingStart = nil
                    }
                    Spacer()
                    PrimaryButton(text: "Done") { datesOpen = false }
                }
                .padding(EdgeInsets(top: 16, leading: 8, bottom: 16, trailing: 16))
            } else {
                Text("Filters")
                    .font(.appTitle)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 20)
                    .padding(.bottom, 12)
                    .accessibilityAddTraits(.isHeader)
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        DatesRow(state: state) { datesOpen = true }
                        SectionLabel(text: "Cabin")
                        CabinBody(state: state, store: model.store)
                        SectionLabel(text: "Points")
                        PointsBody(state: state, store: model.store)
                        SectionLabel(text: "Programs")
                        ProgramsBody(state: state, store: model.store)
                    }
                }
                HStack {
                    TextLink(text: "Clear all") {
                        for filter in state.activeFilters { model.store.clearFilter(kind: filter.kind) }
                        awaitingStart = nil
                    }
                    Spacer()
                    PrimaryButton(text: "Show \(flightCount(Int(state.resultCount)))") { dismiss() }
                }
                .padding(EdgeInsets(top: 16, leading: 8, bottom: 16, trailing: 16))
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.background)
        .foregroundStyle(Color.text)
    }
}

/** The dates in one row, the data's range by default (F2); tapping it opens the calendar. */
private struct DatesRow: View {
    let state: ExplorerState
    let action: () -> Void

    var body: some View {
        let start = state.filters.dateStart ?? state.options.minDate
        let end = state.filters.dateEnd ?? state.options.maxDate
        let value = if let start, let end { "\(Format.chipDate(start)) – \(Format.chipDate(end))" } else { "Dates" }
        VStack(spacing: 0) {
            Button(action: action) {
                HStack {
                    Text("Dates").font(.appBodyLarge).frame(maxWidth: .infinity, alignment: .leading)
                    Text(value).font(.appBodyLarge).foregroundStyle(Color.secondaryText)
                    MSIcon(name: "ms_chevron_right").foregroundStyle(Color.secondaryText)
                }
                .padding(.horizontal, 16)
                .frame(minHeight: 52)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Dates, \(value)")
            .accessibilityAddTraits(.isButton)
            Rectangle().fill(Color.cardBorder).frame(height: 1).padding(.leading, 16)
        }
    }
}

private struct SectionLabel: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.appTitleSmall)
            .foregroundStyle(Color.secondaryText)
            .padding(EdgeInsets(top: 20, leading: 16, bottom: 4, trailing: 16))
            .accessibilityAddTraits(.isHeader)
    }
}

/** Single choice (F5), in rank order from the data. */
private struct CabinBody: View {
    let state: ExplorerState
    let store: ExplorerStoreIos

    var body: some View {
        VStack(spacing: 0) {
            OptionRow(label: "Any", isSelected: state.filters.cabin == nil) { store.setCabin(cabin: nil) }
            ForEach(state.options.cabins, id: \.self) { cabin in
                OptionRow(label: cabin, isSelected: state.filters.cabin == cabin) { store.setCabin(cabin: cabin) }
            }
        }
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
            VStack(alignment: .leading, spacing: 8) {
                Text(points >= max ? "Any" : "Up to \(PointsFormat.shared.full(points: Int32(points)))")
                    .font(.appTitle.monospacedDigit())
                Slider(value: Binding(get: { current }, set: { newValue in
                    value = newValue
                    store.setMaxPoints(points: Int32(newValue))
                }), in: Double(min)...Double(max), step: 500)
                .accessibilityLabel("Maximum points")
            }
            .padding(.horizontal, 16)
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
        VStack(spacing: 0) {
            ForEach(state.options.programs, id: \.self) { program in
                OptionRow(label: program, isSelected: state.filters.programs.contains(program)) {
                    store.toggleProgram(name: program)
                }
            }
        }
    }
}
