package app.tvo.tv.data

import app.tvo.tv.Tvo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * TMDB (themoviedb.org) with the user's own key: French titles, summaries, images and catalogs.
 * Exposed as a built-in "virtual add-on" so rows, genres and search work like any other add-on.
 * Ids stay IMDb ("tt…") wherever possible, so stream add-ons keep working.
 */
object Tmdb {
    const val URL = "tmdb://fr"
    private const val API = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p/"

    val MOVIE_GENRES = mapOf("Action" to 28, "Adventure" to 12, "Animation" to 16, "Comedy" to 35, "Crime" to 80,
        "Documentary" to 99, "Drama" to 18, "Family" to 10751, "Fantasy" to 14, "History" to 36, "Horror" to 27,
        "Mystery" to 9648, "Romance" to 10749, "Sci-Fi" to 878, "Thriller" to 53, "War" to 10752, "Western" to 37)
    val TV_GENRES = mapOf("Action" to 10759, "Animation" to 16, "Comedy" to 35, "Crime" to 80, "Documentary" to 99,
        "Drama" to 18, "Family" to 10751, "Mystery" to 9648, "Sci-Fi" to 10765, "War" to 10768, "Western" to 37)

    private fun cat(type: String, id: String, name: String, genres: Collection<String>? = null, search: Boolean = false) =
        CatalogDef(type, id, name, buildList {
            if (genres != null) add(ExtraDef("genre", options = genres.toList()))
            if (search) add(ExtraDef("search"))
            add(ExtraDef("skip"))
        })

    val addon = Addon(URL, AddonManifest(
        id = "tmdb", name = "TMDB", version = "3", description = "Catalogues, titres et résumés en français",
        types = listOf("movie", "series"), resources = listOf(AddonResource("catalog")),
        catalogs = listOf(
            cat("movie", "trending", "Films tendance cette semaine"),
            cat("series", "trending", "Séries tendance cette semaine"),
            cat("movie", "popular", "Films populaires", MOVIE_GENRES.keys, search = true),
            cat("series", "popular", "Séries populaires", TV_GENRES.keys, search = true),
            cat("movie", "now_playing", "Actuellement au cinéma"),
            cat("movie", "top_rated", "Films les mieux notés"),
            cat("series", "top_rated", "Séries les mieux notées"),
        ),
    ))

    private val key: String? get() = Tvo.store.tmdbKey.value?.takeIf { it.isNotBlank() }
    val enabled: Boolean get() = key != null
    private val cache = ConcurrentHashMap<String, JsonObject>()

    /** Add-ons as seen by catalogs: TMDB first, Cinemeta catalogs hidden (Cinemeta still provides episodes). */
    fun withTmdb(addons: List<Addon>, tmdbKey: String?): List<Addon> {
        if (tmdbKey.isNullOrBlank()) return addons
        return listOf(addon) + addons.map {
            if (it.manifest.id == "com.linvo.cinemeta") it.copy(manifest = it.manifest.copy(catalogs = emptyList())) else it
        }
    }

    private suspend fun get(path: String, params: Map<String, String> = emptyMap(), k: String? = key): JsonObject {
        val kk = k ?: throw IllegalStateException("Clé TMDB manquante")
        val bearer = kk.startsWith("eyJ")
        val q = (params + ("language" to (params["language"] ?: "fr-FR")) + (if (bearer) emptyMap() else mapOf("api_key" to kk)))
            .entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
        val url = "$API$path?$q"
        cache[url]?.let { return it }
        val o = if (bearer) Tvo.client.getJson(url, mapOf("Authorization" to "Bearer $kk")) else Tvo.client.getJson(url)
        cache[url] = o
        return o
    }

    /** Checks a key; returns null if valid, or an error message. */
    suspend fun test(k: String): String? = runCatching { get("/configuration", k = k.trim()); null }
        .getOrElse { "Clé refusée par TMDB (${it.message})." }

    private fun kind(type: String) = if (type == "series") "tv" else "movie"
    private fun img(path: String?, size: String) = path?.takeIf { it.isNotBlank() }?.let { IMG + size + it }

