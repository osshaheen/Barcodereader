import SwiftUI

struct OrdersView: View {
    @EnvironmentObject var store: Store

    private var orders: [Order] { store.orders.sorted { $0.createdAt > $1.createdAt } }

    private func customerName(_ id: String?) -> String {
        guard let id, let c = store.customers.first(where: { $0.id == id }) else { return "بيع نقدي" }
        return c.name
    }

    var body: some View {
        List(orders) { o in
            HStack {
                VStack(alignment: .leading) {
                    Text(customerName(o.customerId)).font(.headline)
                    Text(Format.dateTime(o.createdAt)).font(.caption).foregroundStyle(.secondary)
                    Text("\(o.itemCount) صنف").font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Text(Format.money(o.total)).bold()
            }
        }
        .navigationTitle("الطلبيات")
        .overlay {
            if orders.isEmpty { Text("لا توجد طلبيات بعد.").foregroundStyle(.secondary) }
        }
    }
}
