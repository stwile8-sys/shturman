package ru.shturman.routing

/** Короткая сводка о приёме GPS и привязке к маршруту: попадает в «Отправить отчёт». */
object Diag {
    @Volatile var fixCount = 0
    @Volatile var lastFixAt = 0L
    /** Последнее положение с GPS (для расстояний в поиске). */
    @Volatile var lastPos: LatLon? = null
    @Volatile var lastAccuracy = -1f
    @Volatile var lastProvider = ""
    @Volatile var restarts = 0
    @Volatile var snapOffset = -1.0
    @Volatile var snapIndex = -1
    @Volatile var snapMisses = 0

    // Что приходило от телефона до фильтров — чтобы по отчёту было видно, почему положение не принято
    @Volatile var rawCount = 0
    @Volatile var rejectedCount = 0
    @Volatile var lastRejectedAccuracy = -1f
    @Volatile var satSeen = -1
    @Volatile var satUsed = -1
    @Volatile var firstFixMs = -1L
    @Volatile var permission = "?"
    @Volatile var locationOn = true
    @Volatile var providers = ""
    @Volatile var googleServices = false
    @Volatile var lastError = ""

    /** Есть ли живое положение: последнее принято не раньше чем maxAgeMs назад. */
    fun hasLiveFix(maxAgeMs: Long = 10_000L): Boolean =
        lastFixAt != 0L && System.currentTimeMillis() - lastFixAt <= maxAgeMs

    fun text(): String {
        val ago = if (lastFixAt == 0L) "ни разу" else "${(System.currentTimeMillis() - lastFixAt) / 1000} с назад"
        val sats = if (satSeen < 0) "нет данных" else "видно $satSeen, в расчёте $satUsed"
        val first = if (firstFixMs < 0) "нет" else "${firstFixMs / 1000} с"
        return "GPS: принято $fixCount, последнее $ago, точность $lastAccuracy м, источник $lastProvider, перезапусков приёма $restarts; " +
            "сырых $rawCount, отброшено $rejectedCount (последняя точность ${lastRejectedAccuracy} м), спутники: $sats, первое положение за $first; " +
            "разрешение: $permission, геолокация ${if (locationOn) "вкл" else "выкл"}, источники: $providers, службы Google ${if (googleServices) "есть" else "нет"}" +
            (if (lastError.isNotEmpty()) ", ошибка: $lastError" else "") + "; " +
            "маршрут: отклонение $snapOffset м, вершина $snapIndex, промахов привязки $snapMisses"
    }
}
