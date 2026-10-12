package ru.shturman.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.shturman.BuildConfig
import ru.shturman.Errors
import ru.shturman.update.UpdateInfo
import ru.shturman.update.Updater

/**
 * При входе проверяет, есть ли новая версия. Если есть, предлагает установить; можно отказаться («Не сейчас»),
 * тогда предложение появится снова только при следующем запуске.
 */
@Composable
fun UpdatePrompt(enabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var checked by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(enabled) {
        if (enabled && !checked) {
            checked = true
            info = Updater.check()
        }
    }

    val update = info ?: return
    val downloading = progress != null
    AlertDialog(
        onDismissRequest = { if (!downloading) info = null },
        title = { Text(stringResource(ru.shturman.R.string.s_1c592b84)) },
        text = {
            Column {
                Text("Вышла новая сборка ${update.build}, у вас ${BuildConfig.BUILD_NUMBER}. Установить сейчас?")
                if (update.size > 0) Text("Размер: ${update.size / 1_000_000} МБ")
                progress?.let {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                    Text("Скачивание: ${(it * 100).toInt()}%")
                }
                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = {
                    if (!Updater.canInstall(context)) {
                        message = "Разрешите приложению установку обновлений и нажмите «Установить» ещё раз."
                        Updater.openInstallSettings(context)
                        return@TextButton
                    }
                    message = null
                    progress = 0f
                    scope.launch {
                        try {
                            val file = Updater.download(context, update) { progress = it }
                            progress = null
                            info = null
                            Updater.install(context, file)
                        } catch (e: Exception) {
                            Errors.log("update", e)
                            progress = null
                            message = "Не удалось скачать: ${e.message ?: e.javaClass.simpleName}"
                        }
                    }
                }
            ) { Text(stringResource(ru.shturman.R.string.s_7fe136f1)) }
        },
        dismissButton = {
            TextButton(enabled = !downloading, onClick = { info = null }) { Text(stringResource(ru.shturman.R.string.s_660b3295)) }
        }
    )
}
