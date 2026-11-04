import SwiftUI

/** D39: the Account tab's Settings, the same three choices as Android's, saved under the same name. */
enum Appearance: String, CaseIterable {
    case system = "System", light = "Light", dark = "Dark"

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

private enum AccountPage { case settings, profile, privacy, info }

/**
 * D39: a placeholder like Roame's account tab. The app has no accounts, so only Settings works (light, dark or follow
 * the system); the other rows say what they would do. The same rows and pages as Android's AccountScreen.
 */
struct AccountScreen: View {
    @AppStorage("appearance") private var appearance = Appearance.system.rawValue
    @State private var page: AccountPage?
    let onToast: (String) -> Void

    var body: some View {
        switch page {
        case nil:
            home
        case .settings:
            Subpage(title: "Settings", onBack: { page = nil }) {
                SectionLabel(text: "Appearance")
                SegmentedControl(
                    options: Appearance.allCases.map(\.rawValue),
                    selectedIndex: Appearance.allCases.firstIndex { $0.rawValue == appearance } ?? 0,
                    onSelect: { appearance = Appearance.allCases[$0].rawValue }
                )
                .padding(.horizontal, 16)
                Note(text: "System follows your phone's light or dark setting.")
            }
        case .profile:
            Subpage(title: "Change profile", onBack: { page = nil }) {
                Note(text: "Profiles aren't part of this demo. There's no sign-in, so there's nothing to change yet.")
            }
        case .privacy:
            Subpage(title: "Privacy", onBack: { page = nil }) {
                Paragraph(title: "Your location", text: "Only your approximate location, and only if you allow it. It's used on this phone to put nearby airports first, and it's never sent anywhere.")
                Paragraph(title: "Your flights", text: "The flights you last downloaded and the flights you save stay on this phone. There's no account and no tracking.")
                Paragraph(title: "Maps", text: "Maps come from Apple Maps, under Apple's privacy policy.")
            }
        case .info:
            Subpage(title: "Info", onBack: { page = nil }) {
                Paragraph(title: "Flight Explorer", text: "Version \(Self.version)")
                Paragraph(title: "About", text: "A take-home project inspired by Roame, built natively for Android and iOS on one Kotlin Multiplatform core. It isn't affiliated with Roame or with the loyalty programs shown.")
                Paragraph(title: "Credits", text: "Icons: Material Symbols by Google (Apache 2.0). Font: Inter (SIL Open Font License 1.1). Program logos: Wikimedia Commons, public domain; trademarks of their owners. Map: Apple Maps.")
            }
        }
    }

    private var home: some View {
        ScrollView {
            VStack(spacing: 0) {
                PageTitle(text: "Account")
                ProfileHeader { page = .profile }
                    .padding(.bottom, 8)
                ListRow(icon: "ms_settings", label: "Settings") { page = .settings }
                ListRow(icon: "ms_person", label: "Change profile") { page = .profile }
                ListRow(icon: "ms_lock", label: "Privacy") { page = .privacy }
                ListRow(icon: "ms_info", label: "Info") { page = .info }
                ListRow(icon: "ms_logout", label: "Log out", showChevron: false, tint: .accent) {
                    onToast("There's no account to log out of in this demo.")
                }
            }
        }
    }

    private static var version: String {
        let info = Bundle.main.infoDictionary
        return "\(info?["CFBundleShortVersionString"] as? String ?? "?") (\(info?["CFBundleVersion"] as? String ?? "?"))"
    }
}

/** Roame's profile header: a gradient avatar with an initial, the name, and "View profile". A placeholder here. */
private struct ProfileHeader: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 16) {
                Text("G")
                    .font(.appDisplay)
                    .foregroundStyle(.white)
                    .frame(width: 64, height: 64)
                    .background(LinearGradient(colors: [.gradientStart, .gradientEnd], startPoint: .topLeading, endPoint: .bottomTrailing), in: .circle)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Guest").font(.appTitleLarge)
                    Text("View profile").font(.appBody).foregroundStyle(Color.secondaryText)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                MSIcon(name: "ms_chevron_right").foregroundStyle(Color.secondaryText)
            }
            .foregroundStyle(Color.text)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }
}

private struct Subpage<Content: View>: View {
    let title: String
    let onBack: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: 0) {
            SubpageTopBar(title: title, onBack: onBack)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) { content() }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 16)
            }
        }
    }
}

private struct SectionLabel: View {
    let text: String

    var body: some View {
        Text(text).font(.appTitleSmall).foregroundStyle(Color.secondaryText).padding(.horizontal, 16).accessibilityAddTraits(.isHeader)
    }
}

private struct Note: View {
    let text: String

    var body: some View {
        Text(text).font(.appBody).foregroundStyle(Color.secondaryText).padding(.horizontal, 16)
    }
}

private struct Paragraph: View {
    let title: String
    let text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.appTitle).accessibilityAddTraits(.isHeader)
            Text(text).font(.appBody).foregroundStyle(Color.secondaryText)
        }
        .padding(.horizontal, 16)
    }
}
