package ru.shturman.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** Разделы настроек. Главный экран — список разделов, каждый открывается своей страницей. */
private enum class Page(val title: String) {
    THEME("Оформление"),
    VOICE("Голос и звуки"),
    NAV("Навигация и экран"),
    CAR("Бензин и машина"),
    TRIPS("Поездки и статистика"),
    CARAVAN("Караван"),
    NOTIF("Уведомления и автозапуск"),
    DATA("Данные и резервная копия"),
    PRIVACY("Приватность и синхронизация"),
    ADMIN("Для владельца")
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
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ShColors.Panel,
        contentColor = ShColors.TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ShColors.Divider) }
    ) {
        var pageName by rememberSaveable { mutableStateOf<String?>(null) }
        val page = pageName?.let { n -> Page.values().firstOrNull { it.name == n } }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally(tween()) { w -> if (forward) w / 4 else -w / 4 } + fadeIn(tween())) togetherWith
                    (slideOutHorizontally(tween()) { w -> if (forward) -w / 4 else w / 4 } + fadeOut(tween()))
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
                    Home(user, s, vm, isAdmin, onSignOut) { pageName = it.name }
                } else {
                    PageHeader(current.title) { pageName = null }
                    when (current) {
                        Page.THEME -> ThemePage(s, vm)
                        Page.VOICE -> VoicePage(s, vm)
                        Page.NAV -> NavPage(s, vm)
                        Page.CAR -> CarPage(s, vm)
                        Page.TRIPS -> TripsPage(vm)
                        Page.CARAVAN -> CaravanPage(s, vm)
                        Page.NOTIF -> NotifPage()
                        Page.DATA -> DataPage(vm)
                        Page.PRIVACY -> PrivacyPage(user, s, vm)
                        Page.ADMIN -> AdminPage(onOpenAdmin)
                    }
                }
            }
        }
    }
}

private fun <T> tween() = androidx.compose.animation.core.tween<T>(durationMillis = 220)

// ---------- главный экран настроек ----------

@Composable
private fun Home(user: UserInfo, s: NavState, vm: NavViewModel, isAdmin: Boolean, onSignOut: () -> Unit, open: (Page) -> Unit) {
    val ctx = LocalContext.current
    val trips = remember { vm.tripCount() }
    Profile(user)

    Group("Внешний вид и голос")
    CategoryRow(Icons.Filled.Star, Page.THEME.title,
        (Themes.all.getOrNull(s.theme)?.name ?: "") + if (ru.shturman.ui.themes.ThemeSounds.enabled) " · звуки темы" else "") { open(Page.THEME) }
    CategoryRow(Icons.Filled.PlayArrow, Page.VOICE.title,
        (if (s.voiceOn) "Подсказки включены" else "Подсказки выключены") + if (s.nightVoice) " · ночной режим" else "") { open(Page.VOICE) }

    Group("В дороге")
    CategoryRow(Icons.Filled.LocationOn, Page.NAV.title,
        "Съезд: ${LEVELS.getOrElse(s.devLevel) { "Средняя" }.lowercase()}" + (if (s.fullscreen) " · полный экран" else "") + (if (s.satellite) " · спутник" else "")) { open(Page.NAV) }
    CategoryRow(Icons.Filled.Build, Page.CAR.title,
        "${trimNumber(s.consumption)} л/100 км · ${trimNumber(s.fuelPrice)} ₽/л · заправки и ТО") { open(Page.CAR) }
    CategoryRow(Icons.Filled.DateRange, Page.TRIPS.title,
        if (trips == 0) "Поездок пока нет" else "$trips ${plural(trips, "поездка", "поездки", "поездок")} за 3 месяца") { open(Page.TRIPS) }
    CategoryRow(Icons.Filled.Place, Page.CARAVAN.title,
        s.caravanCode?.let { "Код $it · на карте ${s.members.size}" } ?: "Вы не в караване") { open(Page.CARAVAN) }

    Group("Система")
    CategoryRow(Icons.Filled.Notifications, Page.NOTIF.title,
        listOfNotNull(
            if (AutoPrefs.quietHours(ctx)) "тихие часы" else null,
            if (AutoPrefs.btAutostart(ctx)) "Bluetooth" else null,
            if (AutoPrefs.chargeAutostart(ctx)) "зарядка" else null
        ).joinToString(" · ").ifEmpty { "Итог поездки, тихие часы, автозапуск" }.replaceFirstChar { it.uppercase() }) { open(Page.NOTIF) }
    CategoryRow(Icons.Filled.Settings, Page.DATA.title, "Копия в аккаунте, кэш, сброс") { open(Page.DATA) }
    CategoryRow(Icons.Filled.Lock, Page.PRIVACY.title,
        (if (s.shareData) "Обучение включено" else "Обучение выключено") + " · отчёт разработчику") { open(Page.PRIVACY) }
    if (isAdmin) CategoryRow(Icons.Filled.Edit, Page.ADMIN.title, "Коды активации") { open(Page.ADMIN) }

    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(text = stringResource(R.string.s_6d4183b7), color = ShColors.TextSecondary, style = ShText.Small)
    }
}

