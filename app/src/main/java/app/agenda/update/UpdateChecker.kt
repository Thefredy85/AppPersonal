package app.agenda.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: Int, val pageUrl: String, val apkName: String?)

/**
 * Busca en GitHub la última versión publicada de la agenda (API pública, sin cuenta).
 * Si es mayor que la instalada, devuelve sus datos para ofrecer la descarga en Chrome.
 * Se consulta como mucho cada 6 horas, salvo que se pida a mano.
 */
object UpdateChecker {
    private const val LATEST = "https://api.github.com/repos/Thefredy85/AppPersonal/releases/latest"
    const val RELEASES_PAGE = "https://github.com/Thefredy85/AppPersonal/releases/latest"
    private const val MIN_INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun installedVersion(context: Context): Int =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName?.toInt() }.getOrNull() ?: 0

    suspend fun check(context: Context, force: Boolean = false): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("last_check", 0L) < MIN_INTERVAL_MS) return@withContext Result.success(null)
        runCatching {
            val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000; readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "AppAgenda")
            }
            try {
                if (conn.responseCode != 200) error("GitHub respondió ${conn.responseCode}")
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                prefs.edit().putLong("last_check", now).apply()
                // Etiquetas "v1", "v2"… (las de prueba como "v0.1.2" no cuentan).
                val remote = Regex("^v(\\d+)$").find(json.optString("tag_name"))?.groupValues?.get(1)?.toInt() ?: return@runCatching null
                if (remote <= installedVersion(context)) return@runCatching null
                val assets = json.optJSONArray("assets")
                val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it).optString("name") }.firstOrNull { it.endsWith(".apk") }
                UpdateInfo(remote, json.optString("html_url", RELEASES_PAGE), apk)
            } finally { conn.disconnect() }
        }
    }

    /** Abre la página de descarga en el navegador (Chrome). */
    fun openDownloadPage(context: Context, url: String = RELEASES_PAGE) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
