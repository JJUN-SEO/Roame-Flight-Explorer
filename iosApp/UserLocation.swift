import CoreLocation

/**
 * The device's location, for ranking origins (P2) and the home airport's deals (B5). D32: asked when the app opens.
 * - The first time, iOS asks for location while using the app. `Info.plist` asks for approximate accuracy only.
 *   If allowed, the location is read straight away.
 * - Once allowed, each launch reads it again without asking.
 * - After a denial the app never asks again: iOS won't show the dialog twice. Nothing changes and no error
 *   shows, so ranking stays volume-only (P4) and the deals come from every airport.
 */
@MainActor
final class UserLocation: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private let onLocation: @MainActor (_ latitude: Double, _ longitude: Double) -> Void
    private var located = false
    private var reading = false

    /** Create it on the main thread, once, at launch: Core Location calls back on the thread that created the manager. */
    init(onLocation: @escaping @MainActor (_ latitude: Double, _ longitude: Double) -> Void) {
        self.onLocation = onLocation
        super.init()
        manager.desiredAccuracy = kCLLocationAccuracyReduced
        manager.delegate = self
    }

    private func read() {
        guard !located, !reading else { return }
        reading = true
        manager.requestLocation()
    }

    // Called once when the manager is created, with the current permission, and again after the user answers.
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        MainActor.assumeIsolated {
            switch self.manager.authorizationStatus {
            case .notDetermined: self.manager.requestWhenInUseAuthorization()
            case .authorizedWhenInUse, .authorizedAlways: read()
            default: break
            }
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
