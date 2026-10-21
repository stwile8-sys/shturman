package ru.shturman.routing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.log10

/** Результат поиска и пояснение, если навигатор понял запрос по-своему («Ищу «гоголя 12»»). */
class SearchResult(val places: List<Place>, val hint: String? = null)

/**
 * Поиск, который понимает человека: разбирает запрос ([QueryFix]), спрашивает Photon несколькими
 * вариантами (как написано, в другой раскладке, латиницей по-русски, без окончаний и «улиц»),
 * а потом сам сортирует найденное — по похожести на запрос и по расстоянию от водителя.
 * Сначала — один-три запроса; запасные варианты — только если точного совпадения нет.
 */
object SmartSearch {
    /** Совпадение считается хорошим — запасные варианты не нужны. */
    private const val GOOD = 0.78

    private enum class Kind { EXACT, LAYOUT, LATIN, LOOSE, LOCAL, CATEGORY }

    private class Found(val place: Place, val rank: Double, val kind: Kind, val asked: String)

    suspend fun search(
        raw: String,
        here: LatLon?,
        recent: List<Place> = emptyList(),
        home: Place? = null,
        work: Place? = null
    ): SearchResult = coroutineScope {
        val near = here ?: Geocoder.PSKOV
        QueryFix.coordinates(raw)?.let { p ->
            return@coroutineScope SearchResult(
                listOf(Place("Точка на карте", String.format(java.util.Locale.US, "%.5f, %.5f", p.lat, p.lon), p))
            )
        }
        val q = QueryFix.clean(raw)
        if (q.isBlank()) return@coroutineScope SearchResult(emptyList())

        val found = ArrayList<Found>()
        var lastError: Exception? = null
        var asked = 0

        suspend fun safe(block: suspend () -> List<Place>): List<Place> {
            asked++
            return try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                emptyList()
            }
        }

        fun penalty(p: Place): Double {
            val km = distanceM(near, p.position) / 1000.0
            return 0.07 * log10(1.0 + km / 3.0)
        }

        // «домой», «на работу»
        when (QueryFix.favoriteKind(q)) {
            "home" -> home?.let { found += Found(it, 3.0, Kind.LOCAL, q) }
            "work" -> work?.let { found += Found(it, 3.0, Kind.LOCAL, q) }
        }
        // недавние места, если похожи на запрос
        for (p in recent) {
            val sc = QueryFix.score(q, p.title, p.subtitle)
            if (sc >= 0.8) found += Found(p, sc + 0.15, Kind.LOCAL, q)
        }

        var hint: String? = null

        // Вид места: «заправка», «где поесть», «аптека рядом», «заправка лукойл»
        val cat = QueryFix.category(q)
        if (cat != null) {
            val (c, rest) = cat
            val parts = if (rest.isEmpty()) listOf(
                async { safe { Geocoder.nearby(near, c.tags, 15, 10, c.label) } },
                async { safe { Geocoder.text(c.label, near, c.tags, 10, c.label) } }
            ) else listOf(
                async { safe { Geocoder.text(rest, near, c.tags, 10, c.label) } },
                async { safe { Geocoder.text(rest, near) } }
            )
            var places = parts.awaitAll().flatten()
            if (places.isEmpty() && rest.isEmpty()) {
                // за городом ближайшее может быть дальше 15 км
                places = safe { Geocoder.nearby(near, c.tags, 60, 10, c.label) }
            }
            for (p in places) {
                val km = distanceM(near, p.position) / 1000.0
                val match = if (rest.isEmpty()) 1.0 else QueryFix.score(rest, p.title, p.subtitle)
                // ближайшие — выше всех: сортируем почти только по расстоянию
                found += Found(p, 1.0 + match - km / 200.0, Kind.CATEGORY, rest)
            }
            if (places.isNotEmpty()) hint = if (rest.isEmpty()) "Ближайшие: ${c.label.lowercase()}" else "${c.label}: «$rest»"
        }

        if (found.none { it.kind == Kind.CATEGORY }) {
            // 1) как написано, а если латиницей — ещё в русской раскладке и по-русски
            val first = ArrayList<Pair<String, Kind>>()
            first += q to Kind.EXACT
            if (QueryFix.looksLatin(raw)) {
                QueryFix.clean(QueryFix.fromEnglishLayout(raw)).takeIf { it.isNotBlank() && it != q }?.let { first += it to Kind.LAYOUT }
                QueryFix.clean(QueryFix.transliterate(raw)).takeIf { it.isNotBlank() && it != q && first.none { f -> f.first == it } }
                    ?.let { first += it to Kind.LATIN }
            }
            val firstRes = first.map { (text, kind) -> async { Triple(text, kind, safe { Geocoder.text(text, near) }) } }.awaitAll()
            for ((text, kind, list) in firstRes) {
                for (p in list) found += Found(p, QueryFix.score(text, p.title, p.subtitle) - penalty(p), kind, text)
            }

            // 2) точного совпадения нет — без «улиц», без окончаний, без номера дома
            val bestFirst = found.filter { it.kind != Kind.LOCAL }.maxByOrNull { it.rank }
            if (bestFirst == null || bestFirst.rank < GOOD) {
                val base = bestFirst?.asked ?: q
                val loose = linkedSetOf<String>()
                QueryFix.withoutStreetTypes(base).let { if (it.isNotBlank() && it != base) loose += it }
                QueryFix.stemmed(QueryFix.withoutStreetTypes(base)).let { if (it.isNotBlank() && it != base) loose += it }
                if (base.any { it.isDigit() } && base.any { it.isLetter() }) QueryFix.withoutHouse(base).let { if (it.isNotBlank()) loose += it }
                loose.removeAll(first.map { it.first }.toSet())
                val looseRes = loose.take(3).map { text -> async { safe { Geocoder.text(text, near) } } }.awaitAll()
                for (list in looseRes) for (p in list) {
                    // похожесть меряем по тому, что человек хотел, а не по укороченному запросу
                    found += Found(p, QueryFix.score(base, p.title, p.subtitle) - penalty(p) - 0.02, Kind.LOOSE, base)
                }
            }
        }

        // Повторы (одно место из разных запросов) — оставляем лучший
        val unique = ArrayList<Found>()
        for (f in found.sortedByDescending { it.rank }) {
            val key = QueryFix.fold(f.place.title)
            if (unique.none { QueryFix.fold(it.place.title) == key && distanceM(it.place.position, f.place.position) < 150.0 }) unique += f
        }
        if (unique.isEmpty() && asked > 0 && lastError != null) throw lastError!!

        val top = unique.firstOrNull()
        if (hint == null && top != null) {
            hint = when (top.kind) {
                Kind.LAYOUT -> "Похоже, другая раскладка — ищу «${top.asked}»"
                Kind.LATIN -> "Ищу «${top.asked}»"
                Kind.LOOSE -> if (top.rank < 0.6) "Точно такого нет — вот похожие места" else null
                else -> null
            }
        }
        SearchResult(unique.take(10).map { it.place }, hint)
    }
}
