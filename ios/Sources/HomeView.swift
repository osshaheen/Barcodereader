import SwiftUI

/// One tile on the home grid.
private struct HomeItem: Identifiable {
    let id = UUID()
    let title: String
    let systemImage: String
}

struct HomeView: View {
    private let items: [HomeItem] = [
        HomeItem(title: "طلبية جديدة", systemImage: "cart.badge.plus"),
        HomeItem(title: "المنتجات", systemImage: "shippingbox"),
        HomeItem(title: "الزبائن", systemImage: "person.2"),
        HomeItem(title: "الطلبيات", systemImage: "doc.text"),
        HomeItem(title: "الماسح المباشر", systemImage: "qrcode.viewfinder"),
        HomeItem(title: "النسخ الاحتياطية", systemImage: "externaldrive"),
    ]

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(items) { item in
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
                }
                .padding(12)
            }
            .navigationTitle("قارئ الباركود المتعدد")
        }
    }
}

#Preview {
    HomeView()
        .environment(\.layoutDirection, .rightToLeft)
}
