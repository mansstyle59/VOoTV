package app.tvo.tv.data

import app.tvo.tv.Tvo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; TVO) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

// ---------- JSON helpers (Stremio add-ons are loosely typed) ----------
fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
fun JsonObject.i(k: String): Int? = s(k)?.toDoubleOrNull()?.toInt()
fun JsonObject.arr(k: String): JsonArray? = this[k] as? JsonArray
fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
fun JsonObject.strList(k: String): List<String> = when (val e = this[k]) {
    is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive -> listOfNotNull(e.contentOrNull)
    else -> emptyList()
}

fun enc(v: String): String = URLEncoder.encode(v, "UTF-8").replace("+", "%20")

data class AddonStreams(val addon: Addon, val items: List<StreamItem>, val error: Boolean)

class AddonClient(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val catalogCache = ConcurrentHashMap<String, List<MetaPreview>>()
    val metaCache = ConcurrentHashMap<String, MetaDetail>()

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            json.parseToJsonElement(r.body?.string() ?: "{}").jsonObject
        }
    }

    suspend fun getJsonArray(url: String): kotlinx.serialization.json.JsonArray = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            json.parseToJsonElement(r.body?.string() ?: "[]").jsonArray
        }
    }

    fun normalize(input: String): String {
        var u = input.trim()
        if (u.startsWith("stremio://")) u = "https://" + u.removePrefix("stremio://")
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return u.substringBefore("/manifest.json").trimEnd('/')
    }

    suspend fun install(input: String): Addon {
        if (input.startsWith(FreeTv.URL) || M3u.isM3uInput(input)) throw IllegalStateException(NoTv.MESSAGE)
        val base = normalize(input)
        val a = Addon(base, parseManifest(getJson("$base/manifest.json")))
        if (NoTv.isTv(a)) throw IllegalStateException(NoTv.MESSAGE)
        return NoTv.strip(a)
    }

    fun supports(addon: Addon, resource: String, type: String, id: String? = null): Boolean {
        val m = addon.manifest
        val r = m.resources.firstOrNull { it.name == resource } ?: return false
        val types = r.types ?: m.types
        if (types.isNotEmpty() && type !in types) return false
        val prefixes = r.idPrefixes ?: m.idPrefixes
        if (id != null && !prefixes.isNullOrEmpty() && prefixes.none { id.startsWith(it) }) return false
        return true
    }

    suspend fun catalog(addon: Addon, cat: CatalogDef, extra: Map<String, String> = emptyMap()): List<MetaPreview> {
        if (addon.url == Tmdb.URL) return Tmdb.catalog(cat, extra)
        if (addon.url == FreeTv.URL) return FreeTv.catalog(cat.id, extra)
        if (addon.url.startsWith(M3u.PREFIX)) return M3u.catalog(addon, cat.id, extra)
        val ex = if (extra.isEmpty()) "" else "/" + extra.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
        val url = "${addon.url}/catalog/${cat.type}/${enc(cat.id)}$ex.json"
        catalogCache[url]?.let { return it }
        val list = getJson(url).arr("metas")
            ?.mapNotNull { (it as? JsonObject)?.let { o -> parsePreview(o, cat.type) } }
            ?.map { if (addon.manifest.id == "com.linvo.cinemeta") it.copy(description = null) else it } // résumés anglais masqués
            ?.distinctBy { it.id } ?: emptyList()
        catalogCache[url] = list
        return list
    }

    suspend fun meta(addons: List<Addon>, type: String, id: String): MetaDetail? {
        metaCache["$type/$id"]?.let { return it }
        if (id.startsWith("freetv:")) return FreeTv.meta(id)
        if (id.startsWith(M3u.PREFIX)) return M3u.meta(id)
        // TMDB catalog item: switch to its IMDb id so stream add-ons can find it.
        val tmdbId = if (id.startsWith("tmdb:")) id.split(":").getOrNull(2) else null
        val realId = if (tmdbId != null) Tmdb.imdbId(id) ?: id else id
        var m: MetaDetail? = metaCache["$type/$realId"]
        if (m == null && !realId.startsWith("tmdb:")) {
            for (a in addons.filter { supports(it, "meta", type, realId) }) {
                m = runCatching { getJson("${a.url}/meta/$type/${enc(realId)}.json").obj("meta")?.let { parseMeta(it, type) } }.getOrNull()
                    ?.let { if (a.manifest.id == "com.linvo.cinemeta") it.copy(preview = it.preview.copy(description = null), videos = it.videos.map { v -> v.copy(overview = null) }) else it }
                if (m != null) break
            }
            if (m != null && Tmdb.enabled) m = Tmdb.enrich(type, realId, tmdbId, m)
        }
        if (m == null && Tmdb.enabled) m = Tmdb.enrich(type, realId.takeIf { it.startsWith("tt") }, tmdbId, null)
        // Certains add-ons renvoient des épisodes en double : une clé en double fait planter la fiche
        if (m != null) m = m.copy(videos = m.videos.distinctBy { it.id })
        if (m != null) m = complete(type, m)
        if (m != null) { metaCache["$type/$id"] = m; metaCache["$type/${m.preview.id}"] = m }
        return m
    }

    fun clearCaches() { catalogCache.clear(); metaCache.clear(); Tmdb.clearCache() }

    fun streams(addons: List<Addon>, type: String, videoId: String): Flow<AddonStreams> = channelFlow {
        addons.filter { supports(it, "stream", type, videoId) }.forEach { a ->
            launch {
                val res = withTimeoutOrNull(25_000) {
                    if (a.url == FreeTv.URL) return@withTimeoutOrNull runCatching { FreeTv.streams(videoId) }.getOrNull()
                    if (a.url.startsWith(M3u.PREFIX)) return@withTimeoutOrNull runCatching { M3u.streams(videoId) }.getOrNull()
                    runCatching {
                        getJson("${a.url}/stream/$type/${enc(videoId)}.json").arr("streams")
                            ?.mapNotNull { (it as? JsonObject)?.let { o -> parseStream(o, a.manifest.name) } } ?: emptyList()
                    }.getOrNull()
                }
                send(AddonStreams(a, res ?: emptyList(), res == null))
            }
        }
    }

    fun streamAddonCount(addons: List<Addon>, type: String, videoId: String) =
        addons.count { supports(it, "stream", type, videoId) }

    suspend fun subtitles(addons: List<Addon>, type: String, videoId: String): List<SubtitleItem> = coroutineScope {
        addons.filter { supports(it, "subtitles", type, videoId) }.map { a ->
            async {
                withTimeoutOrNull(6_000) {
                    runCatching {
                        getJson("${a.url}/subtitles/$type/${enc(videoId)}.json").arr("subtitles")?.mapNotNull { e ->
                            (e as? JsonObject)?.let { o -> o.s("url")?.let { SubtitleItem(it, o.s("lang") ?: "und") } }
                        } ?: emptyList()
                    }.getOrNull()
                } ?: emptyList()
            }
        }.awaitAll().flatten()
    }

    // ---------- parsers ----------
    fun parseManifest(o: JsonObject) = AddonManifest(
        id = o.s("id") ?: "addon",
        name = o.s("name") ?: "Add-on",
        version = o.s("version") ?: "",
        description = o.s("description") ?: "",
        logo = o.s("logo"),
        types = o.strList("types"),
        resources = o.arr("resources")?.mapNotNull { e ->
            when (e) {
                is JsonObject -> e.s("name")?.let { AddonResource(it, e.strList("types").ifEmpty { null }, e.strList("idPrefixes").ifEmpty { null }) }
                is JsonPrimitive -> e.contentOrNull?.let { AddonResource(it) }
                else -> null
            }
        } ?: emptyList(),
        catalogs = o.arr("catalogs")?.mapNotNull { c ->
            val co = c as? JsonObject ?: return@mapNotNull null
            val type = co.s("type") ?: return@mapNotNull null
            val id = co.s("id") ?: return@mapNotNull null
            val extras = co.arr("extra")?.mapNotNull { e ->
                val eo = e as? JsonObject ?: return@mapNotNull null
                eo.s("name")?.let { n -> ExtraDef(n, (eo["isRequired"] as? JsonPrimitive)?.booleanOrNull ?: false, eo.strList("options")) }
            } ?: emptyList()
            CatalogDef(type, id, co.s("name") ?: id, extras, co.strList("extraRequired"), co.strList("extraSupported"))
        } ?: emptyList(),
        idPrefixes = o.strList("idPrefixes").ifEmpty { null },
        configurable = o.obj("behaviorHints")?.let { h -> (h["configurable"] as? JsonPrimitive)?.booleanOrNull == true || (h["configurationRequired"] as? JsonPrimitive)?.booleanOrNull == true } ?: false,
    )

    /** Image Metahub (sans clé) par id IMDb : comble les affiches et fonds manquants. */
    private fun metahub(kind: String, id: String): String? =
        if (Regex("tt\\d+").matches(id)) "https://images.metahub.space/$kind/medium/$id/img" else null

    /** Synopsis et infos manquants : Cinemeta, puis TVmaze pour les séries. Jamais bloquant. */
    private suspend fun complete(type: String, m: MetaDetail): MetaDetail {
        val id = m.preview.id
        if (!Regex("tt\\d+").matches(id)) return m
        val p = m.preview
        if (!p.description.isNullOrBlank() && p.genres.isNotEmpty() && !p.releaseInfo.isNullOrBlank() && !m.runtime.isNullOrBlank() && m.cast.isNotEmpty()) return m
        return kotlinx.coroutines.withTimeoutOrNull(4000) {
            runCatching {
                val c = getJson("https://v3-cinemeta.strem.io/meta/$type/$id.json").obj("meta")
                var desc = p.description ?: c?.s("description")
                if (desc.isNullOrBlank() && type == "series") desc = runCatching {
                    getJson("https://api.tvmaze.com/lookup/shows?imdb=$id").s("summary")?.replace(Regex("<[^>]*>"), "")?.trim()
                }.getOrNull()?.takeIf { it.isNotBlank() }
                m.copy(
                    preview = p.copy(
                        description = desc,
                        genres = p.genres.ifEmpty { c?.strList("genres") ?: emptyList() },
                        releaseInfo = p.releaseInfo ?: c?.s("releaseInfo"),
                        imdbRating = p.imdbRating ?: c?.s("imdbRating"),
                    ),
                    runtime = m.runtime ?: c?.s("runtime"),
                    cast = m.cast.ifEmpty { c?.strList("cast")?.take(6) ?: emptyList() },
                    director = m.director.ifEmpty { c?.strList("director") ?: emptyList() },
                )
            }.getOrNull()
        } ?: m
    }

    private fun parsePreview(o: JsonObject, fallbackType: String): MetaPreview? {
        val id = o.s("id") ?: o.s("imdb_id") ?: return null
        return MetaPreview(
            id = id,
            type = o.s("type") ?: fallbackType,
            name = o.s("name") ?: o.s("title") ?: "",
            poster = o.s("poster") ?: metahub("poster", id),
            background = o.s("background") ?: metahub("background", id),
            logo = o.s("logo"),
            description = o.s("description"),
            releaseInfo = o.s("releaseInfo") ?: o.s("year"),
            imdbRating = o.s("imdbRating"),
            genres = o.strList("genres").ifEmpty { o.strList("genre") },
        )
    }

    private fun parseMeta(o: JsonObject, type: String): MetaDetail? {
        val p = parsePreview(o, type) ?: return null
        return MetaDetail(
            preview = p,
            runtime = o.s("runtime"),
            videos = o.arr("videos")?.mapNotNull { e ->
                val v = e as? JsonObject ?: return@mapNotNull null
                val vid = v.s("id") ?: return@mapNotNull null
                Video(vid, v.s("title") ?: v.s("name") ?: "", v.i("season"), v.i("episode") ?: v.i("number"),
                    v.s("released"), v.s("thumbnail"), v.s("overview") ?: v.s("description"))
            } ?: emptyList(),
            cast = o.strList("cast").take(6),
            director = o.strList("director"),
            defaultVideoId = o.obj("behaviorHints")?.s("defaultVideoId"),
        )
    }

    private fun parseStream(o: JsonObject, addonName: String): StreamItem? {
        val url = o.s("url")
        val yt = o.s("ytId")
        val ext = o.s("externalUrl") ?: yt?.let { "https://www.youtube.com/watch?v=$it" }
        val infoHash = o.s("infoHash")
        if (url == null && ext == null && infoHash == null) return null
        val hints = o.obj("behaviorHints")
        val headers = hints?.obj("proxyHeaders")?.obj("request")
            ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap() ?: emptyMap()
        val subs = o.arr("subtitles")?.mapNotNull { e ->
            (e as? JsonObject)?.let { so -> so.s("url")?.let { SubtitleItem(it, so.s("lang") ?: "und") } }
        } ?: emptyList()
        val name = o.s("name") ?: addonName
        val title = o.s("title") ?: o.s("description") ?: ""
        return StreamItem(addonName, name, title, url, ext, infoHash, headers, subs, hints?.s("bingeGroup"), qualityOf("$name $title"))
    }

    companion object {
        fun qualityOf(text: String): Int {
            val t = text.lowercase()
            return when {
                "2160" in t || "4k" in t || "uhd" in t -> 4
                "1080" in t || "fhd" in t -> 3
                "720" in t -> 2
                "480" in t || "sd" in t.split(' ', '\n', '|', '-') -> 1
                else -> 0
            }
        }
        fun qualityLabel(q: Int) = when (q) { 4 -> "4K"; 3 -> "1080p"; 2 -> "720p"; 1 -> "SD"; else -> "Auto" }
    }
}
