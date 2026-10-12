package ru.shturman.ui

import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import ru.shturman.NavState
import ru.shturman.NavViewModel
import ru.shturman.auth.UserInfo
import ru.shturman.ui.theme.ShColors
import ru.shturman.ui.theme.ShText

/** Настройки: профиль, режимы, голос, экран, карточка, бензин, караван, приватность и синхронизация. */
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // Профиль: имя, почта и аватарка из Google-аккаунта
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(ShColors.Divider),
                    contentAlignment = Alignment.Center
                ) {
                    if (user.photoUrl != null) {
                        AsyncImage(
                            model = user.photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp)
                        )
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

            Divider()

            Section(stringResource(ru.shturman.R.string.s_ca179901))
            ru.shturman.ui.themes.ThemePicker(selected = s.theme, onSelect = vm::setTheme)
            var themeSounds by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ru.shturman.ui.themes.ThemeSounds.enabled) }
            SwitchRow(stringResource(ru.shturman.R.string.s_7be14764), stringResource(ru.shturman.R.string.s_3320af3e), themeSounds) {
                themeSounds = it
                vm.setThemeSounds(it)
            }

            Section(stringResource(ru.shturman.R.string.s_9232bbf3))
            SwitchRow(stringResource(ru.shturman.R.string.s_d8914beb), stringResource(ru.shturman.R.string.s_4067a129), s.voiceOn, vm::setVoiceOn)
            Button(
                onClick = vm::testVoice,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text(stringResource(ru.shturman.R.string.s_fc6d20f9), style = ShText.Small) }
            s.voiceStatus?.let { Text(text = it, color = ShColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            SwitchRow(stringResource(ru.shturman.R.string.s_66efd4ed), stringResource(ru.shturman.R.string.s_a483e67b), s.nightVoice, vm::setNightVoice)
            SwitchRow(stringResource(ru.shturman.R.string.s_dfe8ad96), stringResource(ru.shturman.R.string.s_f54ed96c), s.turnVibro, vm::setTurnVibro)
            SwitchRow(stringResource(ru.shturman.R.string.s_29323c03), stringResource(ru.shturman.R.string.s_774dfed6), s.overspeedSound, vm::setOverspeedSound)

            Section(stringResource(ru.shturman.R.string.s_edc3ad9b))
            Text(text = stringResource(ru.shturman.R.string.s_a626bf4b), color = ShColors.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Низкая", "Средняя", "Высокая").forEachIndexed { i, label ->
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

            Section(stringResource(ru.shturman.R.string.s_ebadc474))
            SwitchRow(stringResource(ru.shturman.R.string.s_42fc7088), stringResource(ru.shturman.R.string.s_d64dbd69), s.fullscreen, vm::setFullscreen)
            SwitchRow(stringResource(ru.shturman.R.string.s_5b66acf3), stringResource(ru.shturman.R.string.s_efc34ccd), s.satellite, vm::setSatellite)

            Section(stringResource(ru.shturman.R.string.s_4a9ccc8c))
            CheckRow("Проценты пути", s.cardPercent) { vm.setCardOption("percent", it) }
            CheckRow("Сколько проехали из скольких", s.cardDistance) { vm.setCardOption("distance", it) }
            CheckRow("Время прибытия", s.cardEta) { vm.setCardOption("eta", it) }
            CheckRow("Сколько осталось (время и км)", s.cardRemaining) { vm.setCardOption("remaining", it) }
            CheckRow("Расход бензина и стоимость", s.cardFuel) { vm.setCardOption("fuel", it) }

            Section(stringResource(ru.shturman.R.string.s_5191dcb3))
            var consumption by remember(s.consumption) { mutableStateOf(trimNumber(s.consumption)) }
            var price by remember(s.fuelPrice) { mutableStateOf(trimNumber(s.fuelPrice)) }
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                NumberField("Расход, л на 100 км", consumption, Modifier.weight(1f)) {
                    consumption = it
                    it.replace(',', '.').toFloatOrNull()?.takeIf { v -> v in 1f..40f }?.let(vm::setConsumption)
                }
                NumberField("Цена за литр, ₽", price, Modifier.weight(1f)) {
                    price = it
                    it.replace(',', '.').toFloatOrNull()?.takeIf { v -> v in 1f..500f }?.let(vm::setFuelPrice)
                }
            }

            Section(stringResource(ru.shturman.R.string.s_9c58e95b))
            var code by remember { mutableStateOf("") }
            if (s.caravanCode != null) {
                Text(
                    text = "Код каравана: ${s.caravanCode}" + if (s.caravanLeader) " (вы ведёте)" else "",
                    color = ShColors.TextPrimary,
                    style = ShText.Small
                )
                Text(
                    text = "Участников на карте: ${s.members.size}. Ведущий задаёт общий пункт назначения.",
                    color = ShColors.TextSecondary,
                    fontSize = 13.sp
                )
                TextButton(onClick = vm::leaveCaravan) {
                    Text(stringResource(ru.shturman.R.string.s_25793d41), color = ShColors.Danger, style = ShText.Small)
                }
            } else {
                Button(
                    onClick = vm::createCaravan,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
                ) { Text(stringResource(ru.shturman.R.string.s_c8735bdd), style = ShText.Small) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField("Код из 4 цифр", code, Modifier.weight(1f), integer = true) { code = it.filter(Char::isDigit).take(4) }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { vm.joinCaravan(code) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
                    ) { Text(stringResource(ru.shturman.R.string.s_63a75375), style = ShText.Button) }
                }
            }
            s.caravanMessage?.let { Text(text = it, color = ShColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }

            if (isAdmin) {
                Section(stringResource(ru.shturman.R.string.s_f42d869a))
                Button(
                    onClick = onOpenAdmin,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
                ) { Text(stringResource(ru.shturman.R.string.s_1abf35b1), style = ShText.Small) }
            }

            Section(stringResource(ru.shturman.R.string.s_0899eda1))
            SwitchRow(
                "Участвовать в общем обучении",
                "Уходят только обезличенные данные о дорогах. Ваши координаты — никогда.",
                s.shareData, vm::setShareData
            )
            Spacer(Modifier.height(6.dp))
            Text(text = stringResource(ru.shturman.R.string.s_0f3468a4), color = ShColors.TextSecondary, fontSize = 13.sp)

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = vm::syncNow,
                enabled = !s.syncing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
            ) {
                Text(text = if (s.syncing) "Синхронизирую…" else "Синхронизировать", style = ShText.Button)
            }
            s.syncMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = it, color = ShColors.TextSecondary, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Button(
                onClick = {
                    val sum = "маршрутов: ${s.options.size}, навигация: ${s.navigating}, данные готовы: ${s.dataReady}" +
                        ", ошибка данных: ${s.dataError}, синхр.: ${s.syncMessage}, караван: ${s.caravanMessage}" +
                        ", голос: ${s.voiceMessage}, состояние голоса: ${s.voiceStatus}, услышано: ${s.heardText}, аккаунт: ${user.email}"
                    ru.shturman.Report.send(ctx, sum)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text(stringResource(ru.shturman.R.string.s_d995e9c5), style = ShText.Small) }

            Section(stringResource(ru.shturman.R.string.s_e2ed7ad6))
            val ctxN = androidx.compose.ui.platform.LocalContext.current
            var nTrip by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ru.shturman.service.AutoPrefs.tripNotice(ctxN)) }
            var nQuiet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ru.shturman.service.AutoPrefs.quietHours(ctxN)) }
            var nBt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ru.shturman.service.AutoPrefs.btAutostart(ctxN)) }
            var nBtStop by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ru.shturman.service.AutoPrefs.btAutostop(ctxN)) }
            var nCharge by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ru.shturman.service.AutoPrefs.chargeAutostart(ctxN)) }
            var btName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ru.shturman.service.AutoPrefs.btName(ctxN)) }
            var btList by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<Pair<String, String>>?>(null) }
            SwitchRow(stringResource(ru.shturman.R.string.s_85e8c0f1), stringResource(ru.shturman.R.string.s_76e7af47), nTrip) { nTrip = it; ru.shturman.service.AutoPrefs.set(ctxN, "notif_trip", it) }
            SwitchRow(stringResource(ru.shturman.R.string.s_c1c10eb5), stringResource(ru.shturman.R.string.s_63e3c45e), nQuiet) { nQuiet = it; ru.shturman.service.AutoPrefs.set(ctxN, "quiet_hours", it) }
            fun loadBonded() {
                btList = runCatching {
                    @Suppress("MissingPermission")
                    (ctxN.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager)
                        .adapter?.bondedDevices?.map { (it.name ?: it.address) to it.address } ?: emptyList()
                }.getOrDefault(emptyList())
            }
            val btPerm = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { ok -> if (ok) loadBonded() }
            SwitchRow(stringResource(ru.shturman.R.string.s_1d63dbdf), stringResource(ru.shturman.R.string.s_ce0470a5), nBt) { nBt = it; ru.shturman.service.AutoPrefs.set(ctxN, "bt_autostart", it) }
            SwitchRow(stringResource(ru.shturman.R.string.s_7ef4135a), stringResource(ru.shturman.R.string.s_4cd9559c), nBtStop) { nBtStop = it; ru.shturman.service.AutoPrefs.set(ctxN, "bt_autostop", it) }
            Text(text = "Bluetooth авто: ${btName ?: "не выбрано"}", color = ShColors.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= 31 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(ctxN, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) btPerm.launch(android.Manifest.permission.BLUETOOTH_CONNECT) else loadBonded()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text(stringResource(ru.shturman.R.string.s_05e54559), style = ShText.Small) }
            btList?.let { list ->
                if (list.isEmpty()) Text(stringResource(ru.shturman.R.string.s_120f7f9b), color = ShColors.TextSecondary, fontSize = 13.sp)
                list.forEach { (name, addr) ->
                    TextButton(
                        onClick = { ru.shturman.service.AutoPrefs.setAddress(ctxN, addr, name); btName = name; btList = null },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text(name, color = ShColors.Accent, style = ShText.Small) }
                }
            }
            SwitchRow(stringResource(ru.shturman.R.string.s_d36f16c1), stringResource(ru.shturman.R.string.s_6789d352), nCharge) { nCharge = it; ru.shturman.service.AutoPrefs.set(ctxN, "charge_autostart", it) }

            Section(stringResource(ru.shturman.R.string.s_b230ab07))
            var backupMsg by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            val backupScope = androidx.compose.runtime.rememberCoroutineScope()
            val ctxB = androidx.compose.ui.platform.LocalContext.current
            if (backupMsg.isNotBlank()) Text(text = backupMsg, color = ShColors.TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { backupMsg = "Сохраняю…"; backupScope.launch { backupMsg = vm.backupSettings() } },
                    modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
                ) { Text(stringResource(ru.shturman.R.string.s_85ff0b56), style = ShText.Small) }
                Button(
                    onClick = { backupMsg = "Восстанавливаю…"; backupScope.launch { backupMsg = vm.restoreSettings() } },
                    modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
                ) { Text(stringResource(ru.shturman.R.string.s_f5682b32), style = ShText.Small) }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val link = vm.locationLink()
                    if (link == null) backupMsg = "Положение пока не определено" else runCatching {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "Я здесь: $link")
                        }
                        ctxB.startActivity(android.content.Intent.createChooser(send, "Поделиться местоположением").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text(stringResource(ru.shturman.R.string.s_1db3ddfa), style = ShText.Small) }

            Section(stringResource(ru.shturman.R.string.s_6ac9c557))
            var statsText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(vm.statsText()) }
            Text(text = statsText, color = ShColors.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            val ctxShare = androidx.compose.ui.platform.LocalContext.current
            Button(
                onClick = {
                    runCatching {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Поездки Штурман (CSV)")
                            putExtra(android.content.Intent.EXTRA_TEXT, vm.exportText())
                        }
                        ctxShare.startActivity(android.content.Intent.createChooser(send, "Экспорт поездок").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text(stringResource(ru.shturman.R.string.s_916157e7), style = ShText.Small) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.clearTrips(); statsText = vm.statsText() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text(stringResource(ru.shturman.R.string.s_422a09c7), style = ShText.Small) }

            Section(stringResource(ru.shturman.R.string.s_fb4ac830))
            var refuelList by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(vm.refuelsText()) }
            if (refuelList.isNotBlank()) Text(text = refuelList, color = ShColors.TextSecondary, fontSize = 13.sp)
            var rfPlace by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            var rfLiters by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            var rfPrice by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            Spacer(Modifier.height(6.dp))
            TextField(
                value = rfPlace, onValueChange = { rfPlace = it.take(40) }, label = { Text(stringResource(ru.shturman.R.string.s_a3c3bce3), fontSize = 12.sp) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ShColors.SearchField, unfocusedContainerColor = ShColors.SearchField,
                    focusedTextColor = ShColors.TextPrimary, unfocusedTextColor = ShColors.TextPrimary,
                    focusedLabelColor = ShColors.TextSecondary, unfocusedLabelColor = ShColors.TextSecondary,
                    cursorColor = ShColors.Accent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Литры", rfLiters, Modifier.weight(1f)) { rfLiters = it }
                NumberField("Цена за литр", rfPrice, Modifier.weight(1f)) { rfPrice = it }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    val l = rfLiters.replace(',', '.').toDoubleOrNull()
                    val p = rfPrice.replace(',', '.').toDoubleOrNull()
                    if (l != null && p != null) {
                        vm.addRefuel(rfPlace, l, p)
                        refuelList = vm.refuelsText(); rfLiters = ""; rfPrice = ""; rfPlace = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text(stringResource(ru.shturman.R.string.s_d2e77ad1), style = ShText.Small) }

            Section(stringResource(ru.shturman.R.string.s_57d32d98))
            var svcKm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(vm.serviceIntervalKm.takeIf { it > 0 }?.toString() ?: "") }
            Text(text = stringResource(ru.shturman.R.string.s_1d36c01c), color = ShColors.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            NumberField("Интервал ТО, км", svcKm, Modifier.fillMaxWidth(), integer = true) {
                svcKm = it.filter { c -> c.isDigit() }.take(6)
                vm.serviceIntervalKm = svcKm.toIntOrNull() ?: 0
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { vm.markServiceDone() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text(stringResource(ru.shturman.R.string.s_e639e8c2), style = ShText.Small) }

            Section(stringResource(ru.shturman.R.string.s_145f378b))
            var cacheText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            var resetArmed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                cacheText = "Временные файлы: ${"%.1f".format(java.util.Locale.US, vm.cacheSizeBytes() / 1048576.0)} МБ"
            }
            Text(text = cacheText, color = ShColors.TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    val freed = vm.clearCache()
                    cacheText = "Освобождено ${"%.1f".format(java.util.Locale.US, freed / 1048576.0)} МБ"
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text(stringResource(ru.shturman.R.string.s_abe64288), style = ShText.Small) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (resetArmed) { vm.resetSettings(); resetArmed = false } else resetArmed = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text(if (resetArmed) "Нажмите ещё раз: сбросить настройки" else "Сбросить настройки", style = ShText.Small) }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(ru.shturman.R.string.s_6d4183b7), color = ShColors.TextSecondary, style = ShText.Small)
            }
        }
    }
}

private fun trimNumber(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

@Composable
private fun Divider() {
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(ShColors.Divider))
    Spacer(Modifier.height(12.dp))
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
            colors = SwitchDefaults.colors(
                checkedThumbColor = ShColors.Background,
                checkedTrackColor = ShColors.Accent
            )
        )
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
private fun NumberField(label: String, value: String, modifier: Modifier, integer: Boolean = false, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.NumberPassword else KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ShColors.SearchField,
            unfocusedContainerColor = ShColors.SearchField,
            focusedTextColor = ShColors.TextPrimary,
            unfocusedTextColor = ShColors.TextPrimary,
            focusedLabelColor = ShColors.TextSecondary,
            unfocusedLabelColor = ShColors.TextSecondary,
            cursorColor = ShColors.Accent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = modifier
    )
}
