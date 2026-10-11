package ru.shturman.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.shturman.license.License
import ru.shturman.license.LicenseRow
import ru.shturman.license.prettyCode
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Экран владельца: создать коды доступа и увидеть, какие использованы, кем и когда. */
@Composable
fun AdminCodesScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<LicenseRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }

    BackHandler(onBack = onClose)

    LaunchedEffect(reload) {
        loading = true
        try {
            rows = License.list()
        } catch (e: Exception) {
            message = "Не удалось загрузить: ${e.message?.take(120)}"
        }
        loading = false
    }

    val used = rows.count { it.used }
    val free = rows.size - used

    Column(
        modifier = Modifier.fillMaxSize().background(ShColors.Background).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MapButton(icon = Icons.Filled.Close, description = "Закрыть", onClick = onClose)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Коды доступа", color = ShColors.TextPrimary, style = ShText.Distance)
                Text("Свободных: $free · Использованных: $used", color = ShColors.TextSecondary, fontSize = 13.sp)
            }
        }

        // Создание
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Field("Для кого (имя)", note, Modifier.weight(1f)) { note = it }
                Field("Сколько", count, Modifier.width(92.dp), number = true) { count = it.filter(Char::isDigit).take(2) }
            }
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val made = License.create(count.toIntOrNull() ?: 1, note.trim())
                            message = "Создано: " + made.joinToString(", ") { prettyCode(it) }
                            note = ""
                            reload++
                        } catch (e: Exception) {
                            message = "Не вышло: ${e.message?.take(120)}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
            ) { Text("Создать код", style = ShText.Button) }
            message?.let { Text(it, color = ShColors.TextSecondary, fontSize = 13.sp) }
        }

        Spacer(Modifier.height(10.dp))
        if (loading && rows.isEmpty()) {
            Text("Загружаю…", color = ShColors.TextSecondary, style = ShText.Small, modifier = Modifier.padding(16.dp))
        }

        val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale("ru")) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows) { row ->
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape).background(ShColors.Panel).border(1.dp, ShBorder, shape).padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            prettyCode(row.code),
                            color = if (row.used) ShColors.TextSecondary else ShColors.TextPrimary,
                            style = ShText.Distance.copy(fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (row.used) "использован" else "свободен",
                            color = if (row.used) ShColors.Sign3 else ShColors.Success,
                            style = ShText.Small
                        )
                    }
                    if (row.note.isNotEmpty()) Text("Для: ${row.note}", color = ShColors.TextPrimary, fontSize = 14.sp)
                    if (row.used) {
                        Text(
                            "Активирован: ${row.usedByEmail.ifEmpty { "?" }} · ${fmt.format(Date(row.usedAt))}",
                            color = ShColors.TextSecondary, fontSize = 13.sp
                        )
                    } else {
                        Row {
                            TextButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("code", prettyCode(row.code)))
                                message = "Скопировано: ${prettyCode(row.code)}"
                            }) { Text("Копировать", color = ShColors.Accent, style = ShText.Small) }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { License.delete(row.code) }
                                    reload++
                                }
                            }) { Text("Удалить", color = ShColors.Danger, style = ShText.Small) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier, number: Boolean = false, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ShColors.SearchField,
            unfocusedContainerColor = ShColors.SearchField,
            focusedTextColor = ShColors.TextPrimary,
            unfocusedTextColor = ShColors.TextPrimary,
            focusedLabelColor = ShColors.TextSecondary,
            unfocusedLabelColor = ShColors.TextSecondary,
            cursorColor = ShColors.Accent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = modifier
    )
}
