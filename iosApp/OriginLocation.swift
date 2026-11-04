import CoreLocation

/**
 * The device's location for ranking origins (D16, D30). Call `fromFocused()` when From gets focus:
 * - The first time, iOS asks for location while using the app. `Info.plist` asks for approximate accuracy only.
 *   If allowed, the location is read straight away.
 * - Once allowed, each launch reads it again without asking.
 * - After a denial the app never asks again: iOS won't show the dialog twice. Nothing changes and no error
 *   shows, so ranking stays volume-only (P4).
 */
@MainActor
final class OriginLocation: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private let onLocation: @MainActor (_ latitude: Double, _ longitude: Double) -> Void
    private var located = false
    private var reading = false

    /** Create it on the main thread: Core Location calls back on the thread that created the manager. */
    init(onLocation: @escaping @MainActor (_ latitude: Double, _ longitude: Double) -> Void) {
        self.onLocation = onLocation
        super.init()
        manager.desiredAccuracy = kCLLocationAccuracyReduced
        manager.delegate = self
    }

    func fromFocused() {
        switch manager.authorizationStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways: read()
        default: break
        }
    }

    private func read() {
        guard !located, !reading else { return }
        reading = true
        manager.requestLocation()
    }

    // Also called once when the manager is created, so a later launch with the permission granted reads quietly.
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        MainActor.assumeIsolated {
            let status = self.manager.authorizationStatus
            if status == .authorizedWhenInUse || status == .authorizedAlways { read() }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let coordinate = locations.last?.coordinate else { return }
        MainActor.assumeIsolated {
            reading = false
            located = true
            onLocation(coordinate.latitude, coordinate.longitude)
        }
    }

    // No fix, or the user turned location off: nothing changes and no error shows.
    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        MainActor.assumeIsolated { reading = false }
    }
}
