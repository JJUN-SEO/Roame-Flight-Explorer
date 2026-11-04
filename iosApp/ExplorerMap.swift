import SwiftUI
import MapKit
import Shared

/**
 * The results as destination pills, with the selected flight's great-circle route as dots and a plane flying along
 * them (UI_SPEC §2.1, M1–M9). `camera` lives in the results screen, so it survives leaving and coming back (M8).
 * `bottomInset` is how much of the map the results sheet and the selection card cover: it's the map's bottom safe
 * area, so Apple's Legal link stays visible and routes are framed above them.
 */
struct ExplorerMap: View {
    @Environment(ExplorerModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Binding var camera: MapCameraPosition?
    let isActive: Bool
    let bottomInset: CGFloat
    /** The selected flight once the sheet has dropped and the card has been measured, so its route is fitted above them. */
    let routeToFit: String?
    let onMarkerTap: (String) -> Void
    /** M8: each selection is framed once; the card hiding and showing again doesn't move the camera. */
    @State private var fitted: String?
    /**
     * M4: the selected flight's destination as the map's own selection. SwiftUI's map has no z-order, and MapKit stacks
     * pills by latitude, so a pill further south covered it; MapKit draws its selection above every other pill.
     */
    @State private var raised: String?
    /** M9: when the plane took off, so each new selection flies from the origin. */
    @State private var takeOff = Date.now

    var body: some View {
        let state = model.state
        let selected = state.selected
        MapReader { proxy in
            Map(position: Binding(get: { camera ?? .automatic }, set: { camera = $0 }), interactionModes: [.pan, .zoom], selection: $raised) {
                ForEach(state.markers, id: \.code) { marker in
                    // Centred on the airport, as Android's are, so the route ends under the pill.
                    Annotation(marker.city, coordinate: CLLocationCoordinate2D(latitude: marker.latitude, longitude: marker.longitude), anchor: .center) {
                        MarkerPill(marker: marker, isSelected: marker.code == selected?.destinationCode)
                            .onTapGesture { onMarkerTap(marker.code) }
                    }
                    .annotationTitles(.hidden)
                    .tag(marker.code)
                }
                // M7: a geodesic route with a dot at the origin. MapKit draws the curve and handles the antimeridian.
                if let selected {
                    let origin = CLLocationCoordinate2D(latitude: selected.originLatitude, longitude: selected.originLongitude)
                    let destination = CLLocationCoordinate2D(latitude: selected.destinationLatitude, longitude: selected.destinationLongitude)
                    MapPolyline(coordinates: [origin, destination], contourStyle: .geodesic)
                        .stroke(Color.brand, style: StrokeStyle(lineWidth: dotWidth, lineCap: .round, dash: [0, dotGap]))
                    Annotation("", coordinate: origin) {
                        Circle().fill(Color.brand).frame(width: 10, height: 10).overlay(Circle().stroke(.white, lineWidth: 2))
                    }
                    // Under Reduce Motion the plane rests halfway, so MapKit can hold it in place like any annotation.
                    if reduceMotion {
                        let plane = routePlane(selected, PlaneFlight.shared.resting)
                        Annotation("", coordinate: CLLocationCoordinate2D(latitude: plane.latitude, longitude: plane.longitude)) {
                            PlaneBadge(heading: plane.headingDegrees)
                        }
                        .annotationTitles(.hidden)
                    }
                }
            }
            // D27: a quiet, grey map, so the pills and route stand out. Apple's logo and Legal link must stay.
            .mapStyle(.standard(elevation: .flat, emphasis: .muted, pointsOfInterest: .excludingAll))
            .safeAreaPadding(.bottom, bottomInset)
            // M9: the flying plane is drawn over the map and placed on it every frame, which also keeps it on the route
            // while the map moves. Moved every frame as an annotation, MapKit placed it unevenly, and annotations that
            // MapKit animated left the route (Step 6.3d). It flies only while the route shows on screen.
            .overlay {
                if let selected, !reduceMotion {
                    TimelineView(.animation(minimumInterval: 1.0 / 60, paused: !isActive)) { timeline in
                        let frame = PlaneFlight.shared.at(elapsedMillis: Int64(timeline.date.timeIntervalSince(takeOff) * 1000))
                        let plane = routePlane(selected, frame)
                        if let point = proxy.convert(CLLocationCoordinate2D(latitude: plane.latitude, longitude: plane.longitude), to: .local) {
                            PlaneBadge(heading: plane.headingDegrees).opacity(frame.opacity).position(point)
                        }
                    }
                    .allowsHitTesting(false)
                }
            }
        }
        // M8: the first display uses MapKit's automatic framing, which fits every marker. Recording that camera once
        // turns it into a fixed position, so later filter changes don't reframe the map; the user's own moves reach
        // `camera` through the binding. Recording every move instead caught MapKit mid-animation, while the first
        // version's moving dots redrew the map, and froze a route's framing halfway (found in Step 6.3d).
        .onMapCameraChange(frequency: .onEnd) { context in
            if camera == nil { camera = .camera(context.camera) }
        }
        // M8: the camera moves on an explicit selection, never because filters changed.
        .onChange(of: routeToFit) { _, id in
            guard let id, id != fitted, let flight = model.state.selected, flight.id == id else { return }
            fitted = id
            withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.8)) { camera = routeCamera(flight) }
        }
        .onChange(of: selected?.id) { _, id in
            takeOff = .now
            if id == nil { fitted = nil }
        }
        .onChange(of: selected?.destinationCode, initial: true) { _, code in raised = code }
        // MapKit selects a pill the user taps, and clears its selection on a tap elsewhere. A tap opens the pill's
        // flights, as its own tap does, and the selection goes back to the selected flight's destination.
        .onChange(of: raised) { _, code in
            let destination = model.state.selected?.destinationCode
            guard code != destination else { return }
            if let code { onMarkerTap(code) }
            raised = destination
        }
    }
}

