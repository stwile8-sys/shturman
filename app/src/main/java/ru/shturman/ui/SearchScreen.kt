package ru.shturman.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.shturman.routing.LatLon
import ru.shturman.routing.Place
import ru.shturman.routing.distanceM
import ru.shturman.ui.fx.pressFx
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import ru.shturman.ui.themes.themedEnter

/**
 * Поиск адреса (макет P07): поле с рамкой цветом темы, «Назад», «Очистить» и голосовой ввод; плитки «Дом» и «Работа»;
 * подсказки по мере набора с расстоянием от вас; ниже — недавние места.
 */
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
    onClose: () -> Unit,
    initialQuery: String = "",
    initialSaving: String? = null,
    here: LatLon? = null
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf(initialQuery) }
    // Если не null, ближайший выбранный адрес сохраняется как «home» или «work», а не открывает маршрут
    var saving by remember { mutableStateOf(initialSaving) }
    val focus = remember { FocusRequester() }
    val recents = remember { Recents.load(context) }

    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        delay(400)
        onQuery(query)
    }
    // Голосовой ввод: системное окно распознавания, результат — в строку поиска
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
    }
    val choose: (Place) -> Unit = { place ->
        val kind = saving
        if (kind != null) {
            onSetFavorite(kind, place)
            saving = null
            query = ""
        } else {
            onPick(place)
        }
    }

    Box(Modifier.fillMaxSize().background(ShColors.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Строка поиска
            val fieldShape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .themedEnter(0)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(fieldShape)
                    .background(ShColors.SearchField)
                    .border(1.dp, ShColors.Accent, fieldShape)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FieldKey(ApexIcons.Back, "Назад", onClose)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            when (saving) {
                                "home" -> "Адрес для «Дом»"
                                "work" -> "Адрес для «Работа»"
                                else -> "Куда едем?"
                            },
                            color = ShColors.TextSecondary, fontSize = 17.sp
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = ShText.Body.copy(color = ShColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Normal),
                        cursorBrush = SolidColor(ShColors.Accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (query.isNotEmpty()) FieldKey(ApexIcons.Close, "Очистить", { query = "" }, tint = ShColors.TextSecondary, iconSize = 16)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable {
                            val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Куда едем?")
                            }
                            runCatching { voice.launch(i) }
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(ApexIcons.Mic, contentDescription = "Голосовой поиск", tint = ShColors.TextPrimary, modifier = Modifier.size(18.dp)) }
            }

            Spacer(Modifier.height(16.dp))
            // Дом и Работа: нажатие — поехать (или задать адрес), долгое нажатие — изменить
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((kind, label, place) in listOf(Triple("home", "ДОМ", home), Triple("work", "РАБОТА", work))) {
                    val shape = RoundedCornerShape(12.dp)
                    val on = saving == kind
                    Row(
                        modifier = Modifier
                            .themedEnter(if (kind == "home") 1 else 2)
                            .weight(1f)
                            .height(64.dp)
                            .pressFx()
                            .clip(shape)
                            .background(ShColors.SearchField)
                            .border(1.dp, if (on) ShColors.Accent else Color.White.copy(alpha = 0.07f), shape)
                            .combinedClickable(
                                onClick = { if (place != null && saving == null) onPick(place) else saving = kind },
                                onLongClick = { saving = kind }
                            )
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(if (kind == "home") ApexIcons.Home else ApexIcons.Work, contentDescription = null, tint = ShColors.TextPrimary, modifier = Modifier.size(20.dp))
                        Column {
                            Text(label, color = ShColors.TextSecondary, style = ShText.Caps)
                            Text(
                                place?.title ?: "задать",
                                color = if (place == null) ShColors.TextSecondary else ShColors.TextPrimary,
                                style = ShText.Body.copy(fontWeight = FontWeight(300)),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(ru.shturman.R.string.s_dd1a6281),
                color = ShColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            when {
                error != null -> EmptyState(
                    kind = if (error.contains("интернет", ignoreCase = true)) EmptyKind.OFFLINE else EmptyKind.ERROR,
                    title = "Поиск не удался",
                    hint = error,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                searching && results.isEmpty() -> LoadingLine(
                    text = stringResource(ru.shturman.R.string.s_5ab71ea3),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                )
                query.trim().length >= 3 && !searching && results.isEmpty() -> EmptyState(
                    kind = EmptyKind.SEARCH,
                    title = stringResource(ru.shturman.R.string.s_8767f9ec),
                    hint = "Попробуйте написать короче: улицу и номер дома или название места.",
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (results.isNotEmpty()) {
                    item { Header("РЕЗУЛЬТАТЫ") }
                    itemsIndexed(results) { i, place ->
                        PlaceRow(
                            icon = ApexIcons.Pin,
                            iconTint = if (i == 0) ShColors.Accent else ShColors.TextSecondary,
                            title = highlight(place.title, query),
                            subtitle = place.subtitle,
                            distance = here?.let { formatDistance(distanceM(it, place.position)) },
                            index = i.coerceAtMost(8),
                            onClick = { choose(place) }
                        )
                    }
                }
                if (query.trim().length < 3 && recents.isNotEmpty()) {
                    item { Header("НЕДАВНИЕ") }
                    itemsIndexed(recents) { i, place ->
                        PlaceRow(
                            icon = ApexIcons.Recent,
                            iconTint = ShColors.TextSecondary,
                            title = AnnotatedString(place.title),
                            subtitle = place.subtitle,
                            distance = here?.let { formatDistance(distanceM(it, place.position)) },
                            index = i,
                            onClick = { choose(place) }
                        )
                    }
                }
            }
        }
    }
}

/** Кнопка внутри строки поиска (без фона). */
@Composable
private fun FieldKey(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = ShColors.TextPrimary, iconSize: Int = 20) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize.dp)) }
}

@Composable
private fun Header(text: String) {
    Text(text, color = ShColors.Muted, style = ShText.Caps, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

/** Строка места: значок, название (совпадение с запросом — жирнее), город и расстояние от вас. */
@Composable
private fun PlaceRow(
    icon: ImageVector,
    iconTint: Color,
    title: AnnotatedString,
    subtitle: String,
    distance: String?,
    index: Int,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .themedEnter(index)
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ShColors.TextPrimary, style = ShText.Body.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, color = ShColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (distance != null) {
            Spacer(Modifier.width(10.dp))
            Text(distance, color = ShColors.TextSecondary, style = ShText.Secondary.copy(fontSize = 13.sp, fontWeight = FontWeight(300)))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
}

/** Выделяет в названии совпадение с запросом. */
private fun highlight(title: String, query: String): AnnotatedString {
    val q = query.trim()
    val at = if (q.length >= 2) title.indexOf(q, ignoreCase = true) else -1
    if (at < 0) return AnnotatedString(title)
    return buildAnnotatedString {
        append(title.substring(0, at))
        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
        append(title.substring(at, at + q.length))
        pop()
        append(title.substring(at + q.length))
    }
}
