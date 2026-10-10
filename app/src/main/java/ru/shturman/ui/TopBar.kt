package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/** Верхняя плавающая панель: меню, поиск «Куда едем?», аватарка. */
@Composable
fun TopBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ShColors.Panel.copy(alpha = 0.9f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка «меню» (пока ничего не делает)
        IconButton(onClick = { }) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Меню",
                tint = ShColors.TextPrimary
            )
        }

        // Поисковая строка (пока просто макет)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ShColors.SearchField)
                .clickable { }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Куда едем?",
                color = ShColors.TextSecondary,
                style = ShText.Small
            )
        }

        // Аватарка (заглушка вместо фото из Google)
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(ShColors.Divider),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Профиль",
                tint = ShColors.TextSecondary
            )
        }
    }
}
