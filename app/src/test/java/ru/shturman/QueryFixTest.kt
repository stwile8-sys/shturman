package ru.shturman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shturman.routing.QueryFix

class QueryFixTest {

    @Test fun fillerWordsAreRemoved() {
        assertEquals("гоголя 12", QueryFix.clean("Отвези меня на Гоголя 12, пожалуйста"))
        assertEquals("порхов", QueryFix.clean("как доехать до Порхов"))
        assertEquals("улица гоголя 12", QueryFix.clean("поехали на ул. Гоголя, д. 12"))
    }

    @Test fun abbreviationsAndHouseNumbers() {
        assertEquals("рижский проспект 50", QueryFix.clean("Рижский пр-т, д.50"))
        assertEquals("улица ленина 5а", QueryFix.clean("ул Ленина 5 а"))
        assertEquals("улица ленина 5", QueryFix.clean("ул. Ленина, д. 5, кв. 12"))
        assertEquals("октябрьский проспект 10к1", QueryFix.clean("Октябрьский просп. 10 корп. 1"))
        assertEquals("псков улица гоголя", QueryFix.clean("г. Псков, ул. Гоголя"))
    }

    @Test fun wrongKeyboardLayout() {
        assertTrue(QueryFix.looksLatin("ujujkz 12"))
        assertEquals("гоголя 12", QueryFix.fromEnglishLayout("ujujkz 12"))
        assertEquals("псков", QueryFix.fromEnglishLayout("gcrjd"))
    }

    @Test fun transliteration() {
        assertEquals("гоголя", QueryFix.transliterate("gogolya"))
        assertEquals("псков", QueryFix.transliterate("pskov"))
        assertEquals("великие луки", QueryFix.transliterate("velikie luki"))
    }

    @Test fun typosAndEndingsStillMatch() {
        assertTrue(QueryFix.score("гогля 12", "улица Гоголя, 12", "Псков") > 0.75)
        assertTrue(QueryFix.score("великих лук", "Великие Луки", "Псковская область") > 0.75)
        assertTrue(QueryFix.score("порхове", "Порхов", "Псковская область") > 0.75)
        assertTrue(QueryFix.score("гоголя", "Москва", "Россия") < 0.4)
    }

    @Test fun categories() {
        val fuel = QueryFix.category(QueryFix.clean("где ближайшая заправка"))
        assertNotNull(fuel)
        assertEquals("АЗС", fuel!!.first.label)
        assertEquals("", fuel.second)
        val brand = QueryFix.category(QueryFix.clean("заправка лукойл"))
        assertEquals("лукойл", brand!!.second)
        assertEquals("Кафе", QueryFix.category(QueryFix.clean("где поесть"))!!.first.label)
        // «улица Заправочная, 3» — это адрес, а не вид места
        assertNull(QueryFix.category(QueryFix.clean("ул. Аптечная 3")))
    }

    @Test fun favoritesAndCoordinates() {
        assertEquals("home", QueryFix.favoriteKind(QueryFix.clean("поехали домой")))
        assertEquals("work", QueryFix.favoriteKind(QueryFix.clean("на работу")))
        val p = QueryFix.coordinates("57,8136 28,3496")
        assertNotNull(p)
        assertEquals(57.8136, p!!.lat, 1e-6)
        assertNull(QueryFix.coordinates("гоголя 12"))
    }
}
