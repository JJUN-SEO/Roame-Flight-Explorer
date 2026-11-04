import SwiftUI
import UIKit

/**
 * Roame's look (Step 6.3, owner's design), the same values as Android's theme: a pink accent on warm white, sampled
 * from Roame's app (UI_SPEC §6). The system's own colours are used only where nothing here fits.
 */
extension Color {
    /**
     * The accent for controls and text. In light mode it's a little deeper than Roame's #EA4167, so pink text reads
     * at 4.5:1; in dark mode it's Android's light pink.
     */
    static let accent = adaptive(light: 0xD63A60, dark: 0xFF7A98)

    /** Text and icons on an accent fill. */
    static let onAccent = adaptive(light: 0xFFFFFF, dark: 0x4A0016)

    /** Roame's own pink, for graphics: the route, the plane and the hearts. */
    static let brand = adaptive(light: 0xEA4167, dark: 0xFF7A98)

    /** The warm white behind everything, and the cards on it. */
    static let background = adaptive(light: 0xFEFCF6, dark: 0x151312)
    static let card = adaptive(light: 0xFFFFFF, dark: 0x211E1C)
    static let cardBorder = adaptive(light: 0xE8E5E0, dark: 0x3A3532)
    static let selectedCard = adaptive(light: 0xFDE3E9, dark: 0x5C1A2C)
    static let secondaryText = adaptive(light: 0x6B6661, dark: 0xBDB6AF)

    static let saved = brand

    /** A seat class's icon and text colours. The text is deep enough to read at 4.5:1, where Roame's gold isn't. */
    static func cabin(_ cabin: String) -> (icon: Color, text: Color) {
        switch cabin {
        case "First":
            let text = adaptive(light: 0xD81B50, dark: 0xFF6F93)
            return (adaptive(light: 0xFF2765, dark: 0xFF6F93), text)
        case "Business": return (adaptive(light: 0xEC9D26, dark: 0xF2B24A), adaptive(light: 0xA65F00, dark: 0xF2B24A))
        case "Premium Economy":
            let teal = adaptive(light: 0x007986, dark: 0x4CC3CF)
            return (teal, teal)
        default:
            let dark = adaptive(light: 0x312D2A, dark: 0xE8E2DC)
            return (dark, dark)
        }
    }

    private static func adaptive(light: Int, dark: Int) -> Color {
        Color(UIColor { traits in rgb(traits.userInterfaceStyle == .dark ? dark : light) })
    }

    private static func rgb(_ hex: Int) -> UIColor {
        UIColor(red: CGFloat(hex >> 16 & 0xFF) / 255, green: CGFloat(hex >> 8 & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}
