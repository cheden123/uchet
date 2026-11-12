package com.uchet.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновление из программы: последний релиз GitHub (его делает CI, см.
 * .github/workflows/android.yml) → сравнение номера сборки → скачивание APK.
 *
 * Номер сборки = число коммитов (versionCode), тег релиза — "build-<номер>".
 * Установку запускает экран (нужен Context и разрешение «установка из этого источника»).
 */
class UpdateChecker(
    private val repo: String,
    private val apiBase: String = "https://api.github.com"
) {
    /** Доступная сборка. */
    data class Release(val versionCode: Int, val title: String, val notes: String, val apkUrl: String, val apkSize: Long)

    /**
     * @return релиз новее [currentVersionCode] или null, если обновлений нет.
     * @throws IOException нет сети / GitHub недоступен.
     */
    suspend fun findUpdate(currentVersionCode: Int): Release? = withContext(Dispatchers.IO) {
        val json = httpGet("$apiBase/repos/$repo/releases/latest") ?: return@withContext null
        parseRelease(json)?.takeIf { it.versionCode > currentVersionCode }
    }

    /** Скачать APK в [outFile]; [onProgress] — доля 0..1 (или -1, если размер неизвестен). */
    suspend fun download(release: Release, outFile: File, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        outFile.parentFile?.mkdirs()
        val conn = open(release.apkUrl)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Ошибка загрузки: HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            val tmp = File(outFile.path + ".part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(if (total > 0) done.toFloat() / total else -1f)
                    }
                }
            }
            if (total > 0 && tmp.length() != total) throw IOException("APK скачан не полностью")
            if (!tmp.renameTo(outFile)) throw IOException("Не удалось сохранить APK")
            outFile
        } finally {
            conn.disconnect()
        }
    }

    /** null — релизов ещё нет (404). */
    private fun httpGet(url: String): String? {
        val conn = open(url)
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            return when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> conn.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub ответил HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true // APK отдаётся через редирект на CDN GitHub
        }

    companion object {
        private const val TAG_PREFIX = "build-"

        /**
         * Разбор ответа /releases/latest. null — релиз не наш (тег не build-N) или без APK.
         * Отдельная функция — чтобы проверить тестом без сети.
         */
        fun parseRelease(json: String): Release? = try {
            val o = JSONObject(json)
            val code = o.optString("tag_name").removePrefix(TAG_PREFIX).toIntOrNull()
            val assets = o.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
            if (code == null || apk == null || !o.optString("tag_name").startsWith(TAG_PREFIX)) {
                null
            } else {
                Release(
                    versionCode = code,
                    title = o.optString("name").ifBlank { "Сборка $code" },
                    notes = o.optString("body"),
                    apkUrl = apk.getString("browser_download_url"),
                    apkSize = apk.optLong("size", -1L)
                )
            }
        } catch (e: JSONException) {
            null
        }
    }
}
