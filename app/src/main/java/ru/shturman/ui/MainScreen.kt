package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.shturman.ui.theme.ShColors

/**
 * Главный экран: карта на весь экран.
 * Слева сверху — круглые кнопки (меню, поиск), справа сверху — ближайший поворот,
 * снизу — сколько проехали.
 */
@Composable
fun MainScreen() {
    var showSettings by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShColors.Background)
    ) {
        DarkMap(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MapButton(
                icon = Icons.Filled.Menu,
                description = "Меню",
                onClick = { showSettings = true }
            )
            MapButton(
                icon = Icons.Filled.Search,
                description = "Куда едем?",
                onClick = { }
            )
        }

        TurnCard(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
        )

        ProgressCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
        )

        if (showSettings) {
            SettingsSheet(onDismiss = { showSettings = false })
        }
    }
}
