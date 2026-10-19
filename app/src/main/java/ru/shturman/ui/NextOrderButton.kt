package ru.shturman.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.shturman.api.ExternalTrip
import ru.shturman.ui.fx.panel
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/**
 * Появляется только по приезде на заказ, запущенный из приложения «Заказы», если у него есть следующий.
 * Нажатие открывает «Заказы», и они ведут к следующему заказу. Через минуту кнопка прячется сама.
 */
@Composable
fun BoxScope.NextOrderButton(orderId: Long?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(orderId) {
        if (orderId != null) { delay(60_000); onDismiss() }
    }
    AnimatedVisibility(
        visible = orderId != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 186.dp) // над карточками «Дом»/«Работа» и строкой поиска
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.panel().padding(horizontal = 6.dp)
        ) {
            Box(Modifier.clickable {
                val id = orderId ?: return@clickable
                runCatching { context.startActivity(ExternalTrip.nextOrderIntent(id)) }
                onDismiss()
            }.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text("Следующий заказ", style = ShText.Title, color = ShColors.TextPrimary)
            }
            Box(Modifier.clickable { onDismiss() }.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text("✕", style = ShText.Title, color = ShColors.Muted)
            }
        }
    }
}
