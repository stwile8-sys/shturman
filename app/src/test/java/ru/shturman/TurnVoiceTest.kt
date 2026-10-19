package ru.shturman

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.shturman.routing.PaceNote
import ru.shturman.routing.VoiceClips
import ru.shturman.routing.curveAdvice
import ru.shturman.routing.curvePhrase
import ru.shturman.routing.turnPhrase

class CurveAdviceTest {
    private fun note(dist: Double, radius: Double) = PaceNote(2, true, dist, 40.0, radius)

    @Test fun slowEnoughNoAdvice() {
        assertEquals(null, curveAdvice(listOf(note(100.0, 30.0)), null, 35))
    }

    @Test fun tightCurveGivesAdvice() {
        // радиус 30 м: √(3·30)·3,6 ≈ 34 км/ч → совет 30
        val a = curveAdvice(listOf(note(120.0, 30.0)), null, 70)
        assertEquals(30, a!!.first)
    }

    @Test fun junctionTurnSkipped() {
        // изгиб совпадает с манёвром — это поворот на перекрёстке, совета нет
        assertEquals(null, curveAdvice(listOf(note(120.0, 30.0)), 130.0, 70))
    }

    @Test fun farCurveWaits() {
        assertEquals(null, curveAdvice(listOf(note(400.0, 30.0)), null, 60))
    }

    @Test fun gentleCurveNoAdvice() {
        // радиус 200 м: ≈ 88 км/ч — выше 70, совет не нужен
        assertEquals(null, curveAdvice(listOf(note(100.0, 200.0)), null, 100))
    }
}

class TurnPhraseTest {
    /** Для каждой фразы, которую может сказать навигатор, есть запись голоса (на Huawei нет синтезатора). */
    @Test fun everyPhraseHasRecording() {
        val missing = ArrayList<String>()
        for (level in 0..3) for (d in 40..650 step 5) for (sev in 1..6) for (left in listOf(true, false)) for (exit in 0..7) {
            val p = turnPhrase(level, d.toDouble(), left, sev, exit)
            if (!VoiceClips.map.containsKey(p)) missing += p
        }
        for (v in 20..70 step 10) {
            val p = curvePhrase(v)
            if (!VoiceClips.map.containsKey(p)) missing += p
        }
        if (!VoiceClips.map.containsKey("Осторожно, возможен гололёд")) missing += "гололёд"
        assertEquals(emptyList<String>(), missing.distinct())
    }
}
