import SwiftUI

@main
struct MultiBarcodeApp: App {
    @StateObject private var store = Store()

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(store)
                // The whole app is Arabic, so lay everything out right-to-left.
                .environment(\.layoutDirection, .rightToLeft)
        }
    }
}
