package travel.roame.takehome.android.ui

import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The date picker's UTC midnight millis must never shift a day, whatever the device's time zone (T3, CLAUDE.md). */
class DatePickerConversionTest {

    private val original = TimeZone.getDefault()

    @AfterTest
    fun restoreZone() = TimeZone.setDefault(original)

    /** The millis come from a separate Python calculation of UTC midnight. */
    private val utcMidnights = mapOf(
        "2027-02-01" to 1_801_440_000_000L,
        "2027-02-10" to 1_802_217_600_000L,
        "2027-04-01" to 1_806_537_600_000L,
    )

    /** From UTC-11 to UTC+14, so a conversion through the device's zone would land on the wrong day. */
    private val zones = listOf("Pacific/Pago_Pago", "America/Los_Angeles", "America/New_York", "UTC", "Asia/Tokyo", "Pacific/Kiritimati")

    @Test
    fun t3_pickerMillisBecomeTheSameCalendarDateInEveryZone() {
        for (zone in zones) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            for ((iso, millis) in utcMidnights) {
                assertEquals(iso, utcMillisToIso(millis), zone)
                assertEquals(millis, isoToUtcMillis(iso), zone)
            }
        }
    }

    @Test
    fun t3_theChipShowsTheSameDay() {
        assertEquals("Feb 1", chipDate("2027-02-01"))
        assertEquals("Apr 1", chipDate("2027-04-01"))
    }
}
