import SwiftUI
import GoogleSignIn

@main
struct MultiBarcodeApp: App {
    @StateObject private var store = Store()
    @StateObject private var sync = SyncService()

    init() {
        FirebaseBootstrap.configureIfPossible()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(sync)
                // The whole app is Arabic, so lay everything out right-to-left.
                .environment(\.layoutDirection, .rightToLeft)
                .onOpenURL { url in _ = GIDSignIn.sharedInstance.handle(url) }
        }
    }
}

private struct RootView: View {
    @EnvironmentObject var sync: SyncService

    var body: some View {
        Group {
            switch sync.phase {
            case .loading:
                ProgressView()
            case .needsLogin, .notAllowed:
                LoginView()
            case .authorized:
                HomeView()
            }
        }
        .task { sync.start() }
    }
}
