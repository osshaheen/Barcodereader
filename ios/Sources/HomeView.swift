import SwiftUI

private enum Route: Hashable {
    case products, customers, orders, newOrder, liveScan
}

private struct HomeItem: Identifiable {
    let id = UUID()
    let title: String
    let systemImage: String
    let route: Route
}

struct HomeView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var sync: SyncService

    private let items: [HomeItem] = [
        HomeItem(title: "طلبية جديدة", systemImage: "cart.badge.plus", route: .newOrder),
        HomeItem(title: "المنتجات", systemImage: "shippingbox", route: .products),
        HomeItem(title: "الزبائن", systemImage: "person.2", route: .customers),
        HomeItem(title: "الطلبيات", systemImage: "doc.text", route: .orders),
        HomeItem(title: "الماسح المباشر", systemImage: "qrcode.viewfinder", route: .liveScan),
    ]

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    if sync.hasRemoteUpdates {
                        Banner(text: "توجد إضافات جديدة من مستخدمين آخرين — اضغط لسحبها",
                               systemImage: "icloud.and.arrow.down", tint: .teal) {
                            Task { await sync.pull(store) }
                        }
                    }
                    if store.pendingCount > 0 {
                        Banner(text: "لديك \(store.pendingCount) عنصر بانتظار الرفع — اضغط للرفع",
                               systemImage: "icloud.and.arrow.up", tint: .orange) {
                            Task { await sync.upload(store) }
                        }
                    }

                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(items) { item in
                            NavigationLink(value: item.route) {
                                VStack(spacing: 12) {
                                    Image(systemName: item.systemImage).font(.system(size: 40))
                                    Text(item.title).font(.headline)
                                }
                                .frame(maxWidth: .infinity, minHeight: 140)
                                .background(Color.accentColor.opacity(0.12))
                                .clipShape(RoundedRectangle(cornerRadius: 16))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(12)
            }
            .navigationTitle("قارئ الباركود المتعدد")
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .products: ProductsView()
                case .customers: CustomersView()
                case .orders: OrdersView()
                case .newOrder: NewOrderView()
                case .liveScan: LiveScanView()
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { Task { await sync.pull(store) } } label: {
                        if sync.syncing { ProgressView() } else { Image(systemName: "arrow.clockwise") }
                    }
                    .disabled(sync.syncing)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { sync.signOut() } label: { Image(systemName: "rectangle.portrait.and.arrow.right") }
                }
            }
            .alert("مزامنة", isPresented: Binding(get: { sync.message != nil }, set: { if !$0 { sync.message = nil } })) {
                Button("حسنًا") { sync.message = nil }
            } message: {
                Text(sync.message ?? "")
            }
        }
    }
}

private struct Banner: View {
    let text: String
    let systemImage: String
    let tint: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: systemImage)
                Text(text).bold()
                Spacer()
            }
            .padding()
            .frame(maxWidth: .infinity)
            .background(tint.opacity(0.18))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .foregroundStyle(tint)
    }
}
