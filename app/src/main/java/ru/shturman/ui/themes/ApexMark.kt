package ru.shturman.ui.themes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Знак APEX: буква A как шпилька, вид сверху, и точка апекса внутри. Одноцветный, красится как иконка. */
val ApexMark: ImageVector by lazy {
    ImageVector.Builder(name = "ApexMark", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 128f, viewportHeight = 128f)
        .path(stroke = SolidColor(Color.Black), strokeLineWidth = 17f, strokeLineCap = StrokeCap.Butt) {
            moveTo(22.77f, 121.49f)
            lineTo(48.58f, 28.23f)
            arcTo(16f, 16f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 79.42f, y1 = 28.23f)
            lineTo(105.23f, 121.49f)
        }
        .path(fill = SolidColor(Color.Black)) {
            moveTo(56f, 60f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 72f, y1 = 60f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 56f, y1 = 60f)
            close()
        }
        .build()
}
