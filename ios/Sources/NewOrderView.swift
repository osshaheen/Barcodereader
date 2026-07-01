import SwiftUI

struct NewOrderView: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss

    @State private var items: [OrderItem] = []
    @State private var selectedCustomerId: String? = nil
    @State private var barcodeInput = ""
    @State private var pendingUnknown: String? = nil   // barcode with no product yet
    @State private var showScanner = false

    private var total: Double { items.reduce(0) { $0 + $1.lineTotal } }

    var body: some View {
        Form {
            Section("الزبون") {
                Picker("الزبون", selection: $selectedCustomerId) {
                    Text("بيع نقدي").tag(String?.none)
                    ForEach(store.customers) { c in
                        Text(c.name).tag(String?.some(c.id))
                    }
                }
            }

            Section("إضافة صنف") {
                Button {
                    showScanner = true
                } label: {
                    Label("مسح بالكاميرا", systemImage: "barcode.viewfinder")
                }
                HStack {
                    TextField("أو أدخل الباركود يدويًا", text: $barcodeInput)
                        .keyboardType(.numbersAndPunctuation)
                        .onSubmit(addByInput)
                    Button("إضافة", action: addByInput)
                        .disabled(barcodeInput.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }

            Section("السلة (\(items.reduce(0) { $0 + $1.quantity }) صنف)") {
                if items.isEmpty { Text("لم تُضف أصناف بعد.").foregroundStyle(.secondary) }
                ForEach($items) { $item in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(item.name).font(.headline)
                            Text(Format.money(item.price)).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Stepper(value: $item.quantity, in: 1...999) {
                            Text("×\(item.quantity)")
                        }
                        .frame(width: 140)
                    }
                }
                .onDelete { items.remove(atOffsets: $0) }
            }

            Section {
                HStack { Text("الإجمالي").bold(); Spacer(); Text(Format.money(total)).bold() }
                Button("حفظ الطلبية") { save() }
                    .disabled(items.isEmpty)
            }
        }
        .navigationTitle("طلبية جديدة")
        .fullScreenCover(isPresented: $showScanner) {
            ScannerSheet { code in
                if let p = store.product(barcode: code) {
                    addProductToCart(p)   // keep scanning
                } else {
                    showScanner = false
                    pendingUnknown = code // ask to add the product
                }
            }
        }
        .sheet(isPresented: Binding(get: { pendingUnknown != nil }, set: { if !$0 { pendingUnknown = nil } })) {
            NewProductInline(barcode: pendingUnknown ?? "") { product in
                store.upsert(product)
                addProductToCart(product)
                pendingUnknown = nil
            } onCancel: { pendingUnknown = nil }
        }
    }

    private func addByInput() {
        let code = barcodeInput.trimmingCharacters(in: .whitespaces)
        guard !code.isEmpty else { return }
        barcodeInput = ""
        addByBarcode(code)
    }

    /// Shared entry point (the camera scanner will call this too in Phase B).
    private func addByBarcode(_ code: String) {
        if let p = store.product(barcode: code) {
            addProductToCart(p)
        } else {
            pendingUnknown = code
        }
    }

    private func addProductToCart(_ p: Product) {
        if let i = items.firstIndex(where: { $0.barcode == p.barcode }) {
            items[i].quantity += 1
        } else {
            items.append(OrderItem(barcode: p.barcode, name: p.name, price: p.price, quantity: 1))
        }
    }

    private func save() {
        store.add(Order(customerId: selectedCustomerId, items: items))
        dismiss()
    }
}

/// "Add product" form shown when scanning/entering an unknown barcode.
private struct NewProductInline: View {
    @State private var name = ""
    @State private var price: Double = 0
    let barcode: String
    var onSave: (Product) -> Void
    var onCancel: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                LabeledContent("الباركود", value: barcode)
                TextField("اسم المنتج", text: $name)
                TextField("السعر", value: $price, format: .number).keyboardType(.decimalPad)
            }
            .navigationTitle("منتج غير معروف")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("إلغاء") { onCancel() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("إضافة") { onSave(Product(barcode: barcode, name: name, price: price)) }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
