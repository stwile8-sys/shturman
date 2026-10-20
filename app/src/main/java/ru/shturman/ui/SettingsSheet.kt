package ru.shturman.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.shturman.NavState
import ru.shturman.NavViewModel
import ru.shturman.R
import ru.shturman.auth.UserInfo
import ru.shturman.service.AutoPrefs
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText
import ru.shturman.ui.themes.Themes
import ru.shturman.ui.themes.themedEnter
import ru.shturman.ui.fx.pressFx
import androidx.compose.foundation.border

/** Разделы настроек. Главный экран — список разделов, каждый открывается своей страницей. */
private enum class Page(val title: String) {
    THEME("Оформление"),
    VOICE("Голос и звуки"),
    NAV("Навигация и экран"),
    CAR("Бензин и машина"),
    TRIPS("Поездки и статистика"),
    CARAVAN("Караван"),
    CLAN("Клан"),
    NOTIF("Уведомления и автозапуск"),
    DATA("Данные и резервная копия"),
    PRIVACY("Приватность и синхронизация"),
    ADMIN("Для владельца"),
    /** Скрытый раздел «Штурман 2»: виден только после секретной комбинации. */
    APEX("APEX")
}

private val LEVELS = listOf("Низкая", "Средняя", "Высокая")

/** Настройки: профиль и список разделов; раздел открывается своей страницей со стрелкой «назад». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    user: UserInfo,
    s: NavState,
    vm: NavViewModel,
    isAdmin: Boolean,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    onOpenHud: () -> Unit = {},
    onOpenMusic: () -> Unit = {},
    onOpenRadar: () -> Unit = {},
    onOpenRoadData: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (ShColors.premium) androidx.compose.ui.graphics.lerp(ShColors.Panel, Color.White, 0.012f)
            else androidx.compose.ui.graphics.lerp(ShColors.Panel, Color.White, 0.06f).copy(alpha = 0.97f),
        shape = when (ShColors.L.flavor) {
            ru.shturman.ui.themes.Flavor.PREMIUM -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            ru.shturman.ui.themes.Flavor.TELEMETRY -> androidx.compose.foundation.shape.CutCornerShape(topEnd = 26.dp)
            ru.shturman.ui.themes.Flavor.APEX -> androidx.compose.ui.graphics.RectangleShape
            else -> RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
        },
        contentColor = ShColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ShColors.Divider) }
    ) {
        var pageName by rememberSaveable { mutableStateOf<String?>(null) }
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        // «Штурман 2»: комбинация набрана — открываем режим, включаем APEX и показываем заставку
        val unlock = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            vm.unlockShturman2()
            ru.shturman.ui.themes.Shturman2.introPending = true
            onDismiss()
        }
        val page = pageName?.let { n -> Page.values().firstOrNull { it.name == n } }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != null
                when (ShColors.L.flavor) {
                    ru.shturman.ui.themes.Flavor.DEPTH ->
                        (androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(0.7f, 260f), if (forward) 0.85f else 1.1f) + fadeIn(tween())) togetherWith
                            (androidx.compose.animation.scaleOut(tween(), if (forward) 1.1f else 0.85f) + fadeOut(tween()))
                    ru.shturman.ui.themes.Flavor.TELEMETRY ->
                        slideInHorizontally(androidx.compose.animation.core.spring(0.8f, 600f)) { w -> if (forward) w else -w } togetherWith
                            slideOutHorizontally(tween()) { w -> if (forward) -w else w }
                    ru.shturman.ui.themes.Flavor.NEON ->
                        fadeIn(androidx.compose.animation.core.tween(160)) togetherWith fadeOut(androidx.compose.animation.core.tween(120))
                    ru.shturman.ui.themes.Flavor.APEX ->
                        fadeIn(androidx.compose.animation.core.tween(90)) togetherWith fadeOut(androidx.compose.animation.core.tween(60))
                    else ->
                        (slideInHorizontally(androidx.compose.animation.core.spring(0.75f, 300f)) { w -> if (forward) w / 4 else -w / 4 } + fadeIn(tween())) togetherWith
                            (slideOutHorizontally(tween()) { w -> if (forward) -w / 4 else w / 4 } + fadeOut(tween()))
                }
            },
            label = "settingsPage"
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
            ) {
                if (current == null) {
                    QuickTiles(onOpenMusic, onOpenRadar, onOpenHud)
                    Home(user, s, vm, isAdmin, onSignOut, unlock) { pageName = it.name }
                } else {
                    PageHeader(current.title) { pageName = null }
                    when (current) {
                        Page.THEME -> ThemePage(s, vm)
                        Page.VOICE -> VoicePage(s, vm)
                        Page.NAV -> NavPage(s, vm, onOpenHud)
                        Page.CAR -> CarPage(s, vm)
                        Page.TRIPS -> TripsPage(vm)
                        Page.CARAVAN -> CaravanPage(s, vm)
                        Page.CLAN -> ClanPage(s, vm)
                        Page.NOTIF -> NotifPage()
                        Page.DATA -> DataPage(vm)
                        Page.PRIVACY -> PrivacyPage(user, s, vm)
                        Page.ADMIN -> AdminPage(onOpenAdmin, onOpenRoadData)
                        Page.APEX -> ApexPage {
                            ru.shturman.ui.themes.Shturman2.introPending = true
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

private fun <T> tween() = androidx.compose.animation.core.tween<T>(durationMillis = 220)

// ---------- главный экран настроек ----------

/** Быстрые плитки сверху настроек: музыка, антирадар и проекция на лобовое. */
@Composable
private fun QuickTiles(onMusic: () -> Unit, onRadar: () -> Unit, onHud: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((icon, label, click) in listOf(
            Triple(ApexIcons.Music, "МУЗЫКА", onMusic),
            Triple(ApexIcons.Radar, "АНТИРАДАР", onRadar),
            Triple(ApexIcons.Hud, "ПРОЕКЦИЯ", onHud)
        )) {
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .weight(1f)
                    .height(72.dp)
                    .pressFx()
                    .clip(shape)
                    .background(ShColors.SearchField)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
                    .clickable(onClick = click)
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(icon, contentDescription = null, tint = ShColors.TextPrimary, modifier = Modifier.size(20.dp))
                Text(label, color = ShColors.TextSecondary, style = ShText.Caps.copy(fontSize = 9.sp, letterSpacing = 1.5.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Home(user: UserInfo, s: NavState, vm: NavViewModel, isAdmin: Boolean, onSignOut: () -> Unit, onUnlock: () -> Unit, open: (Page) -> Unit) {
    val ctx = LocalContext.current
    val trips = remember { vm.tripCount() }
    // Секретная комбинация: 5 касаний по «В дороге», затем 2 по «Система», всё за 10 секунд
    val secret = remember { ru.shturman.ui.themes.SecretTaps() }
    Profile(user)

    if (ru.shturman.ui.themes.Shturman2.unlocked) {
        Group("Клан")
        SettingsCard { CategoryRow(ru.shturman.ui.themes.ApexMark, Page.APEX.title, 0, "Заставка", first = true) { open(Page.APEX) } }
    }

    Group("Внешний вид и голос")
    SettingsCard {
        CategoryRow(ApexIcons.Look, Page.THEME.title, Page.THEME.ordinal,
            (Themes.all.getOrNull(s.theme)?.name ?: "") + if (ru.shturman.ui.themes.ThemeSounds.enabled) " · звуки темы" else "", first = true) { open(Page.THEME) }
        CategoryRow(ApexIcons.Sound, Page.VOICE.title, Page.VOICE.ordinal,
            (if (s.voiceOn) "Подсказки включены" else "Подсказки выключены") + if (s.nightVoice) " · ночной режим" else "") { open(Page.VOICE) }
    }

    Group("В дороге") { if (secret.tap('A')) onUnlock() }
    SettingsCard {
        CategoryRow(ApexIcons.Locate, Page.NAV.title, Page.NAV.ordinal,
            "Съезд: ${LEVELS.getOrElse(s.devLevel) { "Средняя" }.lowercase()}" + (if (s.fullscreen) " · полный экран" else "") + (if (s.satellite) " · спутник" else ""), first = true) { open(Page.NAV) }
        CategoryRow(ApexIcons.Fuel, Page.CAR.title, Page.CAR.ordinal,
            "${trimNumber(s.consumption)} л/100 км · ${trimNumber(s.fuelPrice)} ₽/л · заправки и ТО") { open(Page.CAR) }
        CategoryRow(ApexIcons.Stats, Page.TRIPS.title, Page.TRIPS.ordinal,
            if (trips == 0) "Поездок пока нет" else "$trips ${plural(trips, "поездка", "поездки", "поездок")} за 3 месяца") { open(Page.TRIPS) }
        CategoryRow(ApexIcons.Caravan, Page.CARAVAN.title, Page.CARAVAN.ordinal,
            s.caravanCode?.let { "Код $it · на карте ${s.members.size}" } ?: "Вы не в караване") { open(Page.CARAVAN) }
        CategoryRow(ApexIcons.Caravan, Page.CLAN.title, Page.CLAN.ordinal,
            s.clan?.let { "«${it.name}» · ${s.clanMembers.size} ${plural(s.clanMembers.size, "участник", "участника", "участников")}" } ?: "Вы не в клане") { open(Page.CLAN) }
    }

    Group("Система") { if (secret.tap('B')) onUnlock() }
    SettingsCard {
        CategoryRow(ApexIcons.Update, Page.NOTIF.title, Page.NOTIF.ordinal,
            listOfNotNull(
                if (AutoPrefs.quietHours(ctx)) "тихие часы" else null,
                if (AutoPrefs.btAutostart(ctx)) "Bluetooth" else null,
                if (AutoPrefs.chargeAutostart(ctx)) "зарядка" else null,
                if (ru.shturman.update.AutoUpdate.enabled(ctx)) "автообновление" else null
            ).joinToString(" · ").ifEmpty { "Итог поездки, тихие часы, автозапуск" }.replaceFirstChar { it.uppercase() }, first = true) { open(Page.NOTIF) }
        CategoryRow(ApexIcons.Data, Page.DATA.title, Page.DATA.ordinal, "Копия в аккаунте, кэш, сброс") { open(Page.DATA) }
        CategoryRow(ApexIcons.Lock, Page.PRIVACY.title, Page.PRIVACY.ordinal,
            "Сбор данных о дорогах · отчёт разработчику") { open(Page.PRIVACY) }
        if (isAdmin) CategoryRow(ApexIcons.Edit, Page.ADMIN.title, Page.ADMIN.ordinal, "Данные о дорогах, коды активации") { open(Page.ADMIN) }
    }

    Spacer(Modifier.height(16.dp))
    TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(text = stringResource(R.string.s_6d4183b7).uppercase(), color = ShColors.TextSecondary, style = ShText.Caps.copy(fontSize = 12.sp))
    }
}

@Composable
private fun Profile(user: UserInfo) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(ShColors.SearchField).border(1.dp, ShColors.Divider, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (user.photoUrl != null) {
                AsyncImage(model = user.photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
            } else {
                Icon(imageVector = ApexIcons.Profile, contentDescription = null, tint = ShColors.TextPrimary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text = user.name, color = ShColors.TextPrimary, style = ShText.Title)
            Text(text = user.email, color = ShColors.TextSecondary, style = ShText.Secondary)
        }
    }
}

@Composable
private fun Group(title: String, onTap: (() -> Unit)? = null) {
    Spacer(Modifier.height(14.dp))
    // Заголовок выглядит как обычный текст; касания по нему ловятся без отклика (для секретной комбинации)
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    Text(
        text = title.uppercase(), color = ShColors.Muted, style = ShText.Caps,
        modifier = if (onTap == null) Modifier else Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { tap?.invoke() }) }
    )
    Spacer(Modifier.height(8.dp))
}

/** Карточка группы: строки разделами внутри одной рамки. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ShColors.SearchField)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
    ) { content() }
}

@Composable
private fun CategoryRow(icon: ImageVector, title: String, index: Int, summary: String, first: Boolean = false, onClick: () -> Unit) {
    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .themedEnter(index)
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ShColors.TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, color = ShColors.TextPrimary, style = ShText.Body)
            Text(text = summary, color = ShColors.TextSecondary, style = ShText.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(imageVector = ApexIcons.Chevron, contentDescription = null, tint = ShColors.Muted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = ApexIcons.Back, contentDescription = "Назад", tint = ShColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(text = title, color = ShColors.TextPrimary, style = ShText.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- страницы ----------

@Composable
private fun ThemePage(s: NavState, vm: NavViewModel) {
    Section(stringResource(R.string.s_ca179901))
    ru.shturman.ui.themes.ThemePicker(selected = s.theme, onSelect = vm::setTheme)
    var themeSounds by remember { mutableStateOf(ru.shturman.ui.themes.ThemeSounds.enabled) }
    SwitchRow(stringResource(R.string.s_7be14764), stringResource(R.string.s_3320af3e), themeSounds) {
        themeSounds = it
        vm.setThemeSounds(it)
    }
}

@Composable
private fun VoicePage(s: NavState, vm: NavViewModel) {
    Section(stringResource(R.string.s_9232bbf3))
    SwitchRow(stringResource(R.string.s_d8914beb), stringResource(R.string.s_4067a129), s.voiceOn, vm::setVoiceOn)
    WideButton(stringResource(R.string.s_fc6d20f9), onClick = vm::testVoice)
    s.voiceStatus?.let { Hint(it, Modifier.padding(top = 6.dp)) }
    SwitchRow(stringResource(R.string.s_66efd4ed), stringResource(R.string.s_a483e67b), s.nightVoice, vm::setNightVoice)
    SwitchRow(stringResource(R.string.s_dfe8ad96), stringResource(R.string.s_f54ed96c), s.turnVibro, vm::setTurnVibro)
    SwitchRow(stringResource(R.string.s_29323c03), stringResource(R.string.s_774dfed6), s.overspeedSound, vm::setOverspeedSound)
}

@Composable
private fun NavPage(s: NavState, vm: NavViewModel, onOpenHud: () -> Unit) {
    Section(stringResource(R.string.s_edc3ad9b))
    Hint(stringResource(R.string.s_a626bf4b))
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        LEVELS.forEachIndexed { i, label ->
            val on = s.devLevel == i
            Button(
                onClick = { vm.setDevLevel(i) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (on) ShColors.Accent else ShColors.SearchField,
                    contentColor = if (on) ShColors.Background else ShColors.TextPrimary
                )
            ) { Text(label, style = ShText.Small) }
        }
    }

    Section(stringResource(R.string.s_ebadc474))
    SwitchRow(stringResource(R.string.s_42fc7088), stringResource(R.string.s_d64dbd69), s.fullscreen, vm::setFullscreen)
    SwitchRow(stringResource(R.string.s_5b66acf3), stringResource(R.string.s_efc34ccd), s.satellite, vm::setSatellite)
    SwitchRow("3D-карта", "В поездке сильный наклон и объёмные дома. Если на магнитоле тормозит — выключите.", s.map3d, vm::setMap3d)
    SwitchRow("Ночной экран", "После заката (по солнцу там, где вы) карта темнее, чтобы не слепила.", s.nightScreen, vm::setNightScreen)

    Section("Проекция на лобовое (HUD)")
    Hint("Скорость, поворот и ограничение крупно на чёрном. Телефон кладут под лобовое стекло экраном вверх, " +
        "верхом к себе — картинка отражается в стекле. Лучше всего видно в темноте. В поездке — плитка «Проекция» в меню.")
    WideButton("Включить проекцию", onClick = onOpenHud)
    SwitchRow("Зеркальная картинка", "Для отражения в стекле. Выключите, чтобы смотреть прямо на экран.", s.hudMirror, vm::setHudMirror)
}

@Composable
private fun CarPage(s: NavState, vm: NavViewModel) {
    Section(stringResource(R.string.s_5191dcb3))
    var consumption by remember(s.consumption) { mutableStateOf(trimNumber(s.consumption)) }
    var price by remember(s.fuelPrice) { mutableStateOf(trimNumber(s.fuelPrice)) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField("Расход, л на 100 км", consumption, Modifier.weight(1f)) {
            consumption = it
            it.replace(',', '.').toFloatOrNull()?.takeIf { v -> v in 1f..40f }?.let(vm::setConsumption)
        }
        NumberField("Цена за литр, ₽", price, Modifier.weight(1f)) {
            price = it
            it.replace(',', '.').toFloatOrNull()?.takeIf { v -> v in 1f..500f }?.let(vm::setFuelPrice)
        }
    }

    Section(stringResource(R.string.s_fb4ac830))
    var refuelList by remember { mutableStateOf(vm.refuelsText()) }
    if (refuelList.isBlank()) {
        EmptyState(EmptyKind.FUEL, "Заправок ещё нет", "Запишите первую заправку ниже, и здесь появятся три последние.")
    } else {
        InfoCard(refuelList)
    }
    var rfPlace by remember { mutableStateOf("") }
    var rfLiters by remember { mutableStateOf("") }
    var rfPrice by remember { mutableStateOf("") }
    Spacer(Modifier.height(6.dp))
    TextField(
        value = rfPlace, onValueChange = { rfPlace = it.take(40) }, label = { Text(stringResource(R.string.s_a3c3bce3), fontSize = 12.sp) },
        singleLine = true, shape = RoundedCornerShape(14.dp), colors = sheetFieldColors(), modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        NumberField("Литры", rfLiters, Modifier.weight(1f)) { rfLiters = it }
        NumberField("Цена за литр", rfPrice, Modifier.weight(1f)) { rfPrice = it }
    }
    var rfError by remember { mutableStateOf(false) }
    if (rfError) Hint("Введите литры и цену числами, например 40 и 58,5", Modifier.padding(top = 6.dp), ShColors.Danger)
    WideButton(stringResource(R.string.s_d2e77ad1)) {
        val l = rfLiters.replace(',', '.').toDoubleOrNull()
        val p = rfPrice.replace(',', '.').toDoubleOrNull()
        if (l != null && p != null && l > 0 && p > 0) {
            vm.addRefuel(rfPlace, l, p)
            refuelList = vm.refuelsText(); rfLiters = ""; rfPrice = ""; rfPlace = ""; rfError = false
        } else rfError = true
    }

    Section(stringResource(R.string.s_57d32d98))
    var svcKm by remember { mutableStateOf(vm.serviceIntervalKm.takeIf { it > 0 }?.toString() ?: "") }
    Hint(stringResource(R.string.s_1d36c01c))
    Spacer(Modifier.height(6.dp))
    NumberField("Интервал ТО, км", svcKm, Modifier.fillMaxWidth(), integer = true) {
        svcKm = it.filter { c -> c.isDigit() }.take(6)
        vm.serviceIntervalKm = svcKm.toIntOrNull() ?: 0
    }
    var svcDone by remember { mutableStateOf(false) }
    WideButton(if (svcDone) "Отмечено: отсчёт пробега начат заново" else stringResource(R.string.s_e639e8c2)) { vm.markServiceDone(); svcDone = true }
}

@Composable
private fun TripsPage(vm: NavViewModel) {
    var count by remember { mutableStateOf(vm.tripCount()) }
    var statsText by remember { mutableStateOf(vm.statsText()) }
    val ctx = LocalContext.current
    Section(stringResource(R.string.s_6ac9c557))
    if (count == 0) {
        EmptyState(
            EmptyKind.TRIPS, "Поездок пока нет",
            "Постройте маршрут и нажмите «Поехали». После поездки здесь появятся километры, скорость и стоимость."
        )
        return
    }
    InfoCard(statsText)
    WideButton(stringResource(R.string.s_916157e7)) {
        runCatching {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Поездки APEX (CSV)")
                putExtra(android.content.Intent.EXTRA_TEXT, vm.exportText())
            }
            ctx.startActivity(android.content.Intent.createChooser(send, "Экспорт поездок").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    var armed by remember { mutableStateOf(false) }
    WideButton(if (armed) "Нажмите ещё раз: удалить историю" else stringResource(R.string.s_422a09c7)) {
        if (armed) { vm.clearTrips(); statsText = vm.statsText(); count = vm.tripCount(); armed = false } else armed = true
    }
}

@Composable
private fun CaravanPage(s: NavState, vm: NavViewModel) {
    Section(stringResource(R.string.s_9c58e95b))
    var code by remember { mutableStateOf("") }
    val myCode = s.caravanCode
    if (myCode != null) {
        InfoCard("Код каравана: $myCode" + if (s.caravanLeader) " (вы ведёте)" else "")
        if (s.members.isEmpty()) {
            EmptyState(EmptyKind.CARAVAN, "Пока никого", "Отправьте код $myCode друзьям. Когда они войдут, их машины появятся на карте.")
        } else {
            Hint("На карте: " + s.members.joinToString(", ") { it.name + if (it.leader) " (ведущий)" else "" } + ". Ведущий задаёт общий пункт назначения.")
        }
        TextButton(onClick = vm::leaveCaravan, modifier = Modifier.height(48.dp)) {
            Text(stringResource(R.string.s_25793d41), color = ShColors.Danger, style = ShText.Small)
        }
    } else {
        EmptyState(EmptyKind.CARAVAN, "Вы не в караване", "Создайте караван и отправьте код друзьям или введите их код.")
        WideButton(stringResource(R.string.s_c8735bdd), onClick = vm::createCaravan)
        Section("Войти по коду")
        Hint("Введите 4 цифры, которые вам прислал ведущий.")
        Spacer(Modifier.height(8.dp))
        CodeInput(code) { code = it }
        Spacer(Modifier.height(10.dp))
        ru.shturman.ui.fx.GlowButton(
            text = "Войти в караван",
            onClick = { vm.joinCaravan(code) },
            enabled = code.length == 4,
            burst = 0f,
            height = 52.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
    s.caravanMessage?.let { Hint(it, Modifier.padding(top = 6.dp)) }
}

/** Поле ввода текста в настройках (название клана, пароль). */
@Composable
private fun SheetTextField(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = { onChange(it.take(32)) },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        shape = RoundedCornerShape(14.dp),
        colors = sheetFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

/**
 * Клан: друзья видят друг друга на карте — где каждый, с какой скоростью и по какому маршруту едет.
 * Вступить — по названию и паролю. Глава (создатель) может передать главенство, исключить участника,
 * сменить пароль или распустить клан.
 */
@Composable
private fun ClanPage(s: NavState, vm: NavViewModel) {
    val c = s.clan
    if (c == null) {
        InfoCard("Клан — это ваша компания на дорогах. Участники видят на карте, кто где, с какой скоростью едет и по какому маршруту.")
        var jName by remember { mutableStateOf("") }
        var jPass by remember { mutableStateOf("") }
        Section("Вступить в клан")
        SheetTextField("Название клана", jName) { jName = it }
        SheetTextField("Пароль", jPass, password = true) { jPass = it }
        Spacer(Modifier.height(8.dp))
        ru.shturman.ui.fx.GlowButton(
            text = "Вступить",
            onClick = { vm.joinClan(jName, jPass) },
            enabled = jName.isNotBlank() && jPass.isNotEmpty(),
            burst = 0f, height = 52.dp, modifier = Modifier.fillMaxWidth()
        )
        var cName by remember { mutableStateOf("") }
        var cPass by remember { mutableStateOf("") }
        Section("Создать свой клан")
        Hint("Вы станете главой. Название и пароль скажите друзьям.")
        SheetTextField("Название", cName) { cName = it }
        SheetTextField("Пароль (от 4 знаков)", cPass, password = true) { cPass = it }
        WideButton("Создать клан") { if (cName.isNotBlank() && cPass.length >= 4) vm.createClan(cName, cPass) }
        s.clanMessage?.let { Hint(it, Modifier.padding(top = 8.dp)) }
        return
    }

    val me = s.myUid
    val iAmOwner = me != null && c.ownerUid == me
    InfoCard("Клан «${c.name}»\nГлава: ${c.ownerName}" + if (iAmOwner) " (это вы)" else "")
    SwitchRow("Показывать меня клану", "Где я, скорость и маршрут видят только участники клана", s.clanShare) { vm.setClanShare(it) }

    Section("Участники · ${s.clanMembers.size}")
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000L); now = System.currentTimeMillis() } }
    var armed by remember { mutableStateOf<String?>(null) }
    s.clanMembers.forEach { m ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Box(
                Modifier.size(12.dp).clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(m.color)))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.name + (if (m.uid == me) " (вы)" else "") + (if (m.uid == c.ownerUid) " · глава" else ""),
                    color = ShColors.TextPrimary, style = ShText.Small
                )
                Text(ru.shturman.routing.clanStatus(m, now), color = ShColors.TextSecondary, fontSize = 13.sp)
            }
        }
        if (iAmOwner && m.uid != me) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)) {
                val ownerKey = "owner:" + m.uid
                val kickKey = "kick:" + m.uid
                TextButton(onClick = { if (armed == ownerKey) { armed = null; vm.makeClanOwner(m) } else armed = ownerKey }) {
                    Text(if (armed == ownerKey) "Точно сделать главой?" else "Сделать главой", color = ShColors.Accent, style = ShText.Small)
                }
                TextButton(onClick = { if (armed == kickKey) { armed = null; vm.kickClanMember(m) } else armed = kickKey }) {
                    Text(if (armed == kickKey) "Точно исключить?" else "Исключить", color = ShColors.Danger, style = ShText.Small)
                }
            }
        }
    }

    if (iAmOwner) {
        Section("Пароль клана")
        var newPass by remember { mutableStateOf("") }
        SheetTextField("Новый пароль", newPass, password = true) { newPass = it }
        WideButton("Сменить пароль") { if (newPass.length >= 4) { vm.changeClanPassword(newPass); newPass = "" } }
    }

    Spacer(Modifier.height(12.dp))
    val others = s.clanMembers.count { it.uid != me }
    if (iAmOwner && others > 0) {
        Hint("Чтобы выйти, сначала сделайте главой другого участника.")
        TextButton(onClick = { if (armed == "disband") { armed = null; vm.disbandClan() } else armed = "disband" }, modifier = Modifier.height(48.dp)) {
            Text(if (armed == "disband") "Нажмите ещё раз: распустить клан" else "Распустить клан", color = ShColors.Danger, style = ShText.Small)
        }
    } else {
        TextButton(onClick = { if (armed == "leave") { armed = null; vm.leaveClan() } else armed = "leave" }, modifier = Modifier.height(48.dp)) {
            Text(if (armed == "leave") "Нажмите ещё раз: выйти" else "Выйти из клана", color = ShColors.Danger, style = ShText.Small)
        }
    }
    s.clanMessage?.let { Hint(it, Modifier.padding(top = 6.dp)) }
}

