@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package ru.shturman.ui.themes

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import ru.shturman.R

/** Форма кнопок. */
enum class BtnKind { CHAMFER, ROUND, STRIPES, CARBON, SLIM, CONCRETE, GRAIN, SOFT }

/** Как панели появляются на экране. */
enum class EnterKind { RISE, FLOAT, SIDE_FAST, STICK_DOWN, EMERGE, DROP, SIDE_DUST, FOG }

/** Как меняются цифры. */
enum class DigitKind { GLITCH, ROLL, FLY, PRINT, BLUR, BOARD, KNOCK, HAZE }

private fun vf(res: Int, weight: Int): FontFamily =
    FontFamily(Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight))))

/**
 * Одна тема оформления. Здесь собрано всё, что в ней можно менять: цвета, шрифты, форма и размер кнопок и панелей,
 * длительности анимаций, заставка и звуки. Логика приложения от темы не зависит.
 */
class ThemeSpec(
    val id: Int,
    val name: String,
    val vibe: String,
    // цвета
    val bg: Color,
    val panel: Color,
    val accent: Color,
    val second: Color,
    val third: Color,
    val danger: Color,
    val text: Color,
    val textSec: Color,
    val divider: Color,
    val search: Color,
    val iconTint: Color,
    val flash: Color,
    val signs: List<Color>,
    // шрифты
    val numFont: FontFamily,
    val textFont: FontFamily,
    val letterSp: Float,
    // формы и размеры
    val btn: BtnKind,
    val btnRadius: Int,
    val btnW: Int,
    val btnH: Int,
    val panelRadius: Int,
    val borderW: Float,
    val border: Color,
    // анимации
    val enter: EnterKind,
    val enterMs: Int,
    val digit: DigitKind,
    val pressMs: Int,
    val pressScale: Float,
    val transMs: Int,
    val routeMs: Int,
    val routeJerky: Boolean,
    val vibrate: Boolean,
    // заставка и её звук
    val splashMs: Int,
    val splashPeakMs: Int,
    val splashRes: Int
)

object Themes {
    const val PREF = "theme"

