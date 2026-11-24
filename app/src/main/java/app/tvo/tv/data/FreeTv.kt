package app.tvo.tv.data

import app.tvo.tv.Tvo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Chaînes TV gratuites et légales (liste communautaire Free-TV/IPTV sur GitHub).
 * Built-in add-on: catalog "tv", meta and stream resolved from the M3U playlist.
 */
object FreeTv {
    const val URL = "freetv://fr"
    private const val PLAYLIST = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_france.m3u8"

    val CATEGORIES = listOf("gen" to "Chaînes généralistes", "info" to "Chaînes d'info", "sport" to "Sport",
        "kids" to "Jeunesse", "local" to "Chaînes régionales et locales", "other" to "Autres chaînes")

    val addon = Addon(URL, AddonManifest(
        id = "tvo.freetv.fr", name = "Chaînes TV gratuites (France)", version = "1",
        description = "Chaînes gratuites et légales en direct (liste Free-TV/IPTV).",
        types = listOf("tv"),
        resources = listOf(AddonResource("catalog"), AddonResource("meta"), AddonResource("stream")),
        catalogs = CATEGORIES.map { (id, name) -> CatalogDef("tv", id, name) } +
            CatalogDef("tv", "search", "Chaînes TV", listOf(ExtraDef("search", isRequired = true))),
        idPrefixes = listOf("freetv:"),
    ))

    /** Sorts a channel into a category from its name (the playlist has no useful groups). */
    fun categoryOf(name: String): String {
        val n = name.lowercase().replace(Regex("^\\d+\\s*[-.:]?\\s*"), "")
        return when {
            Regex("info|news|24|bfm|lci|cnews|lcp|sénat|senat|cgtn|euronews|africanews|inter").containsMatchIn(n) -> "info"
            Regex("sport|équipe|equipe").containsMatchIn(n) -> "sport"
            Regex("gulli|kids|junior|toon|enfant").containsMatchIn(n) -> "kids"
            Regex("^(tf1|france [2-5]|m6|arte|tmc|tfx|w9|6ter|cstar|c8|nrj|rmc|tv5|chérie|cherie)").containsMatchIn(n) -> "gen"
            Regex("tv |alsace|mulhouse|bretagne|provence|lyon|paris|azur|normandie|corse|antilles|réunion|reunion").containsMatchIn(n) -> "local"
            else -> "other"
        }
    }

    data class Channel(val id: String, val name: String, val logo: String?, val url: String, val chno: Int = 999, val cat: String = "other")
    @Volatile private var channels: List<Channel>? = null

    suspend fun list(): List<Channel> = channels ?: withContext(Dispatchers.IO) {
        val text = Tvo.http.newCall(Request.Builder().url(PLAYLIST).build()).execute().use { it.body?.string() ?: "" }
        val out = mutableListOf<Channel>()
        var info: String? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> info = line
                line.isNotEmpty() && !line.startsWith("#") && info != null -> {
                    val i = info!!
                    val name = i.substringAfterLast(",").replace(Regex("[ⒼⓎⒹⓉⓈⒼ]"), "").trim()
                    val logo = Regex("tvg-logo=\"([^\"]*)\"").find(i)?.groupValues?.get(1)?.ifBlank { null }
                    val chno = Regex("tvg-chno=\"(\\d+)\"").find(i)?.groupValues?.get(1)?.toIntOrNull() ?: 999
                    out += Channel("freetv:" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'), name, logo, line, chno, categoryOf(name))
                    info = null
                }
            }
        }
        out.distinctBy { it.id }.sortedWith(compareBy({ it.chno }, { it.name })).also { channels = it }
    }

    private fun preview(c: Channel) = MetaPreview(c.id, "tv", c.name, poster = c.logo, logo = c.logo,
        description = "Chaîne en direct, gratuite.", genres = listOf("En direct"),
        releaseInfo = if (c.chno < 999) "Canal ${c.chno}" else null)

    suspend fun catalog(catId: String, extra: Map<String, String>): List<MetaPreview> {
        if ((extra["skip"]?.toIntOrNull() ?: 0) > 0) return emptyList()
        val q = extra["search"]?.lowercase()
        return list().filter { if (q != null) it.name.lowercase().contains(q) else (catId == "fr" || it.cat == catId) }.map(::preview)
    }

    suspend fun meta(id: String): MetaDetail? = list().firstOrNull { it.id == id }?.let {
        MetaDetail(preview(it), null, emptyList(), emptyList(), emptyList(), null)
    }

    suspend fun streams(id: String): List<StreamItem> {
        val c = list().firstOrNull { it.id == id } ?: return emptyList()
        val direct = Regex("\\.(m3u8|mp4|mpd)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(c.url)
        return listOf(StreamItem(addon.manifest.name, c.name, if (direct) "Direct" else "S'ouvre dans l'appli ou le site de la chaîne",
            if (direct) c.url else null, if (direct) null else c.url, null, emptyMap(), emptyList(), null, 0))
    }
}

/** Ready-to-install suggestions (checked working, legal sources only). */
object Suggestions {
    data class Item(val url: String, val name: String, val desc: String, val category: String)
    val all = listOf(
        Item("m3u:http://mafreebox.freebox.fr/freeboxtv/playlist.m3u", "Freebox TV (abonnés Free)", "Toutes les chaînes de ta Freebox. Uniquement connecté à la Freebox (Wi-Fi ou câble).", "Chaînes TV"),
        Item("https://v3-channels.strem.io/manifest.json", "YouTube", "Tes chaînes YouTube favorites", "Chaînes TV"),
        Item("https://94c8cb9f702d-tmdb-addon.baby-beamup.club/manifest.json", "The Movie Database Addon", "Catalogues TMDB (configurable en français)", "Catalogues"),
        Item("https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json", "Streaming Catalogs", "Ce qui est sur Netflix, Prime, Disney+…", "Catalogues"),
        Item("https://anime-kitsu.strem.fun/manifest.json", "Anime Kitsu", "Catalogues et fiches d'animés", "Catalogues"),
        Item("https://watchhub.strem.io/manifest.json", "WatchHub", "Liens vers les plateformes officielles où regarder", "Streaming"),
        Item("https://opensubtitles-v3.strem.io/manifest.json", "OpenSubtitles v3", "Sous-titres dans toutes les langues", "Sous-titres"),
    )
}

/** Category used to sort add-ons in the settings. */
fun Addon.category(): String {
    val m = manifest
    val res = m.resources.map { it.name }
    return when {
        m.types.any { it == "tv" || it == "channel" } || m.catalogs.any { it.type == "tv" || it.type == "channel" } -> "Chaînes TV"
        "stream" in res -> "Streaming"
        "catalog" in res || "meta" in res -> "Catalogues"
        "subtitles" in res -> "Sous-titres"
        else -> "Autres"
    }
}
val ADDON_CATEGORIES = listOf("Catalogues", "Streaming", "Chaînes TV", "Sous-titres", "Autres")
