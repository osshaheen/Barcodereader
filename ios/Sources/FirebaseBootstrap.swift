import Foundation
import FirebaseCore

/// Configures Firebase only if a GoogleService-Info.plist is bundled. This lets the app build and
/// run in local-only mode until the user drops their own plist into the project (added in Xcode),
/// instead of crashing on a missing configuration.
enum FirebaseBootstrap {
    private(set) static var isConfigured = false

    static func configureIfPossible() {
        guard !isConfigured else { return }
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            return // no plist yet — run locally only
        }
        FirebaseApp.configure()
        isConfigured = true
    }
}
