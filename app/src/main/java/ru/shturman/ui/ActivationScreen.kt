package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import ru.shturman.license.License
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/**
 * Проверка кода доступа после входа через Google. Пока аккаунт не активирован кодом на этом телефоне,
 * основной экран не открывается. Владелец (админ) проходит без кода.
 */
@Composable
fun LicenseGate(onSignOut: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // null — проверяем; true — пускаем; false — нужен код; ошибка — не смогли проверить
    var state by remember { mutableStateOf<Boolean?>(null) }
    var checkFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        checkFailed = false
        val result = License.isActivated(context)
        if (result == null) checkFailed = true else state = result
    }

    when {
        state == true -> content()
        checkFailed -> Box(
            Modifier.fillMaxSize().background(ShColors.Background).statusBarsPadding().navigationBarsPadding().padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Не удалось проверить доступ. Проверьте интернет.", color = ShColors.TextPrimary, style = ShText.Small, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { attempt++ },
                    colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
                ) { Text("Повторить", style = ShText.Button) }
                TextButton(onClick = onSignOut) { Text("Выйти из аккаунта", color = ShColors.TextSecondary, style = ShText.Small) }
            }
        }
        state == null -> Box(Modifier.fillMaxSize().background(ShColors.Background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ShColors.Accent)
        }
        else -> ActivationScreen(onActivated = { state = true }, onSignOut = onSignOut)
    }
}

@Composable
private fun ActivationScreen(onActivated: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(ShColors.Background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Код доступа", color = ShColors.TextPrimary, style = ShText.BigNumber)
            Text(
                "Введите код, который вам дал владелец приложения. Он работает один раз и привязывается к этому телефону.",
                color = ShColors.TextSecondary, style = ShText.Street, textAlign = TextAlign.Center
            )
            TextField(
                value = code,
                onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(9) },
                placeholder = { Text("XXXX-XXXX", color = ShColors.TextSecondary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ShColors.SearchField,
                    unfocusedContainerColor = ShColors.SearchField,
                    focusedTextColor = ShColors.TextPrimary,
                    unfocusedTextColor = ShColors.TextPrimary,
                    cursorColor = ShColors.Accent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = ShColors.Danger, fontSize = 14.sp, textAlign = TextAlign.Center) }
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val result = License.redeem(context, code)
                        busy = false
                        if (result == null) onActivated() else error = result
                    }
                },
                enabled = !busy && code.length >= 8,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
            ) { Text(if (busy) "Проверяю…" else "Активировать", style = ShText.Button) }
            TextButton(onClick = onSignOut) { Text("Выйти из аккаунта", color = ShColors.TextSecondary, style = ShText.Small) }
        }
    }
}
