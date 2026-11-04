import SwiftUI
import MapKit
import Shared

/**
 * The results as destination pills, with the selected flight's great-circle route as moving dots and a plane
 * (UI_SPEC §2.1, M1–M9). `camera` lives in the results screen, so it survives leaving and coming back (M8).
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

    var body: some View {
        let state = model.state
        let selected = state.selected
        // M9: the dots march only while a route shows on screen, and stand still under Reduce Motion.
        TimelineView(.animation(minimumInterval: 0.06, paused: selected == nil || !isActive || reduceMotion)) { timeline in
            Map(position: Binding(get: { camera ?? .automatic }, set: { camera = $0 }), interactionModes: [.pan, .zoom]) {
                // M4: the selected destination comes last, so it's drawn on top of a nearby one, such as HND and NRT.
                ForEach(markersSelectedLast(state.markers, selected?.destinationCode), id: \.code) { marker in
                    Annotation(marker.city, coordinate: CLLocationCoordinate2D(latitude: marker.latitude, longitude: marker.longitude)) {
                        MarkerPill(marker: marker, isSelected: marker.code == selected?.destinationCode)
                            .onTapGesture { onMarkerTap(marker.code) }
                    }
                    .annotationTitles(.hidden)
                }
                // M7: a geodesic route with a dot at the origin. MapKit draws the curve and handles the antimeridian.
                if let selected {
                    let origin = CLLocationCoordinate2D(latitude: selected.originLatitude, longitude: selected.originLongitude)
                    let destination = CLLocationCoordinate2D(latitude: selected.destinationLatitude, longitude: selected.destinationLongitude)
                    // MapKit redraws a line only when its points change, not when only its dash phase does. So each step
                    // repeats the destination a different number of times: the same line, drawn afresh with the new phase.
                    let step = dotStep(timeline.date)
                    MapPolyline(coordinates: [origin] + Array(repeating: destination, count: step + 1), contourStyle: .geodesic)
                        .stroke(Color.brand, style: StrokeStyle(lineWidth: 4, lineCap: .round, dash: [0, dotGap], dashPhase: dotGap - CGFloat(step)))
                    Annotation("", coordinate: origin) {
                        Circle().fill(Color.brand).frame(width: 10, height: 10).overlay(Circle().stroke(.white, lineWidth: 2))
                    }
                    let plane = GreatCircle.shared.plane(
                        from: GeoPoint(latitude: selected.originLatitude, longitude: selected.originLongitude),
                        to: GeoPoint(latitude: selected.destinationLatitude, longitude: selected.destinationLongitude),
                        segments: 64
                    )
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: plane.latitude, longitude: plane.longitude)) {
                        PlaneBadge(heading: plane.headingDegrees)
                    }
                    .annotationTitles(.hidden)
                }
            }
            // D27: a quiet, grey map, so the pills and route stand out. Apple's logo and Legal link must stay.
            .mapStyle(.standard(elevation: .flat, emphasis: .muted, pointsOfInterest: .excludingAll))
            .safeAreaPadding(.bottom, bottomInset)
        }
        // M8: the first display uses MapKit's automatic framing, which fits every marker. Recording that camera once
        // turns it into a fixed position, so later filter changes don't reframe the map; the user's own moves reach
        // `camera` through the binding. Recording every move instead caught MapKit mid-animation, because the moving
        // dots redraw the map, and froze a route's framing halfway (found in Step 6.3d).
        .onMapCameraChange(frequency: .onEnd) { context in
            if camera == nil { camera = .camera(context.camera) }
        }
        // M8: the camera moves on an explicit selection, never because filters changed.
        .onChange(of: routeToFit) { _, id in
            guard let id, id != fitted, let flight = model.state.selected, flight.id == id else { return }
            fitted = id
            withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.8)) { camera = routeCamera(flight) }
        }
        .onChange(of: selected?.id) { _, id in if id == nil { fitted = nil } }
    }

    /**
     * As Android's: the dots move a quarter of the line's width (1 pt) every 60 ms, in ten steps per gap. A falling
     * phase moves them forward.
     */
    private func dotStep(_ date: Date) -> Int {
        if reduceMotion { return 0 }
        return Int(date.timeIntervalSinceReferenceDate / 0.06) % 10
    }
}

/** M9: dots the line's width across (4 pt), 2.5 widths apart, as on Android. */
private let dotGap: CGFloat = 10

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
            .accessibilityHidden(true)
    }
}

/** `CODE count·minPts` (UI_SPEC §3), accent when selected. */
private struct MarkerPill: View {
    let marker: DestinationMarker
    let isSelected: Bool

    var body: some View {
        Text("\(marker.code) \(marker.flightCount)·\(PointsFormat.shared.compact(points: marker.minPoints))")
            .font(.appBadge.monospacedDigit())
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .foregroundStyle(isSelected ? Color.onAccent : Color.black)
            .background(isSelected ? Color.accent : Color.white, in: .capsule)
            .overlay(Capsule().strokeBorder(isSelected ? Color.accent : Color.black.opacity(0.6), lineWidth: 1))
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

private func markersSelectedLast(_ markers: [DestinationMarker], _ selectedCode: String?) -> [DestinationMarker] {
    markers.filter { $0.code != selectedCode } + markers.filter { $0.code == selectedCode }
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
