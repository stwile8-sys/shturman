package ru.shturman.routing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Понятное сообщение об ошибке связи вместо «Network Error» и названий классов. */
fun friendlyNetError(e: Throwable): String {
    var t: Throwable? = e
    while (t != null) {
        when (t) {
            is UnknownHostException, is ConnectException -> return "Нет интернета. Проверьте связь и попробуйте ещё раз."
            is SocketTimeoutException -> return "Сервер не отвечает. Связь слабая, попробуйте ещё раз."
        }
        val name = t.javaClass.simpleName
        if (name.contains("Unavailable") || name.contains("FirebaseNetwork")) return "Сервер сейчас недоступен. Попробуем позже."
        t = t.cause?.takeIf { it !== t }
    }
    val m = e.message?.take(100)
    return if (m.isNullOrBlank()) "Не получилось. Попробуйте ещё раз." else m
}

/**
 * Выполняет действие до [attempts] раз с паузой между попытками (по умолчанию 3 попытки).
 * Отмену корутины не глотает. Ошибки программы (не связи) повторять не имеет смысла, их пробрасывает сразу.
 */
suspend fun <T> withRetry(attempts: Int = 3, pauseMs: Long = 800, block: suspend () -> T): T {
    var last: Throwable? = null
    for (i in 1..attempts) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            last = e
            if (i < attempts) delay(pauseMs * i)
        }
    }
    throw last ?: IOException("Нет ответа")
}
