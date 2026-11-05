import SwiftUI
import Shared

/**
 * The flight details page (Step 6.3f, D47), like Roame's and drawn the same as Android's: the destination's photo with
 * "From Toronto to Barcelona", then a card with the flight and what the data says about it (DT1). The data has no times,
 * aircraft or travel time, so those show as placeholders, and the page says so (DT2). It follows the live state, so
 * saving updates it.
 */
struct FlightDetailsScreen: View {
    @Environment(ExplorerModel.self) private var model
    let details: FlightDetails
    let isPending: Bool
    let onClose: () -> Void
    let onToggleSave: () -> Void

    var body: some View {
        let flight = details.flight
        ScrollView {
            VStack(spacing: 0) {
                DetailsHero(photoKey: details.photoKey, origin: flight.originCity, destination: flight.destinationCity)
                DetailsCard(details: details, updated: updated)
                    .padding(.horizontal, 16)
                    .padding(.top, -48)
                    .padding(.bottom, 24)
            }
        }
        .ignoresSafeArea(edges: .top)
        .overlay(alignment: .topLeading) {
            Button(action: onClose) {
                MSIcon(name: "ms_arrow_back")
                    .foregroundStyle(Color(red: 0x30 / 255, green: 0x2D / 255, blue: 0x2A / 255))
                    .frame(width: 44, height: 44)
                    .background(.white, in: .circle)
                    .overlay(Circle().strokeBorder(.black.opacity(0.15)))
                    .contentShape(.circle)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            .padding(.leading, 16)
            .padding(.top, 8)
        }
        .safeAreaInset(edge: .bottom) {
            SaveButton(isSaved: flight.isSaved, isPending: isPending, fillsWidth: true, onTap: onToggleSave)
                .padding(EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16))
                .background(Color.background)
        }
        .background(Color.background)
        .foregroundStyle(Color.text)
    }

    /** T4: when the data was last refreshed, as the stale banner says it. */
    private var updated: String? {
        guard let data = model.state.data as? DataStatus.Ready else { return nil }
        return "Updated \(Format.syncTime(data.lastSuccessAt))"
    }
}

/** The destination's photo, darker at the top and bottom so the white text reads, or Roame's gradient without one. */
private struct DetailsHero: View {
    let photoKey: String
    let origin: String
    let destination: String

