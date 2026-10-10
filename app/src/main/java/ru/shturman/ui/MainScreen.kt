package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.shturman.ui.theme.ShColors

/** Главный экран: карта на весь экран, поверх неё верхняя панель и нижняя карточка. */
@Composable
fun MainScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ShColors.Background)
    ) {
        DarkMap(modifier = Modifier.fillMaxSize())

        TopBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp)
        )

        BottomCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
        )
    }
}
