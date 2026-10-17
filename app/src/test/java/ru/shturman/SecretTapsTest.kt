package ru.shturman

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shturman.ui.themes.SecretTaps

class SecretTapsTest {
    private fun run(s: SecretTaps, seq: String, start: Long = 0L, stepMs: Long = 300L): Boolean {
        var hit = false
        seq.forEachIndexed { i, c -> if (s.tap(c, start + i * stepMs)) hit = true }
        return hit
    }

    @Test fun fiveThenTwoOpens() = assertTrue(run(SecretTaps(), "AAAAABB"))

    @Test fun extraTapsBeforeAreFine() = assertTrue(run(SecretTaps(), "BAAAAAAABB"))

    @Test fun wrongOrderDoesNotOpen() = assertFalse(run(SecretTaps(), "BBAAAAA"))

    @Test fun fourTapsAreNotEnough() = assertFalse(run(SecretTaps(), "AAAABB"))

    @Test fun tooSlowDoesNotOpen() = assertFalse(run(SecretTaps(), "AAAAABB", stepMs = 1800L))

    @Test fun opensOnlyOncePerCombo() {
        val s = SecretTaps()
        assertTrue(run(s, "AAAAABB"))
        assertFalse(s.tap('B', 5_000L))
    }
}
