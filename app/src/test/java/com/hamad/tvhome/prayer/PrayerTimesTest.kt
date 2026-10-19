package com.hamad.tvhome.prayer

import org.junit.Assert.assertTrue
import org.junit.Test

/** Reference values: api.aladhan.com method 0 (Shia Ithna-Ashari) for Baghdad 33.3152, 44.3661. */
class PrayerTimesTest {
    private fun hm(s: String) = s.substring(0, 2).toInt() * 60 + s.substring(3).toInt()

    private fun assertDay(y: Int, m: Int, d: Int, expected: Map<Prayer, String>) {
        val got = PrayerTimes.forDay(y, m, d, PrayerConfig.BaghdadJafari)
        expected.forEach { (p, want) ->
            val diff = kotlin.math.abs(got.getValue(p) - hm(want))
            assertTrue("$p on $y-$m-$d: got ${got[p]} want $want", diff <= 1)
        }
    }

    @Test fun `baghdad autumn matches aladhan`() = assertDay(
        2026, 10, 19,
        mapOf(
            Prayer.Fajr to "04:57", Prayer.Sunrise to "06:10", Prayer.Dhuhr to "11:48",
            Prayer.Asr to "15:00", Prayer.Maghrib to "17:40", Prayer.Isha to "18:28",
        ),
    )

    @Test fun `baghdad summer matches aladhan`() = assertDay(
        2026, 6, 1,
        mapOf(
            Prayer.Fajr to "03:27", Prayer.Sunrise to "04:54", Prayer.Dhuhr to "12:00",
            Prayer.Asr to "15:44", Prayer.Maghrib to "19:24", Prayer.Isha to "20:22",
        ),
    )
}
