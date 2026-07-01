import Foundation

// Manual dictionary <-> model mapping for Firestore. Dates are stored as seconds since 1970
// (Double). Ids are stable local UUIDs, so orders/payments reference customers by the same id
// on every device — no id remapping needed.

extension Product {
    var dict: [String: Any] {
        ["id": id, "barcode": barcode, "name": name, "price": price, "createdAt": createdAt.timeIntervalSince1970]
    }
    init?(dict: [String: Any]) {
        guard let id = dict["id"] as? String else { return nil }
        self.init(id: id,
                  barcode: dict["barcode"] as? String ?? "",
                  name: dict["name"] as? String ?? "",
                  price: dict["price"] as? Double ?? 0,
                  createdAt: Date(timeIntervalSince1970: dict["createdAt"] as? Double ?? 0))
    }
}

extension Customer {
    var dict: [String: Any] {
        ["id": id, "name": name, "phone": phone, "note": note, "createdAt": createdAt.timeIntervalSince1970]
    }
    init?(dict: [String: Any]) {
        guard let id = dict["id"] as? String else { return nil }
        self.init(id: id,
                  name: dict["name"] as? String ?? "",
                  phone: dict["phone"] as? String ?? "",
                  note: dict["note"] as? String ?? "",
                  createdAt: Date(timeIntervalSince1970: dict["createdAt"] as? Double ?? 0))
    }
}

extension OrderItem {
    var dict: [String: Any] {
        ["id": id, "barcode": barcode, "name": name, "price": price, "quantity": quantity]
    }
    init(dict: [String: Any]) {
        self.init(id: dict["id"] as? String ?? UUID().uuidString,
                  barcode: dict["barcode"] as? String ?? "",
                  name: dict["name"] as? String ?? "",
                  price: dict["price"] as? Double ?? 0,
                  quantity: dict["quantity"] as? Int ?? 0)
    }
}

extension Order {
    var dict: [String: Any] {
        ["id": id, "customerId": customerId as Any, "note": note,
         "createdAt": createdAt.timeIntervalSince1970,
         "items": items.map { $0.dict }]
    }
    init?(dict: [String: Any]) {
        guard let id = dict["id"] as? String else { return nil }
        let rawItems = dict["items"] as? [[String: Any]] ?? []
        self.init(id: id,
                  customerId: dict["customerId"] as? String,
                  note: dict["note"] as? String ?? "",
                  createdAt: Date(timeIntervalSince1970: dict["createdAt"] as? Double ?? 0),
                  items: rawItems.map { OrderItem(dict: $0) })
    }
}

extension Payment {
    var dict: [String: Any] {
        ["id": id, "customerId": customerId, "amount": amount, "note": note, "createdAt": createdAt.timeIntervalSince1970]
    }
    init?(dict: [String: Any]) {
        guard let id = dict["id"] as? String else { return nil }
        self.init(id: id,
                  customerId: dict["customerId"] as? String ?? "",
                  amount: dict["amount"] as? Double ?? 0,
                  note: dict["note"] as? String ?? "",
                  createdAt: Date(timeIntervalSince1970: dict["createdAt"] as? Double ?? 0))
    }
}
