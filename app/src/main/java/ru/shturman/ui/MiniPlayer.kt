package ru.shturman.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.shturman.R
import ru.shturman.media.Music
import ru.shturman.media.MusicState
import ru.shturman.ui.fx.panel
import ru.shturman.ui.fx.panelShape
import ru.shturman.ui.theme.ShColors

/**
 * Мини-плеер в поездке: что играет и три кнопки — назад, пауза, вперёд. Появляется сам, когда играет музыка
 * (Яндекс Музыка или другой плеер) и выдан «Доступ к уведомлениям». Опрос раз в 2 секунды в фоновом потоке;
 * экран обновляется, только когда сменилась песня, исполнитель, пауза или плеер.
 */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    var music by remember { mutableStateOf<MusicState?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val next = withContext(Dispatchers.IO) {
                if (Music.hasAccess(context)) runCatching { Music.read(context) }.getOrNull() else null
            }
            if (!sameTrack(music, next)) music = next
            delay(2000L)
        }
    }
    val m = music ?: return
    if (m.title.isBlank()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .panel(panelShape(22.dp), elevation = 8.dp, alpha = 0.85f)
            .clickable(onClick = onOpen)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val shape = RoundedCornerShape(8.dp)
        // обложка переводится в картинку для экрана один раз на песню
        val art = remember(m.title, m.art) { m.art?.asImageBitmap() }
        if (art != null) {
            Image(
                bitmap = art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(shape)
            )
        } else {
            Box(Modifier.size(36.dp).clip(shape).background(ShColors.SearchField), contentAlignment = Alignment.Center) {
                Icon(ImageVector.vectorResource(R.drawable.ic_music), null, tint = ShColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(m.title, color = ShColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // строка исполнителя есть всегда (пустая, если его нет): высота плеера не меняется от песни к песне
            Text(m.artist.ifBlank { " " }, color = ShColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PlayerKey({ Music.previous(context) }) { c -> skip(c, next = false) }
        PlayerKey({ Music.toggle(context); music = MusicState(m.title, m.artist, m.art, !m.playing, m.posMs, m.durMs, m.pkg) }) { c ->
            if (m.playing) {
                drawRect(c, Offset(size.width * 0.18f, size.height * 0.1f), Size(size.width * 0.22f, size.height * 0.8f))
                drawRect(c, Offset(size.width * 0.60f, size.height * 0.1f), Size(size.width * 0.22f, size.height * 0.8f))
            } else {
                drawPath(Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.08f); lineTo(size.width * 0.92f, size.height / 2f)
                    lineTo(size.width * 0.22f, size.height * 0.92f); close()
                }, c)
            }
        }
        PlayerKey({ Music.next(context) }) { c -> skip(c, next = true) }
    }
}

/** Кнопка 44 dp со значком, нарисованным вручную (в наборе иконок нет «пауза» и «следующий»). */
@Composable
private fun PlayerKey(onClick: () -> Unit, draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val c = ShColors.TextPrimary
        Canvas(Modifier.size(18.dp)) { draw(c) }
    }
}

/** Для экрана ничего не изменилось: та же песня, исполнитель, пауза, плеер и есть ли обложка. */
private fun sameTrack(a: MusicState?, b: MusicState?): Boolean {
    if (a == null || b == null) return a == null && b == null
    return a.title == b.title && a.artist == b.artist && a.playing == b.playing && a.pkg == b.pkg &&
        (a.art == null) == (b.art == null)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.skip(c: Color, next: Boolean) {
    val w = size.width
    val h = size.height
    drawPath(Path().apply {
        if (next) {
            moveTo(0f, 0f); lineTo(w * 0.72f, h / 2f); lineTo(0f, h); close()
        } else {
            moveTo(w, 0f); lineTo(w * 0.28f, h / 2f); lineTo(w, h); close()
        }
    }, c)
    if (next) drawRect(c, Offset(w * 0.8f, 0f), Size(w * 0.2f, h)) else drawRect(c, Offset(0f, 0f), Size(w * 0.2f, h))
}
