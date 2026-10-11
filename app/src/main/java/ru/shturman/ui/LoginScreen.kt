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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.shturman.auth.signInWithGoogle
import ru.shturman.ui.theme.NumberFont
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/** Экран входа. Без входа через Google приложение дальше не открывается, анонимного режима нет. */
@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Штурман",
                color = ShColors.TextPrimary,
                fontFamily = NumberFont,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Навигатор, который думает наперёд",
                color = ShColors.TextSecondary,
                style = ShText.Street,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            error?.let {
                Text(
                    text = it,
                    color = ShColors.Danger,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = {
                    if (!loading) {
                        loading = true
                        error = null
                        scope.launch {
                            error = signInWithGoogle(context)
                            loading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ShColors.Accent,
                    contentColor = ShColors.Background
                )
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = ShColors.Background,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(text = "Войти через Google", style = ShText.Button)
                }
            }

            Text(
                text = "Вход через Google обязателен, анонимного режима нет.",
                color = ShColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
