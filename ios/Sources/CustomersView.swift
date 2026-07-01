import SwiftUI

struct CustomersView: View {
    @EnvironmentObject var store: Store
    @State private var showAdd = false
    @State private var query = ""

    private var filtered: [Customer] {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return store.customers }
        return store.customers.filter { $0.name.localizedCaseInsensitiveContains(q) || $0.phone.contains(q) }
    }

    var body: some View {
        List {
            ForEach(filtered) { c in
                NavigationLink(value: c) {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(c.name).font(.headline)
                            if !c.phone.isEmpty {
                                Text(c.phone).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        BalanceBadge(balance: store.balance(for: c.id))
                    }
                }
            }
            .onDelete { idx in
                idx.map { filtered[$0] }.forEach { store.delete(customer: $0) }
            }
        }
        .searchable(text: $query, prompt: "بحث بالاسم أو الهاتف")
        .navigationTitle("الزبائن")
        .navigationDestination(for: Customer.self) { CustomerDetailView(customerId: $0.id) }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { showAdd = true } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $showAdd) {
            CustomerEditor(customer: Customer()) { store.upsert($0) }
        }
    }
}

/// Small colored label: red مدين (owes), green دائن (credit), or مسدّد.
struct BalanceBadge: View {
    let balance: Double
    var body: some View {
        if balance > 0.001 {
            Text("مدين \(Format.money(balance))").foregroundStyle(.red).bold()
        } else if balance < -0.001 {
            Text("دائن \(Format.money(-balance))").foregroundStyle(.green).bold()
        } else {
            Text("مسدّد").foregroundStyle(.green)
        }
    }
}

struct CustomerEditor: View {
    @Environment(\.dismiss) private var dismiss
    @State var customer: Customer
    var onSave: (Customer) -> Void

    var body: some View {
        NavigationStack {
            Form {
                TextField("الاسم", text: $customer.name)
                TextField("الهاتف", text: $customer.phone).keyboardType(.phonePad)
                TextField("ملاحظات", text: $customer.note)
            }
            .navigationTitle("زبون")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("إلغاء") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("حفظ") { onSave(customer); dismiss() }
                        .disabled(customer.name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
