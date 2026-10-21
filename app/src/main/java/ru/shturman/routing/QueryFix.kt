package ru.shturman.routing

/**
 * Разбор того, что человек написал в поиске: как бы он ни писал, понять, что он имеет в виду.
 * Всё здесь — без сети, чтобы можно было проверить тестами.
 *
 * - лишние слова: «отвези меня на Гоголя 12», «как доехать до Порхова», «найди аптеку» → «гоголя 12»;
 * - сокращения: «ул.», «пр-т», «д.», «кв. 5», «г. Псков» → полные слова или ничего;
 * - забытая раскладка: «ujujkz» → «гоголя»; латиница: «gogolya» → «гоголя»;
 * - опечатки и окончания: похожесть слов (сравнение с ошибками и по основе слова);
 * - виды мест: «заправка», «где поесть», «ближайшая аптека» → поиск ближайших мест этого вида;
 * - координаты: «57.81, 28.35».
 */
object QueryFix {

    /** Нижний регистр, без знаков препинания, без двойных пробелов. «/» между цифрами остаётся (дом 12/1). */
    fun normalize(s: String): String {
        val low = s.lowercase()
        val sb = StringBuilder(low.length)
        for (i in low.indices) {
            val c = low[i]
            val keepSlash = c == '/' && i > 0 && i < low.length - 1 && low[i - 1].isDigit() && low[i + 1].isDigit()
            sb.append(
                when {
                    c.isLetterOrDigit() || c == '-' || keepSlash -> c
                    else -> ' '
                }
            )
        }
        return sb.toString().split(' ').map { it.trim('-') }.filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Для сравнения: ещё и «ё» → «е». */
    fun fold(s: String): String = normalize(s).replace('ё', 'е')

    // ---------- лишние слова ----------

    private val FILLER_PHRASES = listOf(
        "проложи маршрут", "построй маршрут", "постройте маршрут", "проложить маршрут", "построить маршрут",
        "как доехать", "как проехать", "как добраться", "как попасть", "где находится", "где находятся",
        "отвези меня", "отвезите меня", "довези меня", "вези меня", "мне нужно", "мне надо", "нам нужно", "нам надо",
        "я хочу", "хочу поехать", "хочу попасть", "давай поедем", "давай поехали"
    )
    private val FILLER_WORDS = setOf(
        "отвези", "отвезите", "довези", "вези", "поехали", "поехать", "поедем", "едем", "ехать", "еду", "веди",
        "маршрут", "доехать", "добраться", "нужно", "надо", "хочу", "найди", "найти", "покажи", "показать",
        "где", "пожалуйста", "плиз", "please", "навигатор", "апекс", "штурман", "давай", "срочно", "быстрее"
    )
    private val LEADING_PREPS = setOf("в", "во", "на", "до", "к", "ко", "по", "за")

    /** Убирает слова-просьбы в начале и конце: остаётся только «куда». */
    fun stripFiller(q: String): String {
        var s = " ${normalize(q)} "
        for (p in FILLER_PHRASES) s = s.replace(" $p ", " ")
        var words = s.trim().split(' ').filter { it.isNotEmpty() }
        // слова-просьбы и предлоги в начале: «ну поехали на» → ""
        while (words.isNotEmpty() && (words[0] in FILLER_WORDS || words[0] in LEADING_PREPS || words[0] == "ну" || words[0] == "мне" || words[0] == "меня")) {
            if (words.size == 1 && words[0] in LEADING_PREPS) break
            words = words.drop(1)
        }
        while (words.isNotEmpty() && words.last() in setOf("пожалуйста", "плиз", "please", "срочно")) words = words.dropLast(1)
        return words.joinToString(" ")
    }

    // ---------- сокращения ----------

    private val EXPAND = mapOf(
        "ул" to "улица", "ул-ца" to "улица",
        "пр" to "проспект", "пр-т" to "проспект", "пр-кт" to "проспект", "просп" to "проспект", "пркт" to "проспект", "пр-тт" to "проспект",
        "пл" to "площадь", "пер" to "переулок", "пр-д" to "проезд", "пр-зд" to "проезд",
        "наб" to "набережная", "ш" to "шоссе", "б-р" to "бульвар", "бул" to "бульвар", "бр" to "бульвар",
        "мкр" to "микрорайон", "мкрн" to "микрорайон", "мкр-н" to "микрорайон", "р-н" to "район", "обл" to "область",
        "тц" to "тц", "трц" to "трц"
    )
    /** Слова-приставки к названию населённого пункта: «г. Псков», «д. Неёлово», «пос. Карамышево». */
    private val PLACE_PREFIX = setOf("г", "гор", "город", "д", "дер", "деревня", "п", "пос", "поселок", "посёлок", "пгт", "с", "село", "х", "хутор", "ст", "станция")
    /** Слова, после которых идёт номер, который навигатору не нужен (квартира, подъезд, этаж). */
    private val DROP_WITH_NUMBER = setOf("кв", "квартира", "под", "подъезд", "эт", "этаж", "оф", "офис")
    private val HOUSE_WORDS = setOf("д", "дом", "дом№", "№", "no", "n")
    private val BUILDING_WORDS = setOf("к", "корп", "корпус", "стр", "строение", "лит", "литера")

    /** Сокращения → полные слова, «д. 12» → «12», «12 а» → «12а», «12 корп 1» → «12к1», квартиры и подъезды — прочь. */
    fun expand(q: String): String {
        val w = normalize(q).split(' ').filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var i = 0
        fun isNum(s: String?) = s != null && s.isNotEmpty() && s[0].isDigit()
        while (i < w.size) {
            val t = w[i]
            val next = w.getOrNull(i + 1)
            when {
                t in DROP_WITH_NUMBER && isNum(next) -> i += 2
                t in HOUSE_WORDS && isNum(next) -> i += 1
                t in PLACE_PREFIX && next != null && !isNum(next) && next !in EXPAND -> i += 1
                // «12 корп 1» → «12к1», «12 стр 2» → «12с2»
                t in BUILDING_WORDS && isNum(next) && out.isNotEmpty() && isNum(out.last()) -> {
                    out[out.size - 1] = out.last() + (if (t.startsWith("стр")) "с" else if (t.startsWith("лит")) "" else "к") + next
                    i += 2
                }
                // «12 а» → «12а» (одна буква после номера дома)
                t.length == 1 && t[0].isLetter() && out.isNotEmpty() && out.last().all { it.isDigit() } && t !in setOf("в", "к", "с", "и") -> {
                    out[out.size - 1] = out.last() + t
                    i += 1
                }
                else -> { out.add(EXPAND[t] ?: t); i += 1 }
            }
        }
        return out.joinToString(" ")
    }

    /** Полный разбор: лишние слова и сокращения. */
    fun clean(q: String): String = expand(stripFiller(q))

    private val STREET_TYPES = setOf(
        "улица", "проспект", "площадь", "переулок", "проезд", "набережная", "шоссе", "бульвар",
        "микрорайон", "тупик", "аллея", "линия", "район", "область"
    )

    /** Без слов «улица», «проспект»…: «улица гоголя 12» → «гоголя 12». */
    fun withoutStreetTypes(q: String): String = q.split(' ').filter { it.isNotEmpty() && it !in STREET_TYPES }.joinToString(" ")

    /** Без номера дома: если такого дома нет в базе, найдётся хотя бы улица. */
    fun withoutHouse(q: String): String = q.split(' ').filter { it.isNotEmpty() && !it[0].isDigit() }.joinToString(" ")

    // ---------- раскладка и латиница ----------

    private const val EN_KEYS = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`"
    private const val RU_KEYS = "йцукенгшщзхъфывапролджэячсмитьбюё"

    private fun latinCount(s: String) = s.count { it in 'a'..'z' || it in 'A'..'Z' }
    private fun cyrCount(s: String) = s.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }

    /** Написано латиницей (хотя бы 3 латинские буквы и ни одной русской). */
    fun looksLatin(raw: String) = latinCount(raw) >= 3 && cyrCount(raw) == 0

    /** Забыли переключить раскладку: «ujujkz 12» → «гоголя 12». */
    fun fromEnglishLayout(raw: String): String {
        val sb = StringBuilder()
        for (c in raw.lowercase()) {
            val k = EN_KEYS.indexOf(c)
            sb.append(if (k >= 0) RU_KEYS[k] else c)
        }
        return sb.toString()
    }

    private val TRANSLIT = listOf(
        "shch" to "щ", "sch" to "щ", "yo" to "ё", "yu" to "ю", "ya" to "я", "ye" to "е", "je" to "е", "ju" to "ю", "ja" to "я",
        "zh" to "ж", "kh" to "х", "ch" to "ч", "sh" to "ш", "ts" to "ц", "iy" to "ий", "yy" to "ый", "ck" to "к", "ph" to "ф",
        "a" to "а", "b" to "б", "v" to "в", "g" to "г", "d" to "д", "e" to "е", "z" to "з", "i" to "и", "j" to "й",
        "k" to "к", "l" to "л", "m" to "м", "n" to "н", "o" to "о", "p" to "п", "r" to "р", "s" to "с", "t" to "т",
        "u" to "у", "f" to "ф", "h" to "х", "c" to "к", "w" to "в", "x" to "кс", "q" to "к", "y" to "ы", "'" to "ь"
    )

    /** Русские слова латиницей: «gogolya» → «гоголя», «pskov» → «псков». */
    fun transliterate(raw: String): String {
        val s = raw.lowercase()
        val sb = StringBuilder()
        var i = 0
        outer@ while (i < s.length) {
            for ((lat, ru) in TRANSLIT) {
                if (s.startsWith(lat, i)) { sb.append(ru); i += lat.length; continue@outer }
            }
            sb.append(s[i]); i++
        }
        return sb.toString()
    }

    // ---------- окончания и опечатки ----------

    private val ENDINGS = listOf(
        "ыми", "ими", "ого", "его", "ому", "ему", "ая", "яя", "ой", "ей", "ий", "ый", "ую", "юю", "ое", "ее", "ые", "ие",
        "ых", "их", "ов", "ев", "ам", "ям", "ах", "ях", "ом", "ем", "ью", "а", "я", "ы", "и", "е", "у", "ю", "о", "ь", "й"
    )

    /** Основа слова: «великих» → «велик», «порхове» → «порхов», «гоголя» → «гогол». */
    fun stem(word: String): String {
        val w = word.replace('ё', 'е')
        if (w.length <= 4 || w.any { it.isDigit() }) return w
        for (e in ENDINGS) if (w.endsWith(e) && w.length - e.length >= 3) return w.dropLast(e.length)
        return w
    }

    /** Запрос из основ слов: «до великих лук» → «велик лук». Для поиска, когда точных совпадений нет. */
    fun stemmed(q: String): String = q.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { stem(it) }

    /** Число правок, чтобы из одного слова получить другое (с перестановкой соседних букв). */
    fun editDistance(a: String, b: String): Int {
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, d[i - 2][j - 2] + 1)
            d[i][j] = v
        }
        return d[n][m]
    }

    /** Похожесть слова из запроса на слово из названия: 1 — то же самое, 0 — ничего общего. */
    fun wordSimilarity(q: String, w: String): Double {
        if (q == w) return 1.0
        val qNum = q.any { it.isDigit() }
        if (qNum || w.any { it.isDigit() }) {
            if (!qNum || !w.any { it.isDigit() }) return 0.0
            return when {
                q.filter { it.isDigit() } == w.filter { it.isDigit() } -> 0.85 // 12а и 12
                w.startsWith(q) -> 0.6
                else -> 0.0
            }
        }
        if (q.length >= 2 && w.startsWith(q)) return 0.92 // набирают по буквам
        if (w.length >= 4 && q.startsWith(w) && q.length - w.length <= 3) return 0.9 // «порхове» → «порхов»
        val sq = stem(q)
        val sw = stem(w)
        if (sq == sw) return 0.95
        if (sq.length >= 3 && sw.startsWith(sq)) return 0.88
        val maxLen = maxOf(sq.length, sw.length)
        val sim = 1.0 - editDistance(sq, sw).toDouble() / maxLen
        // короткие слова с ошибкой почти ничего не значат
        return if (maxLen <= 3) (if (sim >= 0.99) 1.0 else 0.0) else sim * 0.9
    }

    private val SKIP_IN_SCORE = STREET_TYPES + setOf("тц", "трц", "в", "на", "и", "у")

    /** Насколько место подходит под запрос: среднее по словам запроса лучшего совпадения в названии и адресе. */
    fun score(query: String, title: String, subtitle: String): Double {
        val qw = fold(query).split(' ').filter { it.isNotEmpty() && it !in SKIP_IN_SCORE }
        if (qw.isEmpty()) return 0.5
        val pw = (fold(title) + " " + fold(subtitle)).split(' ').filter { it.isNotEmpty() }
        if (pw.isEmpty()) return 0.0
        var sum = 0.0
        for (q in qw) sum += pw.maxOf { wordSimilarity(q, it) }
        // слова, совпавшие с названием, весят больше совпадений с городом
        val inTitle = fold(title).split(' ').filter { it.isNotEmpty() }
        val titleHit = if (inTitle.isEmpty()) 0.0 else qw.count { q -> inTitle.any { wordSimilarity(q, it) >= 0.85 } }.toDouble() / qw.size
        return (sum / qw.size) * 0.85 + titleHit * 0.15
    }

    // ---------- координаты ----------

    private val COORDS = Regex("""^\s*(-?\d{1,2}[.,]\d+)\s*[,; ]\s*(-?\d{1,3}[.,]\d+)\s*$""")

    /** «57.81, 28.35» или «57,81 28,35» → точка. */
    fun coordinates(raw: String): LatLon? {
        val m = COORDS.find(raw) ?: return null
        val lat = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return LatLon(lat, lon)
    }

    // ---------- дом и работа ----------

    /** «домой», «дом» → "home"; «на работу», «работа» → "work". */
    fun favoriteKind(q: String): String? = when (fold(q)) {
        "дом", "домой", "дома", "до дома", "к дому", "home", "домашний адрес" -> "home"
        "работа", "работу", "на работу", "работы", "до работы", "офис", "в офис", "work" -> "work"
        else -> null
    }

    // ---------- виды мест ----------

    /** Вид места: слова, по которым его узнать, фильтр OpenStreetMap и подпись для безымянных мест. */
    class Category(val label: String, val tags: List<String>, val words: List<String>)

    val CATEGORIES = listOf(
        Category("АЗС", listOf("amenity:fuel"), listOf("заправка", "заправки", "заправку", "заправиться", "заправок", "азс", "бензин", "бензоколонка", "дизель", "топливо", "соляра", "солярка")),
        Category("Зарядка для электромобиля", listOf("amenity:charging_station"), listOf("зарядка", "зарядку", "зарядная", "электрозаправка", "электрозарядка")),
        Category("Кафе", listOf("amenity:cafe", "amenity:restaurant", "amenity:fast_food"), listOf("кафе", "поесть", "покушать", "перекусить", "еда", "ресторан", "рестораны", "столовая", "столовую", "кофе", "кофейня", "фастфуд", "бургер", "шаурма", "пицца", "пиццерия", "обед", "пообедать", "позавтракать", "поужинать")),
        Category("Аптека", listOf("amenity:pharmacy"), listOf("аптека", "аптеку", "аптеки", "лекарства")),
        Category("Больница", listOf("amenity:hospital", "amenity:clinic"), listOf("больница", "больницу", "травмпункт", "поликлиника", "поликлинику", "скорая", "врач", "клиника")),
        Category("Парковка", listOf("amenity:parking"), listOf("парковка", "парковку", "стоянка", "стоянку", "припарковаться", "паркинг")),
        Category("Автомойка", listOf("amenity:car_wash"), listOf("мойка", "мойку", "автомойка", "автомойку", "помыть")),
        Category("Автосервис", listOf("shop:car_repair", "shop:tyres"), listOf("автосервис", "сто", "шиномонтаж", "шиномонтажка", "ремонт", "автомастерская", "развал")),
        Category("Автозапчасти", listOf("shop:car_parts"), listOf("автозапчасти", "запчасти", "автомагазин")),
        Category("Продукты", listOf("shop:supermarket", "shop:convenience"), listOf("магазин", "продукты", "продуктовый", "супермаркет", "гипермаркет", "магаз")),
        Category("Торговый центр", listOf("shop:mall"), listOf("тц", "трц", "торговый центр", "молл")),
        Category("Гостиница", listOf("tourism:hotel", "tourism:motel", "tourism:hostel", "tourism:guest_house"), listOf("гостиница", "гостиницу", "отель", "мотель", "хостел", "переночевать", "ночлег", "гостевой дом")),
        Category("Банкомат", listOf("amenity:atm", "amenity:bank"), listOf("банкомат", "банк", "снять деньги", "наличные")),
        Category("Туалет", listOf("amenity:toilets"), listOf("туалет", "wc")),
        Category("Почта", listOf("amenity:post_office"), listOf("почта", "почту", "почтовое отделение")),
        Category("Вокзал", listOf("railway:station", "amenity:bus_station"), listOf("вокзал", "автовокзал", "жд вокзал", "ж/д вокзал")),
        Category("Аэропорт", listOf("aeroway:aerodrome"), listOf("аэропорт")),
        Category("Полиция", listOf("amenity:police"), listOf("полиция", "полицию", "гибдд", "гаи", "отдел полиции")),
        Category("Пункт выдачи", listOf("shop:outpost", "amenity:parcel_locker"), listOf("пвз", "пункт выдачи", "озон", "вайлдберриз", "wildberries", "ozon"))
    )

    private val NEAR_WORDS = setOf("ближайшая", "ближайший", "ближайшее", "ближайшую", "ближайшие", "рядом", "поблизости", "недалеко", "около", "возле", "тут", "здесь", "по", "пути", "дороге", "где-нибудь", "какой-нибудь", "какая-нибудь", "любая", "любой", "можно", "хорошее", "хорошая", "хороший", "вкусно", "открытая", "открытый", "сейчас", "мне", "бы")

    /** Вид места в запросе и что осталось после его слов («заправка лукойл» → АЗС + «лукойл»). */
    fun category(q: String): Pair<Category, String>? {
        val f = " ${fold(q)} "
        for (c in CATEGORIES) {
            val hit = c.words.map { fold(it) }.firstOrNull { f.contains(" $it ") } ?: continue
            val rest = f.replace(" $hit ", " ").trim().split(' ')
                .filter { it.isNotEmpty() && it !in NEAR_WORDS && it !in LEADING_PREPS && it !in FILLER_WORDS }
                .joinToString(" ")
            // «улица Заправочная» и подобное — это адрес, а не вид места
            if (rest.split(' ').any { it in STREET_TYPES } || rest.any { it.isDigit() }) return null
            return c to rest
        }
        return null
    }
}
