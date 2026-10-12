package ru.shturman

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Отслеживание ошибок. Вылеты и пойманные ошибки уходят в Firebase Crashlytics (консоль Firebase → Crashlytics),
 * а последние события хранятся в памяти и попадают в «Отправить отчёт разработчику».
 */
object Errors {
    private val recent = ArrayDeque<String>()

    /** Записывает пойманную ошибку: приложение не падает, но разработчик её увидит. */
    fun log(where: String, e: Throwable) {
        synchronized(recent) {
            recent.addLast("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} $where: ${e.javaClass.simpleName} ${e.message ?: ""}")
            while (recent.size > 30) recent.removeFirst()
        }
        runCatching {
            val c = FirebaseCrashlytics.getInstance()
            c.log(where)
            c.recordException(e)
        }
    }

    fun note(message: String) {
        runCatching { FirebaseCrashlytics.getInstance().log(message) }
    }

    fun setUser(uid: String?) {
        runCatching { FirebaseCrashlytics.getInstance().setUserId(uid ?: "") }
    }

    fun text(): String = synchronized(recent) { recent.joinToString("\n") }
}
