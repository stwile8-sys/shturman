package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.ui.fx.LocalFx
import ru.shturman.ui.fx.accentBrush
import ru.shturman.ui.fx.fxAnchor
import ru.shturman.ui.fx.panel
import ru.shturman.ui.fx.pressFx
import ru.shturman.ui.fx.rememberFxAnchor
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import ru.shturman.ui.themes.Flavor
import ru.shturman.ui.themes.ThemeSound
import ru.shturman.ui.themes.ThemeSounds
import ru.shturman.ui.themes.themedEnter

/** Высота строки поиска с отступом снизу: под неё поднимаются скорость и показания в углу. */
val SearchPillReserve = 100.dp

/**
 * Нижняя строка «Куда едем?» на карте (когда маршрута нет): стеклянная капсула,
 * справа — круглая кнопка «Где я» с градиентом темы.
 */
@Composable
fun SearchPill(onSearch: () -> Unit, onLocate: () -> Unit, modifier: Modifier = Modifier) {
    val l = ShColors.L
    val shape: Shape = when (l.flavor) {
        Flavor.TELEMETRY -> CutCornerShape(topEnd = 18.dp, bottomStart = 18.dp)
        Flavor.NEON -> RoundedCornerShape(16.dp)
        else -> RoundedCornerShape(31.dp)
    }
    val fx = LocalFx.current
    val anchor = rememberFxAnchor()
    val src = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .themedEnter(2)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .height(62.dp)
            .pressFx()
            .panel(shape, elevation = 16.dp)
            .clickable(interactionSource = src, indication = null) {
                ThemeSounds.play(ThemeSound.TAP)
                onSearch()
            }
            .padding(start = 22.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = ShColors.Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = "Куда едем?",
            color = ShColors.TextPrimary.copy(alpha = 0.88f),
            style = ShText.Small.copy(fontSize = 17.sp),
            modifier = Modifier.weight(1f)
        )
        val locShape: Shape = if (l.flavor == Flavor.TELEMETRY) CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp) else CircleShape
        Box(
            modifier = Modifier
                .fxAnchor(anchor)
                .size(46.dp)
                .pressFx()
                .clip(locShape)
                .background(accentBrush())
                .clickable {
                    ThemeSounds.play(ThemeSound.TAP)
                    fx.ring(anchor.center)
                    onLocate()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = "Где я", tint = ShColors.OnAccent, modifier = Modifier.size(22.dp))
        }
    }
}
