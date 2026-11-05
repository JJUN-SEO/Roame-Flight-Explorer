import SwiftUI
import Shared

/**
 * The selected flight with Save or Remove (UI_SPEC §2.3, L6), the same in Explore and Saved.
 * `onDebugLongPress` is set in debug builds only: long-pressing the title arms "Fail next DB write" (TEST_PLAN §6).
 */
struct FlightCard: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    let flight: FlightItem
    let isPending: Bool
    var isListed = true
    let onClose: () -> Void
    let onToggleSave: () -> Void
    var onDebugLongPress: (() -> Void)?
    /** Opens the details page (Step 6.3f). */
    var onDetails: (() -> Void)?

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
            // When it's taller than the room it's given, as at the largest text sizes, the details scroll and the title
            // and ✕ stay in view (Step 6.3e).
            ViewThatFits(in: .vertical) {
                details
                ScrollView { details }.scrollBounceBehavior(.basedOnSize)
            }
        }
        .padding(.leading, 16)
        .padding(.trailing, 4)
        .padding(.bottom, 16)
        .background(Color.card, in: .rect(cornerRadius: 20))
        // The outline, darker than a row's border, so the card stands out from the rows behind it in dark mode too.
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(Color.outline))
        .shadow(color: .black.opacity(0.15), radius: 8, y: 2)
    }

    /** Everything under the title, 16 pt in; the ✕ sits 4 pt from the edge, as on Android. */
    private var details: some View {
        VStack(alignment: .leading, spacing: 4) {
            RouteConnector(origin: flight.originCode, destination: flight.destinationCode)
            // At the accessibility text sizes each pair stacks, so nothing is cut short (UI_SPEC §8), as on Android.
            let stacked = typeSize.isAccessibilitySize
            let layout = stacked ? AnyLayout(VStackLayout(alignment: .leading, spacing: 4)) : AnyLayout(HStackLayout())
            layout {
                Text(flight.originCity)
                if !stacked { Spacer() }
                Text(flight.destinationCity)
            }
            .font(.appBody)
            .foregroundStyle(Color.secondaryText)
            layout {
                Text(Format.cardDate(flight.departureDate)).font(.appBody)
                    .accessibilityLabel(Format.spokenCardDate(flight.departureDate))
                if !stacked { Spacer() }
                CabinLabel(cabin: flight.cabin)
            }
            .padding(.top, 4)
            HStack(spacing: 8) {
                ProgramLogo(program: flight.program, height: 18)
                if !isListed { NotListedTag() }
            }
            layout {
                Text(PointsFormat.shared.full(points: flight.points))
                    .font(.appPoints.monospacedDigit())
                    .accessibilityLabel(Format.spokenPoints(flight.points))
                if !stacked { Spacer() }
                SaveButton(isSaved: flight.isSaved, isPending: isPending, onTap: onToggleSave)
            }
            .padding(.top, 8)
            if let onDetails {
                Button(action: onDetails) {
                    HStack(spacing: 2) {
                        Text("View details").font(.appButton)
                        MSIcon(name: "ms_chevron_right", size: 18)
                    }
                    .foregroundStyle(Color.accent)
                    .frame(minHeight: 44)
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.trailing, 12)
    }
}

/** V7: no optimistic change. While the write runs, the button shows a spinner and ignores taps. */
struct SaveButton: View {
    let isSaved: Bool
    let isPending: Bool
    var fillsWidth = false
    let onTap: () -> Void

    var body: some View {
        if isSaved {
            TintedButton(text: "Remove", isEnabled: !isPending, fillsWidth: fillsWidth, action: onTap) { icon }
        } else {
            PrimaryButton(text: "Save flight", isEnabled: !isPending, fillsWidth: fillsWidth, action: onTap) { icon }
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
