import SwiftUI

struct ProductsView: View {
    @EnvironmentObject var store: Store
    @State private var editing: Product?
    @State private var showAdd = false
    @State private var query = ""

    private var filtered: [Product] {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return store.products }
        return store.products.filter { $0.name.localizedCaseInsensitiveContains(q) || $0.barcode.contains(q) }
    }

    var body: some View {
        List {
            ForEach(filtered) { p in
                Button {
                    editing = p
                } label: {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(p.name).font(.headline)
                            Text(p.barcode).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(Format.money(p.price)).bold()
                    }
                }
                .buttonStyle(.plain)
            }
            .onDelete { idx in
                idx.map { filtered[$0] }.forEach { store.delete(product: $0) }
            }
        }
        .searchable(text: $query, prompt: "بحث بالاسم أو الباركود")
        .navigationTitle("المنتجات")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { showAdd = true } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $showAdd) {
            ProductEditor(product: Product()) { store.upsert($0) }
        }
        .sheet(item: $editing) { p in
            ProductEditor(product: p) { store.upsert($0) }
        }
    }
}

struct ProductEditor: View {
    @Environment(\.dismiss) private var dismiss
    @State var product: Product
    var onSave: (Product) -> Void

    var body: some View {
        NavigationStack {
            Form {
                TextField("الباركود", text: $product.barcode)
                TextField("اسم المنتج", text: $product.name)
                TextField("السعر", value: $product.price, format: .number)
                    .keyboardType(.decimalPad)
            }
            .navigationTitle("منتج")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("إلغاء") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("حفظ") { onSave(product); dismiss() }
                        .disabled(product.name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
