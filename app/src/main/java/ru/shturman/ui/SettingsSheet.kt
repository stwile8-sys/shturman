package ru.shturman.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ru.shturman.auth.UserInfo
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/** Настройки: профиль, приватность и ручная синхронизация. Пока всё — макет. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(user: UserInfo, onSignOut: () -> Unit, onDismiss: () -> Unit) {
    var shareData by remember { mutableStateOf(true) }

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // Профиль: имя, почта и аватарка из Google-аккаунта
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(ShColors.Divider),
                    contentAlignment = Alignment.Center
                ) {
                    if (user.photoUrl != null) {
                        AsyncImage(
                            model = user.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = ShColors.TextSecondary
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = user.name,
                        color = ShColors.TextPrimary,
                        style = ShText.Distance.copy(fontSize = 20.sp)
                    )
                    Text(
                        text = user.email,
                        color = ShColors.TextSecondary,
                        style = ShText.Street
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ShColors.Divider)
            )
            Spacer(Modifier.height(16.dp))

            // Приватность
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Участвовать в общем обучении",
                        color = ShColors.TextPrimary,
                        style = ShText.Small
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Уходят только обезличенные данные о дорогах. Ваши координаты — никогда.",
                        color = ShColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = shareData,
                    onCheckedChange = { shareData = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ShColors.Background,
                        checkedTrackColor = ShColors.Accent
                    )
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Вход через Google обязателен, анонимного режима нет.",
                color = ShColors.TextSecondary,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(20.dp))

            // Синхронизация теперь здесь
            Button(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ShColors.Accent,
                    contentColor = ShColors.Background
                )
            ) {
                Text(text = "Синхронизировать", style = ShText.Button)
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Выйти из аккаунта", color = ShColors.TextSecondary, style = ShText.Small)
            }
        }
    }
}
