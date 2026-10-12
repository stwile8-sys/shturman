package ru.shturman.ui

import androidx.compose.foundation.background
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

            Section("Тема оформления")
            ru.shturman.ui.themes.ThemePicker(selected = s.theme, onSelect = vm::setTheme)
            var themeSounds by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ru.shturman.ui.themes.ThemeSounds.enabled) }
            SwitchRow("Звуки темы", "Короткие звуки нажатий, начала маршрута, синхронизации и ошибок. Голос навигатора не меняется.", themeSounds) {
                themeSounds = it
                vm.setThemeSounds(it)
            }

            Section("Голос и звуки")
            SwitchRow("Голосовые подсказки", "Говорит о поворотах голосом телефона. Нужен русский голос в настройках речи Android.", s.voiceOn, vm::setVoiceOn)
            Button(
                onClick = vm::testVoice,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text("Проверить голос", style = ShText.Small) }
            s.voiceStatus?.let { Text(text = it, color = ShColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            SwitchRow("Ночной режим голоса", "С 22:00 до 6:00 голос тише, а фразы короче.", s.nightVoice, vm::setNightVoice)
            SwitchRow("Звук при превышении", "Мягкое «вжжж», громче по мере превышения скорости.", s.overspeedSound, vm::setOverspeedSound)

            Section("Съезд с маршрута")
            Text(text = "Чувствительность: как быстро навигатор решает, что вы свернули, и строит новый путь.", color = ShColors.TextSecondary, fontSize = 13.sp)
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

            Section("Экран")
            SwitchRow("Полноэкранный режим", "Скрывает системные панели Android. Вернуть их можно свайпом от края.", s.fullscreen, vm::setFullscreen)
            SwitchRow("Спутник", "Снимки со спутника вместо обычной карты.", s.satellite, vm::setSatellite)

            Section("Нижняя карточка в поездке")
            CheckRow("Проценты пути", s.cardPercent) { vm.setCardOption("percent", it) }
            CheckRow("Сколько проехали из скольких", s.cardDistance) { vm.setCardOption("distance", it) }
            CheckRow("Время прибытия", s.cardEta) { vm.setCardOption("eta", it) }
            CheckRow("Сколько осталось (время и км)", s.cardRemaining) { vm.setCardOption("remaining", it) }
            CheckRow("Расход бензина и стоимость", s.cardFuel) { vm.setCardOption("fuel", it) }

            Section("Бензин")
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

            Section("Караван")
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
                    Text("Выйти из каравана", color = ShColors.Danger, style = ShText.Small)
                }
            } else {
                Button(
                    onClick = vm::createCaravan,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
                ) { Text("Создать караван (я веду)", style = ShText.Small) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField("Код из 4 цифр", code, Modifier.weight(1f), integer = true) { code = it.filter(Char::isDigit).take(4) }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { vm.joinCaravan(code) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ShColors.Accent, contentColor = ShColors.Background)
                    ) { Text("Войти", style = ShText.Button) }
                }
            }
            s.caravanMessage?.let { Text(text = it, color = ShColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }

            if (isAdmin) {
                Section("Для владельца")
                Button(
                    onClick = onOpenAdmin,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
                ) { Text("Коды доступа", style = ShText.Small) }
            }

            Section("Приватность")
            SwitchRow(
                "Участвовать в общем обучении",
                "Уходят только обезличенные данные о дорогах. Ваши координаты — никогда.",
                s.shareData, vm::setShareData
            )
            Spacer(Modifier.height(6.dp))
            Text(text = "Вход через Google обязателен, анонимного режима нет.", color = ShColors.TextSecondary, fontSize = 13.sp)

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
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShColors.SearchField, contentColor = ShColors.TextPrimary)
            ) { Text("Отправить отчёт разработчику", style = ShText.Small) }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Выйти из аккаунта", color = ShColors.TextSecondary, style = ShText.Small)
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
