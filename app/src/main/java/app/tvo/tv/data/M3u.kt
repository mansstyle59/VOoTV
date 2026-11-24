package app.tvo.tv.data

import app.tvo.tv.Tvo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Listes M3U de l'utilisateur (chaînes avec leurs logos), vues comme des add-ons.
 * Add-on url: "m3u:<adresse de la liste>"; ids des chaînes: "m3u:<hash>:<slug>".
 * Les rangées viennent des « group-title » de la liste (ou d'un classement automatique).
 */
object M3u {
    const val PREFIX = "m3u:"
    data class Channel(val id: String, val name: String, val logo: String?, val url: String, val group: String, val chno: Int, val headers: Map<String, String>)
    private val cache = ConcurrentHashMap<String, List<Channel>>()

    fun isM3uInput(s: String) = s.startsWith(PREFIX) || Regex("\\.m3u8?(\\?|$)|get\\.php|type=m3u", RegexOption.IGNORE_CASE).containsMatchIn(s.trim())
    private fun listUrl(addonUrl: String) = addonUrl.removePrefix(PREFIX)
    private fun hash(url: String) = Integer.toHexString(url.hashCode())
    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    suspend fun channels(addonUrl: String): List<Channel> = cache[addonUrl] ?: withContext(Dispatchers.IO) {
        val url = listUrl(addonUrl)
        val text = Tvo.http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { it.body?.string() ?: "" }
        parse(text, hash(url)).also { cache[addonUrl] = it }
    }

    fun parse(text: String, h: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var info: String? = null
        val headers = mutableMapOf<String, String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> { info = line; headers.clear() }
                line.startsWith("#EXTVLCOPT:http-user-agent=") -> headers["User-Agent"] = line.substringAfter("=")
                line.startsWith("#EXTVLCOPT:http-referrer=") -> headers["Referer"] = line.substringAfter("=")
                line.isNotEmpty() && !line.startsWith("#") && info != null -> {
                    val i = info!!
                    fun attr(n: String) = Regex("$n=\"([^\"]*)\"").find(i)?.groupValues?.get(1)?.ifBlank { null }
                    val name = i.substringAfterLast(",").replace(Regex("[ⒼⓎⒹⓉⓈ]"), "").trim().ifBlank { attr("tvg-name") ?: "Chaîne" }
                    val group = attr("group-title") ?: ""
                    val num = attr("tvg-chno")?.toIntOrNull() ?: Regex("^(\\d+)\\s*[-.:]").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 9999
                    val clean = name.replace(Regex("^\\d+\\s*[-.:]\\s*"), "")
                    out += Channel("$PREFIX$h:${slug(clean)}", clean, attr("tvg-logo"), line, group, num, headers.toMap())
                    info = null
                }
            }
        }
        return out.distinctBy { it.id }
    }

    /** Category for a channel: the list's group if it has several, otherwise an automatic one. */
    private fun groupsOf(list: List<Channel>): Boolean = list.map { it.group }.filter { it.isNotBlank() }.distinct().size > 1
    private fun catOf(c: Channel, useGroups: Boolean) = if (useGroups) c.group.ifBlank { "Autres" } else FreeTv.categoryOf(c.name)

    suspend fun install(input: String, name: String? = null): Addon {
        val url = input.trim().removePrefix(PREFIX)
        val addonUrl = PREFIX + url
        cache.remove(addonUrl)
        val list = runCatching { channels(addonUrl) }.getOrElse {
            if (url.contains("mafreebox")) throw IllegalStateException("Freebox introuvable : connecte-toi au Wi-Fi de ta Freebox.") else throw it
        }
        if (list.isEmpty()) throw IllegalStateException("Liste vide ou illisible")
        val useGroups = groupsOf(list)
        val cats = if (useGroups) list.map { it.group.ifBlank { "Autres" } }.distinct().take(40).map { it to it }
        else FreeTv.CATEGORIES.filter { (id, _) -> list.any { FreeTv.categoryOf(it.name) == id } }
        return Addon(addonUrl, AddonManifest(
            id = "tvo.m3u.${hash(url)}", name = name ?: if (url.contains("mafreebox")) "Freebox TV (${list.size} chaînes)" else "Ma liste M3U (${list.size} chaînes)", version = "1",
            description = url, types = listOf("tv"),
            resources = listOf(AddonResource("catalog"), AddonResource("meta"), AddonResource("stream")),
            catalogs = cats.map { (id, title) -> CatalogDef("tv", id, title) } +
                CatalogDef("tv", "search", "Chaînes M3U", listOf(ExtraDef("search", isRequired = true))),
            idPrefixes = listOf("$PREFIX${hash(url)}:"),
        ))
    }

    private fun preview(c: Channel) = MetaPreview(c.id, "tv", c.name, poster = c.logo, logo = c.logo,
        description = "Chaîne en direct" + if (c.group.isNotBlank()) " · ${c.group}" else "", genres = listOf("En direct"),
        releaseInfo = if (c.chno < 9999) "Canal ${c.chno}" else null)

    suspend fun catalog(addon: Addon, catId: String, extra: Map<String, String>): List<MetaPreview> {
        if ((extra["skip"]?.toIntOrNull() ?: 0) > 0) return emptyList()
        val list = channels(addon.url)
        val useGroups = groupsOf(list)
        val q = extra["search"]?.lowercase()
        return list.filter { if (q != null) it.name.lowercase().contains(q) else catOf(it, useGroups) == catId }
            .sortedWith(compareBy({ it.chno }, { it.name })).map(::preview)
    }

    private fun addonFor(id: String) = Tvo.store.addons.value.firstOrNull { a -> a.url.startsWith(PREFIX) && id.startsWith("$PREFIX${hash(listUrl(a.url))}:") }

    suspend fun meta(id: String): MetaDetail? {
        val a = addonFor(id) ?: return null
        return channels(a.url).firstOrNull { it.id == id }?.let { MetaDetail(preview(it), null, emptyList(), emptyList(), emptyList(), null) }
    }

    suspend fun streams(id: String): List<StreamItem> {
        val a = addonFor(id) ?: return emptyList()
        val c = channels(a.url).firstOrNull { it.id == id } ?: return emptyList()
        val web = Regex("youtube\\.com|youtu\\.be|dailymotion|twitch\\.tv", RegexOption.IGNORE_CASE).containsMatchIn(c.url)
        return listOf(StreamItem(a.manifest.name, c.name, if (web) "S'ouvre dans l'appli de la chaîne" else "Direct",
            if (web) null else c.url, if (web) c.url else null, null, c.headers, emptyList(), null, 0))
    }
}
