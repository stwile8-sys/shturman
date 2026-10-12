package ru.shturman.ui.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shturman.ui.theme.ShColors

/** Раздел «Тема оформления»: восемь карточек, каждая показывает цвета и название. Выбор применяется сразу. */
@Composable
fun ThemePicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for (th in Themes.all) {
            val on = th.id == selected
            val shape = RoundedCornerShape(14.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(th.bg)
                    .border(if (on) 2.dp else 1.dp, if (on) th.accent else th.divider, shape)
                    .clickable { onSelect(th.id) }
                    .padding(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in listOf(th.accent, th.second, th.third, th.panel)) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(c).border(1.dp, th.divider, CircleShape))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = th.name, color = th.text, fontFamily = th.numFont, fontSize = 17.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(text = th.vibe, color = th.textSec, fontFamily = th.textFont, fontSize = 12.sp)
                }
                if (on) Text(text = "✓", color = th.accent, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
