import Foundation
import UIKit
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore
import GoogleSignIn

/// Handles authentication (Google Sign-In + allowlist) and the shared-shop Firestore sync
/// (manual upload of local changes, manual pull of others' additions). Mirrors the Android app.
@MainActor
final class SyncService: ObservableObject {
    enum Phase { case loading, needsLogin, notAllowed, authorized }

    @Published var phase: Phase = .loading
    @Published var email = ""
    @Published var isSuperAdmin = false
    @Published var syncing = false
    @Published var message: String?
    @Published var hasRemoteUpdates = false

    static let superAdminEmail = "osahsh991@gmail.com"

    private var db: Firestore { Firestore.firestore() }
    private func shopRoot() -> DocumentReference { db.collection("shop").document("main") }
    private func col(_ name: String) -> CollectionReference { shopRoot().collection(name) }
    private var metaListener: ListenerRegistration?

    private var lastPulled: Double {
        get { UserDefaults.standard.double(forKey: "lastPulled") }
        set { UserDefaults.standard.set(newValue, forKey: "lastPulled") }
    }

    // MARK: - Lifecycle

    func start() {
        // Local-only mode when no GoogleService-Info.plist is bundled yet.
        guard FirebaseBootstrap.isConfigured else { phase = .authorized; return }
        if let user = Auth.auth().currentUser, let email = user.email {
            Task { await evaluate(email) }
        } else {
            phase = .needsLogin
        }
    }

    // MARK: - Auth

    func signIn() {
        guard FirebaseBootstrap.isConfigured, let clientID = FirebaseApp.app()?.options.clientID else {
            message = "Firebase غير مهيأ — أضف GoogleService-Info.plist"
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        guard let presenting = Self.rootViewController() else { return }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenting) { result, error in
            guard error == nil, let user = result?.user, let idToken = user.idToken?.tokenString else {
                Task { @MainActor in self.message = "تعذّر تسجيل الدخول" }
                return
            }
            let credential = GoogleAuthProvider.credential(withIDToken: idToken,
                                                           accessToken: user.accessToken.tokenString)
            Auth.auth().signIn(with: credential) { authResult, err in
                guard err == nil, let email = authResult?.user.email else {
                    Task { @MainActor in self.message = "تعذّر تسجيل الدخول" }
                    return
                }
                Task { @MainActor in await self.evaluate(email) }
            }
        }
    }

    func signOut() {
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
        metaListener?.remove()
        isSuperAdmin = false
        phase = .needsLogin
    }

    private func evaluate(_ email: String) async {
        self.email = email
        let lower = email.lowercased()
        if lower == Self.superAdminEmail {
            isSuperAdmin = true
            phase = .authorized
            startMetaListener()
            return
        }
        do {
            let doc = try await db.collection("allowlist").document(lower).getDocument()
            if doc.exists {
                isSuperAdmin = (doc.get("admin") as? Bool) == true
                phase = .authorized
                startMetaListener()
            } else {
                try? await db.collection("accessRequests").document(lower)
                    .setData(["email": lower, "requestedAt": Date().timeIntervalSince1970])
                phase = .notAllowed
            }
        } catch {
            phase = .notAllowed
        }
    }

    // MARK: - Sync

    func upload(_ store: Store) async {
        guard FirebaseBootstrap.isConfigured else { return }
        syncing = true
        defer { syncing = false }
        let ids = store.pending
        var uploaded: [String] = []
        for c in store.customers where ids.contains(c.id) {
            if (try? await col("customers").document(c.id).setData(c.dict)) != nil { uploaded.append(c.id) }
        }
        for p in store.products where ids.contains(p.id) {
            if (try? await col("products").document(p.id).setData(p.dict)) != nil { uploaded.append(p.id) }
        }
        for o in store.orders where ids.contains(o.id) {
            if (try? await col("orders").document(o.id).setData(o.dict)) != nil { uploaded.append(o.id) }
        }
        for p in store.payments where ids.contains(p.id) {
            if (try? await col("payments").document(p.id).setData(p.dict)) != nil { uploaded.append(p.id) }
        }
        store.markUploaded(uploaded)
        await bumpAndRefresh(store)
        message = uploaded.isEmpty ? "تعذّر الرفع — تحقّق من الاتصال" : "تم رفع \(uploaded.count) عنصر"
    }

    func pull(_ store: Store) async {
        guard FirebaseBootstrap.isConfigured else { return }
        syncing = true
        defer { syncing = false }
        do {
            let products = try await col("products").getDocuments().documents.compactMap { Product(dict: $0.data()) }
            let customers = try await col("customers").getDocuments().documents.compactMap { Customer(dict: $0.data()) }
            let orders = try await col("orders").getDocuments().documents.compactMap { Order(dict: $0.data()) }
            let payments = try await col("payments").getDocuments().documents.compactMap { Payment(dict: $0.data()) }
            store.mergePulled(products: products, customers: customers, orders: orders, payments: payments)
            let meta = try await shopRoot().getDocument()
            lastPulled = (meta.get("lastChange") as? Double) ?? Date().timeIntervalSince1970
            hasRemoteUpdates = false
        } catch {
            message = "تعذّر السحب — تحقّق من الاتصال"
        }
    }

    private func bumpAndRefresh(_ store: Store) async {
        try? await shopRoot().setData(["lastChange": Date().timeIntervalSince1970], merge: true)
        await pull(store)
    }

    private func startMetaListener() {
        guard FirebaseBootstrap.isConfigured else { return }
        metaListener?.remove()
        metaListener = shopRoot().addSnapshotListener { [weak self] snap, _ in
            let remote = (snap?.get("lastChange") as? Double) ?? 0
            Task { @MainActor in
                guard let self else { return }
                self.hasRemoteUpdates = remote > self.lastPulled + 1
            }
        }
    }

    // MARK: - Helpers

    @MainActor
    static func rootViewController() -> UIViewController? {
        guard let scene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene
                ?? UIApplication.shared.connectedScenes.first as? UIWindowScene,
              var top = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController
                ?? scene.windows.first?.rootViewController else { return nil }
        while let presented = top.presentedViewController { top = presented }
        return top
    }
}