    private fun preview(o: JsonObject, type: String): MetaPreview? {
        val id = o.i("id") ?: return null
        val genresMap = (if (type == "series") TV_GENRES else MOVIE_GENRES).entries.associate { (k, v) -> v to k }
        val ids = (o.arr("genre_ids") ?: emptyList()).mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
        val date = o.s("release_date") ?: o.s("first_air_date")
        return MetaPreview(
            id = "tmdb:$type:$id", type = type,
            name = o.s("title") ?: o.s("name") ?: "",
            poster = img(o.s("poster_path"), "w342"),
            background = img(o.s("backdrop_path"), "w780"),
            description = o.s("overview"),
            releaseInfo = date?.take(4),
            imdbRating = o.s("vote_average")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "%.1f".format(java.util.Locale.US, it) },
            genres = ids.mapNotNull { genresMap[it] },
        )
    }

    suspend fun catalog(cat: CatalogDef, extra: Map<String, String>): List<MetaPreview> {
        val k = kind(cat.type)
        val page = ((extra["skip"]?.toIntOrNull() ?: 0) / 20 + 1).toString()
        val search = extra["search"]
        val genre = extra["genre"]?.let { (if (cat.type == "series") TV_GENRES else MOVIE_GENRES)[it] }
        val o = when {
            search != null -> get("/search/$k", mapOf("query" to search, "page" to page))
            genre != null -> get("/discover/$k", mapOf("with_genres" to genre.toString(), "sort_by" to "popularity.desc", "page" to page, "vote_count.gte" to "50"))
            cat.id == "trending" -> get("/trending/$k/week", mapOf("page" to page))
            cat.id == "now_playing" -> get("/movie/now_playing", mapOf("page" to page, "region" to "FR"))
            else -> get("/$k/${cat.id}", mapOf("page" to page))
        }
        return o.arr("results")?.mapNotNull { (it as? JsonObject)?.let { r -> preview(r, cat.type) } } ?: emptyList()
    }

    /** "tmdb:movie:123" → "tt…" (or null if TMDB has no IMDb id). */
    suspend fun imdbId(id: String): String? {
        val (_, type, tid) = id.split(":").takeIf { it.size == 3 } ?: return null
        return runCatching { get("/${kind(type)}/$tid/external_ids").s("imdb_id") }.getOrNull()
    }

    /** French details on top of an add-on's meta (or a TMDB-only meta when [base] is null). */
    suspend fun enrich(type: String, imdb: String?, tmdbId: String?, base: MetaDetail?): MetaDetail? = runCatching {
        val k = kind(type)
        val tid = tmdbId ?: imdb?.let { tt ->
            val f = get("/find/$tt", mapOf("external_source" to "imdb_id"))
            (f.arr(if (k == "tv") "tv_results" else "movie_results")?.firstOrNull() as? JsonObject)?.i("id")?.toString()
        } ?: return@runCatching base
        val d = get("/$k/$tid", mapOf("append_to_response" to "credits,images", "include_image_language" to "fr,en,null"))
        val logos = d.obj("images")?.arr("logos")?.mapNotNull { it as? JsonObject } ?: emptyList()
        val logo = (logos.firstOrNull { it.s("iso_639_1") == "fr" } ?: logos.firstOrNull { it.s("iso_639_1") == "en" })?.s("file_path")
        val frGenres = d.arr("genres")?.mapNotNull { (it as? JsonObject)?.s("name") } ?: emptyList()
        val cast = d.obj("credits")?.arr("cast")?.mapNotNull { (it as? JsonObject)?.s("name") }?.take(6) ?: emptyList()
        val runtime = d.i("runtime") ?: (d.arr("episode_run_time")?.firstOrNull() as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
        val date = d.s("release_date") ?: d.s("first_air_date")
        val p0 = base?.preview
        val id = imdb ?: p0?.id ?: "tmdb:$type:$tid"
        val preview = MetaPreview(
            id = id, type = type,
            name = d.s("title") ?: d.s("name") ?: p0?.name ?: "",
            poster = img(d.s("poster_path"), "w500") ?: p0?.poster,
            background = img(d.s("backdrop_path"), "w1280") ?: p0?.background,
            logo = img(logo, "w500") ?: p0?.logo,
            description = d.s("overview") ?: p0?.description,
            releaseInfo = p0?.releaseInfo ?: date?.take(4),
            imdbRating = p0?.imdbRating ?: d.s("vote_average")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "%.1f".format(java.util.Locale.US, it) },
            genres = frGenres.ifEmpty { p0?.genres ?: emptyList() },
        )
        var videos = base?.videos ?: emptyList()
        if (k == "tv") {
            val seasons = (d.arr("seasons")?.mapNotNull { (it as? JsonObject)?.i("season_number") } ?: emptyList()).filter { it > 0 }
            val eps = coroutineScope {
                seasons.map { s -> async { runCatching { get("/tv/$tid/season/$s") }.getOrNull() } }.awaitAll()
            }.filterNotNull().flatMap { so -> so.arr("episodes")?.mapNotNull { it as? JsonObject } ?: emptyList() }
            val byKey = eps.associateBy { "${it.i("season_number")}:${it.i("episode_number")}" }
            if (videos.isEmpty() && imdb != null) {
                videos = eps.map { e ->
                    Video("$imdb:${e.i("season_number")}:${e.i("episode_number")}", "", e.i("season_number"), e.i("episode_number"), e.s("air_date"), null, null)
                }
            }
            videos = videos.map { v ->
                val e = byKey["${v.season}:${v.episode}"] ?: return@map v
                val name = e.s("name")?.takeUnless { Regex("^[ÉE]pisode \\d+$").matches(it) }
                v.copy(title = name ?: v.title, overview = e.s("overview") ?: v.overview,
                    thumbnail = img(e.s("still_path"), "w300") ?: v.thumbnail, released = v.released ?: e.s("air_date"))
            }
        }
        MetaDetail(preview, runtime?.let { "$it min" } ?: base?.runtime, videos, cast.ifEmpty { base?.cast ?: emptyList() },
            base?.director ?: emptyList(), base?.defaultVideoId)
    }.getOrElse { base }
}
