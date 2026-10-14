package ru.shturman.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.shturman.R
import ru.shturman.media.Music
import ru.shturman.media.MusicState
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/** Плеер: обложка, название, исполнитель, перемотка и кнопки. Работает с Яндекс Музыкой и другими плеерами. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(Music.hasAccess(context)) }
    var music by remember { mutableStateOf<MusicState?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    // Раз в секунду обновляем состояние: пользователь мог выдать доступ и вернуться из настроек
    LaunchedEffect(Unit) {
        while (true) {
            access = Music.hasAccess(context)
            music = if (access) runCatching { Music.read(context) }.getOrNull() else null
            delay(1000)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.lerp(ShColors.Panel, androidx.compose.ui.graphics.Color.White, 0.06f).copy(alpha = 0.97f),
        shape = if (ShColors.L.flavor == ru.shturman.ui.themes.Flavor.TELEMETRY) androidx.compose.foundation.shape.CutCornerShape(topEnd = 26.dp)
            else androidx.compose.foundation.shape.RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        contentColor = ShColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ShColors.Divider) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val m = music
            when {
                !access -> {
                    Cover(null)
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(ru.shturman.R.string.s_9ae289ed), style = ShText.Distance.copy(fontSize = 22.sp), color = ShColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(ru.shturman.R.string.s_4da9d786),
                        color = ShColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("Разрешить доступ") { Music.openAccessSettings(context) }
                }
                m == null -> {
                    Cover(null)
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(ru.shturman.R.string.s_87e63aae), style = ShText.Distance.copy(fontSize = 22.sp), color = ShColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(ru.shturman.R.string.s_842334ad),
                        color = ShColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("Открыть Яндекс Музыку") {
                        if (!Music.openYandex(context)) message = "Яндекс Музыка не установлена на этом телефоне"
                    }
                }
                else -> Player(m, context)
            }
            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = ShColors.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            if (access && music != null) {
                TextButton(onClick = {
                    if (!Music.openYandex(context)) message = "Яндекс Музыка не установлена на этом телефоне"
                }) { Text(stringResource(ru.shturman.R.string.s_8f7d2d75), color = ShColors.TextSecondary, style = ShText.Small) }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
    ) { Text(text, style = ShText.Button) }
}

@Composable
private fun Cover(state: MusicState?) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(ShColors.Accent.copy(alpha = 0.35f), ShColors.SearchField))),
        contentAlignment = Alignment.Center
    ) {
        val art = state?.art
        if (art != null) {
            Image(bitmap = art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(220.dp))
        } else {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_music),
                contentDescription = null, tint = ShColors.TextPrimary.copy(alpha = 0.7f), modifier = Modifier.size(84.dp)
            )
        }
    }
}

@Composable
private fun Player(m: MusicState, context: android.content.Context) {
    Cover(m)
    Spacer(Modifier.height(18.dp))
    Text(
        text = m.title.ifBlank { "Без названия" },
        color = ShColors.TextPrimary, style = ShText.Distance.copy(fontSize = 22.sp),
        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
    )
    if (m.artist.isNotBlank()) {
        Text(
            text = m.artist, color = ShColors.TextSecondary, style = ShText.Street,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(10.dp))

    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = m.durMs.toFloat().coerceAtLeast(1f)
    if (m.durMs > 0) {
        Slider(
            value = dragging ?: m.posMs.toFloat().coerceIn(0f, dur),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { Music.seek(context, it.toLong()) }; dragging = null },
            valueRange = 0f..dur,
            colors = SliderDefaults.colors(
                thumbColor = ShColors.Accent, activeTrackColor = ShColors.Accent, inactiveTrackColor = ShColors.Divider
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(dragging?.toLong() ?: m.posMs), color = ShColors.TextSecondary, fontSize = 12.sp)
            Text(clock(m.durMs), color = ShColors.TextSecondary, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIcon(56) { Music.previous(context) }.also { }
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(ShColors.Accent)
                .clickable { Music.toggle(context) },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(30.dp)) {
                val c = ShColors.Background
                if (m.playing) {
                    drawRect(c, Offset(size.width * 0.12f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height))
                    drawRect(c, Offset(size.width * 0.60f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height))
                } else {
                    val p = Path().apply {
                        moveTo(size.width * 0.18f, 0f); lineTo(size.width, size.height / 2f); lineTo(size.width * 0.18f, size.height); close()
                    }
                    drawPath(p, c)
                }
            }
        }
        RoundIcon(56, next = true) { Music.next(context) }
    }
}

/** Кнопка «предыдущий/следующий»: две стрелки-треугольника и планка, нарисованы вручную. */
@Composable
private fun RoundIcon(sizeDp: Int, next: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(ShColors.SearchField)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) {
            val c = ShColors.TextPrimary
            val w = size.width
            val h = size.height
            val p = Path().apply {
                if (next) {
                    moveTo(0f, 0f); lineTo(w * 0.72f, h / 2f); lineTo(0f, h); close()
                } else {
                    moveTo(w, 0f); lineTo(w * 0.28f, h / 2f); lineTo(w, h); close()
                }
            }
            drawPath(p, c)
            if (next) drawRect(c, Offset(w * 0.8f, 0f), androidx.compose.ui.geometry.Size(w * 0.2f, h))
            else drawRect(c, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w * 0.2f, h))
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
