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

    /** Roame's warm near-black text, Android's onSurface, in place of the system's pure black and white. */
    static let text = adaptive(light: 0x302D2A, dark: 0xF2EEE9)

    /** The warm white behind everything, and the cards on it. */
    static let background = adaptive(light: 0xFEFCF6, dark: 0x151312)
    static let card = adaptive(light: 0xFFFFFF, dark: 0x211E1C)
    static let cardBorder = adaptive(light: 0xE8E5E0, dark: 0x3A3532)
    static let selectedCard = adaptive(light: 0xFDE3E9, dark: 0x5C1A2C)
    static let secondaryText = adaptive(light: 0x6B6661, dark: 0xBDB6AF)

    static let saved = brand

    /** The segmented control's track, chip borders, and the toast (D38, the same as Android's). */
    static let track = adaptive(light: 0xF2F0EA, dark: 0x34302E)
    static let outline = adaptive(light: 0xD6D3CE, dark: 0x5A5450)
    static let toast = Color(red: 0x2B / 255, green: 0x2B / 255, blue: 0x2B / 255)
    static let toastAction = Color(red: 0xFF / 255, green: 0xB3 / 255, blue: 0xC4 / 255)
    static let gradientStart = adaptive(light: 0xF2BD93, dark: 0xC98E68)
    static let gradientEnd = adaptive(light: 0xDA7083, dark: 0xB4506A)

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

/**
 * D38, D40: one type scale on both platforms, in Inter, at the same sizes as Android's (UI_SPEC §6). Each scales with
 * Dynamic Type from the text style it stands in for.
 */
extension Font {
    static let appDisplay = inter("Inter-Bold", 28, .largeTitle)
    static let appPoints = inter("Inter-Bold", 24, .title2)
    static let appTitleLarge = inter("Inter-SemiBold", 22, .title2)
    static let appTitle = inter("Inter-SemiBold", 16, .headline)
    static let appTitleSmall = inter("Inter-SemiBold", 14, .subheadline)
    static let appBodyLarge = inter("Inter-Regular", 16, .body)
    static let appBody = inter("Inter-Regular", 14, .subheadline)
    static let appCaption = inter("Inter-Regular", 12, .caption)
    static let appLabel = inter("Inter-Medium", 14, .subheadline)
    static let appButton = inter("Inter-SemiBold", 14, .subheadline)
    static let appLabelSmall = inter("Inter-Medium", 12, .caption)
    static let appLabelTiny = inter("Inter-Bold", 11, .caption2)
    /** The program badge and the map's pills. */
    static let appBadge = inter("Inter-Bold", 12, .caption)

    private static func inter(_ name: String, _ size: CGFloat, _ style: Font.TextStyle) -> Font {
        .custom(name, size: size, relativeTo: style)
    }
}
