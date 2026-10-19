package ru.shturman

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shturman.routing.LatLon
import ru.shturman.routing.isDark
import ru.shturman.routing.sunElevationDeg
import java.util.Calendar
import java.util.TimeZone

class SunTest {
    private val pskov = LatLon(57.8136, 28.3496)

    private fun msk(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).apply {
            clear(); set(y, m - 1, d, h, min)
        }.timeInMillis

    @Test fun noonIsLight() {
        assertFalse(isDark(pskov, msk(2026, 10, 17, 13)))
        assertTrue(sunElevationDeg(pskov, msk(2026, 6, 21, 13)) > 50)
    }

    @Test fun nightIsDark() {
        assertTrue(isDark(pskov, msk(2026, 10, 17, 22)))
        assertTrue(isDark(pskov, msk(2026, 1, 15, 6)))
    }

    @Test fun octoberEvening() {
        // В Пскове в середине октября закат около 17:40 по Москве
        assertFalse(isDark(pskov, msk(2026, 10, 17, 17, 0)))
        assertTrue(isDark(pskov, msk(2026, 10, 17, 18, 45)))
    }
}
