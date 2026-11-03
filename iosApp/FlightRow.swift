import SwiftUI
import Shared

/** One result (UI_SPEC §2.1). Tapping it selects it; the heart saves or removes it (V7). */
struct FlightRow: View {
    let flight: FlightItem
    var isSelected = false
    var isPending = false
    var isListed = true
    var onTap: () -> Void = {}
    var onToggleSave: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 12) {
                RouteConnector(origin: flight.originCode, destination: flight.destinationCode)
                Text(PointsFormat.shared.full(points: flight.points))
                    .font(.headline.monospacedDigit())
            }
            HStack(spacing: 8) {
                Text("\(flight.originCity) → \(flight.destinationCity)")
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                CabinTag(cabin: flight.cabin)
            }
            .font(.subheadline)
            .foregroundStyle(.secondary)
            HStack(spacing: 8) {
                Text("\(Format.rowDate(flight.departureDate)) · \(flight.program)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if !isListed { NotListedTag() }
                // Leaves room for the heart, which sits over this corner with its own 44 pt target.
                Color.clear.frame(width: 28, height: 1)
            }
        }
        .padding(16)
        .contentShape(.rect)
        .onTapGesture(perform: onTap)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
        .overlay(alignment: .bottomTrailing) {
            SaveHeart(isSaved: flight.isSaved, isPending: isPending, onTap: onToggleSave)
                .padding(.trailing, 4)
                .padding(.bottom, 2)
        }
        // UI_SPEC §8: colour is never the only signal, so a selected row also gets a border and the selected trait.
        .background(isSelected ? Color.accent.opacity(0.15) : Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(isSelected ? Color.accent : .clear, lineWidth: 2))
    }
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
                    Image(systemName: isSaved ? "heart.fill" : "heart")
                        .foregroundStyle(isSaved ? Color.saved : .secondary)
                }
            }
            .frame(width: 44, height: 44)
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
            Text(origin).font(.headline)
            line
            Image(systemName: "airplane").font(.caption).foregroundStyle(.tint)
            line
            Text(destination).font(.headline)
        }
    }

    private var line: some View {
        Rectangle().fill(Color(.separator)).frame(height: 1)
    }
}

/** Grey Economy, teal Premium Economy, indigo Business (UI_SPEC §6). */
struct CabinTag: View {
    let cabin: String

    var body: some View {
        Tag(text: cabin, color: .cabin(cabin))
    }
}

/** V4: the latest refresh no longer has this saved flight. */
struct NotListedTag: View {
    var body: some View {
        Tag(text: "No longer listed", color: .gray)
    }
}

/** A tinted capsule with full-colour text (UI_SPEC §6). */
private struct Tag: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.caption.weight(.medium))
            .lineLimit(1)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .foregroundStyle(color)
            .background(color.opacity(0.15), in: .capsule)
    }
}
