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

    /** For `DatePicker`, which works in the device's calendar: local midnight of that day. */
    static func localDate(_ iso: String) -> Date? {
        calendarDate(iso, in: .current)
    }

    /** Back from `DatePicker`: the day's year, month and day in the same calendar (UI_SPEC §9). */
    static func iso(_ localDate: Date) -> String {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: localDate)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
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