@Composable
private fun NotifPage() {
    val ctx = LocalContext.current
    var nTrip by remember { mutableStateOf(AutoPrefs.tripNotice(ctx)) }
    var nQuiet by remember { mutableStateOf(AutoPrefs.quietHours(ctx)) }
    var nBt by remember { mutableStateOf(AutoPrefs.btAutostart(ctx)) }
    var nBtStop by remember { mutableStateOf(AutoPrefs.btAutostop(ctx)) }
    var nCharge by remember { mutableStateOf(AutoPrefs.chargeAutostart(ctx)) }
    var btName by remember { mutableStateOf(AutoPrefs.btName(ctx)) }
    var btList by remember { mutableStateOf<List<Pair<String, String>>?>(null) }

    Section("Уведомления")
    SwitchRow(stringResource(R.string.s_85e8c0f1), stringResource(R.string.s_76e7af47), nTrip) { nTrip = it; AutoPrefs.set(ctx, "notif_trip", it) }
    SwitchRow(stringResource(R.string.s_c1c10eb5), stringResource(R.string.s_63e3c45e), nQuiet) { nQuiet = it; AutoPrefs.set(ctx, "quiet_hours", it) }

    fun loadBonded() {
        btList = runCatching {
            @Suppress("MissingPermission")
            (ctx.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager)
                .adapter?.bondedDevices?.map { (it.name ?: it.address) to it.address } ?: emptyList()
        }.getOrDefault(emptyList())
    }
    val btPerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok -> if (ok) loadBonded() }

    Section("Bluetooth авто")
    SwitchRow(stringResource(R.string.s_1d63dbdf), stringResource(R.string.s_ce0470a5), nBt) { nBt = it; AutoPrefs.set(ctx, "bt_autostart", it) }
    SwitchRow(stringResource(R.string.s_7ef4135a), stringResource(R.string.s_4cd9559c), nBtStop) { nBtStop = it; AutoPrefs.set(ctx, "bt_autostop", it) }
    Hint("Выбрано: ${btName ?: "ничего"}")
    WideButton(stringResource(R.string.s_05e54559)) {
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) btPerm.launch(android.Manifest.permission.BLUETOOTH_CONNECT) else loadBonded()
    }
    btList?.let { list ->
        if (list.isEmpty()) {
            EmptyState(EmptyKind.SEARCH, "Нет спаренных устройств", stringResource(R.string.s_120f7f9b))
        }
        list.forEach { (name, addr) ->
            TextButton(
                onClick = { AutoPrefs.setAddress(ctx, addr, name); btName = name; btList = null },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(name, color = ShColors.Accent, style = ShText.Small) }
        }
    }

    Section("Зарядка")
    SwitchRow(stringResource(R.string.s_d36f16c1), stringResource(R.string.s_6789d352), nCharge) { nCharge = it; AutoPrefs.set(ctx, "charge_autostart", it) }

    var autoUpdate by remember { mutableStateOf(ru.shturman.update.AutoUpdate.enabled(ctx)) }
    var updateMobile by remember { mutableStateOf(ru.shturman.update.AutoUpdate.mobileAllowed(ctx)) }
    Section("Обновления")
    SwitchRow(
        "Автообновление в фоне",
        "Новая сборка скачивается сама и ставится, когда приложение закрыто и вы не в поездке. " +
            "На Android 12 и новее — без вопросов, на старых — одно нажатие на уведомление.",
        autoUpdate
    ) { autoUpdate = it; ru.shturman.update.AutoUpdate.setEnabled(ctx, it) }
    if (autoUpdate) {
        SwitchRow("Скачивать и по мобильному интернету", "Сборка весит около 100 МБ. Выключено — только по Wi-Fi.", updateMobile) {
            updateMobile = it
            ru.shturman.update.AutoUpdate.setMobileAllowed(ctx, it)
        }
    }
}

