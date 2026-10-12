package ru.shturman.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.shturman.access.Access
import ru.shturman.access.CodeRow
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Раздел владельца: создание кодов доступа и список, какие свободны, а какие использованы и кем. */
@Composable
fun AdminCodesScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var rows by remember { mutableStateOf<List<CodeRow>>(emptyList()) }
    var message by remember { mutableStateOf<String?>("Загружаю…") }
    var reload by remember { mutableStateOf(0) }

    BackHandler(onBack = onClose)
    LaunchedEffect(reload) {
        try {
            rows = Access.listCodes()
            message = if (rows.isEmpty()) "Кодов пока нет. Нажмите «Создать код»." else null
        } catch (e: Exception) {
            message = "Не удалось загрузить: ${(e.message ?: "").take(120)}. Проверьте правила Firebase."
        }
    }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            try { block(); reload++ } catch (e: Exception) { message = "Ошибка: ${(e.message ?: "").take(120)}" }
        }
    }

    val free = rows.count { !it.used }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MapButton(icon = Icons.Filled.Close, description = "Закрыть", onClick = onClose)
            Spacer(Modifier.padding(start = 12.dp))
            Column {
                Text(stringResource(ru.shturman.R.string.s_1abf35b1), color = ShColors.TextPrimary, style = ShText.Distance.copy(fontSize = 22.sp))
                Text("Свободно: $free · использовано: ${rows.size - free}", color = ShColors.TextSecondary, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { run { Access.createCodes(1) } },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
            ) { Text(stringResource(ru.shturman.R.string.s_8f07f035), style = ShText.Button) }
            Button(
                onClick = { run { Access.createCodes(5) } },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text(stringResource(ru.shturman.R.string.s_fc80f0ff), style = ShText.Button) }
        }
        message?.let { Text(it, color = ShColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(rows) { row ->
                val shape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .clip(shape)
                        .background(ShColors.Panel)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.code, color = ShColors.TextPrimary,
                            style = ShText.Small.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (row.used) "использован" else "свободен",
                            color = if (row.used) ShColors.TextSecondary else ShColors.Success,
                            fontSize = 13.sp
                        )
                    }
                    if (row.used) {
                        val who = listOf(row.usedByName, row.usedByEmail).filter { it.isNotBlank() }.joinToString(" · ")
                        Text("$who", color = ShColors.TextSecondary, fontSize = 13.sp)
                        if (row.usedAt > 0) {
                            Text(
                                SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(row.usedAt)),
                                color = ShColors.TextSecondary, fontSize = 12.sp
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(row.code)); message = "Скопировано: ${row.code}" }) {
                            Text(stringResource(ru.shturman.R.string.s_112b141b), color = ShColors.Accent, fontSize = 13.sp)
                        }
                        if (!row.used) {
                            TextButton(onClick = { run { Access.deleteCode(row.code) } }) {
                                Text(stringResource(ru.shturman.R.string.s_ed2bbfbc), color = ShColors.Danger, fontSize = 13.sp)
                            }
                        } else {
                            TextButton(onClick = { run { Access.resetDevice(row.usedByUid) } }) {
                                Text(stringResource(ru.shturman.R.string.s_b48e76ae), color = ShColors.Accent, fontSize = 13.sp)
                            }
                            TextButton(onClick = { run { Access.revoke(row.usedByUid) } }) {
                                Text(stringResource(ru.shturman.R.string.s_7e645147), color = ShColors.Danger, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
