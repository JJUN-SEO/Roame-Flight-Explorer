import SwiftUI

/*
 * D38: the app's own components, drawn the same way here and in Android's DesignSystem.kt, so the two apps look and
 * behave alike. Sizes are in points here and dp on Android, which match.
 */

/** A Material Symbol from the asset catalog, the same icons as Android's (D37), tinted by the foreground style. */
struct MSIcon: View {
    let name: String
    var size: CGFloat = 24

    var body: some View {
        // Decorative, so VoiceOver skips it rather than reading the asset's name.
        Image(decorative: name)
            .resizable()
            .renderingMode(.template)
            .scaledToFit()
            .frame(width: size, height: size)
    }
}

enum AppTab { case explore, saved, account }

/** Roame's tab bar: white, a thin top border, and the selected tab in pink with a pink line along its top. */
struct AppTabBar: View {
    let selected: AppTab
    let savedCount: Int
    let onSelect: (AppTab) -> Void

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Color.cardBorder).frame(height: 1)
            HStack(spacing: 0) {
                TabItem(label: "Explore", icon: "ms_search", isSelected: selected == .explore, badge: nil) { onSelect(.explore) }
                TabItem(
                    label: "Saved",
                    icon: selected == .saved ? "ms_favorite_filled" : "ms_favorite",
                    isSelected: selected == .saved,
                    badge: savedCount > 0 ? savedCount : nil
                ) { onSelect(.saved) }
                TabItem(label: "Account", icon: "ms_person", isSelected: selected == .account, badge: nil) { onSelect(.account) }
            }
        }
        .background(Color.card.ignoresSafeArea(edges: .bottom))
    }
}

private struct TabItem: View {
    let label: String
    let icon: String
    let isSelected: Bool
    let badge: Int?
    let onTap: () -> Void

    var body: some View {
        let color = isSelected ? Color.accent : Color.text
        Button(action: onTap) {
            VStack(spacing: 0) {
                Rectangle().fill(isSelected ? Color.accent : .clear).frame(height: 2)
                MSIcon(name: icon)
                    .overlay(alignment: .topTrailing) {
                        if let badge { CountBadge(count: badge).offset(x: 10, y: -6) }
                    }
                    .padding(.top, 8)
                Text(label).font(.appLabelSmall).padding(.top, 4).padding(.bottom, 8)
            }
            .foregroundStyle(color)
            .frame(maxWidth: .infinity)
            .contentShape(.rect)
            // At the accessibility sizes the labels broke mid-word ("Explor / e"), so they stop growing there, as
            // Apple's tab bars do, and a long press shows the tab in large type instead (Step 6.3e).
            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
        }
        .buttonStyle(.plain)
        .accessibilityShowsLargeContentViewer {
            MSIcon(name: icon)
            Text(label)
        }
        // The badge's count is part of what VoiceOver reads, in the same words as Android's tab_with_count.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(badge.map { "\(label), \(Format.count($0)) saved \($0 == 1 ? "flight" : "flights")" } ?? label)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

struct CountBadge: View {
    let count: Int

    var body: some View {
        Text(Format.count(count))
            .font(.appLabelTiny)
            .foregroundStyle(.white)
            .padding(.horizontal, 4)
            .frame(minWidth: 16, minHeight: 16)
            .background(Color.brand, in: .capsule)
    }
}

/** Roame's segmented control: a grey track with the chosen option as a pink pill. */
struct SegmentedControl: View {
    let options: [String]
    let selectedIndex: Int
    let onSelect: (Int) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(options.enumerated()), id: \.offset) { index, label in
                let isSelected = index == selectedIndex
                Button { onSelect(index) } label: {
                    Text(label)
                        .font(isSelected ? .appButton : .appLabel)
                        .foregroundStyle(isSelected ? Color.onAccent : Color.secondaryText)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(isSelected ? Color.accent : .clear, in: .rect(cornerRadius: 17))
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
            }
        }
        .padding(3)
        .frame(height: 40)
        .background(Color.track, in: .rect(cornerRadius: 20))
    }
}

/** A filter chip: white with a border, or pink when active, with ✕ to clear it on its own. */
struct AppChip: View {
    let label: String
    let isActive: Bool
    let description: String
    let onTap: () -> Void
    var onClear: (() -> Void)?
    var clearDescription: String?

    var body: some View {
        HStack(spacing: 4) {
            Text(label).font(.appLabel).lineLimit(1)
            if isActive, let onClear {
                Button(action: onClear) {
                    MSIcon(name: "ms_close", size: 18).frame(width: 24, height: 24).contentShape(.circle)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(clearDescription ?? "Clear")
            }
        }
        .foregroundStyle(isActive ? Color.onAccent : Color.text)
        .padding(.leading, 14)
        .padding(.trailing, isActive && onClear != nil ? 6 : 14)
        .frame(height: 36)
        .background(isActive ? Color.accent : Color.card, in: .capsule)
        .overlay(Capsule().strokeBorder(isActive ? .clear : Color.outline))
        .contentShape(.capsule)
        .onTapGesture(perform: onTap)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(description)
        .accessibilityAddTraits(.isButton)
    }
}

/** The main action: a pink pill with white text. */
struct PrimaryButton<Leading: View>: View {
    let text: String
    var isEnabled = true
    var fillsWidth = false
    let action: () -> Void
    @ViewBuilder var leading: () -> Leading