@Composable
private fun DataPage(vm: NavViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupMsg by remember { mutableStateOf("") }

    Section(stringResource(R.string.s_b230ab07))
    Hint("Настройки шифруются на телефоне и сохраняются в вашем аккаунте. На новом телефоне войдите тем же аккаунтом и нажмите «Восстановить».")
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { backupMsg = "Сохраняю…"; scope.launch { backupMsg = vm.backupSettings() } },
            modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp), colors = plainButton()
        ) { Text(stringResource(R.string.s_85ff0b56), style = ShText.Small) }
        Button(
            onClick = { backupMsg = "Восстанавливаю…"; scope.launch { backupMsg = vm.restoreSettings() } },
            modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp), colors = plainButton()
        ) { Text(stringResource(R.string.s_f5682b32), style = ShText.Small) }
    }
    if (backupMsg.endsWith("…")) LoadingLine(backupMsg, Modifier.padding(top = 6.dp))
    else if (backupMsg.isNotBlank()) Hint(backupMsg, Modifier.padding(top = 6.dp))
    WideButton(stringResource(R.string.s_1db3ddfa)) {
        val link = vm.locationLink()
        if (link == null) backupMsg = "Положение пока не определено" else runCatching {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "Я здесь: $link")
            }
            ctx.startActivity(android.content.Intent.createChooser(send, "Поделиться местоположением").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Section(stringResource(R.string.s_145f378b))
    var cacheText by remember { mutableStateOf("") }
    var resetArmed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        cacheText = "Временные файлы: ${"%.1f".format(java.util.Locale.US, vm.cacheSizeBytes() / 1048576.0)} МБ"
    }
    if (cacheText.isBlank()) LoadingLine("Считаю размер…") else Hint(cacheText)
    WideButton(stringResource(R.string.s_abe64288)) {
        val freed = vm.clearCache()
        cacheText = "Освобождено ${"%.1f".format(java.util.Locale.US, freed / 1048576.0)} МБ"
    }
    WideButton(if (resetArmed) "Нажмите ещё раз: сбросить настройки" else "Сбросить настройки") {
        if (resetArmed) { vm.resetSettings(); resetArmed = false } else resetArmed = true
    }
    Hint("Сброс не трогает дом, работу, тему, поездки и заправки.", Modifier.padding(top = 6.dp))
}

