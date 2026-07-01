import SwiftUI

struct CustomerDetailView: View {
    @EnvironmentObject var store: Store
    let customerId: String

    @State private var showPayment = false
    @State private var showDetails = false
    @State private var confirmReset = false

    private var customer: Customer? { store.customers.first { $0.id == customerId } }
    private var orders: [Order] { store.orders.filter { $0.customerId == customerId }.sorted { $0.createdAt > $1.createdAt } }
    private var payments: [Payment] { store.payments.filter { $0.customerId == customerId }.sorted { $0.createdAt > $1.createdAt } }
    private var ordersTotal: Double { orders.reduce(0) { $0 + $1.total } }
    private var paymentsTotal: Double { payments.reduce(0) { $0 + $1.amount } }
    private var balance: Double { ordersTotal - paymentsTotal }

    var body: some View {
        List {
            Section {
                Button { showDetails = true } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        BalanceBadge(balance: balance).font(.title3)
                        Text("اضغط لعرض كل التفاصيل").font(.caption).foregroundStyle(.secondary)
                        HStack { Text("إجمالي الطلبيات"); Spacer(); Text(Format.money(ordersTotal)) }
                        HStack { Text("إجمالي المدفوعات"); Spacer(); Text(Format.money(paymentsTotal)) }
                    }
                }
                .buttonStyle(.plain)

                Button(role: .destructive) { confirmReset = true } label: {
                    Label("تصفير الحساب", systemImage: "arrow.counterclockwise")
                }
            }

            Section("الطلبيات (\(orders.count))") {
                if orders.isEmpty { Text("لا توجد طلبيات.").foregroundStyle(.secondary) }
                ForEach(orders) { o in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(Format.dateTime(o.createdAt)).font(.caption)
                            Text("\(o.itemCount) صنف").font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(Format.money(o.total)).bold()
                    }
                }
            }

            Section("المدفوعات (\(payments.count))") {
                if payments.isEmpty { Text("لا توجد مدفوعات.").foregroundStyle(.secondary) }
                ForEach(payments) { p in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(Format.money(p.amount)).bold().foregroundStyle(.green)
                            Text(Format.dateTime(p.createdAt)).font(.caption)
                        }
                        Spacer()
                    }
                }
                .onDelete { idx in idx.map { payments[$0] }.forEach { store.delete(payment: $0) } }
            }
        }
        .navigationTitle(customer?.name ?? "زبون")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { showPayment = true } label: { Label("دفعة", systemImage: "banknote") }
            }
        }
        .sheet(isPresented: $showPayment) {
            PaymentEditor { amount, note in
                store.add(Payment(customerId: customerId, amount: amount, note: note))
            }
        }
        .sheet(isPresented: $showDetails) {
            BalanceDetailsView(name: customer?.name ?? "الزبون", ordersTotal: ordersTotal,
                               paymentsTotal: paymentsTotal, balance: balance, orders: orders, payments: payments)
        }
        .alert("تصفير الحساب", isPresented: $confirmReset) {
            Button("إلغاء", role: .cancel) {}
            Button("تصفير", role: .destructive) { store.resetAccount(customerId) }
        } message: {
            Text("سيتم حذف جميع طلبيات ومدفوعات هذا الزبون. هل تريد المتابعة؟")
        }
    }
}

struct PaymentEditor: View {
    @Environment(\.dismiss) private var dismiss
    @State private var amount: Double = 0
    @State private var note = ""
    var onSave: (Double, String) -> Void

    var body: some View {
        NavigationStack {
            Form {
                TextField("المبلغ", value: $amount, format: .number).keyboardType(.decimalPad)
                TextField("ملاحظة (اختياري)", text: $note)
            }
            .navigationTitle("تسجيل دفعة")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("إلغاء") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("حفظ") { onSave(amount, note); dismiss() }.disabled(amount <= 0)
                }
            }
        }
    }
}

struct BalanceDetailsView: View {
    @Environment(\.dismiss) private var dismiss
    let name: String
    let ordersTotal: Double
    let paymentsTotal: Double
    let balance: Double
    let orders: [Order]
    let payments: [Payment]

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack { Text("الحالة"); Spacer(); BalanceBadge(balance: balance) }
                    HStack { Text("إجمالي الطلبيات"); Spacer(); Text(Format.money(ordersTotal)) }
                    HStack { Text("إجمالي المدفوعات"); Spacer(); Text(Format.money(paymentsTotal)) }
                }
                Section("الطلبيات") {
                    ForEach(orders) { o in
                        HStack { Text(Format.dateTime(o.createdAt)).font(.caption); Spacer(); Text(Format.money(o.total)) }
                    }
                }
                Section("المدفوعات") {
                    ForEach(payments) { p in
                        HStack { Text(Format.dateTime(p.createdAt)).font(.caption); Spacer(); Text(Format.money(p.amount)).foregroundStyle(.green) }
                    }
                }
            }
            .navigationTitle("تفاصيل \(name)")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("إغلاق") { dismiss() } } }
        }
    }
}
