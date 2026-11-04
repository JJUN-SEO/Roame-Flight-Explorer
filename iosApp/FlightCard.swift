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
                    .font(.appLabel)
                    .foregroundStyle(Color.secondaryText)
                    .onLongPressGesture { onDebugLongPress?() }
                Spacer()
                Button(action: onClose) {
                    MSIcon(name: "ms_close").frame(width: 48, height: 48).contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")
            }
            // As on Android: the close button sits 4 pt from the edge, the rest 16 pt in.
            VStack(alignment: .leading, spacing: 4) {
            RouteConnector(origin: flight.originCode, destination: flight.destinationCode)
            HStack {
                Text(flight.originCity)
                Spacer()
                Text(flight.destinationCity)
            }
            .font(.appBody)
            .foregroundStyle(Color.secondaryText)
            HStack {
                Text(Format.cardDate(flight.departureDate)).font(.appBody)
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
                    .font(.appPoints.monospacedDigit())
                Spacer()
                SaveButton(isSaved: flight.isSaved, isPending: isPending, onTap: onToggleSave)
            }
            .padding(.top, 8)
            }
            .padding(.trailing, 12)
        }
        .padding(.leading, 16)
        .padding(.trailing, 4)
        .padding(.bottom, 16)
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
        if isSaved {
            TintedButton(text: "Remove", isEnabled: !isPending, action: onTap) { icon }
        } else {
            PrimaryButton(text: "Save flight", isEnabled: !isPending, action: onTap) { icon }
        }
    }

    @ViewBuilder private var icon: some View {
        if isPending {
            ProgressView().tint(isSaved ? Color.accent : Color.onAccent).frame(width: 18, height: 18)
        } else {
            MSIcon(name: isSaved ? "ms_favorite_filled" : "ms_favorite", size: 18)
        }
    }
}
