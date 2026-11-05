import SwiftUI
import MapKit
import Shared

/**
 * The results as destination pills, with the selected flight's route as an arc of dots (D50) and a plane flying along
 * it (UI_SPEC §2.1, M1–M9). `camera` lives in the results screen, so it survives leaving and coming back (M8).
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
    /** The selected destination's pill as MapKit draws it, measured, so the route can be cut out under it. */
    @State private var pillSize = CGSize.zero

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
                    let destination = state.markers.first { $0.code == selected.destinationCode }
                    RouteLayer(
                        flight: selected,
                        destination: destination.map { CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) },
                        pillSize: pillSize,
                        convert: { proxy.convert($0, to: .local) },
                        takeOff: takeOff,
                        flying: !reduceMotion,
                        isActive: isActive
                    )
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                    // Over the whole map, including the part the sheet and card cover.
                    .ignoresSafeArea()
                    .background {
                        if let destination {
                            MarkerPill(marker: destination, isSelected: true)
                                .hidden()
                                .accessibilityHidden(true)
                                .onGeometryChange(for: CGSize.self) { $0.size } action: { pillSize = $0 }
                        }
                    }
                }
            }
        }
        // D45: the map stays light in dark mode, with its pills, route and plane, as Android's always-light map does.
        .environment(\.colorScheme, .light)
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

/**
 * M7, M9: the selected route over the map: the dots, the origin's dot and the plane, with the destination's pill cut out
 * so MapKit's own pill shows above them. MapKit always draws lines under annotations, and the owner asked for the dots to
 * cross the other pills (review of Step 6.3d).
 *
 * Core Animation layers follow the map every frame from a display link, outside SwiftUI and out of reach of touches.
 * Drawn in SwiftUI and redrawn on every camera change and plane frame, the route held up the map's own drags: on the
 * owner's iPhone they only took hold on the line, and on the simulator a drag moved the map less than half as far
 * (review of Step 6.3e). Moved every frame as an annotation, MapKit placed the plane unevenly.
 */
private struct RouteLayer: UIViewRepresentable {
    let flight: FlightItem
    let destination: CLLocationCoordinate2D?
    let pillSize: CGSize
    let convert: (CLLocationCoordinate2D) -> CGPoint?
    let takeOff: Date
    /** False under Reduce Motion, when the plane rests halfway. */
    let flying: Bool
    /** The results are on screen; otherwise the display link stops. */
    let isActive: Bool

    func makeUIView(context: Context) -> RouteLayerView { RouteLayerView() }

    func updateUIView(_ view: RouteLayerView, context: Context) {
        view.configure(self)
    }

    static func dismantleUIView(_ view: RouteLayerView, coordinator: ()) {
        view.stopFollowing()
    }
}

