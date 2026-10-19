package com.hamad.tvhome.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PrayerScheduleTest {
    private fun at(h: Int, m: Int): Long = Calendar.getInstance(TimeZone.getTimeZone("Asia/Baghdad")).apply {
        clear(); set(2026, Calendar.OCTOBER, 19, h, m)
    }.timeInMillis

    @Test fun `afternoon counts down to maghrib`() {
        val s = PrayerSchedule.at(at(17, 0))
        assertEquals(Prayer.Maghrib, s.next)
        assertEquals(40, s.minutesUntilNext) // Maghrib 17:40
        assertNull(s.current)
    }

    @Test fun `just after maghrib it is current`() {
        val s = PrayerSchedule.at(at(17, 45))
        assertEquals(Prayer.Maghrib, s.current)
        assertEquals(Prayer.Isha, s.next)
    }

    @Test fun `after isha the next prayer is tomorrow's fajr`() {
        val s = PrayerSchedule.at(at(23, 0))
        assertEquals(Prayer.Fajr, s.next)
        // Tomorrow's Fajr is ~04:58: one hour to midnight plus ~298 minutes.
        assertTrue(s.minutesUntilNext in 356..360)
    }

    @Test fun `sunrise is never shown as now`() {
        val s = PrayerSchedule.at(at(6, 15))
        assertNull(s.current)
        assertEquals(Prayer.Dhuhr, s.next)
    }
}
