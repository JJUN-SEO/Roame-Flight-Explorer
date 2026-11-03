import SwiftUI
import Shared

/** One result (UI_SPEC §2.1). */
struct FlightRow: View {
    let flight: FlightItem

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
            Text("\(Format.rowDate(flight.departureDate)) · \(flight.program)")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 16))
        .accessibilityElement(children: .combine)
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

/** A tinted capsule with full-colour text (UI_SPEC §6). */
struct CabinTag: View {
    let cabin: String

    var body: some View {
        let color = Color.cabin(cabin)
        Text(cabin)
            .font(.caption.weight(.medium))
            .lineLimit(1)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .foregroundStyle(color)
            .background(color.opacity(0.15), in: .capsule)
    }
}
