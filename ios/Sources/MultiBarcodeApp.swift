import SwiftUI

@main
struct MultiBarcodeApp: App {
    var body: some Scene {
        WindowGroup {
            HomeView()
                // The whole app is Arabic, so lay everything out right-to-left.
                .environment(\.layoutDirection, .rightToLeft)
        }
    }
}
