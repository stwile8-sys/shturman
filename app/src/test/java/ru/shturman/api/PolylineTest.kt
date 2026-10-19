package ru.shturman.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shturman.routing.LatLon

class PolylineTest {
    @Test fun encodeDecodeRoundTrip() {
        val pts = listOf(LatLon(57.809796, 28.361781), LatLon(57.823454, 28.289952), LatLon(57.815621, 28.26858))
        val back = Polyline.decode(Polyline.encode(pts))
        assertEquals(pts.size, back.size)
        for (i in pts.indices) {
            assertEquals(pts[i].lat, back[i].lat, 1e-6)
            assertEquals(pts[i].lon, back[i].lon, 1e-6)
        }
    }

    @Test fun knownGoogleExample() {
        // Пример из описания формата Google (точность 5)
        val pts = listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", Polyline.encode(pts, 5))
    }

    @Test fun pointValidation() {
        assertTrue(RouteApi.validPoint(LatLon(57.8, 28.3)))
        assertFalse(RouteApi.validPoint(LatLon(0.0, 0.0)))
        assertFalse(RouteApi.validPoint(LatLon(Double.NaN, 28.3)))
        assertFalse(RouteApi.validPoint(LatLon(95.0, 28.3)))
    }
}