private final class RouteLayerView: UIView {
    private let content = CALayer()
    private let dots = CAShapeLayer()
    private let origin = CAShapeLayer()
    private let plane = CALayer()
    private let cutOut = CAShapeLayer()
    private var link: CADisplayLink?
    private var route: RouteLayer?
    private var from = GeoPoint(latitude: 0, longitude: 0)
    private var to = GeoPoint(latitude: 0, longitude: 0)
    /** The shared arc's points (M7), their longitudes back in -180..180 for MapKit. */
    private var points: [CLLocationCoordinate2D] = []
    /** Where the route was last drawn, so a still map with a resting plane isn't drawn again every frame. */
    private var drawnAt: [CGPoint?] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        backgroundColor = .clear
        // The light theme's pink, as the map stays light (D45).
        let brand = UIColor(Color.brand).resolvedColor(with: UITraitCollection(userInterfaceStyle: .light)).cgColor
        dots.fillColor = nil
        dots.strokeColor = brand
        dots.lineWidth = dotWidth
        dots.lineCap = .round
        dots.lineDashPattern = [0, NSNumber(value: Double(dotGap))]
        origin.path = UIBezierPath(ovalIn: CGRect(x: -5, y: -5, width: 10, height: 10)).cgPath
        origin.fillColor = brand
        origin.strokeColor = UIColor.white.cgColor
        origin.lineWidth = 2
        plane.bounds = CGRect(x: 0, y: 0, width: 32, height: 32)
        cutOut.fillRule = .evenOdd
        content.addSublayer(dots)
        content.addSublayer(origin)
        content.addSublayer(plane)
        content.mask = cutOut
        layer.addSublayer(content)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not used")
    }

    func configure(_ route: RouteLayer) {
        if route.flight.id != self.route?.flight.id {
            from = GeoPoint(latitude: route.flight.originLatitude, longitude: route.flight.originLongitude)
            to = GeoPoint(latitude: route.flight.destinationLatitude, longitude: route.flight.destinationLongitude)
            points = RouteArc.shared.interpolate(from: from, to: to, segments: 64).map { coordinate($0.latitude, $0.longitude) }
        }
        self.route = route
        if plane.contents == nil {
            let renderer = ImageRenderer(content: PlaneBadge(heading: 0))
            renderer.scale = traitCollection.displayScale
            plane.contents = renderer.cgImage
        }
        drawnAt = []
        updateFollowing()
        draw()
    }

    func stopFollowing() {
        link?.invalidate()
        link = nil
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        updateFollowing()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        content.frame = bounds
        cutOut.frame = content.bounds
        CATransaction.commit()
        drawnAt = []
        draw()
    }

    /** The map can move at any time, so the layers follow it every frame while the results are on screen. */
    private func updateFollowing() {
        let follow = window != nil && route?.isActive == true
        if follow, link == nil {
            let link = CADisplayLink(target: self, selector: #selector(frameDue))
            link.add(to: .main, forMode: .common)
            self.link = link
        } else if !follow {
            stopFollowing()
        }
    }

    @objc private func frameDue() {
        draw()
    }

    private func draw() {
        guard let route, !points.isEmpty, bounds.width > 0 else { return }
        let ends = [points.first, points.last].map { $0.flatMap(route.convert) }
        if !route.flying, ends == drawnAt { return }
        drawnAt = ends
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        defer { CATransaction.commit() }

        // MapKit may place a point far from the view on another copy of the world, so a step longer than half the
        // world's width starts a new piece of line rather than crossing the screen.
        let line = UIBezierPath()
        if let a = route.convert(coordinate(0, 0)), let b = route.convert(coordinate(0, 1)) {
            let halfWorld = abs(b.x - a.x) * 180
            var last: CGPoint?
            for point in points {
                guard let next = route.convert(point) else {
                    last = nil
                    continue
                }
                if let last, abs(next.x - last.x) < halfWorld { line.addLine(to: next) } else { line.move(to: next) }
                last = next
            }
        }
        dots.path = line.cgPath
        origin.isHidden = ends[0] == nil
        if let start = ends[0] { origin.position = start }

        let frame = route.flying
            ? PlaneFlight.shared.at(elapsedMillis: Int64(Date.now.timeIntervalSince(route.takeOff) * 1000))
            : PlaneFlight.shared.resting
        let at = RouteArc.shared.plane(from: from, to: to, fraction: frame.fraction)
        if let point = route.convert(coordinate(at.latitude, at.longitude)) {
            plane.isHidden = false
            plane.position = point
            plane.setAffineTransform(CGAffineTransform(rotationAngle: at.headingDegrees * .pi / 180))
            plane.opacity = Float(frame.opacity)
        } else {
            plane.isHidden = true
        }

        // The destination's pill is cut out, so MapKit's own pill shows above the route.
        let mask = UIBezierPath(rect: bounds)
        if let destination = route.destination, route.pillSize != .zero, let point = route.convert(destination) {
            let size = route.pillSize
            let pill = CGRect(x: point.x - size.width / 2, y: point.y - size.height / 2, width: size.width, height: size.height)
            mask.append(UIBezierPath(roundedRect: pill, cornerRadius: size.height / 2))
        }
        cutOut.path = mask.cgPath
    }

    /** Back into -180..180, which MapKit needs; it places the point on the copy of the world in view. */
    private func coordinate(_ latitude: Double, _ longitude: Double) -> CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude - 360 * (longitude / 360).rounded())
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
        .accessibilityLabel("\(marker.city), \(flightCount(Int(marker.flightCount))), from \(Format.spokenPoints(marker.minPoints))")
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
                    .accessibilityLabel("\(marker.city), \(marker.code), \(flightCount(Int(marker.flightCount))) from \(Format.spokenPoints(marker.minPoints))")
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
 * Fits the route: a map rectangle around the shared `RouteArc` points, with room around it. The points' longitudes are
 * unwrapped, so a route across the antimeridian gives one rectangle running past the world's edge, which MapKit
 * accepts, rather than the long way round the world. MapKit fits a rectangle exactly, where it shortened regions here.
 */
private func routeCamera(_ flight: FlightItem) -> MapCameraPosition {
    let points = RouteArc.shared.interpolate(
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
