import SwiftUI
import Shared

/**
 * Explore's landing page, like Roame's home (Step 6.3b, UI_SPEC §2.0): the search bar, a banner, the destination deck
 * and each program's best offer, drawn the same as Android's LandingScreen (D38). Flights appear only after a search (D36).
 */
struct LandingScreen: View {
    @Environment(ExplorerModel.self) private var model
    let onSearch: () -> Void
    let onDestination: (DestinationDeal) -> Void
    let onOffer: (ProgramOffer) -> Void

    var body: some View {
        let state = model.state
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Flight Explorer")
                    .font(.appPoints)
                    .foregroundStyle(Color.brand)
                    .padding(EdgeInsets(top: 12, leading: 16, bottom: 0, trailing: 16))
                    .accessibilityAddTraits(.isHeader)
                SearchPill(state: state, action: onSearch)
                    .padding(EdgeInsets(top: 12, leading: 16, bottom: 0, trailing: 16))
                switch state.data {
                case let unavailable as DataStatus.Unavailable:
                    UnavailableMessage(error: unavailable.error) { model.store.refresh() }.frame(minHeight: 400)
                case let data as DataStatus.Ready:
                    ready(state, data)
                default:
                    // DataStatus.Loading, the only other case.
                    LandingPlaceholders()
                }
            }
            .padding(.bottom, 24)
        }
        // Stops below the status bar, as Android's does, rather than scrolling under the clock.
        .clipped()
    }

    @ViewBuilder private func ready(_ state: ExplorerState, _ data: DataStatus.Ready) -> some View {
        Spacer().frame(height: 8)
        StaleBanner(data: data)
        RefreshProgress(data: data)
        if state.options.isEmpty {
            // UI_SPEC §4: a successful empty response is not an error (R4).
            EmptyState(icon: "ms_flight", title: "No flights available right now", message: "The server returned no flights.")
                .frame(minHeight: 400)
        } else {
            HeroCard(action: onSearch).padding(EdgeInsets(top: 8, leading: 16, bottom: 0, trailing: 16))
            // New deals start from their first card. Otherwise, when the location arrives and reorders them, the
            // rows keep the old first card in view and the new best deal is hidden off to the left.
            Group {
                SectionHeader(title: "Best deals by destination")
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: 12) {
                        ForEach(state.landing.destinations, id: \.city) { deal in
                            DestinationCard(deal: deal) { onDestination(deal) }
                        }
                    }
                    .padding(.horizontal, 16)
                }
                SectionHeader(title: "Best offers by program")
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: 12) {
                        ForEach(state.landing.offers, id: \.program) { offer in
                            OfferCard(offer: offer) { onOffer(offer) }
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }
            .id(state.landing.homeAirport?.code)
        }
    }
}

/** Roame's search bar: the current search, or "Where to?", and its dates and cabin. Tapping it opens the search. */
struct SearchPill: View {
    let state: ExplorerState
    let action: () -> Void

    var body: some View {
        let origin = state.originPick.map(airportLabel) ?? state.originQuery.nonBlank
        let destination = state.destinationPick.map(airportLabel) ?? state.destinationQuery.nonBlank
        let title = origin == nil && destination == nil ? "Where to?" : "\(origin ?? "Anywhere") → \(destination ?? "Anywhere")"
        let start = state.filters.dateStart ?? state.options.minDate
        let end = state.filters.dateEnd ?? state.options.maxDate
        let cabin = state.filters.cabin ?? "Any cabin"
        let details = if let start, let end { "\(Format.chipDate(start)) – \(Format.chipDate(end)) · \(cabin)" } else { cabin }
        Button(action: action) {
            HStack(spacing: 16) {
                MSIcon(name: "ms_search")
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).font(.appTitle).lineLimit(1)
                    Text(details).font(.appBody).foregroundStyle(Color.secondaryText).lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Color.card, in: .capsule)
            .shadow(color: .black.opacity(0.12), radius: 8, y: 2)
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Search flights. \(title), \(details)")
        .accessibilityAddTraits(.isButton)
    }
}

/** Roame's banner: a landscape, a line of copy and "Find a flight", which opens the search like the search bar. */
private struct HeroCard: View {
    let action: () -> Void

    var body: some View {
        Color.clear
            .aspectRatio(4 / 3, contentMode: .fit)
            .overlay {
                Image("hero_landscape").resizable().scaledToFill().accessibilityHidden(true)
            }
            .overlay { Color.black.opacity(0.25) }
            .overlay {
                VStack(spacing: 20) {
                    Text("See where your points can take you")
                        .font(.appDisplay)
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                    Button(action: action) {
                        Text("Find a flight")
                            .font(.appButton)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 32)
                            .padding(.vertical, 14)
                            .frame(minHeight: 48)
                            .background(LinearGradient(colors: [.gradientStart, .gradientEnd], startPoint: .leading, endPoint: .trailing), in: .capsule)
                            .contentShape(.capsule)
                    }
                    .buttonStyle(.plain)
                }
                .padding(24)
            }
            .clipShape(.rect(cornerRadius: 16))
    }
}

