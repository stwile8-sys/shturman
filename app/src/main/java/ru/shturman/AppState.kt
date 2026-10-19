package ru.shturman

/** Что сейчас происходит с приложением — чтобы фоновые задачи (например, автообновление) не мешали. */
object AppState {
    /** Экран приложения открыт. */
    @Volatile var foreground = false

    /** Идёт поездка по маршруту. */
    @Volatile var navigating = false
}