    val all: List<ThemeSpec> = listOf(
        // 1. КЛАССИКА — неоновый киберпанк
        ThemeSpec(
            id = 0, name = "Классика", vibe = "Неоновый киберпанк, цифровой шторм",
            bg = Color(0xFF050608), panel = Color(0xFF0D1015), accent = Color(0xFF00F0FF), second = Color(0xFFFF00A8),
            third = Color(0xFFB4FF00), danger = Color(0xFFFF2233), text = Color(0xFFFFFFFF), textSec = Color(0xFF8A9099),
            divider = Color(0xFF252B34), search = Color(0xFF161B22), iconTint = Color(0xFF00F0FF), flash = Color(0xFF00F0FF),
            signs = listOf(Color(0xFFFF2233), Color(0xFFFF00A8), Color(0xFFFFD400), Color(0xFFB4FF00), Color(0xFF00F0FF)),
            numFont = vf(R.font.orbitron, 700), textFont = vf(R.font.inter, 500), letterSp = 0f,
            btn = BtnKind.CHAMFER, btnRadius = 14, btnW = 48, btnH = 48, panelRadius = 18, borderW = 1f, border = Color(0x8800F0FF),
            enter = EnterKind.RISE, enterMs = 300, digit = DigitKind.GLITCH, pressMs = 100, pressScale = 1f, transMs = 250,
            routeMs = 1000, routeJerky = false, vibrate = false,
            splashMs = 8700, splashPeakMs = 2900, splashRes = R.raw.welcome
        ),
        // 2. ПРИНЦЕССА — мягкий пастельный
        ThemeSpec(
            id = 1, name = "Принцесса", vibe = "Мягкий пастельный, романтичный",
            bg = Color(0xFF0F0510), panel = Color(0xFF1A0F1A), accent = Color(0xFFFF69B4), second = Color(0xFFB57EDC),
            third = Color(0xFFFFD700), danger = Color(0xFFFF4D6D), text = Color(0xFFFFEAF5), textSec = Color(0xFFC9A0C4),
            divider = Color(0xFF3A233A), search = Color(0xFF261526), iconTint = Color(0xFFFF8FC8), flash = Color(0xFFFF69B4),
            signs = listOf(Color(0xFFFF4D6D), Color(0xFFFF8FA3), Color(0xFFFFD700), Color(0xFFB57EDC), Color(0xFFFF69B4)),
            numFont = vf(R.font.comfortaa, 700), textFont = vf(R.font.comfortaa, 500), letterSp = 0f,
            btn = BtnKind.ROUND, btnRadius = 20, btnW = 50, btnH = 50, panelRadius = 28, borderW = 1.2f, border = Color(0x99FF69B4),
            enter = EnterKind.FLOAT, enterMs = 400, digit = DigitKind.ROLL, pressMs = 200, pressScale = 1.05f, transMs = 400,
            routeMs = 1500, routeJerky = false, vibrate = false,
            splashMs = 3200, splashPeakMs = 750, splashRes = R.raw.splash_t2
        ),
        // 3. NASCAR — ночной стадион
        ThemeSpec(
            id = 2, name = "NASCAR", vibe = "Ночной стадион, прожекторы, дым от шин",
            bg = Color(0xFF0A0A0A), panel = Color(0xFF141414), accent = Color(0xFFFFFFFF), second = Color(0xFFE60019),
            third = Color(0xFF1E4FBF), danger = Color(0xFFFF2233), text = Color(0xFFFFFFFF), textSec = Color(0xFFB0B0B0),
            divider = Color(0xFF2A2A2A), search = Color(0xFF1E1E1E), iconTint = Color(0xFFFFFFFF), flash = Color(0xFFFFFFFF),
            signs = listOf(Color(0xFFE60019), Color(0xFFFF6A00), Color(0xFFFFD400), Color(0xFFFFFFFF), Color(0xFF1E4FBF)),
            numFont = FontFamily(Font(R.font.bebas_neue)), textFont = vf(R.font.roboto_condensed, 500), letterSp = 0.5f,
            btn = BtnKind.STRIPES, btnRadius = 4, btnW = 50, btnH = 50, panelRadius = 6, borderW = 2f, border = Color(0xFF000000),
            enter = EnterKind.SIDE_FAST, enterMs = 200, digit = DigitKind.FLY, pressMs = 100, pressScale = 0.96f, transMs = 150,
            routeMs = 800, routeJerky = true, vibrate = false,
            splashMs = 8600, splashPeakMs = 2250, splashRes = R.raw.splash_t3
        ),
        // 4. GT RACING — дневная гонка, карбон
        ThemeSpec(
            id = 3, name = "GT Racing", vibe = "Дневная гонка, наклейки, карбон",
            bg = Color(0xFF0A0505), panel = Color(0xFF1A0A0A), accent = Color(0xFFE60019), second = Color(0xFFFFFFFF),
            third = Color(0xFF0A0A0A), danger = Color(0xFFFF6B00), text = Color(0xFFFFFFFF), textSec = Color(0xFFC4A8A8),
            divider = Color(0xFF3A1A1A), search = Color(0xFF241010), iconTint = Color(0xFFFFFFFF), flash = Color(0xFFE60019),
            signs = listOf(Color(0xFFE60019), Color(0xFFFF6B00), Color(0xFFFFD400), Color(0xFFFFFFFF), Color(0xFFB8B8B8)),
            numFont = FontFamily(Font(R.font.racing_sans_one)), textFont = FontFamily.SansSerif, letterSp = 0.5f,
            btn = BtnKind.CARBON, btnRadius = 4, btnW = 50, btnH = 50, panelRadius = 4, borderW = 1.5f, border = Color(0xFFE60019),
            enter = EnterKind.STICK_DOWN, enterMs = 250, digit = DigitKind.PRINT, pressMs = 150, pressScale = 1f, transMs = 200,
            routeMs = 1000, routeJerky = false, vibrate = false,
            splashMs = 3850, splashPeakMs = 1250, splashRes = R.raw.splash_t4
        ),
        // 5. NIGHT RIDER — синее пламя
        ThemeSpec(
            id = 4, name = "Night Rider", vibe = "Ночная трасса, реактивный форсаж, синее пламя",
            bg = Color(0xFF050A18), panel = Color(0xFF0A1428), accent = Color(0xFF00D9FF), second = Color(0xFFFF2233),
            third = Color(0xFF1A3A8A), danger = Color(0xFFFF2233), text = Color(0xFFE8F8FF), textSec = Color(0xFF7FA6C8),
            divider = Color(0xFF16284A), search = Color(0xFF0F1D38), iconTint = Color(0xFF00D9FF), flash = Color(0xFF00D9FF),
            signs = listOf(Color(0xFFFF2233), Color(0xFFFF7A00), Color(0xFFFFD400), Color(0xFF3AA0FF), Color(0xFF00D9FF)),
            numFont = vf(R.font.orbitron, 700), textFont = vf(R.font.orbitron, 500), letterSp = 3f,
            btn = BtnKind.SLIM, btnRadius = 26, btnW = 54, btnH = 40, panelRadius = 22, borderW = 1.2f, border = Color(0xAA00D9FF),
            enter = EnterKind.EMERGE, enterMs = 350, digit = DigitKind.BLUR, pressMs = 150, pressScale = 1f, transMs = 300,
            routeMs = 1000, routeJerky = false, vibrate = false,
            splashMs = 6800, splashPeakMs = 2250, splashRes = R.raw.splash_t5
        ),
        // 6. STREET UNDERGROUND — подпольные гонки
        ThemeSpec(
            id = 5, name = "Street Underground", vibe = "Подпольные гонки, красный свет, пыль",
            bg = Color(0xFF1A0A08), panel = Color(0xFF2A1208), accent = Color(0xFFFF3A20), second = Color(0xFFFF8A00),
            third = Color(0xFFFFD400), danger = Color(0xFFFF0000), text = Color(0xFFFFEDE6), textSec = Color(0xFFC49A8A),
            divider = Color(0xFF4A2414), search = Color(0xFF331A0E), iconTint = Color(0xFFFF8A00), flash = Color(0xFFFF3A20),
            signs = listOf(Color(0xFFFF0000), Color(0xFFFF3A20), Color(0xFFFF8A00), Color(0xFFFFD400), Color(0xFFFFE9A0)),
            numFont = FontFamily(Font(R.font.bebas_neue)), textFont = vf(R.font.oswald, 500), letterSp = 0.5f,
            btn = BtnKind.CONCRETE, btnRadius = 3, btnW = 50, btnH = 50, panelRadius = 4, borderW = 1.5f, border = Color(0xFF6A2E18),
            enter = EnterKind.DROP, enterMs = 250, digit = DigitKind.BOARD, pressMs = 120, pressScale = 0.95f, transMs = 200,
            routeMs = 900, routeJerky = true, vibrate = true,
            splashMs = 3300, splashPeakMs = 500, splashRes = R.raw.splash_t6
        ),
        // 7. RALLY GRAVEL — пыль, гравий, лес
        ThemeSpec(
            id = 6, name = "Rally Gravel", vibe = "Ралли, пыль, гравий, лес",
            bg = Color(0xFF1A0F08), panel = Color(0xFF2A1A0A), accent = Color(0xFFD97830), second = Color(0xFF1E3A8A),
            third = Color(0xFFFFFFFF), danger = Color(0xFFFF3A20), text = Color(0xFFFFF1E4), textSec = Color(0xFFC4A688),
            divider = Color(0xFF4A3216), search = Color(0xFF35220E), iconTint = Color(0xFFE8933F), flash = Color(0xFFD97830),
            signs = listOf(Color(0xFFFF3A20), Color(0xFFD97830), Color(0xFFFFD400), Color(0xFFE8E0D0), Color(0xFF4A78D8)),
            numFont = FontFamily(Font(R.font.bebas_neue)), textFont = vf(R.font.roboto_condensed, 500), letterSp = 0.5f,
            btn = BtnKind.GRAIN, btnRadius = 8, btnW = 50, btnH = 50, panelRadius = 8, borderW = 1.2f, border = Color(0xFF7A4A22),
            enter = EnterKind.SIDE_DUST, enterMs = 300, digit = DigitKind.KNOCK, pressMs = 150, pressScale = 0.97f, transMs = 250,
            routeMs = 1200, routeJerky = false, vibrate = false,
            splashMs = 7100, splashPeakMs = 2500, splashRes = R.raw.splash_t7
        ),
        // 8. DRIFT NIGHT — туман, горная дорога
        ThemeSpec(
            id = 7, name = "Drift Night", vibe = "Ночной дрифт на горной дороге, туман",
            bg = Color(0xFF0A0F1E), panel = Color(0xFF141A2E), accent = Color(0xFF4A6FFF), second = Color(0xFF8A2BE2),
            third = Color(0xFFB4FF00), danger = Color(0xFFFF2233), text = Color(0xFFEAF0FF), textSec = Color(0xFF8A98C4),
            divider = Color(0xFF232C4A), search = Color(0xFF1B2340), iconTint = Color(0xFF8AA4FF), flash = Color(0xFF6A5BFF),
            signs = listOf(Color(0xFFFF2233), Color(0xFFB04BE8), Color(0xFFFFD400), Color(0xFFB4FF00), Color(0xFF4A6FFF)),
            numFont = vf(R.font.orbitron, 600), textFont = vf(R.font.inter, 500), letterSp = 0.5f,
            btn = BtnKind.SOFT, btnRadius = 10, btnW = 50, btnH = 50, panelRadius = 18, borderW = 1f, border = Color(0x884A6FFF),
            enter = EnterKind.FOG, enterMs = 400, digit = DigitKind.HAZE, pressMs = 200, pressScale = 1f, transMs = 400,
            routeMs = 1300, routeJerky = false, vibrate = false,
            splashMs = 5000, splashPeakMs = 750, splashRes = R.raw.splash_t8
        )
    )

    /** Номер сохранённой темы. Старая настройка «режим принцессы» превращается в тему №2. */
    fun load(prefs: SharedPreferences): Int {
        if (prefs.contains(PREF)) return prefs.getInt(PREF, 0).coerceIn(0, all.size - 1)
        return if (prefs.getBoolean("princess", false)) 1 else 0
    }
}
