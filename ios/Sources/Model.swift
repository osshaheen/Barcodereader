import Foundation

struct Product: Identifiable, Codable, Hashable {
    var id: String = UUID().uuidString
    var barcode: String = ""
    var name: String = ""
    var price: Double = 0
    var createdAt: Date = Date()
}

struct Customer: Identifiable, Codable, Hashable {
    var id: String = UUID().uuidString
    var name: String = ""
    var phone: String = ""
    var note: String = ""
    var createdAt: Date = Date()
}

struct OrderItem: Identifiable, Codable, Hashable {
    var id: String = UUID().uuidString
    var barcode: String = ""
    var name: String = ""
    var price: Double = 0
    var quantity: Int = 0
    var lineTotal: Double { price * Double(quantity) }
}

struct Order: Identifiable, Codable, Hashable {
    var id: String = UUID().uuidString
    var customerId: String? = nil
    var note: String = ""
    var createdAt: Date = Date()
    var items: [OrderItem] = []
    var total: Double { items.reduce(0) { $0 + $1.lineTotal } }
    var itemCount: Int { items.reduce(0) { $0 + $1.quantity } }
}

struct Payment: Identifiable, Codable, Hashable {
    var id: String = UUID().uuidString
    var customerId: String = ""
    var amount: Double = 0
    var note: String = ""
    var createdAt: Date = Date()
}
