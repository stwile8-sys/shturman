package ru.shturman.ui.themes

import androidx.compose.ui.graphics.Color

/**
 * Характер оформления темы поверх общей «стеклянной» основы:
 * GLASS — чистое стекло и мягкие пружины; NEON — бегущая неоновая обводка, глитч, пиксели;
 * TELEMETRY — гоночная телеметрия: срезанные углы, полоса акцента, резкие въезды, стартовые огни;
 * DEPTH — глубина: объёмные тени, наклон от касания, карточки выезжают из глубины;
 * APEX — табло и терминал: плоские панели с метками по углам, строки развёртки, элементы загораются с мерцанием;
 * PREMIUM — новое оформление «Спорт-премиум» у всех тем, кроме APEX: ровное тёмное стекло с тонкой рамкой,
 * радиусы 14/10, спокойные короткие анимации без частиц и вспышек.
 */
enum class Flavor { GLASS, NEON, TELEMETRY, DEPTH, APEX, PREMIUM }

/** Какие частицы разлетаются при нажатиях и на старте. */
enum class Particle { HEART, PIXEL, SPARK, EMBER, DUST, SMOKE }

class ThemeLook(
    val flavor: Flavor,
    /** Второй цвет градиента (кнопка «Поехали», заливки, иконки поворота). */
    val acc2: Color,
    val particle: Particle,
    val particleColors: List<Color>,
    /** Цвет свечения вокруг панелей и кнопок. */
    val glow: Color,
    /** Цвет «главной точки» (точка назначения на карте), если он не цвет акцента. */
    val dot: Color? = null,
    /** Цвет следа за машиной на карте, если не обычный дымчатый. */
    val trail: Color? = null
)

object Looks {
    val all: List<ThemeLook> = listOf(
        // Классика — стекло + неон
        ThemeLook(Flavor.PREMIUM, Color(0xFFFF00A8), Particle.PIXEL, listOf(Color(0xFF00F0FF), Color(0xFFFF00A8), Color(0xFFB4FF00)), Color(0xFF00F0FF)),
        // Принцесса — чистое мягкое стекло, сердечки
        ThemeLook(Flavor.PREMIUM, Color(0xFFB98BFF), Particle.HEART, listOf(Color(0xFFFF69B4), Color(0xFFB57EDC), Color(0xFFFFD700), Color(0xFFFFB3D9)), Color(0xFFFF69B4)),
        // NASCAR — стекло + телеметрия, искры
        ThemeLook(Flavor.PREMIUM, Color(0xFFE60019), Particle.SPARK, listOf(Color(0xFFFFC14D), Color(0xFFFF7A1A), Color(0xFFFFFFFF)), Color(0xFFE60019)),
        // GT Racing — стекло + карбон, тахометр
        ThemeLook(Flavor.PREMIUM, Color(0xFFFF5A1F), Particle.SPARK, listOf(Color(0xFFFF3B1F), Color(0xFFFFC14D), Color(0xFFFFFFFF)), Color(0xFFE60019)),
        // Night Rider — стекло + глубина, синие угли
        ThemeLook(Flavor.PREMIUM, Color(0xFF1F6BFF), Particle.EMBER, listOf(Color(0xFF00D9FF), Color(0xFF3A8BFF), Color(0xFFBFF4FF)), Color(0xFF00D9FF)),
        // Street Underground — стекло + янтарный неон, мигалки
        ThemeLook(Flavor.PREMIUM, Color(0xFFFF3B1F), Particle.SPARK, listOf(Color(0xFFFFB000), Color(0xFFFF6A00), Color(0xFFFFE08A)), Color(0xFFFFB000)),
        // Rally Gravel — стекло + телеметрия, пыль и гравий
        ThemeLook(Flavor.PREMIUM, Color(0xFFFFB45A), Particle.DUST, listOf(Color(0xFFC79A66), Color(0xFFE8C9A0), Color(0xFF8A6A48)), Color(0xFFD97830)),
        // Drift Night — стекло + глубина, туман и дым
        ThemeLook(Flavor.PREMIUM, Color(0xFFB04BE8), Particle.SMOKE, listOf(Color(0xFFDDE2FF), Color(0xFFB8C0F0), Color(0xFF8A7BFF)), Color(0xFF6A5BFF)),
        // APEX — табло, мел и одна красная точка
        ThemeLook(Flavor.APEX, Color(0xFFD9D6CC), Particle.PIXEL, listOf(Color(0xFFF3F1EA), Color(0xFFFF2B2B), Color(0xFFA6A39A)), Color(0xFFF3F1EA),
            dot = Color(0xFFFF2B2B), trail = Color(0xFFFF2B2B)),
        // Спорт-премиум — графит и красный акцент, без свечения
        ThemeLook(Flavor.PREMIUM, Color(0xFFB0242C), Particle.SPARK, listOf(Color(0xFFE0313A), Color(0xFFF5F5F5)), Color(0xFF000000))
    )

    fun of(id: Int): ThemeLook = all[id.coerceIn(0, all.size - 1)]
}
