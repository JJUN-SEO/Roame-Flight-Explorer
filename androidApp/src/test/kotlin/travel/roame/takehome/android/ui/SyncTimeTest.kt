package travel.roame.takehome.android.ui

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/** The stale banner's time (T4): an instant, shown in the device's zone. The millis come from a separate Python calculation. */
class SyncTimeTest {

    private val lastSyncUtc2042 = 1_793_738_520_000L // 2026-11-03T20:42Z
    private val nowUtc2200 = 1_793_743_200_000L // 2026-11-03T22:00Z

    @Test
    fun t4_sameDay_showsOnlyTheLocalTime() {
        assertEquals("3:42 PM", syncTime(lastSyncUtc2042, nowUtc2200, ZoneId.of("America/Toronto")))
        assertEquals("5:42 AM", syncTime(lastSyncUtc2042, nowUtc2200, ZoneId.of("Asia/Tokyo")))
        assertEquals("9:42 AM", syncTime(lastSyncUtc2042, nowUtc2200, ZoneId.of("Pacific/Auckland")))
    }

    @Test
    fun t4_earlierDay_addsTheDate() {
        val dayBefore = 1_793_652_120_000L // 2026-11-02T20:42Z
        assertEquals("Nov 2, 3:42 PM", syncTime(dayBefore, nowUtc2200, ZoneId.of("America/Toronto")))
    }

    @Test
    fun t4_theDayChangesAtLocalMidnight_notUtcMidnight() {
        val lateEvening = 1_793_766_600_000L // 2026-11-04T04:30Z, 11:30 PM on Nov 3 in Toronto
        val afterMidnight = 1_793_770_200_000L // 2026-11-04T05:30Z, 12:30 AM on Nov 4 in Toronto
        assertEquals("Nov 3, 11:30 PM", syncTime(lateEvening, afterMidnight, ZoneId.of("America/Toronto")))
    }
}
