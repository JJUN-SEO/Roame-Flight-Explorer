import SwiftUI
import Shared

/** From and To, each with its suggestions and hint (S13, S14, UI_SPEC §1). From's focus may ask for location (D30). */
struct SearchHeader: View {
    @Environment(ExplorerModel.self) private var model
    let onOriginFocused: () -> Void

    var body: some View {
        let state = model.state
        VStack(spacing: 8) {
            SearchField(
                label: "From",
                query: state.originQuery,
                pick: state.originPick,
                suggestions: state.originSuggestions,
                hint: state.originHint,
                onQueryChange: { model.store.setOriginQuery(text: $0) },
                onPick: { model.store.pickOrigin(codes: $0) },
                onFocused: onOriginFocused
            )
            SearchField(
                label: "To",
                query: state.destinationQuery,
                pick: state.destinationPick,
                suggestions: state.destinationSuggestions,
                hint: state.destinationHint,
                onQueryChange: { model.store.setDestinationQuery(text: $0) },
                onPick: { model.store.pickDestination(codes: $0) }
            )
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }
}

private struct SearchField: View {
    let label: String
    let query: String
    let pick: AirportSuggestion?
    let suggestions: [AirportSuggestion]
    let hint: [AirportSuggestion]
    let onQueryChange: (String) -> Void
    let onPick: ([String]) -> Void
    let onFocused: () -> Void

    // What the user typed. The store's copy arrives a moment later, so the field doesn't read it back while
    // typing; it only follows the store when the store empties it (Reset).
    @State private var text: String
    @FocusState private var focused: Bool

    init(
        label: String, query: String, pick: AirportSuggestion?, suggestions: [AirportSuggestion], hint: [AirportSuggestion],
        onQueryChange: @escaping (String) -> Void, onPick: @escaping ([String]) -> Void, onFocused: @escaping () -> Void = {}
    ) {
        self.label = label
        self.query = query
        self.pick = pick
        self.suggestions = suggestions
        self.hint = hint
        self.onQueryChange = onQueryChange
        self.onPick = onPick
        self.onFocused = onFocused
        _text = State(initialValue: query)
    }

    var body: some View {
        // S14: while a pick is active the field shows it; editing the text clears the pick in the store.
        let shown = pick.map(airportLabel) ?? text
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                Text(label)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
                    .frame(width: 40, alignment: .leading)
                TextField("City or airport", text: Binding(get: { shown }, set: { newText in
                    // Losing focus can write the old text back unchanged. Only a real edit may clear a pick (S14).
                    guard newText != text, newText != shown else { return }
                    text = newText
                    onQueryChange(newText)
                }))
                .focused($focused)
                .submitLabel(.search)
                .onSubmit { focused = false }
                .autocorrectionDisabled()
                .accessibilityLabel(label)
                if !shown.isEmpty {
                    Button {
                        text = ""
                        onQueryChange("")
                    } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityLabel("Clear \(label)")
                }
            }
            .padding(.leading, 12)
            .frame(minHeight: 48)
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 16))
            if !hint.isEmpty {
                Text(hintText(hint))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .padding(.leading, 12)
            }
            if focused && !suggestions.isEmpty {
                SuggestionList(suggestions: suggestions) { suggestion in
                    onPick(suggestion.codes)
                    focused = false
                }
            }
        }
        .onChange(of: query) { _, newQuery in
            if newQuery.isEmpty { text = "" }
        }
        .onChange(of: focused) { _, isFocused in
            if isFocused { onFocused() }
        }
    }
}

/** At most 5 rows; the store has already capped and ranked them (S14). */
private struct SuggestionList: View {
    let suggestions: [AirportSuggestion]
    let onTap: (AirportSuggestion) -> Void

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(suggestions.enumerated()), id: \.element) { index, suggestion in
                if index > 0 { Divider() }
                Button { onTap(suggestion) } label: {
                    Text(airportLabel(suggestion))
                        .font(suggestion.isCityRow ? .subheadline.weight(.semibold) : .body)
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        .padding(.horizontal, 16)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
        }
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 16))
        .shadow(color: .black.opacity(0.12), radius: 6, y: 2)
    }
}

/** "Tokyo (HND)", or "Tokyo (all airports)" for a city row (S14). */
func airportLabel(_ suggestion: AirportSuggestion) -> String {
    suggestion.isCityRow ? "\(suggestion.city) (all airports)" : "\(suggestion.city) (\(suggestion.codes.first ?? ""))"
}

/** S13: the first two, then "+N more". */
private func hintText(_ hint: [AirportSuggestion]) -> String {
    let shown = hint.prefix(2).map(airportLabel).joined(separator: ", ")
    let more = hint.count - 2
    return more > 0 ? "Showing \(shown) +\(more) more" : "Showing \(shown)"
}

/** UI_SPEC §4: an empty result that isn't an error, with the active filters and a way back. */
struct FilteredEmpty: View {
    @Environment(ExplorerModel.self) private var model

    var body: some View {
        ContentUnavailableView {
            Label("No flights match your search", systemImage: "magnifyingglass")
        } description: {
            ActiveFilterList(state: model.state)
        } actions: {
            Button("Reset filters") { model.store.resetAll() }
        }
    }
}
