import SwiftUI

private enum Route: Hashable {
    case products, customers, orders, newOrder
}

private struct HomeItem: Identifiable {
    let id = UUID()
    let title: String
    let systemImage: String
    let route: Route
}

struct HomeView: View {
    private let items: [HomeItem] = [
        HomeItem(title: "طلبية جديدة", systemImage: "cart.badge.plus", route: .newOrder),
        HomeItem(title: "المنتجات", systemImage: "shippingbox", route: .products),
        HomeItem(title: "الزبائن", systemImage: "person.2", route: .customers),
        HomeItem(title: "الطلبيات", systemImage: "doc.text", route: .orders),
    ]

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(items) { item in
                        NavigationLink(value: item.route) {
                            VStack(spacing: 12) {
                                Image(systemName: item.systemImage)
                                    .font(.system(size: 40))
                                Text(item.title)
                                    .font(.headline)
                            }
                            .frame(maxWidth: .infinity, minHeight: 140)
                            .background(Color.accentColor.opacity(0.12))
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                        }
                        .buttonStyle(.plain)
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
                }
            }
        }
    }
}
