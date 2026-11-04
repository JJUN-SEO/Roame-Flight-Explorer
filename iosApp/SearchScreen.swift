import SwiftUI
import Shared

private enum SearchStep { case form, from, to, dates }

/**
 * The full-screen search, like Roame's (Step 6.3c, UI_SPEC §2.0b), drawn the same as Android's SearchScreen (D38): From
 * and To, with a swap button (S15), the dates on the shared calendar (K1–K5) and the cabin, then "Search flights".
 * Every choice reaches the store as it's made, so closing keeps it.
 */
struct SearchScreen: View {
    @Environment(ExplorerModel.self) private var model
    let onClose: () -> Void
    let onSearch: () -> Void
    @State private var step = SearchStep.form
    @State private var cabinOpen = false
    /** K3: a start whose end hasn't been picked yet. "Any dates" clears it with the filter. */
    @State private var awaitingStart: String?

    var body: some View {
        let state = model.state
        Group {
            switch step {
            case .form:
                VStack(spacing: 0) {
                    SubpageTopBar(title: "Search flights", closes: true, onBack: onClose)
                    ScrollView {
                        VStack(spacing: 24) {
                            SearchForm(state: state, onSwap: { model.store.swapSides() }, onOpen: { step = $0 }, onCabin: { cabinOpen = true })
                            PrimaryButton(text: "Search flights", fillsWidth: true, action: onSearch)
                        }
                        .padding(16)
                    }
                }
            case .from:
                AirportStep(title: "From", onDone: { step = .form }) {
                    SearchField(
                        label: "From",
                        query: state.originQuery,
                        pick: state.originPick,
                        suggestions: state.originSuggestions,
                        hint: state.originHint,
                        onQueryChange: { model.store.setOriginQuery(text: $0) },
                        onPick: { codes in
                            model.store.pickOrigin(codes: codes)
                            step = .form
                        },
                        onSubmit: { step = .form },
                        autoFocus: true
                    )
                }
            case .to:
                AirportStep(title: "To", onDone: { step = .form }) {
                    SearchField(
                        label: "To",
                        query: state.destinationQuery,
                        pick: state.destinationPick,
                        suggestions: state.destinationSuggestions,
                        hint: state.destinationHint,
                        onQueryChange: { model.store.setDestinationQuery(text: $0) },
                        onPick: { codes in
                            model.store.pickDestination(codes: codes)
                            step = .form
                        },
                        onSubmit: { step = .form },
                        autoFocus: true
                    )
                }
            case .dates:
                VStack(spacing: 0) {
                    SubpageTopBar(title: "Dates") { step = .form }
                    DateRangeCalendar(awaitingStart: $awaitingStart).frame(maxHeight: .infinity, alignment: .top)
                    HStack {
                        TextLink(text: "Any dates") {
                            model.store.clearFilter(kind: .dates)
                            awaitingStart = nil
                        }
                        Spacer()
                        PrimaryButton(text: "Done") { step = .form }
                    }
                    .padding(EdgeInsets(top: 8, leading: 8, bottom: 16, trailing: 16))
                }
            }
        }
        .sheet(isPresented: $cabinOpen) { FilterSheet(kind: .cabin) }
    }
}

/** Roame's search card: four rows in one bordered card, with the swap button on the line between From and To. */
private struct SearchForm: View {
    let state: ExplorerState
    let onSwap: () -> Void
    let onOpen: (SearchStep) -> Void
    let onCabin: () -> Void

    var body: some View {
        let from = state.originPick.map(airportLabel) ?? nonBlank(state.originQuery)
        let to = state.destinationPick.map(airportLabel) ?? nonBlank(state.destinationQuery)
        let start = state.filters.dateStart ?? state.options.minDate
        let end = state.filters.dateEnd ?? state.options.maxDate
        let dates = if let start, let end { "\(Format.chipDate(start)) – \(Format.chipDate(end))" } else { "Dates" }
        VStack(spacing: 0) {
            ZStack(alignment: .trailing) {
                VStack(spacing: 0) {
                    FieldRow(icon: "ms_flight_takeoff", label: "From", value: from) { onOpen(.from) }
                    divider
                    FieldRow(icon: "ms_flight_land", label: "To", value: to) { onOpen(.to) }
                }
                SwapButton(action: onSwap).padding(.trailing, 16)
            }
            divider
            FieldRow(icon: "ms_calendar", label: "Dates", value: dates) { onOpen(.dates) }
            divider
            FieldRow(icon: "ms_seat", label: "Cabin", value: state.filters.cabin ?? "Any cabin", trailing: "ms_arrow_down", action: onCabin)
        }
        .background(Color.card, in: .rect(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.cardBorder))
    }

    private var divider: some View {
        Rectangle().fill(Color.cardBorder).frame(height: 1)
    }

    private func nonBlank(_ text: String) -> String? {
        text.trimmingCharacters(in: .whitespaces).isEmpty ? nil : text
    }
}

/** A field's name over its value, or the placeholder in grey; one button for VoiceOver. */
private struct FieldRow: View {
    let icon: String
    let label: String
    let value: String?
    var trailing: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                MSIcon(name: icon)
                VStack(alignment: .leading, spacing: 0) {
                    Text(label).font(.appLabelSmall).foregroundStyle(Color.secondaryText)
                    Text(value ?? "City or airport")
                        .font(.appBodyLarge)
                        .foregroundStyle(value == nil ? Color.secondaryText : Color.text)
                        .lineLimit(2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let trailing { MSIcon(name: trailing).foregroundStyle(Color.secondaryText) }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(minHeight: 72)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label), \(value ?? "City or airport")")
        .accessibilityAddTraits(.isButton)
    }
}

/** S15: a grey circle with ⇅, sitting on the line between From and To, as in Roame's search. */
private struct SwapButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            MSIcon(name: "ms_swap_vert", size: 20)
                .frame(width: 40, height: 40)
                .background(Color.track, in: .circle)
                .overlay(Circle().strokeBorder(Color.cardBorder))
                .frame(width: 48, height: 48)
                .contentShape(.circle)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Swap From and To")
    }
}

/** Picking From or To: the field, focused, with its suggestions under it. A pick or the Search key returns to the form. */
private struct AirportStep<Field: View>: View {
    let title: String
    let onDone: () -> Void
    @ViewBuilder let field: () -> Field

    var body: some View {
        VStack(spacing: 0) {
            SubpageTopBar(title: title, onBack: onDone)
            field().padding(.horizontal, 16).padding(.top, 8)
            Spacer(minLength: 0)
        }
    }
}
