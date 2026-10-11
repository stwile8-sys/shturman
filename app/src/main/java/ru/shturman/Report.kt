package ru.shturman

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File

/** Собирает текстовый отчёт о состоянии приложения и отправляет его через «Поделиться». */
object Report {
    fun send(context: Context, summary: String) {
        val text = build(context, summary)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Отчёт Штурман")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Отправить отчёт").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun build(context: Context, summary: String): String {
        val sb = StringBuilder()
        sb.append("=== Штурман: отчёт ===\n")
        sb.append("Телефон: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n")
        runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            sb.append("Версия: ${pi.versionName}, сборка от ${java.util.Date(pi.lastUpdateTime)}\n")
        }
        sb.append("\n--- Состояние ---\n").append(summary).append("\n")
        val crash = runCatching { File(context.filesDir, "crash_last.txt").takeIf { it.exists() }?.readText() }.getOrNull()
        if (crash != null) sb.append("\n--- Последний вылет ---\n").append(crash.take(4000)).append("\n")
        val log = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "400", "-v", "time", "*:W"))
            p.inputStream.bufferedReader().readText()
        }.getOrNull()
        if (!log.isNullOrBlank()) sb.append("\n--- Журнал (предупреждения и ошибки) ---\n").append(log.takeLast(12000))
        return sb.toString()
    }
}
