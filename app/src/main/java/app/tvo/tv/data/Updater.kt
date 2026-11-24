package app.tvo.tv.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.tvo.tv.BuildConfig
import app.tvo.tv.Tvo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import java.io.File
import java.io.IOException

object Updater {
    data class Release(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

    fun cleanNotes(b: String): String = b.lines().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("- TVO-") && !it.startsWith("Co-Authored-By") && !it.startsWith("Claude-Session") && !it.startsWith("http") }
        .joinToString("\n")

    /** Nouveautés des versions publiées entre [from] (exclu) et [to] (inclus), la plus récente d'abord. */
    suspend fun notesSince(from: Int, to: Int): List<Pair<String, String>> = runCatching {
        val arr = Tvo.client.getJsonArray("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=30")
        arr.mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val tag = o.s("tag_name") ?: return@mapNotNull null
            val code = tag.substringAfterLast('.').filter { it.isDigit() }.toIntOrNull() ?: return@mapNotNull null
            val n = cleanNotes(o.s("body") ?: "")
            if (code in (from + 1)..to && n.isNotBlank()) Triple(code, tag.removePrefix("v"), n) else null
        }.sortedByDescending { it.first }.map { it.second to it.third }
    }.getOrDefault(emptyList())

    suspend fun check(): Release? = runCatching {
        val o = Tvo.client.getJson("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
        val tag = o.s("tag_name") ?: return@runCatching null
        val code = tag.substringAfterLast('.').filter { it.isDigit() }.toIntOrNull() ?: return@runCatching null
        val apk = o.arr("assets")?.mapNotNull { it as? JsonObject }
            // Uniquement l'APK télé (« TVO-TV… ») : jamais la version téléphone.
            ?.let { list -> list.firstOrNull { it.s("name") == "TVO-TV.apk" }
                ?: list.firstOrNull { it.s("name")?.let { n -> n.startsWith("TVO-TV") && n.endsWith(".apk") } == true } }
            ?.s("browser_download_url")
            ?: return@runCatching null
        if (code > BuildConfig.VERSION_CODE) Release(code, tag.removePrefix("v"), apk, o.s("body") ?: "") else null
    }.getOrNull()

    suspend fun download(ctx: Context, url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "tvo-update.apk")
        Tvo.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("Réponse vide")
            val total = body.contentLength().coerceAtLeast(1)
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        out
    }

    /** Returns null when the installer opened, or a message to show. */
    fun install(ctx: Context, file: File): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            return try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "Autorise TVO à installer des applis, puis appuie à nouveau sur Installer."
            } catch (e: ActivityNotFoundException) {
                "Active « Sources inconnues » pour TVO dans les paramètres de la TV, puis réessaie."
            }
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        return try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            null
        } catch (e: ActivityNotFoundException) {
            "Impossible d'ouvrir l'installateur sur cet appareil."
        }
    }
}
