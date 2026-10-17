package ru.shturman.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.shturman.service.DriveWatch
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.themes.Shturman2
import ru.shturman.ui.themes.SplashGate

/**
 * Один раз, когда доступ к местоположению уже дан, объясняет сбор данных о дорогах и просит
 * местоположение «Всегда» (чтобы сбор сам включался после перезагрузки) и работу без ограничения батареи.
 * Отказ ничего не ломает: сбор всё равно идёт, пока приложение запущено; включить можно в «Приватности».
 */
@Composable
fun RoadDataPrompt() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (prefs.getBoolean("bg_asked", false)) return@LaunchedEffect
        while (true) {
            // не поверх заставки и видео, и не в первые секунды после старта
            if (!SplashGate.covering && !Shturman2.introPending && DriveWatch.hasLocation(ctx)) {
                delay(2500L)
                if (DriveWatch.hasBackground(ctx)) {
                    prefs.edit().putBoolean("bg_asked", true).apply()
                    askBatteryFree(ctx)
                } else {
                    show = true
                }
                return@LaunchedEffect
            }
            delay(1500L)
        }
    }

    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        DriveWatch.start(ctx)
        askBatteryFree(ctx)
    }

    if (!show) return
    AlertDialog(
        containerColor = lerp(ShColors.Panel, Color.White, 0.07f),
        shape = ru.shturman.ui.fx.panelShape(28.dp),
        onDismissRequest = { },
        title = { Text("Данные о дорогах") },
        text = {
            Text(
                "Чтобы прогноз времени в пути был точнее, APEX во время поездок записывает, с какой скоростью едут " +
                    "по участкам дорог и сколько стоят на светофорах. Без имени, аккаунта и маршрутов.\n\n" +
                    "Разрешите доступ к местоположению «Всегда» — тогда запись будет идти и при закрытом приложении. " +
                    "Пока пишется поездка, в шторке виден значок APEX."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                show = false
                prefs.edit().putBoolean("bg_asked", true).apply()
                if (Build.VERSION.SDK_INT >= 29) {
                    runCatching { bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
                        .onFailure { askBatteryFree(ctx) }
                } else {
                    askBatteryFree(ctx)
                }
            }) { Text("Разрешить", color = ShColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = {
                show = false
                prefs.edit().putBoolean("bg_asked", true).apply()
            }) { Text("Не сейчас", color = ShColors.TextSecondary) }
        }
    )
}
