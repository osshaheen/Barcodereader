import SwiftUI

/// Continuously scans and lists what was detected, with product name/price when known.
struct LiveScanView: View {
    @EnvironmentObject var store: Store
    @State private var detected: [String] = []   // barcodes, most recent first

    var body: some View {
        ZStack(alignment: .bottom) {
            BarcodeScannerView { code in
                if !detected.contains(code) { detected.insert(code, at: 0) }
            }
            .ignoresSafeArea()

            if !detected.isEmpty {
                List(detected, id: \.self) { code in
                    let product = store.product(barcode: code)
                    HStack {
                        VStack(alignment: .leading) {
                            Text(product?.name ?? "غير معروف").font(.headline)
                            Text(code).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if let product { Text(Format.money(product.price)).bold() }
                    }
                }
                .frame(maxHeight: 240)
                .background(.ultraThinMaterial)
            }
        }
        .navigationTitle("الماسح المباشر")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("مسح") { detected.removeAll() }
            }
        }
    }
}
