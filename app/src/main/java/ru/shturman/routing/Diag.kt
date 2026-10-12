package ru.shturman.routing

/** Короткая сводка о приёме GPS и привязке к маршруту: попадает в «Отправить отчёт». */
object Diag {
    @Volatile var fixCount = 0
    @Volatile var lastFixAt = 0L
    @Volatile var lastAccuracy = -1f
    @Volatile var lastProvider = ""
    @Volatile var restarts = 0
    @Volatile var snapOffset = -1.0
    @Volatile var snapIndex = -1
    @Volatile var snapMisses = 0

    fun text(): String {
        val ago = if (lastFixAt == 0L) "ни разу" else "${(System.currentTimeMillis() - lastFixAt) / 1000} с назад"
        return "GPS: принято $fixCount, последнее $ago, точность $lastAccuracy м, источник $lastProvider, перезапусков приёма $restarts; " +
            "маршрут: отклонение $snapOffset м, вершина $snapIndex, промахов привязки $snapMisses"
    }
}
