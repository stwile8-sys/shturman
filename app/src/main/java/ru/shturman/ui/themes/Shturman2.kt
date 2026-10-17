package ru.shturman.ui.themes

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * «Штурман 2» — скрытый режим клана APEX.
 *
 * Открывается секретной комбинацией на главном экране настроек: за 10 секунд 5 касаний по заголовку
 * «В дороге», затем 2 касания по заголовку «Система» (заголовки выглядят как обычный текст).
 * После открытия тема APEX появляется в списке тем и сразу включается, играет видео-заставка,
 * в настройках появляется скрытый раздел «Клан». Открытие сохраняется навсегда, обратно не закрывается.
 */
object Shturman2 {
    /** Ключ в настройках (settings): открыт ли режим. */
    const val PREF = "s2_unlocked"

    /** Открыт ли режим. Пока закрыт — тема APEX и скрытые функции не видны. */
    var unlocked by mutableStateOf(false)
        private set

    /** Показать видео-заставку APEX поверх приложения (после открытия и по кнопке в скрытом разделе). */
    var introPending by mutableStateOf(false)

    fun load(prefs: SharedPreferences) {
        unlocked = prefs.getBoolean(PREF, false)
    }

    fun unlock(prefs: SharedPreferences) {
        prefs.edit().putBoolean(PREF, true).apply()
        unlocked = true
    }
}

/**
 * Распознаёт секретную комбинацию касаний. Каждое касание помечается буквой надписи ('A', 'B' …);
 * комбинация сработала, когда последние касания совпали с pattern и уложились в windowMs.
 * Лишние касания до комбинации не мешают: важны только последние.
 */
class SecretTaps(private val pattern: String = "AAAAABB", private val windowMs: Long = 10_000L) {
    private val taps = ArrayDeque<Pair<Char, Long>>()

    /** Возвращает true, если этим касанием комбинация набрана. */
    fun tap(label: Char, nowMs: Long = System.nanoTime() / 1_000_000L): Boolean {
        taps.addLast(label to nowMs)
        while (taps.size > pattern.length) taps.removeFirst()
        if (taps.size < pattern.length) return false
        val typed = String(CharArray(taps.size) { taps[it].first })
        val ok = typed == pattern && nowMs - taps.first().second <= windowMs
        if (ok) taps.clear()
        return ok
    }
}
