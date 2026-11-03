package travel.roame.takehome.android.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val rowDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

/** T3: parsed as a calendar date, never through an instant or timezone, so Feb 1 stays Feb 1. */
fun rowDate(isoDate: String): String = LocalDate.parse(isoDate).format(rowDate)

/** Counts use the same US grouping as the shared points formatter. */
fun groupedCount(count: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(count)
