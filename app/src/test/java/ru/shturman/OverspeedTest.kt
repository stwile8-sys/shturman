package ru.shturman

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.shturman.routing.OverspeedAlarm

class OverspeedTest {
    /** Прогоняет скорости при одном ограничении и возвращает, сколько раз был сигнал. */
    private fun beeps(a: OverspeedAlarm, limit: Int?, vararg kmh: Int): Int = kmh.count { a.update(it, limit) }

    @Test fun withinToleranceSilent() {
        assertEquals(0, beeps(OverspeedAlarm(), 60, 60, 65, 70, 75, 74, 75))
    }

    @Test fun oneBeepPerExcess() {
        // 76 и выше при знаке 60 — превышение больше 15: один сигнал, сколько бы ни ехали быстро
        assertEquals(1, beeps(OverspeedAlarm(), 60, 70, 76, 80, 90, 95, 88, 79))
    }

    @Test fun gpsJitterDoesNotRepeat() {
        // Колебания около порога (74–77) не дают повторов, пока не сбросили до 70
        assertEquals(1, beeps(OverspeedAlarm(), 60, 76, 74, 77, 73, 76, 72, 77))
    }

    @Test fun beepsAgainAfterSlowingDown() {
        assertEquals(2, beeps(OverspeedAlarm(), 60, 80, 70, 80))
    }

    @Test fun lowerLimitIsNewExcess() {
        val a = OverspeedAlarm()
        assertEquals(1, beeps(a, 90, 110))
        // знак 60 при тех же 110 — новое превышение
        assertEquals(1, beeps(a, 60, 110, 110))
        // знак стал выше (90), а едем 110 — то же превышение, без повтора
        assertEquals(0, beeps(a, 90, 110))
    }

    @Test fun unknownLimitKeepsState() {
        val a = OverspeedAlarm()
        assertEquals(1, beeps(a, 60, 80))
        assertEquals(0, beeps(a, null, 80, 85))
        // данные снова есть, скорость не сбрасывали — повтора нет
        assertEquals(0, beeps(a, 60, 80))
        // сбросили на участке без данных — снова готовы
        assertEquals(0, beeps(a, null, 65))
        assertEquals(1, beeps(a, 60, 80))
    }

    @Test fun resetStartsOver() {
        val a = OverspeedAlarm()
        assertEquals(1, beeps(a, 60, 80))
        a.reset()
        assertEquals(1, beeps(a, 60, 80))
    }
}
