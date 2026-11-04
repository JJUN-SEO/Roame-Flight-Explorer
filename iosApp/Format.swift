import Foundation

/**
 * T3: a departure date is a calendar date. It's built from its year, month and day and formatted in UTC,
 * so "2027-02-01" stays Feb 1 whatever the device's time zone.
 */
enum Format {
    private static let utc = TimeZone(identifier: "UTC")!

    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = utc
        return calendar
    }()

    private static func formatter(_ pattern: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.timeZone = utc
        formatter.dateFormat = pattern
        return formatter
    }

    private static let row = formatter("EEE, MMM d")
    private static let chip = formatter("MMM d")
    private static let card = formatter("EEE, MMM d, yyyy")

    /** "Mon, Feb 1, 2027", for the selection card. */
    static func cardDate(_ iso: String) -> String {
        guard let date = calendarDate(iso, in: calendar) else { return iso }
        return card.string(from: date)
    }

    /** "Mon, Feb 1". */
    static func rowDate(_ iso: String) -> String {
        guard let date = calendarDate(iso, in: calendar) else { return iso }
        return row.string(from: date)
    }

    /** "Feb 10", for the dates chip. */
    static func chipDate(_ iso: String) -> String {
        guard let date = calendarDate(iso, in: calendar) else { return iso }
        return chip.string(from: date)
    }

    private static let month = formatter("MMMM yyyy")

    /** "February 2027", above each month of the calendar. */
    static func monthTitle(year: Int, month: Int) -> String {
        guard let date = calendar.date(from: DateComponents(year: year, month: month, day: 1)) else { return "\(year)-\(month)" }
        return self.month.string(from: date)
    }

    /**
     * T4: `lastSuccessAt` is an instant, so unlike flight dates it's shown in the device's zone.
     * "3:42 PM" if it was today there, otherwise "Nov 2, 3:42 PM".
     */
    static func syncTime(_ epochMillis: Int64, now: Date = .now, zone: TimeZone = .current) -> String {
        let at = Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1000)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let formatter = formatter(calendar.isDate(at, inSameDayAs: now) ? "h:mm a" : "MMM d, h:mm a")
        formatter.timeZone = zone
        return formatter.string(from: at)
    }

    private static func calendarDate(_ iso: String, in calendar: Calendar) -> Date? {
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }

    private static let counts: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.numberStyle = .decimal
        return formatter
    }()

    /** Counts use the same US grouping as the shared points formatter: "1,000". */
    static func count(_ count: Int) -> String {
        counts.string(from: NSNumber(value: count)) ?? String(count)
    }
}