    var body: some View {
        Color.clear
            .frame(height: 340)
            .overlay {
                if let photo = UIImage(named: photoKey) {
                    Image(uiImage: photo).resizable().scaledToFill()
                } else {
                    NoPhoto()
                }
            }
            .overlay {
                LinearGradient(
                    stops: [.init(color: .black.opacity(0.35), location: 0), .init(color: .clear, location: 0.35), .init(color: .black.opacity(0.45), location: 1)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
            .overlay {
                VStack(spacing: 4) {
                    Text("From \(origin) to".uppercased()).font(.appLabel).tracking(1.5).multilineTextAlignment(.center)
                    Text(destination).font(.appHero).multilineTextAlignment(.center).lineLimit(2).minimumScaleFactor(0.6)
                }
                // The photo's height is fixed, so the title, already 40 pt, grows only so far; VoiceOver reads it in full.
                .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                .foregroundStyle(.white)
                .padding(.horizontal, 24)
                .padding(.top, 24)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("From \(origin) to \(destination)")
                .accessibilityAddTraits(.isHeader)
            }
            .clipped()
    }
}

/** Roame's "Flight Details" card: the departure, the program, the stops, and the points with what the data says of them. */
private struct DetailsCard: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    let details: FlightDetails
    let updated: String?

    var body: some View {
        let flight = details.flight
        VStack(alignment: .leading, spacing: 0) {
            Text("Flight Details").font(.appTitleLarge).accessibilityAddTraits(.isHeader)
            if let updated {
                Text(updated).font(.appBody).foregroundStyle(Color.secondaryText).padding(.top, 4)
            }
            Divider().padding(.vertical, 16)

            Text("Departure · \(Format.cardDate(flight.departureDate))")
                .font(.appTitle)
                .accessibilityLabel("Departure, \(Format.spokenCardDate(flight.departureDate))")
            // At accessibility sizes the logo goes above, so the words don't break mid-word beside it.
            let programLayout = typeSize.isAccessibilitySize
                ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
                : AnyLayout(HStackLayout(alignment: .top, spacing: 12))
            programLayout {
                ProgramLogo(program: flight.program, height: 18)
                VStack(alignment: .leading, spacing: 6) {
                    Text("\(flight.program) · Aircraft TBD")
                        .font(.appBody)
                        .foregroundStyle(Color.secondaryText)
                        .accessibilityLabel("\(flight.program). Aircraft not in the data")
                    CabinLabel(cabin: flight.cabin)
                }
            }
            .padding(.top, 12)

            VStack(alignment: .leading, spacing: 0) {
                Stop(time: "XX:XX", place: "\(flight.originCity) (\(flight.originCode))", kind: "Departure")
                HStack(spacing: 12) {
                    DottedLine().frame(width: 16, height: 44)
                    Text("Travel time: XX hr XX min")
                        .font(.appBody)
                        .foregroundStyle(Color.secondaryText)
                        .accessibilityLabel("Travel time not in the data")
                }
                Stop(time: "XX:XX", place: "\(flight.destinationCity) (\(flight.destinationCode))", kind: "Arrival")
            }
            .padding(.top, 16)

            Divider().padding(.vertical, 16)

            Text(PointsFormat.shared.full(points: flight.points))
                .font(.appPoints.monospacedDigit())
                .accessibilityLabel(Format.spokenPoints(flight.points))
            if details.percentBelowTypical > 0 {
                Text("\(details.percentBelowTypical)% below typical for \(flight.cabin)")
                    .font(.appBadge)
                    .foregroundStyle(Color.accent)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Color.selectedCard, in: .capsule)
                    .padding(.top, 8)
            }
            VStack(spacing: 0) {
                Fact(label: "Distance", value: "\(Format.count(Int(details.distanceKm))) km", stacked: typeSize.isAccessibilitySize)
                if let value = details.estimatedValueUsd {
                    Fact(label: "Estimated value", value: "about $\(Format.count(Int(value.int32Value)))", stacked: typeSize.isAccessibilitySize)
                }
            }
            .padding(.top, 12)

            Text("Times, travel time and aircraft aren't in the data, so they're placeholders. The value is an estimate from Roame's average cents per point for the cabin.")
                .font(.appCaption)
                .foregroundStyle(Color.secondaryText)
                .padding(.top, 16)
        }
        .padding(20)
        .background(Color.card, in: .rect(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(Color.outline))
        .shadow(color: .black.opacity(0.12), radius: 8, y: 2)
    }
}

/** A stop on the timeline: an open circle, the time, and the place. */
private struct Stop: View {
    let time: String
    let place: String
    let kind: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Circle().strokeBorder(Color.outline, lineWidth: 3).frame(width: 16, height: 16).padding(.top, 2)
            VStack(alignment: .leading, spacing: 2) {
                Text(time).font(.appTitle)
                Text(place).font(.appBody)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(kind), \(place), time not in the data")
    }
}

/** The dotted line between the stops. */
private struct DottedLine: View {
    var body: some View {
        Path { path in
            path.move(to: CGPoint(x: 8, y: 0))
            path.addLine(to: CGPoint(x: 8, y: 44))
        }
        .stroke(Color.outline, style: StrokeStyle(lineWidth: 1.5, dash: [2, 3]))
        .accessibilityHidden(true)
    }
}

/** A label on the left and its value on the right, or the value under it at accessibility sizes. */
private struct Fact: View {
    let label: String
    let value: String
    let stacked: Bool

    var body: some View {
        let layout = stacked ? AnyLayout(VStackLayout(alignment: .leading, spacing: 2)) : AnyLayout(HStackLayout())
        layout {
            Text(label).font(.appBody).foregroundStyle(Color.secondaryText)
            if !stacked { Spacer() }
            Text(value).font(.appLabel)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 6)
        .accessibilityElement(children: .combine)
    }
}