    var body: some View {
        PillButton(text: text, isEnabled: isEnabled, fillsWidth: fillsWidth, background: .accent, content: .onAccent, action: action, leading: leading)
    }
}

extension PrimaryButton where Leading == EmptyView {
    init(text: String, isEnabled: Bool = true, fillsWidth: Bool = false, action: @escaping () -> Void) {
        self.init(text: text, isEnabled: isEnabled, fillsWidth: fillsWidth, action: action) { EmptyView() }
    }
}

/** A secondary action, such as Remove: pink text on a light pink pill. */
struct TintedButton<Leading: View>: View {
    let text: String
    var isEnabled = true
    let action: () -> Void
    @ViewBuilder var leading: () -> Leading

    var body: some View {
        PillButton(text: text, isEnabled: isEnabled, fillsWidth: false, background: .selectedCard, content: .accent, action: action, leading: leading)
    }
}

private struct PillButton<Leading: View>: View {
    let text: String
    let isEnabled: Bool
    let fillsWidth: Bool
    let background: Color
    let content: Color
    let action: () -> Void
    @ViewBuilder var leading: () -> Leading

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                leading()
                Text(text).font(.appButton)
            }
            .foregroundStyle(content)
            .padding(.horizontal, 18)
            .frame(maxWidth: fillsWidth ? .infinity : nil, minHeight: 44)
            .background(background.opacity(isEnabled ? 1 : 0.6), in: .capsule)
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }
}

/** Pink text that acts as a button, such as Reset, with a 44 pt touch target. */
struct TextLink: View {
    let text: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text).font(.appButton).foregroundStyle(Color.accent).padding(.horizontal, 8).frame(minHeight: 44).contentShape(.rect)
        }
        .buttonStyle(.plain)
    }
}

/** A message at the top of the screen, with an optional action such as Retry. */
struct AppToast: View {
    let text: String
    var action: String?
    var onAction: () -> Void = {}

    var body: some View {
        HStack {
            Text(text).font(.appBody).foregroundStyle(.white).padding(.vertical, 12)
            Spacer()
            if let action {
                Button(action: onAction) {
                    Text(action).font(.appButton).foregroundStyle(Color.toastAction).padding(.horizontal, 12).frame(minHeight: 44)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.leading, 16)
        .padding(.trailing, 4)
        .frame(minHeight: 48)
        .background(Color.toast, in: .rect(cornerRadius: 12))
        .shadow(color: .black.opacity(0.25), radius: 6, y: 2)
    }
}

/** An empty or error screen: an icon in a pink circle, a title, a line of explanation, and an optional action. */
struct EmptyState<Action: View>: View {
    let icon: String
    let title: String
    var message: String?
    @ViewBuilder var action: () -> Action

    var body: some View {
        VStack(spacing: 0) {
            MSIcon(name: icon, size: 32)
                .foregroundStyle(Color.accent)
                .frame(width: 72, height: 72)
                .background(Color.selectedCard, in: .circle)
            Text(title).font(.appTitle).multilineTextAlignment(.center).padding(.top, 16)
            if let message {
                Text(message).font(.appBody).foregroundStyle(Color.secondaryText).multilineTextAlignment(.center).padding(.top, 6)
            }
            action().padding(.top, 16)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

extension EmptyState where Action == EmptyView {
    init(icon: String, title: String, message: String?) {
        self.init(icon: icon, title: title, message: message) { EmptyView() }
    }
}

/** A tab's large title, like Roame's. */
struct PageTitle: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.appDisplay)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(EdgeInsets(top: 16, leading: 16, bottom: 8, trailing: 16))
            .accessibilityAddTraits(.isHeader)
    }
}

/** A pushed page's header: back on the left, or ✕ for a page that closes, and the title centred. */
struct SubpageTopBar: View {
    let title: String
    var closes = false
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { MSIcon(name: closes ? "ms_close" : "ms_arrow_back").frame(width: 48, height: 48).contentShape(.rect) }
                    .buttonStyle(.plain)
                    .accessibilityLabel(closes ? "Close" : "Back")
                Text(title).font(.appTitle).frame(maxWidth: .infinity)
                Color.clear.frame(width: 48, height: 48)
            }
            .padding(.horizontal, 4)
            .frame(height: 56)
            Rectangle().fill(Color.cardBorder).frame(height: 1)
        }
    }
}

/** A Roame list row: an icon, a label and a chevron. */
struct ListRow: View {
    let icon: String
    let label: String
    var showChevron = true
    var tint: Color = .text
    let action: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Button(action: action) {
                HStack(spacing: 16) {
                    MSIcon(name: icon)
                    Text(label).font(.appBodyLarge).frame(maxWidth: .infinity, alignment: .leading)
                    if showChevron { MSIcon(name: "ms_chevron_right").foregroundStyle(Color.secondaryText) }
                }
                .foregroundStyle(tint)
                .padding(.horizontal, 16)
                .frame(minHeight: 56)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            Rectangle().fill(Color.cardBorder).frame(height: 1).padding(.leading, 56)
        }
    }
}

/** One choice in a sheet: its label, and a pink check when chosen. */
struct OptionRow: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Button(action: action) {
                HStack {
                    Text(label).font(.appBodyLarge).foregroundStyle(Color.text).frame(maxWidth: .infinity, alignment: .leading)
                    if isSelected { MSIcon(name: "ms_check").foregroundStyle(Color.accent) }
                }
                .padding(.horizontal, 16)
                .frame(minHeight: 52)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(isSelected ? .isSelected : [])
            Rectangle().fill(Color.cardBorder).frame(height: 1).padding(.leading, 16)
        }
    }
}
