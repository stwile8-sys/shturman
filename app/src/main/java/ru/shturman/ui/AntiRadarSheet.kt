package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.shturman.R
import ru.shturman.media.AntiRadar
import ru.shturman.media.AntiRadarInfo
import ru.shturman.media.Music
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/** Панель антирадара: видно, запущен ли он, что пишет в уведомлении, можно открыть или остановить. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntiRadarSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(Music.hasAccess(context)) }
    var info by remember { mutableStateOf<AntiRadarInfo?>(null) }
    var installed by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            access = Music.hasAccess(context)
            info = runCatching { AntiRadar.read(context) }.getOrNull()
            installed = info != null || AntiRadar.isInstalled(context)
            delay(1000)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ShColors.Panel,
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
            val running = info?.running == true
            val ring = if (running) ShColors.Success else ShColors.TextSecondary
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(ring.copy(alpha = 0.14f))
                    .border(3.dp, ring, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_radar),
                    contentDescription = null, tint = ring, modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = when {
                    !installed -> "Антирадар не найден"
                    running -> "Антирадар работает"
                    else -> "Антирадар не запущен"
                },
                style = ShText.Distance.copy(fontSize = 22.sp), color = ShColors.TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    !installed -> "Установите приложение «GPS Антирадар» (или другое с «антирадар» в названии), и оно появится здесь."
                    running -> info?.text?.ifBlank { null } ?: "Работает в фоне и предупреждает голосом о камерах."
                    else -> "Нажмите «Открыть», затем «СТАРТ» в Антирадаре. Он будет предупреждать голосом о камерах поверх навигатора."
                },
                color = ShColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
            )

            if (installed && !access) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Чтобы видеть его статус и останавливать его отсюда, разрешите «Штурману» доступ к уведомлениям.",
                    color = ShColors.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Wide("Разрешить доступ", primary = false) { Music.openAccessSettings(context) }
            }

            if (installed) {
                Spacer(Modifier.height(18.dp))
                Row {
                    Box(Modifier.weight(1f)) {
                        Wide(if (running) "Открыть" else "Открыть Антирадар", primary = !running) {
                            if (!AntiRadar.open(context)) message = "Не удалось открыть приложение"
                        }
                    }
                    if (running && info?.canStop == true) {
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            Wide("Остановить", primary = false) {
                                message = if (AntiRadar.stop(context)) "Остановил" else "Не получилось, остановите в самом приложении"
                            }
                        }
                    }
                }
            }
            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = ShColors.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Wide(text: String, primary: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) ShColors.Accent else ShColors.SearchField,
            contentColor = if (primary) ShColors.Background else ShColors.TextPrimary
        )
    ) { Text(text, style = ShText.Button) }
}
