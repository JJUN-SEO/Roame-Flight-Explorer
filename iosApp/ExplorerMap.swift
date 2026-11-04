import SwiftUI
import MapKit
import Shared

/**
 * The results as destination pills, with the selected flight's great-circle route (UI_SPEC §2.2, M1–M8).
 * `camera` lives in the Explore screen, so switching to the list and back keeps the view (M8).
 */
struct ExplorerMap: View {
    @Environment(ExplorerModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Binding var camera: MapCameraPosition?
    let onMarkerTap: (String) -> Void

    var body: some View {
        let state = model.state
        let selected = state.selected
        Map(position: Binding(get: { camera ?? .automatic }, set: { camera = $0 })) {
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
                MapPolyline(coordinates: [origin, destination], contourStyle: .geodesic)
                    .stroke(Color.brand, lineWidth: 3)
                Annotation("", coordinate: origin) {
                    Circle().fill(Color.brand).frame(width: 10, height: 10).overlay(Circle().stroke(.white, lineWidth: 2))
                }
            }
        }
        // D27: a quiet, grey map, so the pills and route stand out. Apple's logo and Legal link must stay.
        .mapStyle(.standard(elevation: .flat, emphasis: .muted, pointsOfInterest: .excludingAll))
        // M8: the first display uses MapKit's automatic framing, which fits every marker. Recording the camera
        // after each move turns that into a fixed position, so later filter changes don't reframe the map.
        .onMapCameraChange(frequency: .onEnd) { context in
            camera = .camera(context.camera)
        }
        // M8: the camera moves on an explicit selection, never because filters changed.
        .onChange(of: selected?.id) { _, id in
            guard id != nil, let flight = model.state.selected else { return }
            withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.8)) { camera = routeCamera(flight) }
        }
        .overlay(alignment: .top) {
            // M6: no markers, but the map stays usable around the message.
            if state.results.isEmpty {
                VStack(spacing: 4) {
                    Text("No flights match").font(.appTitle)
                    TextLink(text: "Reset filters") { model.store.resetAll() }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Color.card, in: .rect(cornerRadius: 16))
                .shadow(color: .black.opacity(0.15), radius: 6, y: 2)
                .padding(16)
            }
        }
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
 * Fits the route. The shared `GreatCircle` unwraps longitudes, so a route across the antimeridian gives
 * a narrow span around it rather than the long way round the world.
 */
private func routeCamera(_ flight: FlightItem) -> MapCameraPosition {
    let points = GreatCircle.shared.interpolate(
        from: GeoPoint(latitude: flight.originLatitude, longitude: flight.originLongitude),
        to: GeoPoint(latitude: flight.destinationLatitude, longitude: flight.destinationLongitude),
        segments: 64
    )
    return region(lats: points.map(\.latitude), lons: points.map(\.longitude)) ?? .automatic
}

private func region(lats: [Double], lons: [Double]) -> MapCameraPosition? {
    guard let minLat = lats.min(), let maxLat = lats.max(), let minLon = lons.min(), let maxLon = lons.max() else { return nil }
    var centerLon = (minLon + maxLon) / 2
    while centerLon > 180 { centerLon -= 360 }
    while centerLon < -180 { centerLon += 360 }
    let span = MKCoordinateSpan(
        latitudeDelta: min(max((maxLat - minLat) * 1.4, 8), 150),
        longitudeDelta: min(max((maxLon - minLon) * 1.3, 8), 350)
    )
    return .region(MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: (minLat + maxLat) / 2, longitude: centerLon), span: span))
}