@Composable
private fun PrivacyPage(user: UserInfo, s: NavState, vm: NavViewModel) {
    Section(stringResource(R.string.s_0899eda1))
    RoadDataCard()
    Spacer(Modifier.height(6.dp))
    Hint(stringResource(R.string.s_0f3468a4))

    Section("Синхронизация")
    ru.shturman.ui.fx.GlowButton(
        text = if (s.syncing) "Синхронизирую…" else "Синхронизировать",
        onClick = { vm.syncNow() },
        enabled = !s.syncing,
        burst = 0f,
        height = 52.dp,
        modifier = Modifier.fillMaxWidth()
    )
    s.syncMessage?.let { Hint(it, Modifier.padding(top = 8.dp)) }
    Hint("В фоне синхронизация идёт сама раз в 2 часа, когда есть интернет.", Modifier.padding(top = 6.dp))

    Section("Помощь")
    val ctx = LocalContext.current
    WideButton(stringResource(R.string.s_d995e9c5)) {
        val sum = "маршрутов: ${s.options.size}, навигация: ${s.navigating}, данные готовы: ${s.dataReady}" +
            ", ошибка данных: ${s.dataError}, синхр.: ${s.syncMessage}, караван: ${s.caravanMessage}" +
            ", голос: ${s.voiceMessage}, состояние голоса: ${s.voiceStatus}, услышано: ${s.heardText}, аккаунт: ${user.email}"
        ru.shturman.Report.send(ctx, sum)
    }
}

