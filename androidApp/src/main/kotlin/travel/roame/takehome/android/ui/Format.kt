package travel.roame.takehome.android.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val rowDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val chipDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val cardDate = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
private val spokenDate = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
private val spokenCardDate = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)

/** T3: parsed as a calendar date, never through an instant or timezone, so Feb 1 stays Feb 1. */
fun rowDate(isoDate: String): String = LocalDate.parse(isoDate).format(rowDate)

/** "Mon, Feb 1, 2027", for the selection card. */
fun cardDate(isoDate: String): String = LocalDate.parse(isoDate).format(cardDate)

/** UI_SPEC §8: "Monday, February 1", spoken in full in place of the row's "Mon, Feb 1". */
fun spokenDate(isoDate: String): String = LocalDate.parse(isoDate).format(spokenDate)

/** "Monday, February 1, 2027", spoken in place of the card's date. */
fun spokenCardDate(isoDate: String): String = LocalDate.parse(isoDate).format(spokenCardDate)

/** "Feb 10", for the dates chip. */
fun chipDate(isoDate: String): String = LocalDate.parse(isoDate).format(chipDate)

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

/** "February 2027", above each month of the calendar. */
fun monthTitle(year: Int, month: Int): String = LocalDate.of(year, month, 1).format(monthTitle)

private val syncTime = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val syncDayAndTime = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

/**
 * T4: `lastSuccessAt` is an instant, so unlike flight dates it's shown in the device's zone.
 * "3:42 PM" if it was today there, otherwise "Nov 2, 3:42 PM".
 */
fun syncTime(epochMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return at.format(if (at.toLocalDate() == today) syncTime else syncDayAndTime)
}

/** Counts use the same US grouping as the shared points formatter. */
fun groupedCount(count: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(count)