@Composable
private fun Profile(user: UserInfo) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(ShColors.Divider),
            contentAlignment = Alignment.Center
        ) {
            if (user.photoUrl != null) {
                AsyncImage(model = user.photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
            } else {
                Icon(imageVector = Icons.Filled.Person, contentDescription = null, tint = ShColors.TextSecondary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text = user.name, color = ShColors.TextPrimary, style = ShText.Distance.copy(fontSize = 20.sp))
            Text(text = user.email, color = ShColors.TextSecondary, style = ShText.Street)
        }
    }
}

@Composable
private fun Group(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(text = title.uppercase(), color = ShColors.TextSecondary, fontSize = 12.sp, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CategoryRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ShColors.SearchField)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(ShColors.Accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ShColors.Accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, color = ShColors.TextPrimary, style = ShText.Small)
            Text(text = summary, color = ShColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ShColors.TextSecondary)
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = ShColors.Accent)
        }
        Spacer(Modifier.width(4.dp))
        Text(text = title, color = ShColors.TextPrimary, style = ShText.Distance.copy(fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun NavPage(s: NavState, vm: NavViewModel) {
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

    Section(stringResource(R.string.s_4a9ccc8c))
    CheckRow("Проценты пути", s.cardPercent) { vm.setCardOption("percent", it) }
    CheckRow("Сколько проехали из скольких", s.cardDistance) { vm.setCardOption("distance", it) }
    CheckRow("Время прибытия", s.cardEta) { vm.setCardOption("eta", it) }
    CheckRow("Сколько осталось (время и км)", s.cardRemaining) { vm.setCardOption("remaining", it) }
    CheckRow("Расход бензина и стоимость", s.cardFuel) { vm.setCardOption("fuel", it) }
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
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Поездки Штурман (CSV)")
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
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberField("Код из 4 цифр", code, Modifier.weight(1f), integer = true) { code = it.filter(Char::isDigit).take(4) }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { vm.joinCaravan(code) },
                enabled = code.length == 4,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
            ) { Text(stringResource(R.string.s_63a75375), style = ShText.Button) }
        }
    }
    s.caravanMessage?.let { Hint(it, Modifier.padding(top = 6.dp)) }
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
    SwitchRow(
        "Участвовать в общем обучении",
        "Уходят только обезличенные данные о дорогах. Ваши координаты — никогда.",
        s.shareData, vm::setShareData
    )
    Spacer(Modifier.height(6.dp))
    Hint(stringResource(R.string.s_0f3468a4))

    Section("Синхронизация")
    Button(
        onClick = vm::syncNow,
        enabled = !s.syncing,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
    ) {
        Text(text = if (s.syncing) "Синхронизирую…" else "Синхронизировать", style = ShText.Button)
    }
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

@Composable
private fun AdminPage(onOpenAdmin: () -> Unit) {
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
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = plainButton()
    ) { Text(text, style = ShText.Small) }
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
    Spacer(Modifier.height(18.dp))
    Text(text = title, color = ShColors.Accent, style = ShText.Button)
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
            colors = SwitchDefaults.colors(checkedThumbColor = ShColors.Background, checkedTrackColor = ShColors.Accent)
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

@Composable
private fun sheetFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = ShColors.SearchField,
    unfocusedContainerColor = ShColors.SearchField,
    focusedTextColor = ShColors.TextPrimary,
    unfocusedTextColor = ShColors.TextPrimary,
    focusedLabelColor = ShColors.TextSecondary,
    unfocusedLabelColor = ShColors.TextSecondary,
    cursorColor = ShColors.Accent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
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
