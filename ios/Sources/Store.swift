import Foundation
import Combine

/// Local, on-device data store (persisted as JSON in UserDefaults). This is the Phase A backend;
/// Firebase syncing is layered on later. The UI only ever talks to this object.
final class Store: ObservableObject {
    @Published var products: [Product] = []
    @Published var customers: [Customer] = []
    @Published var orders: [Order] = []
    @Published var payments: [Payment] = []

    /// Ids of records created/edited locally that have not been uploaded to the shared DB yet.
    @Published var pending: Set<String> = []
    var pendingCount: Int { pending.count }

    init() { load() }

    // MARK: - Derived

    /// Positive = customer owes us (مدين); negative = customer has credit (دائن).
    func balance(for customerId: String) -> Double {
        let o = orders.filter { $0.customerId == customerId }.reduce(0) { $0 + $1.total }
        let p = payments.filter { $0.customerId == customerId }.reduce(0) { $0 + $1.amount }
        return o - p
    }

    func product(barcode: String) -> Product? { products.first { $0.barcode == barcode } }

    // MARK: - Mutations

    func upsert(_ p: Product) { upsert(&products, p); pending.insert(p.id); save() }
    func upsert(_ c: Customer) { upsert(&customers, c); pending.insert(c.id); save() }
    func add(_ o: Order) { orders.append(o); pending.insert(o.id); save() }
    func add(_ p: Payment) { payments.append(p); pending.insert(p.id); save() }
    func delete(product: Product) { products.removeAll { $0.id == product.id }; pending.remove(product.id); save() }
    func delete(customer: Customer) { customers.removeAll { $0.id == customer.id }; pending.remove(customer.id); save() }
    func delete(payment: Payment) { payments.removeAll { $0.id == payment.id }; pending.remove(payment.id); save() }

    /// Merge records pulled from the shared DB (they are already uploaded, so not pending).
    func mergePulled(products: [Product], customers: [Customer], orders: [Order], payments: [Payment]) {
        for p in products where !pending.contains(p.id) { upsert(&self.products, p) }
        for c in customers where !pending.contains(c.id) { upsert(&self.customers, c) }
        for o in orders where !pending.contains(o.id) { upsert(&self.orders, o) }
        for p in payments where !pending.contains(p.id) { upsert(&self.payments, p) }
        save()
    }

    /// Called after a successful upload of the given ids.
    func markUploaded(_ ids: [String]) { ids.forEach { pending.remove($0) }; save() }

    /// Backup-then-wipe a customer's orders/payments ("تصفير الحساب").
    func resetAccount(_ customerId: String) {
        orders.removeAll { $0.customerId == customerId }
        payments.removeAll { $0.customerId == customerId }
        save()
    }

    private func upsert<T: Identifiable>(_ arr: inout [T], _ item: T) where T.ID == String {
        if let i = arr.firstIndex(where: { $0.id == item.id }) { arr[i] = item } else { arr.append(item) }
    }

    // MARK: - Persistence

    private func save() {
        let d = UserDefaults.standard
        d.set(try? JSONEncoder().encode(products), forKey: "products")
        d.set(try? JSONEncoder().encode(customers), forKey: "customers")
        d.set(try? JSONEncoder().encode(orders), forKey: "orders")
        d.set(try? JSONEncoder().encode(payments), forKey: "payments")
        d.set(Array(pending), forKey: "pending")
    }

    private func load() {
        let d = UserDefaults.standard
        products = decode(d.data(forKey: "products")) ?? []
        customers = decode(d.data(forKey: "customers")) ?? []
        orders = decode(d.data(forKey: "orders")) ?? []
        payments = decode(d.data(forKey: "payments")) ?? []
        pending = Set(d.stringArray(forKey: "pending") ?? [])
    }

    private func decode<T: Decodable>(_ data: Data?) -> T? {
        guard let data else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
}
