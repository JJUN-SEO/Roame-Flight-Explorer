import SwiftUI

/** Colours with no system role (UI_SPEC §6). The system colours adapt to dark mode by themselves. */
extension Color {
    /**
     * The accent, the same as Android's primary: deep blue in light mode, and Android's light blue in dark
     * mode, where the deep blue is too dark to read as text (about 1.8:1 on a dark sheet).
     */
    static let accent = adaptive(light: 0x1E4FA3, dark: 0xAFC6FF)

    /** Text and icons on an accent fill, the same as Android's onPrimary. */
    static let onAccent = adaptive(light: 0xFFFFFF, dark: 0x002D6C)

    static let saved = Color.pink

    /** Grey Economy, teal Premium Economy, indigo Business. */
    static func cabin(_ cabin: String) -> Color {
        switch cabin {
        case "Premium Economy": .teal
        case "Business": .indigo
        default: .gray
        }
    }

    private static func adaptive(light: Int, dark: Int) -> Color {
        Color(UIColor { traits in rgb(traits.userInterfaceStyle == .dark ? dark : light) })
    }

    private static func rgb(_ hex: Int) -> UIColor {
        UIColor(red: CGFloat(hex >> 16 & 0xFF) / 255, green: CGFloat(hex >> 8 & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}