private func routePlane(_ flight: FlightItem, _ frame: PlaneFrame) -> RoutePlane {
    GreatCircle.shared.plane(
        from: GeoPoint(latitude: flight.originLatitude, longitude: flight.originLongitude),
        to: GeoPoint(latitude: flight.destinationLatitude, longitude: flight.destinationLongitude),
        fraction: frame.fraction
    )
}

/** M9: the route's dots, as Android's. MapKit draws a line's width and dashes about twice as large as asked (Step 6.3d). */
private let dotWidth: CGFloat = 3
private let dotGap: CGFloat = 8

/** M9: the plane in Roame's pink on a white disc, turned to the route's heading. The map doesn't rotate, so it stays true. */
private struct PlaneBadge: View {
    let heading: Double

    var body: some View {
        MSIcon(name: "ms_flight", size: 20)
            .foregroundStyle(Color.brand)
            .rotationEffect(.degrees(heading))
            .frame(width: 32, height: 32)
            .background(.white, in: .circle)
            .overlay(Circle().stroke(.black.opacity(0.2), lineWidth: 1))
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

/**
 * The code and the lowest points, with the flight count on a badge marked with a plane (UI_SPEC §3, MAP-2); pink when
 * selected. Drawn the same as Android's.
 */
private struct MarkerPill: View {
    let marker: DestinationMarker
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 0) {
            Text(marker.code).font(.appBadge)
            Text(PointsFormat.shared.compact(points: marker.minPoints)).font(.appLabelSmall).padding(.leading, 5)
            HStack(spacing: 2) {
                MSIcon(name: "ms_flight", size: 11).rotationEffect(.degrees(90))
                Text(Format.count(Int(marker.flightCount))).font(.appLabelTiny)
            }
            .foregroundStyle(Color.accent)
            .padding(.horizontal, 5)
            .frame(minHeight: 18)
            .background(isSelected ? Color.onAccent : Color.selectedCard, in: .capsule)
            .padding(.leading, 6)
        }
        .padding(.leading, 10)
        .padding(.trailing, 4)
        .padding(.vertical, 4)
        .frame(minHeight: 26)
        .foregroundStyle(isSelected ? Color.onAccent : Color.text)
        .background(isSelected ? Color.accent : Color.card, in: .capsule)
        .overlay(Capsule().strokeBorder(isSelected ? Color.accent : Color.cardBorder, lineWidth: 1))
        .shadow(color: .black.opacity(0.2), radius: 3, y: 1)
        // MapKit offers an annotation less room than the pill needs, which cut off the count.
        .fixedSize()
        .accessibilityElement()
        .accessibilityLabel("\(marker.city), \(flightCount(Int(marker.flightCount))), from \(PointsFormat.shared.full(points: marker.minPoints))")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

/** Every flight to one destination, in O2 order (M5). Choosing one selects it and closes the sheet. */
struct MarkerSheet: View {
    @Environment(ExplorerModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let code: String

    var body: some View {
        let state = model.state
        if let marker = state.markers.first(where: { $0.code == code }) {
            // The same layout as Android's sheet (D38): a centred title over the destination's flights.
            VStack(spacing: 0) {
                Text("\(marker.city) · \(marker.code) — \(flightCount(Int(marker.flightCount))) from \(PointsFormat.shared.full(points: marker.minPoints))")
                    .font(.appTitle)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 16)
                    .padding(.top, 20)
                    .accessibilityAddTraits(.isHeader)
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(marker.flights, id: \.id) { flight in
                            FlightRow(
                                flight: flight,
                                isSelected: flight.id == state.selected?.id,
                                isPending: state.pendingWriteIds.contains(flight.id),
                                onTap: {
                                    model.store.select(flightId: flight.id)
                                    dismiss()
                                },
                                onToggleSave: { model.store.toggleSave(flightId: flight.id) }
                            )
                        }
                    }
                    .padding(16)
                }
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
            .presentationBackground(Color.background)
            .foregroundStyle(Color.text)
        }
    }
}

/**
 * Fits the route: a map rectangle around the shared `GreatCircle` points, with room around it. The points' longitudes are
 * unwrapped, so a route across the antimeridian gives one rectangle running past the world's edge, which MapKit
 * accepts, rather than the long way round the world. MapKit fits a rectangle exactly, where it shortened regions here.
 */
private func routeCamera(_ flight: FlightItem) -> MapCameraPosition {
    let points = GreatCircle.shared.interpolate(
        from: GeoPoint(latitude: flight.originLatitude, longitude: flight.originLongitude),
        to: GeoPoint(latitude: flight.destinationLatitude, longitude: flight.destinationLongitude),
        segments: 64
    )
    let world = MKMapSize.world
    let xs = points.map { ($0.longitude + 180) / 360 * world.width }
    let ys = points.map { MKMapPoint(CLLocationCoordinate2D(latitude: $0.latitude, longitude: 0)).y }
    guard let minX = xs.min(), let maxX = xs.max(), let minY = ys.min(), let maxY = ys.max() else { return .automatic }
    var rect = MKMapRect(x: minX, y: minY, width: maxX - minX, height: maxY - minY)
    rect = rect.insetBy(dx: -max(rect.width * 0.15, world.width * 0.01), dy: -max(rect.height * 0.25, world.height * 0.01))
    if rect.minX < 0 { rect.origin.x += world.width }
    return .rect(rect)
}
