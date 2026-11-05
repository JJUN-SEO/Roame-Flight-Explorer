import SwiftUI
import Shared

/**
 * The date range calendar (UI_SPEC §6), drawn the same as Android's DateRangeCalendar. Which days show, which can be
 * picked, how they're marked and what a tap does all come from the shared DateCalendar (K1–K5, D41). `awaitingStart`
 * holds a start whose end hasn't been picked yet; the caller clears it with "Any dates". Each tap applies at once.
 */
struct DateRangeCalendar: View {
    @Environment(ExplorerModel.self) private var model
    @Binding var awaitingStart: String?

    var body: some View {
        let state = model.state
        if let min = state.options.minDate, let max = state.options.maxDate {
            let selection = awaitingStart.map { DateSelection(startIso: $0, endIso: nil, awaitingEnd: true) }
                ?? DateCalendar.shared.selectionOf(filters: state.filters)
            VStack(spacing: 0) {
                WeekdayHeader()
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        // Months and weeks keep their identity across taps, as Android's are keyed, so a tap only redraws
                        // the marks. Identified by their contents, every month was rebuilt on each tap: on the owner's
                        // iPhone (iOS 26) the calendar animated oddly while picking dates.
                        ForEach(DateCalendar.shared.months(minIso: min, maxIso: max, selection: selection), id: \.key) { month in
                            Text(Format.monthTitle(year: Int(month.year), month: Int(month.month)))
                                .font(.appTitle)
                                .padding(.top, 20)
                                .padding(.bottom, 8)
                                .accessibilityAddTraits(.isHeader)
                            ForEach(month.weeks, id: \.key) { week in
                                HStack(spacing: 0) {
                                    ForEach(week.days, id: \.iso) { day in
                                        DayCell(day: day) {
                                            let next = DateCalendar.shared.tap(selection: selection, dayIso: day.iso, minIso: min, maxIso: max)
                                            awaitingStart = next.awaitingEnd ? next.startIso : nil
                                            model.store.setDateRange(startIso: next.startIso, endIso: next.endIso)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(EdgeInsets(top: 0, leading: 16, bottom: 16, trailing: 16))
                }
            }
        }
    }
}

private extension CalendarMonth {
    var key: Int { Int(year) * 100 + Int(month) }
}

private extension CalendarWeek {
    /**
     * The week's first day in its own month, which never changes with the selection. Not its first day: a week across
     * two months is in both, starting on the same Sunday, and the duplicate identity hid March's and April's first
     * weeks (found while recording the demo).
     */
    var key: String { days.first { $0.inMonth }?.iso ?? "" }
}

/** S M T W T F S, Sunday first like the weeks (K1). */
private struct WeekdayHeader: View {
    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(Array(["S", "M", "T", "W", "T", "F", "S"].enumerated()), id: \.offset) { _, letter in
                    Text(letter).font(.appLabelSmall).foregroundStyle(Color.secondaryText).frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .accessibilityHidden(true)
            Rectangle().fill(Color.cardBorder).frame(height: 1)
        }
    }
}

/**
 * K4: a pink circle on the range's ends (or a lone start), and a light pink band joining them. The neighbouring months'
 * days stay empty.
 */
private struct DayCell: View {
    let day: CalendarDay
    let onTap: () -> Void

    var body: some View {
        ZStack {
            if day.inMonth {
                band
                let ends = day.mark == .start || day.mark == .end || day.mark == .single
                Button(action: onTap) {
                    Text("\(day.day)")
                        .font(ends ? .appTitleSmall : .appBody)
                        .foregroundStyle(ends ? Color.onAccent : day.isSelectable ? Color.text : Color.secondaryText.opacity(0.4))
                        .frame(width: 40, height: 40)
                        .background(ends ? Color.accent : .clear, in: .circle)
                        .contentShape(.circle)
                }
                .buttonStyle(.plain)
                .disabled(!day.isSelectable)
                .accessibilityLabel(description)
                .accessibilityAddTraits(ends ? .isSelected : [])
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 44)
    }

    /** The band runs under the whole cell between the ends, and under the inner half of each end. */
    @ViewBuilder private var band: some View {
        switch day.mark {
        case .between: Color.selectedCard.padding(.vertical, 2)
        case .start: HStack(spacing: 0) { Color.clear; Color.selectedCard }.padding(.vertical, 2)
        case .end: HStack(spacing: 0) { Color.selectedCard; Color.clear }.padding(.vertical, 2)
        default: EmptyView()
        }
    }

    private var description: String {
        let date = Format.cardDate(day.iso)
        switch day.mark {
        case .start: return "\(date), start date"
        case .end: return "\(date), end date"
        case .single: return "\(date), selected"
        case .between: return "\(date), in range"
        default: return date
        }
    }
}
