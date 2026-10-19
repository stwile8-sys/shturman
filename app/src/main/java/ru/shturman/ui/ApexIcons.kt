package ru.shturman.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Контурные значки нового оформления (сетка 24×24, линия 1,6). Цвет задаёт `Icon(tint = …)`.
 * Пути — те же, что на макетах (docs/design).
 */
object ApexIcons {
    private fun icon(name: String, stroke: Float = 1.6f, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return b.build()
    }

    val Menu by lazy { icon("menu", 1.6f, "M4 7h16M4 12h16M4 17h16") }
    val Sound by lazy { icon("sound", 1.6f, "M4 9v6h4l5 4V5L8 9H4z", "M17 9a4 4 0 0 1 0 6") }
    val SoundOff by lazy { icon("soundOff", 1.6f, "M4 9v6h4l5 4V5L8 9H4z", "M17 9.5l5 5M22 9.5l-5 5") }
    val Overview by lazy { icon("overview", 1.6f, "M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3z", "M9 3v15M15 6v15") }
    val Close by lazy { icon("close", 2f, "M6 6l12 12M18 6L6 18") }
    val Layers by lazy { icon("layers", 1.6f, "M12 3l9 5-9 5-9-5 9-5z", "M3 13l9 5 9-5") }
    val Locate by lazy { icon("locate", 1.6f, "M12 3L19 20L12 16L5 20Z") }
    val Search by lazy { icon("search", 1.8f, "M17.5 11a6.5 6.5 0 1 1-13 0a6.5 6.5 0 1 1 13 0z", "M16 16l4.5 4.5") }
    val Mic by lazy { icon("mic", 1.6f, "M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1-6 0z", "M5 11a7 7 0 0 0 14 0M12 18v3") }
    val Home by lazy { icon("home", 1.6f, "M3 11l9-7 9 7v9h-6v-6H9v6H3z") }
    val Work by lazy { icon("work", 1.6f, "M4 8h16v12H4z", "M9 8V5h6v3") }
    val Recent by lazy { icon("recent", 1.6f, "M20.5 12a8.5 8.5 0 1 1-17 0a8.5 8.5 0 1 1 17 0z", "M12 7.5V12l3 2") }
    val Pin by lazy { icon("pin", 1.6f, "M12 21s7-6.2 7-11.5A7 7 0 0 0 5 9.5C5 14.8 12 21 12 21z", "M14.5 9.5a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z") }
    val Street by lazy { icon("street", 1.6f, "M4 20L20 4M4 12h8M12 20v-8") }
    val Back by lazy { icon("back", 1.8f, "M15 5l-7 7 7 7") }
    val Chevron by lazy { icon("chevron", 1.8f, "M9 5l7 7-7 7") }
    val ChevronDown by lazy { icon("chevronDown", 2.2f, "M6 9l6 6 6-6") }
    val Look by lazy { icon("look", 1.6f, "M20.5 12a8.5 8.5 0 1 1-17 0a8.5 8.5 0 1 1 17 0z", "M12 3.5v17M3.5 12h17") }
    val Moon by lazy { icon("moon", 1.6f, "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z") }
    val Hud by lazy { icon("hud", 1.6f, "M4 18h16M7 18l2-12h6l2 12") }
    val Fuel by lazy { icon("fuel", 1.6f, "M5 20V5a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v15H5zM14 9h3l2 3v6a1.5 1.5 0 0 1-3 0v-3h-2") }
    val Stats by lazy { icon("stats", 1.6f, "M4 20V10M10 20V4M16 20v-7M22 20H2") }
    val Update by lazy { icon("update", 1.6f, "M12 4v11M7 10l5 5 5-5M5 20h14") }
    val Lock by lazy { icon("lock", 1.6f, "M7 10h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 10V7a4 4 0 0 1 8 0v3") }
    val Profile by lazy { icon("profile", 1.6f, "M16 8.5a4 4 0 1 1-8 0a4 4 0 1 1 8 0z", "M4.5 20a7.5 7.5 0 0 1 15 0") }
    val Caravan by lazy { icon("caravan", 1.6f, "M12.5 7.5a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0z", "M2.5 20a6.5 6.5 0 0 1 13 0", "M16 4.5a3.5 3.5 0 0 1 0 6.5M18 14.5a6.5 6.5 0 0 1 3.5 5.5") }
    val Data by lazy { icon("data", 1.6f, "M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3z", "M4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6", "M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3") }
    val Edit by lazy { icon("edit", 1.6f, "M4 20h4L19 9l-4-4L4 16v4z") }
    val Music by lazy { icon("music", 1.6f, "M9 18V5l11-2v13", "M9 18a3 3 0 1 1-6 0a3 3 0 1 1 6 0z", "M20 16a3 3 0 1 1-6 0a3 3 0 1 1 6 0z") }
    val Radar by lazy { icon("radar", 1.6f, "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M16 12a4 4 0 1 1-4-4", "M12 12l6-6") }
    val Bell by lazy { icon("bell", 1.6f, "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z", "M10 20a2 2 0 0 0 4 0") }
    val Wrench by lazy { icon("wrench", 1.6f, "M14.5 6.5a4 4 0 0 0-5.3 5.3L4 17v3h3l5.2-5.2a4 4 0 0 0 5.3-5.3l-2.5 2.5-2.5-.5-.5-2.5z") }
    val Share by lazy { icon("share", 1.6f, "M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M12 3v12M8 7l4-4 4 4") }
}
