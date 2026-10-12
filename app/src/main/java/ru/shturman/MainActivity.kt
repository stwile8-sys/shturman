package ru.shturman

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.File
import ru.shturman.auth.UserInfo
import ru.shturman.auth.currentUserInfo
import ru.shturman.auth.signOut
import ru.shturman.auth.toInfo
import ru.shturman.ui.LoginScreen
import ru.shturman.ui.AccessGate
import ru.shturman.ui.MainScreen
import ru.shturman.ui.SplashOverlay
import ru.shturman.ui.theme.ShturmanTheme

class MainActivity : ComponentActivity() {
    private var welcome: ru.shturman.routing.Welcome? = null

    override fun onDestroy() {
        welcome?.release()
        welcome = null
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // навигатор: экран не гаснет
        ru.shturman.ui.theme.ShColors.princess = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("princess", false)
        // Если приложение вылетит, причина сохранится и покажется при следующем запуске
        val crashFile = File(filesDir, "crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val t = android.util.Log.getStackTraceString(error)
                crashFile.writeText(t)
                File(filesDir, "crash_last.txt").writeText(t)
            }
            previous?.uncaughtException(thread, error)
        }
        val lastCrash = runCatching { crashFile.takeIf { it.exists() }?.readText() }.getOrNull()
        // Карта на весь экран, в том числе под строкой статуса и кнопками навигации
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        // Приветствие голосом только при свежем запуске, не при повороте экрана
        welcome = ru.shturman.routing.Welcome(this)
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

                var crash by remember { mutableStateOf(lastCrash) }
                crash?.let { text ->
                    AlertDialog(
                        onDismissRequest = { runCatching { crashFile.delete() }; crash = null },
                        confirmButton = {
                            TextButton(onClick = { runCatching { crashFile.delete() }; crash = null }) { Text("Закрыть") }
                        },
                        title = { Text("Прошлый запуск завершился ошибкой") },
                        text = { Text(text.take(1200), style = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp))) }
                    )
                }

                var showSplash by rememberSaveable { mutableStateOf(true) }
                Box {
                    val current = user
                    if (current == null) {
                        LoginScreen()
                    } else {
                        AccessGate(uid = current.uid, onSignOut = { scope.launch { signOut(context) } }) {
                            MainScreen(
                                user = current,
                                onSignOut = { scope.launch { signOut(context) } }
                            )
                        }
                    }
                    if (showSplash) SplashOverlay(onPlaySound = { welcome?.play() }, onFinished = { showSplash = false })
                }
            }
        }
    }
}
