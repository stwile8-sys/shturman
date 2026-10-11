package ru.shturman

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import ru.shturman.auth.UserInfo
import ru.shturman.auth.currentUserInfo
import ru.shturman.auth.signOut
import ru.shturman.auth.toInfo
import ru.shturman.ui.LoginScreen
import ru.shturman.ui.MainScreen
import ru.shturman.ui.theme.ShturmanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Карта на весь экран, в том числе под строкой статуса и кнопками навигации
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            ShturmanTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var user by remember { mutableStateOf<UserInfo?>(currentUserInfo()) }

                // Следим за входом и выходом: приложение работает только после входа через Google
                DisposableEffect(Unit) {
                    val auth = FirebaseAuth.getInstance()
                    val listener = FirebaseAuth.AuthStateListener { user = it.currentUser?.toInfo() }
                    auth.addAuthStateListener(listener)
                    onDispose { auth.removeAuthStateListener(listener) }
                }

                val current = user
                if (current == null) {
                    LoginScreen()
                } else {
                    MainScreen(
                        user = current,
                        onSignOut = { scope.launch { signOut(context) } }
                    )
                }
            }
        }
    }
}
