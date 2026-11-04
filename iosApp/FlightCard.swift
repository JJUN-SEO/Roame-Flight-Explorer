import SwiftUI
import Shared

/**
 * The selected flight with Save or Remove (UI_SPEC §2.3, L6), the same in Explore and Saved.
 * `onDebugLongPress` is set in debug builds only: long-pressing the title arms "Fail next DB write" (TEST_PLAN §6).
 */
struct FlightCard: View {
    let flight: FlightItem
    let isPending: Bool
    var isListed = true
    let onClose: () -> Void
    let onToggleSave: () -> Void
    var onDebugLongPress: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("Selected flight")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Color.secondaryText)
                    .onLongPressGesture { onDebugLongPress?() }
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark").font(.subheadline.weight(.semibold)).frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")
            }
            RouteConnector(origin: flight.originCode, destination: flight.destinationCode)
            HStack {
                Text(flight.originCity)
                Spacer()
                Text(flight.destinationCity)
            }
            .font(.subheadline)
            .foregroundStyle(Color.secondaryText)
            HStack {
                Text(Format.cardDate(flight.departureDate)).font(.subheadline)
                Spacer()
                CabinLabel(cabin: flight.cabin)
            }
            .padding(.top, 4)
            HStack(spacing: 8) {
                ProgramLogo(program: flight.program, height: 18)
                if !isListed { NotListedTag() }
            }
            HStack {
                Text(PointsFormat.shared.full(points: flight.points))
                    .font(.title2.weight(.bold).monospacedDigit())
                Spacer()
                SaveButton(isSaved: flight.isSaved, isPending: isPending, onTap: onToggleSave)
            }
            .padding(.top, 8)
        }
        .padding(.leading, 16)
        .padding([.trailing, .bottom], 16)
        .background(Color.card, in: .rect(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(Color.cardBorder))
        .shadow(color: .black.opacity(0.15), radius: 8, y: 2)
    }
}

/** V7: no optimistic change. While the write runs, the button shows a spinner and ignores taps. */
private struct SaveButton: View {
    let isSaved: Bool
    let isPending: Bool
    let onTap: () -> Void

    var body: some View {
        let label = HStack(spacing: 6) {
            if isPending {
                ProgressView()
            } else {
                Image(systemName: isSaved ? "heart.fill" : "heart")
            }
            Text(isSaved ? "Remove" : "Save flight")
        }
        if isSaved {
            Button(action: onTap) { label }.buttonStyle(.bordered).disabled(isPending)
        } else {
            Button(action: onTap) { label }.buttonStyle(.borderedProminent).foregroundStyle(Color.onAccent).disabled(isPending)
        }
    }
}