/** Скрытый раздел «Штурман 2». Сюда будут добавляться функции клана. */
@Composable
private fun ApexPage(onIntro: () -> Unit) {
    Section("Заставка")
    WideButton("Показать заставку APEX", onClick = onIntro)
}

@Composable
private fun AdminPage(onOpenAdmin: () -> Unit, onOpenRoadData: () -> Unit) {
    Section("Данные о дорогах")
    Hint("Насколько хорошо собраны пробки и светофоры: оценка, карта и цифры. Видно только вам.")
    WideButton("Открыть «Данные о дорогах»", onClick = onOpenRoadData)
    Section(stringResource(R.string.s_f42d869a))
    WideButton(stringResource(R.string.s_1abf35b1), onClick = onOpenAdmin)
}

// ---------- общие элементы ----------

private fun trimNumber(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

private fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

@Composable
private fun plainButton() = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)

@Composable
private fun WideButton(text: String, onClick: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    val shape = ru.shturman.ui.fx.chipShape()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pressFx()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = ShColors.TextPrimary, style = ShText.Small) }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier, color: Color = ShColors.TextSecondary) {
    Text(text = text, color = color, fontSize = 13.sp, modifier = modifier)
}

@Composable
private fun InfoCard(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ShColors.SearchField)
            .padding(14.dp)
    ) {
        Text(text = text, color = ShColors.TextPrimary, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(text = title.uppercase(), color = ShColors.Muted, style = ShText.Caps)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = ShColors.TextPrimary, style = ShText.Small)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, color = ShColors.TextSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ShColors.Accent)
        )
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = ShColors.Accent,
                checkmarkColor = ShColors.Background,
                uncheckedColor = ShColors.TextSecondary
            )
        )
        Text(text = title, color = ShColors.TextPrimary, style = ShText.Small)
    }
}

