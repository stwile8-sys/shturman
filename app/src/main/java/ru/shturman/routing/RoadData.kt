package ru.shturman.routing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Дорожные данные Псковской области для Valhalla. Скачиваются один раз из релиза на GitHub
 * (их туда выкладывает сборка «Build road data») и дальше работают без интернета.
 */
class RoadData(private val context: Context) {

    val dir: File = File(context.filesDir, "valhalla").apply { mkdirs() }
    val tiles = File(dir, "tiles.tar")
    val timezones = File(dir, "timezones.sqlite")
    val admins = File(dir, "admins.sqlite")

    fun isReady(): Boolean = tiles.length() > 0 && timezones.length() > 0

    /** Скачивает недостающие файлы. onProgress получает число от 0 до 1. */
    suspend fun download(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        // Основной файл самый большой, поэтому почти весь прогресс приходится на него
        if (tiles.length() == 0L) {
            fetch("tiles.tar", tiles, required = true) { onProgress(it * 0.95f) }
        }
        if (timezones.length() == 0L) {
            fetch("timezones.sqlite", timezones, required = true) { onProgress(0.95f + it * 0.03f) }
        }
        if (admins.length() == 0L) {
            fetch("admins.sqlite", admins, required = false) { onProgress(0.98f + it * 0.02f) }
        }
        onProgress(1f)
    }

    /**
     * Раз в неделю проверяет, не выложили ли свежие дорожные данные, и если да, тихо скачивает их
     * (только не по мобильному интернету). Так устаревшие значения (скорость, ограничения, дороги)
     * не живут вечно. Возвращает true, если файлы заменены и движок надо перезапустить.
     */
    suspend fun updateIfStale(): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("roaddata", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!isReady() || now - prefs.getLong("last_check", 0L) < WEEK_MS) return@withContext false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (cm.isActiveNetworkMetered) return@withContext false
        try {
            val conn = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val json = try {
                if (conn.responseCode != 200) return@withContext false
                JSONObject(conn.inputStream.use { it.readBytes().decodeToString() })
            } finally {
                conn.disconnect()
            }
            prefs.edit().putLong("last_check", now).apply()
            val assets = json.getJSONArray("assets")
            var remote = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name") == "tiles.tar") remote = a.getString("updated_at")
            }
            if (remote.isEmpty() || remote == prefs.getString("version", null)) {
                if (remote.isNotEmpty() && prefs.getString("version", null) == null) {
                    prefs.edit().putString("version", remote).apply()
                }
                return@withContext false
            }
            val fresh = File(dir, "tiles.tar.new")
            try {
                fetch("tiles.tar", fresh, required = true) { }
            } catch (e: Exception) {
                return@withContext false
            }
            if (!fresh.renameTo(tiles)) {
                fresh.delete()
                return@withContext false
            }
            File(dir, "tiles").deleteRecursively()
            prefs.edit().putString("version", remote).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun fetch(name: String, target: File, required: Boolean, progress: (Float) -> Unit) {
        val part = File(dir, "$name.part")
        try {
            val conn = (URL(BASE_URL + name).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode != 200) {
                    if (!required) return
                    throw FileNotFoundException("Файл $name недоступен (код ${conn.responseCode})")
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) progress(done.toFloat() / total)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (!part.renameTo(target)) throw IllegalStateException("Не удалось сохранить $name")
            progress(1f)
        } finally {
            if (part.exists()) part.delete()
        }
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        const val RELEASE_API =
            "https://api.github.com/repos/stwile8-sys/shturman/releases/tags/roaddata-pskov"
        const val BASE_URL =
            "https://github.com/stwile8-sys/shturman/releases/download/roaddata-pskov/"
    }
}
