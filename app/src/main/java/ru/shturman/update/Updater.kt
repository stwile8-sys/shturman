package ru.shturman.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.shturman.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Новая версия на GitHub: номер сборки, ссылка на APK и размер. */
class UpdateInfo(val build: Int, val url: String, val size: Long)

/**
 * Обновление приложения. При входе смотрим последний релиз на GitHub (его создаёт сборка).
 * Если номер сборки больше нашего, предлагаем скачать и установить.
 */
object Updater {
    private const val RELEASES = "https://api.github.com/repos/stwile8-sys/shturman/releases?per_page=20"

    /** Возвращает информацию о более новой версии или null, если обновлений нет или нет сети. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(RELEASES).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return@runCatching null
            val list = org.json.JSONArray(c.inputStream.bufferedReader().readText())
            // Среди релизов есть и дорожные данные (roaddata-...), поэтому берём самую большую сборку build-N
            var best: UpdateInfo? = null
            for (r in 0 until list.length()) {
                val json = list.getJSONObject(r)
                val build = json.optString("tag_name").substringAfter("build-", "").toIntOrNull() ?: continue
                if (build <= BuildConfig.BUILD_NUMBER || build <= (best?.build ?: 0)) continue
                val assets = json.optJSONArray("assets") ?: continue
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        best = UpdateInfo(build, a.getString("browser_download_url"), a.optLong("size", 0L))
                        break
                    }
                }
            }
            best
        }.getOrNull()
    }

    /** Папка для скачанных сборок. */
    fun dir(context: Context): File = File(context.cacheDir, "updates").apply { mkdirs() }

    /** Уже полностью скачанная сборка новее установленной, если есть. */
    fun readyFile(context: Context): File? = dir(context).listFiles()
        ?.filter { it.name.endsWith(".apk") }
        ?.maxByOrNull { it.name.substringAfter("-", "").substringBefore(".").toIntOrNull() ?: 0 }
        ?.takeIf { (it.name.substringAfter("-", "").substringBefore(".").toIntOrNull() ?: 0) > BuildConfig.BUILD_NUMBER }

    /**
     * Скачивает APK во временную папку. onProgress получает долю от 0 до 1.
     * Если эта сборка уже скачана целиком (например, в фоне), второй раз не качает.
     */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = dir(context)
        val file = File(dir, "apex-${info.build}.apk")
        if (file.exists() && info.size > 0 && file.length() == info.size) {
            onProgress(1f)
            return@withContext file
        }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val part = File(dir, "apex-${info.build}.part")
        val c = URL(info.url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        if (c.responseCode != 200) throw IllegalStateException("Сервер ответил ${c.responseCode}")
        val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
        c.inputStream.use { input ->
            part.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        if (total > 0 && part.length() != total) {
            part.delete()
            throw IllegalStateException("Файл скачался не полностью")
        }
        if (!part.renameTo(file)) throw IllegalStateException("Не удалось сохранить файл обновления")
        file
    }

    /** Можно ли приложению ставить обновления. Если нет, открываем нужную настройку Android. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(context: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    /** Запускает установку скачанного APK системным установщиком. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }
}
