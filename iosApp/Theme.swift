import SwiftUI

/** Colours with no system role (UI_SPEC §6). The system colours adapt to dark mode by themselves. */
extension Color {
    /** The deep blue accent, the same as Android's primary. */
    static let accent = Color(red: 0x1E / 255, green: 0x4F / 255, blue: 0xA3 / 255)

    static let saved = Color.pink

    /** Grey Economy, teal Premium Economy, indigo Business. */
    static func cabin(_ cabin: String) -> Color {
        switch cabin {
        case "Premium Economy": .teal
        case "Business": .indigo
        default: .gray
        }
    }
}
