import SwiftUI
import Shared

/**
 * One result (UI_SPEC §2.1), as a white card like Roame's. Tapping it selects it; the heart saves or removes it (V7).
 * At the accessibility text sizes its parts stack instead of truncating (UI_SPEC §8), as Android's do.
 */
struct FlightRow: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    let flight: FlightItem
    var isSelected = false
    var isPending = false
    var isListed = true
    var onTap: () -> Void = {}
    var onToggleSave: () -> Void = {}

    var body: some View {
        Group {
            if typeSize.isAccessibilitySize { stacked } else { compact }
        }
        .padding(EdgeInsets(top: 16, leading: 16, bottom: 4, trailing: 16))
        .contentShape(.rect)
        .onTapGesture(perform: onTap)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(rowLabel(flight, isListed: isListed))
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
        .overlay(alignment: .bottomTrailing) {
            SaveHeart(isSaved: flight.isSaved, isPending: isPending, onTap: onToggleSave)
                .padding(.trailing, 4)
                .padding(.bottom, 4)
        }
        // UI_SPEC §8: colour is never the only signal, so a selected row also gets a border and the selected trait.
        .background(isSelected ? Color.selectedCard : Color.card, in: .rect(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(isSelected ? Color.accent : Color.cardBorder, lineWidth: isSelected ? 2 : 1))
    }

    private var points: some View {
        Text(PointsFormat.shared.full(points: flight.points)).font(.appTitle.monospacedDigit())
    }

    private var cities: some View {
        Text("\(flight.originCity) → \(flight.destinationCity)").font(.appBody).foregroundStyle(Color.secondaryText)
    }

    private var date: some View {
        Text(Format.rowDate(flight.departureDate)).font(.appBody).foregroundStyle(Color.secondaryText)
    }

    private var compact: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 12) {
                RouteConnector(origin: flight.originCode, destination: flight.destinationCode)
                points
            }
            HStack(spacing: 8) {
                cities.lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                CabinLabel(cabin: flight.cabin)
            }
            HStack(spacing: 8) {
                date
                ProgramLogo(program: flight.program)
                Spacer(minLength: 0)
                if !isListed { NotListedTag() }
                // Leaves room for the heart, which sits over this corner with its own 48 pt target, as on Android.
                Color.clear.frame(width: 36, height: 48)
            }
        }
    }

    /** One part per line, nothing cut short; the heart keeps the bottom corner. */
    private var stacked: some View {
        VStack(alignment: .leading, spacing: 6) {
            RouteConnector(origin: flight.originCode, destination: flight.destinationCode)
            points
            cities
            CabinLabel(cabin: flight.cabin)
            date
            ProgramLogo(program: flight.program)
            if !isListed { NotListedTag() }
            Color.clear.frame(height: 48)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/**
 * UI_SPEC §8: "Toronto to Barcelona, Monday, February 1, Economy, Aeroplan, 16,000 points, saved", in full words. The
 * heart is its own button.
 */
func rowLabel(_ flight: FlightItem, isListed: Bool) -> String {
    var parts = [
        "\(flight.originCity) to \(flight.destinationCity)",
        Format.spokenDate(flight.departureDate),
        flight.cabin,
        flight.program,
        Format.spokenPoints(flight.points),
    ]
    if flight.isSaved { parts.append("saved") }
    if !isListed { parts.append("no longer listed") }
    return parts.joined(separator: ", ")
}

/** ♡ or ♥, or a spinner while the write runs (V7). */
private struct SaveHeart: View {
    let isSaved: Bool
    let isPending: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Group {
                if isPending {
                    ProgressView()
                } else {
                    MSIcon(name: isSaved ? "ms_favorite_filled" : "ms_favorite")
                        .foregroundStyle(isSaved ? Color.saved : Color.secondaryText)
                }
            }
            .frame(width: 48, height: 48)
        }
        .buttonStyle(.plain)
        .disabled(isPending)
        .accessibilityLabel(isSaved ? "Remove from saved" : "Save flight")
    }
}

/** `ORIGIN ──✈── DEST` (UI_SPEC §3). */
struct RouteConnector: View {
    let origin: String
    let destination: String

    var body: some View {
        HStack(spacing: 4) {
            // A code never breaks across lines, however large the text (UI_SPEC §8).
            Text(origin).font(.appTitle).fixedSize()
            line.padding(.leading, 4)
            MSIcon(name: "ms_flight", size: 18).rotationEffect(.degrees(90)).foregroundStyle(Color.brand)
            line.padding(.trailing, 4)
            Text(destination).font(.appTitle).fixedSize()
        }
    }

    private var line: some View {
        Rectangle().fill(Color.cardBorder).frame(height: 1)
    }
}

/**
 * Roame's seat-class tag: an icon and the name in the class's colour (UI_SPEC §6). A cabin the app doesn't know yet
 * gets the Economy look, so new data can't break it (DM1).
 */
struct CabinLabel: View {
    let cabin: String

    var body: some View {
        let colors = Color.cabin(cabin)
        HStack(spacing: 4) {
            MSIcon(name: Self.icon(cabin), size: 16).foregroundStyle(colors.icon)
            Text(cabin).font(.appButton).foregroundStyle(colors.text).lineLimit(1)
        }
    }

    /** The same Material Symbols as Android's (D37, D38). */
    private static func icon(_ cabin: String) -> String {
        switch cabin {
        case "First": "ms_sparkles"
        case "Business": "ms_crown"
        case "Premium Economy": "ms_seat_premium"
        default: "ms_seat"
        }
    }
}

/**
 * The loyalty program's logo on a white pill, like Roame's program chips; white in dark mode too, so the logos keep
 * their own colours (D34). Programs without a freely licensed logo show their name instead.
 */
struct ProgramLogo: View {
    let program: String
    var height: CGFloat = 14

    var body: some View {
        Group {
            if let logo = Self.logo(program) {
                Image(uiImage: logo).resizable().scaledToFit().frame(height: height).accessibilityLabel(program)
            } else {
                Text(program).font(.appBadge).foregroundStyle(Color(red: 0x30 / 255, green: 0x2D / 255, blue: 0x2A / 255)).lineLimit(1)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(.white, in: .capsule)
        .overlay(Capsule().strokeBorder(Color(red: 0xE8 / 255, green: 0xE5 / 255, blue: 0xE0 / 255)))
    }

    private static func logo(_ program: String) -> UIImage? {
        switch program {
        case "Aeroplan": UIImage(named: "logo_aeroplan")
        case "MileagePlus": UIImage(named: "logo_mileageplus")
        case "Mileage Plan": UIImage(named: "logo_mileage_plan")
        default: nil
        }
    }
}

/** V4: the latest refresh no longer has this saved flight. */
struct NotListedTag: View {
    var body: some View {
        Tag(text: "No longer listed", color: .secondaryText)
    }
}

/** A tinted capsule with full-colour text (UI_SPEC §6). */
private struct Tag: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.appLabelSmall)
            .lineLimit(1)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .foregroundStyle(color)
            .background(color.opacity(0.15), in: .capsule)
    }
}
