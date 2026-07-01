import Foundation

/// Centralised formatting. Currency is the Israeli shekel (₪).
enum Format {
    static let currency = "₪"

    static func money(_ value: Double) -> String {
        let rounded = (value * 100).rounded() / 100
        if rounded == rounded.rounded() {
            return "\(currency) \(Int(rounded))"
        }
        return String(format: "\(currency) %.2f", rounded)
    }

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd  HH:mm"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f
    }()

    static func dateTime(_ date: Date) -> String { dateFormatter.string(from: date) }
}
