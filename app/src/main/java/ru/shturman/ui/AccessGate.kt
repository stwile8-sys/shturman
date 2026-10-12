package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.shturman.access.Access
import ru.shturman.access.AccessState
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/**
 * Проверка доступа после входа через Google: пускает дальше, если аккаунт активирован кодом на этом устройстве;
 * иначе просит код, показывает «привязано к другому устройству» или ошибку связи.
 */
@Composable
fun AccessGate(uid: String, onSignOut: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(uid) { mutableStateOf<AccessState>(AccessState.Checking) }
    var attempt by remember(uid) { mutableStateOf(0) }

    LaunchedEffect(uid, attempt) {
        state = AccessState.Checking
        state = Access.check(context)
    }

    when (val st = state) {
        AccessState.Granted -> content()
        AccessState.Checking -> GateBox { Text(stringResource(ru.shturman.R.string.s_cf1ae449), color = ShColors.TextSecondary, style = ShText.Small) }
        AccessState.NeedCode -> {
            var code by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            var busy by remember { mutableStateOf(false) }
            GateBox {
                Text(stringResource(ru.shturman.R.string.s_2064be22), color = ShColors.TextPrimary, style = ShText.Distance.copy(fontSize = 24.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(ru.shturman.R.string.s_c08dc6d8),
                    color = ShColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                TextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(14); error = null },
                    placeholder = { Text(stringResource(ru.shturman.R.string.s_2c73d337), color = ShColors.TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = ShColors.Danger, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = Access.redeem(context, code)
                            busy = false
                            if (result == null) attempt++ else error = result
                        }
                    },
                    enabled = !busy && code.length >= 6,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
                ) { Text(if (busy) "Проверяю…" else "Активировать", style = ShText.Button) }
                TextButton(onClick = onSignOut) { Text(stringResource(ru.shturman.R.string.s_6d4183b7), color = ShColors.TextSecondary, style = ShText.Small) }
            }
        }
        is AccessState.Blocked -> GateBox {
            Text(st.message, color = ShColors.Danger, style = ShText.Small, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onSignOut) { Text(stringResource(ru.shturman.R.string.s_6d4183b7), color = ShColors.TextSecondary, style = ShText.Small) }
        }
        is AccessState.Failed -> GateBox {
            Text(st.message, color = ShColors.Danger, style = ShText.Small, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { attempt++ },
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
            ) { Text(stringResource(ru.shturman.R.string.s_b914bbbd), style = ShText.Button) }
            TextButton(onClick = onSignOut) { Text(stringResource(ru.shturman.R.string.s_6d4183b7), color = ShColors.TextSecondary, style = ShText.Small) }
        }
    }
}

@Composable
private fun GateBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShColors.Background)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) { content() }
}

@Composable
internal fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = ShColors.SearchField,
    unfocusedContainerColor = ShColors.SearchField,
    focusedTextColor = ShColors.TextPrimary,
    unfocusedTextColor = ShColors.TextPrimary,
    cursorColor = ShColors.Accent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
)