/**
 * Код каравана: четыре крупные клетки под цифры. Нажатие по клеткам открывает цифровую клавиатуру,
 * текущая клетка подсвечена цветом темы.
 */
@Composable
private fun CodeInput(value: String, onChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    for (i in 0 until 4) {
                        val ch = value.getOrNull(i)
                        val current = i == value.length.coerceAtMost(3)
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(shape)
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(if (current) 1.5.dp else 1.dp, if (current) ShColors.Accent else Color.White.copy(alpha = 0.22f), shape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(ch?.toString() ?: "", color = ShColors.TextPrimary, style = ShText.Value.copy(fontSize = 30.sp, lineHeight = 34.sp))
                        }
                    }
                }
                // настоящее поле невидимо: цифры рисуют клетки, поле только принимает ввод
                Box(Modifier.size(1.dp)) { inner() }
            }
        }
    )
}

/** Поля ввода в настройках: заметная светлая подложка и линия снизу (цвета темы при вводе). */
@Composable
private fun sheetFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.White.copy(alpha = 0.08f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
    focusedTextColor = ShColors.TextPrimary,
    unfocusedTextColor = ShColors.TextPrimary,
    focusedLabelColor = ShColors.TextSecondary,
    unfocusedLabelColor = ShColors.TextSecondary,
    cursorColor = ShColors.Accent,
    focusedIndicatorColor = ShColors.Accent,
    unfocusedIndicatorColor = Color.White.copy(alpha = 0.25f)
)

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, integer: Boolean = false, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.NumberPassword else KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        colors = sheetFieldColors(),
        modifier = modifier
    )
}

