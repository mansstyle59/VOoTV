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
    data class Release(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String, val important: Boolean = true)

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

    /** D'où vient l'appli : un magasin (Google Play, Amazon) gère lui-même les mises à jour, on ne doit pas le faire à sa place. */
    enum class Source { PLAY, AMAZON, SIDELOAD }
    fun source(ctx: Context): Source {
        val pm = ctx.packageManager
        val inst = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getInstallSourceInfo(ctx.packageName).installingPackageName
            else @Suppress("DEPRECATION") pm.getInstallerPackageName(ctx.packageName)
        }.getOrNull()
        return when (inst) { "com.android.vending", "com.google.android.feedback" -> Source.PLAY; "com.amazon.venezia" -> Source.AMAZON; else -> Source.SIDELOAD }
    }
    fun isFireTv(ctx: Context) = Build.MANUFACTURER.equals("Amazon", true) || ctx.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
    fun isFreebox() = listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE).any { it?.contains("freebox", true) == true || it.equals("Free", true) }
    /** Où activer l'installation pour TVO, selon l'appareil. */
    fun unknownSourcesHelp(ctx: Context): String = when {
        isFireTv(ctx) -> "Sur Fire TV : Paramètres › Ma Fire TV › Options pour les développeurs › Installer des applis inconnues › TVO : Activé. (Si « Options pour les développeurs » est absent : Ma Fire TV › À propos, appuie 7 fois sur le nom de l'appareil.)"
        isFreebox() -> "Sur la Freebox : Paramètres › Applications › Sécurité et restrictions › Sources inconnues › TVO : Activé."
        else -> "Paramètres › Applications › Sécurité et restrictions › Sources inconnues › TVO : Activé."
    }

    suspend fun check(): Release? = runCatching {
        // Installée depuis Google Play ou l'Amazon Appstore : c'est le magasin qui met à jour (leurs règles l'imposent)
        if (source(Tvo.app) != Source.SIDELOAD) return@runCatching null
        // On prend la version la plus haute parmi les dernières publications (et non « latest »,
        // qui peut pointer sur une version plus ancienne si deux compilations finissent dans le désordre).
        val arr = Tvo.client.getJsonArray("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=15")
        arr.mapNotNull { it as? JsonObject }.filter { it.s("draft") != "true" }.mapNotNull { o ->
            val tag = o.s("tag_name") ?: return@mapNotNull null
            val code = tag.substringAfterLast('.').filter { it.isDigit() }.toIntOrNull() ?: return@mapNotNull null
            val apk = o.arr("assets")?.mapNotNull { it as? JsonObject }
                // Uniquement l'APK télé (« TVO-TV… ») : jamais la version téléphone.
                ?.let { list -> list.firstOrNull { it.s("name") == "TVO-TV.apk" }
                    ?: list.firstOrNull { it.s("name")?.let { n -> n.startsWith("TVO-TV") && n.endsWith(".apk") } == true } }
                ?.s("browser_download_url") ?: return@mapNotNull null
            Release(code, tag.removePrefix("v"), apk, o.s("body") ?: "")
        }.maxByOrNull { it.versionCode }?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
            // Importante = la version installée est plus ancienne que « minTv » (docs/update.json) : correctif, lecteur vidéo…
            // Sinon (catalogues, petits ajustements) : rien à installer, les catalogues se mettent à jour seuls.
            ?.let { r -> r.copy(important = BuildConfig.VERSION_CODE < minTv()) }
    }.getOrNull()

    private suspend fun minTv(): Int = runCatching {
        Tvo.client.getJson("https://raw.githubusercontent.com/${BuildConfig.UPDATE_REPO}/main/docs/update.json").i("minTv") ?: 0
    }.getOrDefault(0)

    suspend fun download(ctx: Context, url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        if (dir.usableSpace in 1 until 250L * 1024 * 1024) throw IOException("espace de stockage insuffisant sur la TV (libère de la place puis réessaie)")
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
                "Autorise TVO à installer des applis, puis appuie à nouveau sur Installer. " + unknownSourcesHelp(ctx)
            } catch (e: Exception) {
                unknownSourcesHelp(ctx) + " Puis appuie à nouveau sur Installer."
            }
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        return try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            null
        } catch (e: Exception) {
            "Impossible d'ouvrir l'installateur sur cet appareil. " + unknownSourcesHelp(ctx)
        }
    }
}
