import SwiftUI
import Shared

/** One search field with its hint and suggestions. `onSubmit` runs on the keyboard's Search key, after the field lets go. */
struct SearchField: View {
    let label: String
    let query: String
    let pick: AirportSuggestion?
    let suggestions: [AirportSuggestion]
    let hint: [AirportSuggestion]
    let onQueryChange: (String) -> Void
    let onPick: ([String]) -> Void
    let onSubmit: () -> Void
    let autoFocus: Bool

    // What the user typed. The store's copy arrives a moment later, so the field doesn't read back its own echoes,
    // or a fast typist would lose letters. A text it didn't send is a change from elsewhere (Reset, a deal, a swap),
    // and the field takes it.
    @State private var text: String
    @State private var echoes = Echoes()
    @FocusState private var focused: Bool

    init(
        label: String, query: String, pick: AirportSuggestion?, suggestions: [AirportSuggestion], hint: [AirportSuggestion],
        onQueryChange: @escaping (String) -> Void, onPick: @escaping ([String]) -> Void,
        onSubmit: @escaping () -> Void = {}, autoFocus: Bool = false
    ) {
        self.label = label
        self.query = query
        self.pick = pick
        self.suggestions = suggestions
        self.hint = hint
        self.onQueryChange = onQueryChange
        self.onPick = onPick
        self.onSubmit = onSubmit
        self.autoFocus = autoFocus
        _text = State(initialValue: query)
    }

    var body: some View {
        // S14: while a pick is active the field shows it; editing the text clears the pick in the store.
        let shown = pick.map(airportLabel) ?? text
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                MSIcon(name: "ms_search")
                    .foregroundStyle(Color.secondaryText)
                Text(label)
                    .font(.appLabel)
                    .foregroundStyle(Color.secondaryText)
                    .frame(width: 40, alignment: .leading)
                TextField("City or airport", text: Binding(get: { shown }, set: { newText in
                    // Losing focus can write the old text back unchanged. Only a real edit may clear a pick (S14).
                    guard newText != text, newText != shown else { return }
                    text = newText
                    echoes.sent(newText)
                    onQueryChange(newText)
                }))
                .focused($focused)
                .submitLabel(.search)
                .onSubmit {
                    focused = false
                    onSubmit()
                }
                .autocorrectionDisabled()
                .font(.appBodyLarge)
                .accessibilityLabel(label)
                if !shown.isEmpty {
                    Button {
                        text = ""
                        echoes.sent("")
                        onQueryChange("")
                    } label: {
                        MSIcon(name: "ms_close", size: 20).foregroundStyle(Color.secondaryText)
                    }
                    .buttonStyle(.plain)
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityLabel("Clear \(label)")
                }
            }
            .padding(.leading, 16)
            .frame(minHeight: 48)
            // Roame's search pill: white, fully rounded, with a thin warm border that turns pink while typing, as on Android.
            .background(Color.card, in: .capsule)
            .overlay(Capsule().strokeBorder(focused ? Color.accent : Color.cardBorder))
            if !hint.isEmpty {
                Text(hintText(hint))
                    .font(.appLabelSmall)
                    .foregroundStyle(Color.secondaryText)
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
            if let outside = echoes.arrived(newQuery) { text = outside }
        }
        // Shortly after the page appears, or the keyboard may not come up.
        .task {
            guard autoFocus else { return }
            try? await Task.sleep(for: .milliseconds(250))
            focused = true
        }
    }
}

/**
 * Tells the store's echoes of what the field typed from changes made elsewhere, as Android's does. Echoes arrive late
 * and in order, so until the store shows the last text sent, any earlier text sent is an echo; anything else is an
 * outside change.
 */
private final class Echoes {
    private var pending: Set<String> = []
    private var last: String?

    func sent(_ text: String) {
        pending.insert(text)
        last = text
    }

    /** The store's text, or nil when it's only an echo the field already shows. */
    func arrived(_ query: String) -> String? {
        if query == last {
            pending.removeAll()
            last = nil
            return nil
        }
        if pending.contains(query) { return nil }
        pending.removeAll()
        last = nil
        return query
    }
}

/** At most 5 rows; the store has already capped and ranked them (S14). */
private struct SuggestionList: View {
    let suggestions: [AirportSuggestion]
    let onTap: (AirportSuggestion) -> Void

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(suggestions.enumerated()), id: \.element) { index, suggestion in
                if index > 0 { Rectangle().fill(Color.cardBorder).frame(height: 1) }
                Button { onTap(suggestion) } label: {
                    Text(airportLabel(suggestion))
                        .font(suggestion.isCityRow ? .appTitleSmall : .appBodyLarge)
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        .padding(.horizontal, 16)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
        }
        .background(Color.card, in: .rect(cornerRadius: 16))
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
        // The shared empty state, as on Android (D38).
        EmptyState(icon: "ms_search_off", title: "No flights match your search", message: nil) {
            VStack(spacing: 16) {
                ActiveFilterList(state: model.state).font(.appBody).foregroundStyle(Color.secondaryText)
                PrimaryButton(text: "Reset filters") { model.store.resetAll() }
            }
        }
    }
}
