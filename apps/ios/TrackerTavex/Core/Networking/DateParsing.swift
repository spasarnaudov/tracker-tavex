import Foundation

/// Parses the naive (no UTC offset) ISO-ish timestamps `_serialize()` in
/// `apps/flask/api.py` produces from Python's `datetime`/`date` objects — e.g.
/// "2026-07-20T14:30:00.123456", "2026-07-20T14:30:00", or a bare "2026-07-20".
enum DateParsing {
    private static let isoWithFractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let iso: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    private static let dateOnly: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .iso8601)
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter
    }()

    private static let naiveDateTime: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .iso8601)
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter
    }()

    static func parse(_ value: String) -> Date? {
        if let date = isoWithFractional.date(from: value) { return date }
        if let date = iso.date(from: value) { return date }
        if value.count == 10, let date = dateOnly.date(from: value) { return date }
        return naiveDateTime.date(from: String(value.prefix(19)))
    }
}
