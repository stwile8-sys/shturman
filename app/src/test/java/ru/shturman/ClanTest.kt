package ru.shturman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shturman.routing.ClanMember
import ru.shturman.routing.LatLon
import ru.shturman.routing.clanId
import ru.shturman.routing.clanPassHash
import ru.shturman.routing.clanStatus
import ru.shturman.routing.thinRoute

class ClanTest {
    private fun member(speed: Int, updated: Long, sharing: Boolean = true, dest: String? = null) = ClanMember(
        "u1", "Иван", LatLon(57.8, 28.3), speed, updated, null, dest, sharing, "#FF8A3D"
    )

    @Test fun idIgnoresCaseAndSpaces() {
        assertEquals("апекс_псков", clanId("  Апекс   Псков "))
        assertEquals(clanId("APEX"), clanId("apex"))
    }

    @Test fun passwordHashDependsOnSaltAndPassword() {
        assertEquals(clanPassHash("s", "1234"), clanPassHash("s", "1234"))
        assertNotEquals(clanPassHash("s", "1234"), clanPassHash("s", "1235"))
        assertNotEquals(clanPassHash("a", "1234"), clanPassHash("b", "1234"))
    }

    @Test fun statusShowsSpeedOrAge() {
        val now = 10_000_000L
        assertEquals("62 км/ч · едет: Псков", clanStatus(member(62, now - 5_000, dest = "Псков"), now))
        assertEquals("стоит", clanStatus(member(2, now - 5_000), now))
        assertEquals("был 15 мин назад", clanStatus(member(60, now - 15 * 60_000), now))
        assertEquals("скрыл себя", clanStatus(member(60, now, sharing = false), now))
    }

    @Test fun thinRouteKeepsEndsAndLimit() {
        val pts = List(1000) { LatLon(57.0 + it * 1e-4, 28.0) }
        val thin = thinRoute(pts, 300)
        assertEquals(300, thin.size)
        assertEquals(pts.first(), thin.first())
        assertEquals(pts.last(), thin.last())
        assertTrue(thinRoute(pts.take(10)).size == 10)
    }
}
