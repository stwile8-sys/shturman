package ru.shturman.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.shturman.routing.Place
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/** Экран ввода адреса: подсказки появляются по мере набора, сверху быстрые «Дом» и «Работа». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    home: Place?,
    work: Place?,
    onSetFavorite: (String, Place) -> Unit,
    results: List<Place>,
    searching: Boolean,
    error: String?,
    onQuery: (String) -> Unit,
    onPick: (Place) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    // Если не null, ближайший выбранный адрес сохраняется как «home» или «work», а не открывает маршрут
    var saving by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        delay(400)
        onQuery(query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MapButton(icon = Icons.Filled.Close, description = "Закрыть", onClick = onClose)
            Spacer(Modifier.padding(start = 10.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        when (saving) {
                            "home" -> "Адрес для «Дом»"
                            "work" -> "Адрес для «Работа»"
                            else -> "Куда едем?"
                        },
                        color = ShColors.TextSecondary
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ShColors.SearchField,
                    unfocusedContainerColor = ShColors.SearchField,
                    focusedTextColor = ShColors.TextPrimary,
                    unfocusedTextColor = ShColors.TextPrimary,
                    cursorColor = ShColors.Accent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
        ) {
            for ((kind, label, place) in listOf(Triple("home", "Дом", home), Triple("work", "Работа", work))) {
                val shape = RoundedCornerShape(20.dp)
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(ShColors.SearchField)
                        .border(1.dp, if (saving == kind) ShColors.Accent else ShBorder, shape)
                        .combinedClickable(
                            onClick = { if (place != null) onPick(place) else saving = kind },
                            onLongClick = { saving = kind }
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (place == null) "$label: задать" else label,
                        color = if (place == null) ShColors.TextSecondary else ShColors.TextPrimary,
                        style = ShText.Small
                    )
                }
            }
        }
        Text(text = stringResource(ru.shturman.R.string.s_dd1a6281),
            color = ShColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        when {
            error != null -> EmptyState(
                kind = if (error.contains("интернет", ignoreCase = true)) EmptyKind.OFFLINE else EmptyKind.ERROR,
                title = "Поиск не удался",
                hint = error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            searching && results.isEmpty() -> LoadingLine(
                text = stringResource(ru.shturman.R.string.s_5ab71ea3),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            query.trim().length >= 3 && !searching && results.isEmpty() -> EmptyState(
                kind = EmptyKind.SEARCH,
                title = stringResource(ru.shturman.R.string.s_8767f9ec),
                hint = "Попробуйте написать короче: улицу и номер дома или название места.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results) { place ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val kind = saving
                            if (kind != null) {
                                onSetFavorite(kind, place)
                                saving = null
                                query = ""
                            } else {
                                onPick(place)
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(text = place.title, color = ShColors.TextPrimary, style = ShText.Small)
                    if (place.subtitle.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(text = place.subtitle, color = ShColors.TextSecondary, fontSize = 13.sp)
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(1.dp)
                        .background(ShColors.Divider)
                )
            }
        }
    }
}