/**
 * Сбор данных о дорогах: что собирается, работает ли сейчас и что включить, чтобы сбор шёл в фоне.
 * Выключателя нет: обезличенные скорости и ожидание на светофорах собираются у всех.
 */
@Composable
private fun RoadDataCard() {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    // статус обновляется сам: человек может вернуться из настроек телефона с новым разрешением
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500L)
            tick++
        }
    }
    val bgLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ru.shturman.service.DriveWatch.start(ctx) else openAppSettings(ctx)
        tick++
    }
    // перечитываем состояние на каждом такте
    val hasLoc = remember(tick) { ru.shturman.service.DriveWatch.hasLocation(ctx) }
    val always = remember(tick) { ru.shturman.service.DriveWatch.hasBackground(ctx) }
    val battery = remember(tick) { ru.shturman.service.DriveWatch.batteryFree(ctx) }
    val running = remember(tick) { ru.shturman.service.DriveWatch.running }
    val mode = remember(tick) { ru.shturman.service.DriveWatch.mode }

    InfoCard(
        "APEX записывает обезличенные данные о дорогах во время любой поездки на машине, даже когда приложение закрыто: " +
            "с какой скоростью едут на участках дорог и сколько стоят на светофорах. Имя, аккаунт и маршруты не записываются " +
            "и не отправляются. Пока идёт запись поездки, в шторке виден значок APEX. Так прогнозы времени в пути становятся точнее у всех."
    )
    val status = when {
        !hasLoc -> "Сбор не работает: нет доступа к местоположению."
        running && always && battery -> "Сбор работает в фоне: $mode."
        running -> "Сбор работает, пока телефон не перезагрузят или система не остановит приложение: $mode."
        else -> "Сбор сейчас остановлен."
    }
    Hint(status, Modifier.padding(top = 6.dp), if (running && always && battery) ShColors.Accent else ShColors.TextSecondary)
    if (hasLoc && !always) {
        WideButton("Разрешить местоположение «Всегда»") {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                runCatching { bgLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) }.onFailure { openAppSettings(ctx) }
            } else openAppSettings(ctx)
        }
    }
    if (hasLoc && !battery) {
        WideButton("Не ограничивать работу в фоне") { askBatteryFree(ctx) }
    }
    if (hasLoc && !running) {
        WideButton("Включить сбор") { ru.shturman.service.DriveWatch.start(ctx); tick++ }
    }
    val maker = android.os.Build.MANUFACTURER.lowercase()
    if (maker.contains("huawei") || maker.contains("honor")) {
        Hint(
            "Huawei и Honor останавливают приложения в фоне. Откройте Настройки → Батарея → Запуск приложений → APEX, " +
                "выберите «Управлять вручную» и включите все три пункта.",
            Modifier.padding(top = 6.dp)
        )
    }
}

private fun openAppSettings(ctx: android.content.Context) {
    runCatching {
        ctx.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Просит Android не ограничивать APEX в фоне (иначе сбор могут остановить для экономии батареи). */
@android.annotation.SuppressLint("BatteryLife")
fun askBatteryFree(ctx: android.content.Context) {
    if (ru.shturman.service.DriveWatch.batteryFree(ctx)) return
    val direct = runCatching {
        ctx.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + ctx.packageName))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    if (direct.isFailure) runCatching {
        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