private struct SectionHeader: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.appTitleLarge)
            .padding(EdgeInsets(top: 28, leading: 16, bottom: 12, trailing: 16))
            .accessibilityAddTraits(.isHeader)
    }
}

/** B8: a photo of the city (D33), its lowest points, and how far its best flight is below typical. */
private struct DestinationCard: View {
    let deal: DestinationDeal
    let action: () -> Void

    var body: some View {
        let below = Int(deal.percentBelowTypical)
        let points = Format.count(Int(deal.minPoints))
        Button(action: action) {
            Color.clear
                .frame(width: 260, height: 260 * 2 / 3)
                .overlay {
                    if let photo = UIImage(named: deal.photoKey) {
                        Image(uiImage: photo).resizable().scaledToFill()
                    } else {
                        NoPhoto()
                    }
                }
                // Darker towards the bottom, so the white text reads on any photo.
                .overlay {
                    LinearGradient(stops: [.init(color: .clear, location: 0.4), .init(color: .black.opacity(0.65), location: 1)], startPoint: .top, endPoint: .bottom)
                }
                .overlay(alignment: .topLeading) {
                    if below > 0 {
                        Text("Up to \(below)% below typical")
                            .font(.appBadge)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color.brand, in: .capsule)
                            .padding(12)
                    }
                }
                .overlay(alignment: .bottomLeading) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(deal.city).font(.appTitleLarge).lineLimit(1)
                        Text("from \(PointsFormat.shared.full(points: deal.minPoints))").font(.appLabel)
                    }
                    .foregroundStyle(.white)
                    .padding(16)
                }
                .clipShape(.rect(cornerRadius: 16))
                .contentShape(.rect(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(below > 0 ? "\(deal.city), from \(points) points, up to \(below)% below typical" : "\(deal.city), from \(points) points")
        .accessibilityAddTraits(.isButton)
    }
}

/** D33: a city the app has no photo for gets a drawn card in Roame's gradient. */
struct NoPhoto: View {
    var body: some View {
        LinearGradient(colors: [.gradientStart, .gradientEnd], startPoint: .topLeading, endPoint: .bottomTrailing)
            .overlay { MSIcon(name: "ms_flight", size: 48).foregroundStyle(.white.opacity(0.6)) }
    }
}

/** B7: a program's best-value flight, under the program's logo, like Roame's program deals. */
private struct OfferCard: View {
    let offer: ProgramOffer
    let action: () -> Void

    var body: some View {
        let flight = offer.flight
        let below = Int(offer.percentBelowTypical)
        let value = offer.estimatedValueUsd.map { Format.count(Int($0.int32Value)) }
        VStack(alignment: .leading, spacing: 8) {
            ProgramLogo(program: offer.program, height: 18)
            Button(action: action) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(Format.rowDate(flight.departureDate)).font(.appTitle)
                        Spacer(minLength: 8)
                        CabinLabel(cabin: flight.cabin)
                    }
                    RouteConnector(origin: flight.originCode, destination: flight.destinationCode)
                    Text("\(flight.originCity) → \(flight.destinationCity)")
                        .font(.appBody)
                        .foregroundStyle(Color.secondaryText)
                        .lineLimit(1)
                    Rectangle().fill(Color.cardBorder).frame(height: 1).padding(.vertical, 4)
                    HStack(alignment: .center) {
                        if below > 0 {
                            Text("\(below)% below typical")
                                .font(.appBadge)
                                .foregroundStyle(Color.accent)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(Color.selectedCard, in: .capsule)
                        }
                        Spacer(minLength: 8)
                        VStack(alignment: .trailing, spacing: 0) {
                            Text(PointsFormat.shared.full(points: flight.points)).font(.appTitle).monospacedDigit()
                            if let value {
                                Text("≈ $\(value) value").font(.appCaption).foregroundStyle(Color.secondaryText)
                            }
                        }
                    }
                }
                .padding(16)
                .frame(width: 300, alignment: .leading)
                .background(Color.card, in: .rect(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.cardBorder))
                .contentShape(.rect(cornerRadius: 16))
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(
                "\(offer.program) best offer: \(flight.originCity) to \(flight.destinationCity), \(Format.rowDate(flight.departureDate)), "
                    + "\(flight.cabin), \(Format.count(Int(flight.points))) points"
                    + (below > 0 ? ", \(below)% below typical" : "")
                    + (value.map { ", worth about $\($0)" } ?? "")
            )
            .accessibilityAddTraits(.isButton)
        }
    }
}

/** UI_SPEC §4 first load: grey shapes where the banner and the deck will be. */
private struct LandingPlaceholders: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Finding flights…").font(.appTitle).foregroundStyle(Color.secondaryText)
            RoundedRectangle(cornerRadius: 16).fill(Color.track).aspectRatio(4 / 3, contentMode: .fit)
            RoundedRectangle(cornerRadius: 16).fill(Color.track).frame(width: 260, height: 260 * 2 / 3)
        }
        .padding(16)
    }
}

private extension String {
    var nonBlank: String? { trimmingCharacters(in: .whitespaces).isEmpty ? nil : self }
}
