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
    /** Counts the camera's moves, so the route drawn over the map follows it. Only `RouteOverlay` reads it. */
    @State private var cameraMoves = CameraMoves()

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
            }
            // D27: a quiet, grey map, so the pills and route stand out. Apple's logo and Legal link must stay.
            .mapStyle(.standard(elevation: .flat, emphasis: .muted, pointsOfInterest: .excludingAll))
            .safeAreaPadding(.bottom, bottomInset)
            .overlay {
                if let selected {
                    RouteOverlay(
                        flight: selected,
                        destination: state.markers.first { $0.code == selected.destinationCode },
                        proxy: proxy,
                        cameraMoves: cameraMoves,
                        takeOff: takeOff,
                        flying: isActive && !reduceMotion
                    )
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                }
            }
        }
        // D45: the map stays light in dark mode, with its pills, route and plane, as Android's always-light map does.
        .environment(\.colorScheme, .light)
        .onMapCameraChange(frequency: .continuous) { cameraMoves.count &+= 1 }
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

@Observable
private final class CameraMoves {
    var count = 0
}

/**
 * M7, M9: the selected route, drawn over the map: the dots, the origin's dot and the plane, under the destination's pill.
 * MapKit always draws lines under annotations, and the owner asked for the dots to cross the other pills (review of
 * Step 6.3d). Everything is placed with `convert` each time the map moves, and the plane every frame while it flies.
 * Moved every frame as an annotation, MapKit placed the plane unevenly, and annotations that MapKit animated left the
 * route.
 */
private struct RouteOverlay: View {
    let flight: FlightItem
    let destination: DestinationMarker?
    let proxy: MapProxy
    let cameraMoves: CameraMoves
    let takeOff: Date
    /** The plane flies only while the route shows on screen, and rests halfway under Reduce Motion. */
    let flying: Bool

    var body: some View {
        // Read here, so the route is drawn again whenever the map moves. Only the plane is drawn every frame.
        let _ = cameraMoves.count
        ZStack {
            route.stroke(Color.brand, style: StrokeStyle(lineWidth: dotWidth, lineCap: .round, dash: [0, dotGap]))
            if let origin = point(flight.originLatitude, flight.originLongitude) {
                Circle().fill(Color.brand).frame(width: 10, height: 10).overlay(Circle().stroke(.white, lineWidth: 2)).position(origin)
            }
            TimelineView(.animation(minimumInterval: 1.0 / 60, paused: !flying)) { timeline in
                let frame = flying ? PlaneFlight.shared.at(elapsedMillis: Int64(timeline.date.timeIntervalSince(takeOff) * 1000)) : PlaneFlight.shared.resting
                let plane = GreatCircle.shared.plane(from: from, to: to, fraction: frame.fraction)
                if let at = point(plane.latitude, plane.longitude) {
                    PlaneBadge(heading: plane.headingDegrees).opacity(frame.opacity).position(at)
                }
            }
        }
        // The destination's pill is cut out, so MapKit's own pill shows above the route. A copy drawn here instead
        // trailed the map's pill by a frame while dragging, and showed twice.
        .mask {
            ZStack {
                Rectangle()
                if let destination, let at = point(destination.latitude, destination.longitude) {
                    MarkerPill(marker: destination, isSelected: true).position(at).blendMode(.destinationOut)
                }
            }
            .compositingGroup()
        }
        // Over the whole map, including the part the sheet and card cover, so the mask isn't limited to its safe area.
        .ignoresSafeArea()
    }

    private var from: GeoPoint { GeoPoint(latitude: flight.originLatitude, longitude: flight.originLongitude) }
    private var to: GeoPoint { GeoPoint(latitude: flight.destinationLatitude, longitude: flight.destinationLongitude) }

    private func point(_ latitude: Double, _ longitude: Double) -> CGPoint? {
        // Back into -180..180, which MapKit needs; it places the point on the copy of the world in view.
        let normal = longitude - 360 * (longitude / 360).rounded()
        return proxy.convert(CLLocationCoordinate2D(latitude: latitude, longitude: normal), to: .local)
    }

    /**
     * The shared great-circle points. MapKit may place a point far from the view on another copy of the world, so a
     * step longer than half the world's width starts a new piece of line rather than crossing the screen.
     */
    private var route: Path {
        var path = Path()
        guard let a = point(0, 0), let b = point(0, 1) else { return path }
        let halfWorld = abs(b.x - a.x) * 180
        var last: CGPoint?
        for geo in GreatCircle.shared.interpolate(from: from, to: to, segments: 64) {
            guard let next = point(geo.latitude, geo.longitude) else {
                last = nil
                continue
            }
            if let last, abs(next.x - last.x) < halfWorld { path.addLine(to: next) } else { path.move(to: next) }
            last = next
        }
        return path
    }
}

/** M9: the route's dots, 5.5 pt across and 16 pt apart, as Android's. */
private let dotWidth: CGFloat = 5.5
private let dotGap: CGFloat = 16

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
 * The code, with the flight count on a badge marked with a plane (UI_SPEC §3); pink when selected. Drawn the same as
 * Android's. The lowest points are spoken, not drawn (the owner's decision, D44).
 */
private struct MarkerPill: View {
    let marker: DestinationMarker
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 0) {
            Text(marker.code).font(.appBadge)
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
