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
            *seasonCats().toTypedArray(),
            cat("movie", "latest", "Derniers films sortis"),
            cat("movie", "now_playing", "Au cinéma en ce moment"),
            cat("movie", "recent_pop", "Nouveautés populaires"),
            cat("movie", "trending", "Films tendance cette semaine"),
            cat("movie", "popular", "Films populaires", MOVIE_GENRES.keys, search = true),
            cat("movie", "prov_8", "Nouveaux films sur Netflix"),
            cat("movie", "prov_119", "Nouveaux films sur Prime Video"),
            cat("movie", "prov_337", "Nouveautés Disney+"),
            cat("series", "latest_tv", "Nouvelles séries"),
            cat("series", "trending", "Séries tendance cette semaine"),
            cat("series", "on_the_air", "Séries en cours de diffusion"),
            cat("series", "popular", "Séries populaires", TV_GENRES.keys, search = true),
            cat("series", "prov_8", "Nouvelles séries sur Netflix"),
            cat("series", "prov_119", "Nouvelles séries sur Prime Video"),
            cat("series", "prov_381", "Nouveautés Canal+"),
            cat("series", "prov_350", "Nouveautés Apple TV+"),
            cat("series", "anime", "Animés et mangas"),
            // Page Kids : beaucoup de films récents, peu de séries
            cat("movie", "kids_new", "Nouveaux films pour enfants"),
            cat("movie", "kids", "Films pour enfants du moment"),
            cat("movie", "kids_anim", "Films d'animation récents"),
            cat("movie", "kids_fun", "Comédies en famille"),
            cat("movie", "kids_adv", "Aventures en famille"),
            cat("movie", "kids_disney", "Disney+ pour enfants"),
            cat("movie", "kids_netflix", "Films famille sur Netflix"),
            cat("movie", "kids_prime", "Films famille sur Prime Video"),
            cat("series", "kids_tv", "Dessins animés récents"),
            cat("series", "kids", "Séries pour enfants"),
            cat("series", "kids_tiny", "Pour les tout-petits"),
        ),
    ))

    /** Catalogues d'événements selon la date : récents puis classiques. */
    private fun seasonCats(): List<CatalogDef> {
        val d = java.time.LocalDate.now(); val m = d.monthValue; val day = d.dayOfMonth
        val ev = when {
            m == 12 || (m == 11 && day >= 15) -> "christmas" to "Noël"
            m == 10 || (m == 11 && day <= 2) -> "halloween" to "Halloween"
            m == 2 && day <= 15 -> "valentine's day" to "Saint-Valentin"
            (m == 3 && day >= 15) || (m == 4 && day <= 25) -> "easter" to "Pâques"
            m == 7 || m == 8 -> "summer vacation" to "Vacances d'été"
            m == 1 && day <= 7 -> "new year's eve" to "Nouvel An"
            else -> null
        } ?: return emptyList()
        return listOf(cat("movie", "ev_${ev.first}_new", "Films de ${ev.second} récents"), cat("series", "ev_${ev.first}_tv", "Séries de ${ev.second}"), cat("movie", "ev_${ev.first}_old", "Classiques de ${ev.second}"))
    }
    private val kw = ConcurrentHashMap<String, String>()
    private suspend fun keywordId(q: String): String? = kw[q] ?: runCatching {
        val r = get("/search/keyword", mapOf("query" to q)).arr("results")?.mapNotNull { it as? JsonObject } ?: emptyList()
        (r.firstOrNull { it.s("name")?.lowercase() == q } ?: r.firstOrNull())?.i("id")?.toString()
    }.getOrNull()?.also { kw[q] = it }

    private val key: String? get() = Tvo.store.tmdbKey.value?.takeIf { it.isNotBlank() }
    /** Relais TVO : la clé TMDB reste sur le serveur (jamais dans l'appli). */
    private const val PROXY = "https://vfxblubjsxwseacmslnu.supabase.co/functions/v1/tvo-tmdb"
    @Volatile var proxyOk: Boolean = false
    val enabled: Boolean get() = key != null || proxyOk

    suspend fun checkProxy(): Boolean {
        proxyOk = runCatching { Tvo.client.getJson("$PROXY?path=/configuration"); true }.getOrDefault(false)
        return proxyOk
    }
    private val cache = ConcurrentHashMap<String, JsonObject>()
    fun clearCache() = cache.clear()
    private fun today() = java.time.LocalDate.now().toString()
    private fun monthsAgo(n: Long) = java.time.LocalDate.now().minusMonths(n).toString()

    // ---- Catalogues à jour sans réinstaller : liste publiée sur GitHub (docs/catalogs.json), lue en arrière-plan ----
    private const val REMOTE_URL = "https://raw.githubusercontent.com/mansstyle59/VOoTV/main/docs/catalogs.json"
    private class RC(val cat: CatalogDef, val path: String, val params: Map<String, String>)
    @Volatile private var remote: Map<String, RC> = emptyMap()
    @Volatile private var hidden: Set<String> = emptySet()
    // Ordre logique des catalogues (nouveautés, tendances, plateformes, genres, classiques) ; surchargeable par catalogs.json
    private val DEFAULT_ORDER: List<String> = listOf("ev:new", "now_playing", "recent_pop", "trending", "series:trending", "latest", "latest_tv", "prov_8", "prov_119", "prov_337", "r_m_toprated", "on_the_air", "r_s_toprated", "r_m_action", "r_s_action", "r_m_comedy", "r_s_comedy", "r_m_scifi", "r_s_scifi", "r_m_thriller", "r_s_crime", "r_m_crime", "r_m_drama", "r_s_drama", "r_m_romance", "r_m_fantasy", "r_m_anim", "r_s_anime", "r_m_horror", "r_m_french", "r_s_french", "r_m_apple", "prov_381", "prov_350", "r_s_disney", "r_m_doc", "r_s_doc", "r_s_mystery", "r_m_war", "popular", "ev:old", "movie:kids_new", "movie:kids", "movie:kids_anim", "kids_fun", "kids_adv", "kids_disney", "kids_netflix", "kids_prime", "kids_r_toprated", "kids_tv", "kids_r_tv_disney", "kids_r_tv_netflix", "series:kids", "kids_tiny", "kids_r_tv_family", "kids_r_fantasy", "kids_r_music", "kids_r_french")
    @Volatile private var order: List<String> = DEFAULT_ORDER
    /** Rang d'un catalogue : « type:id » puis « id » ; saison : nouveautés en tête, classiques en fin ; genres en dernier. */
    fun rank(type: String, id: String, genre: Boolean): Int {
        if (genre) return 800
        var i = order.indexOf("$type:$id"); if (i < 0) i = order.indexOf(id)
        if (i < 0 && id.startsWith("ev_")) i = order.indexOf(if (id.endsWith("_old")) "ev:old" else if (id.endsWith("_tv")) "ev:tv" else "ev:new")
        return if (i < 0) 500 else i
    }
    private fun applyRemote(o: JsonObject) {
        order = o.arr("order")?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }?.takeIf { it.isNotEmpty() } ?: DEFAULT_ORDER
        hidden = o.arr("hide")?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }?.toSet() ?: emptySet()
        remote = o.arr("catalogs")?.mapNotNull { it as? JsonObject }?.mapNotNull { c ->
            val id = c.s("id") ?: return@mapNotNull null; val type = c.s("type") ?: return@mapNotNull null
            val name = c.s("name") ?: return@mapNotNull null; val path = c.s("path") ?: return@mapNotNull null
            val params = (c["params"] as? JsonObject)?.mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "" } ?: emptyMap()
            id to RC(cat(type, id, name), path, params)
        }?.toMap() ?: emptyMap()
    }
    private fun expand(v: String): String = when {
        v == "{today}" -> today()
        v.startsWith("{m:") -> monthsAgo(v.substring(3, v.length - 1).toLongOrNull() ?: 12)
        else -> v
    }
    /** Dernière liste connue (au démarrage), puis mise à jour en arrière-plan pour le prochain affichage. */
    fun loadRemoteCached() { Tvo.store.remoteCats()?.let { s -> runCatching { applyRemote(kotlinx.serialization.json.Json.parseToJsonElement(s) as JsonObject) } } }
    suspend fun refreshRemote() {
        runCatching {
            val o = Tvo.client.getJson(REMOTE_URL)
            if (o.arr("catalogs") != null) { applyRemote(o); Tvo.store.setRemoteCats(o.toString()) }
        }
    }
    internal fun live(): Addon {
        val base = addon.manifest.catalogs.filter { it.id !in hidden && it.id !in remote }
        return addon.copy(manifest = addon.manifest.copy(catalogs = base + remote.values.map { it.cat }.filter { it.id !in hidden }))
    }

    /** Add-ons as seen by catalogs: TMDB first, Cinemeta catalogs hidden (Cinemeta still provides episodes). */
    fun withTmdb(addons: List<Addon>, tmdbKey: String?, kids: Boolean = false): List<Addon> {
        if (kids) return listOf(live().let { l -> l.copy(manifest = l.manifest.copy(catalogs = l.manifest.catalogs.filter { it.id.startsWith("kids") })) })
        if (tmdbKey.isNullOrBlank() && !proxyOk) return addons
        return listOf(live()) + addons.map {
            if (it.manifest.id == "com.linvo.cinemeta") it.copy(manifest = it.manifest.copy(catalogs = emptyList())) else it
        }
    }

    private suspend fun get(path: String, params: Map<String, String> = emptyMap(), k: String? = key): JsonObject {
        if (k == null) {
            val q = (mapOf("path" to path, "language" to "fr-FR") + params).entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
            val url = "$PROXY?$q"
            cache[url]?.let { return it }
            return Tvo.client.getJson(url).also { cache[url] = it }
        }
        val kk = k
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
            remote[cat.id] != null -> remote.getValue(cat.id).let { rc -> get("/${rc.path}", rc.params.mapValues { expand(it.value) } + ("page" to page)) }
            search != null -> get("/search/$k", mapOf("query" to search, "page" to page))
            // Profil enfant : le genre est croisé avec « Famille » (films) ou « Enfants » (séries), et les genres adultes sont exclus
            genre != null && extra["kids"] == "1" -> get("/discover/$k", mapOf("with_genres" to (if (genre == 10751 || genre == 10762) "$genre" else if (k == "tv") "$genre,10762" else "$genre,10751"),
                "without_genres" to "27,53,80,10749,10752", "sort_by" to "popularity.desc", "page" to page, "vote_count.gte" to "10"))
            genre != null -> get("/discover/$k", mapOf("with_genres" to genre.toString(), "sort_by" to "popularity.desc", "page" to page, "vote_count.gte" to "50"))
            cat.id == "trending" -> get("/trending/$k/week", mapOf("page" to page))
            cat.id == "now_playing" -> get("/movie/now_playing", mapOf("page" to page, "region" to "FR"))
            cat.id == "upcoming" -> get("/movie/upcoming", mapOf("page" to page, "region" to "FR"))
            cat.id == "kids" -> get("/discover/$k", if (k == "tv") mapOf("page" to page, "with_genres" to "10762", "sort_by" to "popularity.desc", "first_air_date.gte" to monthsAgo(36))
                else mapOf("page" to page, "with_genres" to "10751", "without_genres" to "27,53,80", "sort_by" to "popularity.desc", "primary_release_date.gte" to monthsAgo(30), "primary_release_date.lte" to today(), "vote_count.gte" to "30"))
            cat.id == "kids_new" -> get("/discover/movie", mapOf("page" to page, "with_genres" to "10751|16", "without_genres" to "27,53,80,10749", "sort_by" to "primary_release_date.desc", "primary_release_date.gte" to monthsAgo(14), "primary_release_date.lte" to today(), "vote_count.gte" to "8"))
            cat.id == "kids_anim" -> get("/discover/movie", mapOf("page" to page, "with_genres" to "16", "without_genres" to "27,53,80,10749", "sort_by" to "popularity.desc", "primary_release_date.gte" to monthsAgo(30), "primary_release_date.lte" to today(), "vote_count.gte" to "30"))
            cat.id == "kids_fun" -> get("/discover/movie", mapOf("page" to page, "with_genres" to "35,10751", "without_genres" to "27,53,80", "sort_by" to "popularity.desc", "primary_release_date.gte" to monthsAgo(30), "primary_release_date.lte" to today(), "vote_count.gte" to "20"))
            cat.id == "kids_adv" -> get("/discover/movie", mapOf("page" to page, "with_genres" to "10751", "without_genres" to "27,53,80,35,10749", "sort_by" to "popularity.desc", "primary_release_date.gte" to monthsAgo(30), "primary_release_date.lte" to today(), "vote_count.gte" to "20"))
            cat.id == "kids_tiny" -> get("/discover/tv", mapOf("page" to page, "with_genres" to "10762", "without_genres" to "10768,18", "sort_by" to "popularity.desc", "first_air_date.gte" to monthsAgo(48), "first_air_date.lte" to today(), "vote_count.gte" to "5"))
            cat.id == "kids_tv" -> get("/discover/tv", mapOf("page" to page, "with_genres" to "16|10762", "without_genres" to "18,10768", "sort_by" to "popularity.desc", "first_air_date.gte" to monthsAgo(30), "first_air_date.lte" to today(), "vote_count.gte" to "10"))
            cat.id in listOf("kids_disney", "kids_netflix", "kids_prime") -> get("/discover/movie", mapOf("page" to page, "watch_region" to "FR",
                "with_watch_providers" to mapOf("kids_disney" to "337", "kids_netflix" to "8", "kids_prime" to "119").getValue(cat.id), "with_watch_monetization_types" to "flatrate",
                "with_genres" to "10751|16", "without_genres" to "27,53,80,10749", "sort_by" to "popularity.desc", "primary_release_date.gte" to monthsAgo(36), "primary_release_date.lte" to today(), "vote_count.gte" to "20"))
            cat.id == "recent_pop" -> get("/discover/movie", mapOf("page" to page, "region" to "FR", "sort_by" to "popularity.desc", "primary_release_date.gte" to monthsAgo(8), "primary_release_date.lte" to today(), "vote_count.gte" to "40"))
            cat.id.startsWith("tvtype_") -> get("/discover/tv", mapOf("page" to page, "with_type" to cat.id.removePrefix("tvtype_"), "sort_by" to "popularity.desc", "vote_count.gte" to "20"))
            cat.id == "anime" -> get("/discover/tv", mapOf("page" to page, "with_genres" to "16", "with_original_language" to "ja", "sort_by" to "popularity.desc", "vote_count.gte" to "50"))
            cat.id.startsWith("ev_") -> {
                val parts = cat.id.split("_"); val id = keywordId(parts[1])
                if (id == null) JsonObject(emptyMap()) else if (parts[2] == "tv") get("/discover/tv", mapOf("page" to page, "with_keywords" to id, "sort_by" to "popularity.desc",
                    "first_air_date.lte" to java.time.LocalDate.now().toString(), "vote_count.gte" to "5"))
                else get("/discover/movie", mapOf("page" to page, "with_keywords" to id,
                    "sort_by" to if (parts[2] == "new") "primary_release_date.desc" else "vote_average.desc",
                    "primary_release_date.lte" to java.time.LocalDate.now().toString(), "vote_count.gte" to if (parts[2] == "new") "15" else "200"))
            }
            cat.id == "latest" -> get("/discover/movie", mapOf("page" to page, "region" to "FR", "sort_by" to "primary_release_date.desc",
                "primary_release_date.lte" to java.time.LocalDate.now().toString(), "vote_count.gte" to "20", "with_release_type" to "3|4|6"))
            cat.id == "latest_tv" -> get("/discover/tv", mapOf("page" to page, "sort_by" to "popularity.desc",
                "first_air_date.gte" to monthsAgo(8), "first_air_date.lte" to today(), "vote_count.gte" to "15"))
            cat.id.startsWith("prov_") -> get("/discover/$k", mapOf("page" to page, "watch_region" to "FR",
                "with_watch_providers" to cat.id.removePrefix("prov_"), "with_watch_monetization_types" to "flatrate",
                "sort_by" to if (k == "tv") "first_air_date.desc" else "primary_release_date.desc",
                (if (k == "tv") "first_air_date.lte" else "primary_release_date.lte") to java.time.LocalDate.now().toString(),
                "vote_count.gte" to "5"))
            else -> get("/$k/${cat.id}", mapOf("page" to page))
        }
        return o.arr("results")?.mapNotNull { (it as? JsonObject)?.let { r -> preview(r, cat.type) } } ?: emptyList()
    }

    /** "tmdb:movie:123" → "tt…" (or null if TMDB has no IMDb id). */
    suspend fun imdbId(id: String): String? {
        val (_, type, tid) = id.split(":").takeIf { it.size == 3 } ?: return null
        return runCatching { get("/${kind(type)}/$tid/external_ids").s("imdb_id") }.getOrNull()
    }

    private const val FANART = "https://vfxblubjsxwseacmslnu.supabase.co/functions/v1/tvo-fanart"
    /** Logo HD de Fanart.tv (via le relais Supabase, clé côté serveur). Null si absent ou indisponible. */
    private suspend fun fanartLogo(type: String, id: String): String? = runCatching {
        val rid = if (id.startsWith("tmdb:")) id.split(":").getOrNull(2) else id.substringBefore(":")
        if (rid.isNullOrBlank()) return@runCatching null
        // séries : Fanart.tv les range par identifiant TheTVDB, retrouvé avec la clé TMDB de l'appareil
        val tvdb = if (type == "series") {
            val tid = if (id.startsWith("tmdb:")) rid else (get("/find/$rid", mapOf("external_source" to "imdb_id")).arr("tv_results")?.firstOrNull() as? JsonObject)?.i("id")?.toString()
            tid?.let { get("/tv/$it/external_ids").i("tvdb_id")?.toString() }
        } else null
        Tvo.client.getJson("$FANART?type=$type&id=$rid" + (Tvo.store.fanartKey?.let { "&key=$it" } ?: "") + (tvdb?.let { "&tvdb=$it" } ?: "")).s("logo")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private val logos = ConcurrentHashMap<String, String>()
    /** Logo officiel du titre (français de préférence, sinon anglais), mis en cache. "" = aucun logo. */
    suspend fun logo(type: String, id: String): String? {
        logos[id]?.let { return it.ifEmpty { null } }
        fanartLogo(type, id)?.let { logos[id] = it; return it }
        val r = runCatching {
            val k = kind(type)
            val tid = when {
                id.startsWith("tmdb:") -> id.split(":").getOrNull(2)
                id.startsWith("tt") -> (get("/find/${id.substringBefore(":")}", mapOf("external_source" to "imdb_id"))
                    .arr(if (k == "tv") "tv_results" else "movie_results")?.firstOrNull() as? JsonObject)?.i("id")?.toString()
                else -> null
            } ?: return@runCatching ""
            val l = get("/$k/$tid/images", mapOf("include_image_language" to "fr,en,null")).arr("logos")?.mapNotNull { it as? JsonObject } ?: emptyList()
            ((l.firstOrNull { it.s("iso_639_1") == "fr" } ?: l.firstOrNull { it.s("iso_639_1") == "en" } ?: l.firstOrNull())?.s("file_path"))?.let { img(it, "w500") } ?: ""
        }.getOrDefault("")
        logos[id] = r
        return r.ifEmpty { null }
    }

    /** Recherche d'acteurs et d'actrices (avec photo). */
    suspend fun searchPeople(q: String): List<Person> = runCatching {
        get("/search/person", mapOf("query" to q)).arr("results")?.mapNotNull { it as? JsonObject }
            ?.filter { !it.s("profile_path").isNullOrBlank() }?.take(15)
            ?.mapNotNull { o -> o.i("id")?.let { id ->
                val kf = (o.arr("known_for")?.firstOrNull() as? JsonObject)?.let { it.s("title") ?: it.s("name") }
                Person(id.toString(), o.s("name") ?: "", img(o.s("profile_path"), "w185"), kf) } } ?: emptyList()
    }.getOrDefault(emptyList())

    /** Titres similaires (recommandations TMDB, puis « similaires » si vide). */
    suspend fun similar(type: String, id: String): List<MetaPreview> = runCatching {
        val k = kind(type)
        val tid = when {
            id.startsWith("tmdb:") -> id.split(":").getOrNull(2)
            id.startsWith("tt") -> (get("/find/${id.substringBefore(":")}", mapOf("external_source" to "imdb_id"))
                .arr(if (k == "tv") "tv_results" else "movie_results")?.firstOrNull() as? JsonObject)?.i("id")?.toString()
            else -> null
        } ?: return@runCatching emptyList()
        val rec = get("/$k/$tid/recommendations").arr("results")?.mapNotNull { (it as? JsonObject)?.let { o -> preview(o, type) } } ?: emptyList()
        (if (rec.size >= 6) rec else rec + (get("/$k/$tid/similar").arr("results")?.mapNotNull { (it as? JsonObject)?.let { o -> preview(o, type) } } ?: emptyList()))
            .filter { it.poster != null }.distinctBy { it.id }.take(20)
    }.getOrDefault(emptyList())

    data class Saga(val id: String, val name: String, val poster: String?, val backdrop: String?)
    private val SAGA_IDS = listOf(1241, 10, 86311, 645, 9485, 119, 328, 10194, 87359, 295, 2344, 131635, 2150, 404609, 8091, 748, 263, 87096, 86055)

    private fun saga(o: JsonObject, id: String) = Saga(id, o.s("name") ?: "", img(o.s("poster_path"), "w342"), img(o.s("backdrop_path"), "w1280"))

    /** Top 10 de la semaine : films, séries, ou les deux mélangés (accueil). */
    suspend fun top10(type: String?): List<MetaPreview> = runCatching {
        // Vrai classement TMDB de la semaine ; accueil = films et séries mélangés selon leur popularité réelle
        val path = when (type) { "movie" -> "/trending/movie/week"; "series" -> "/trending/tv/week"; else -> "/trending/all/week" }
        get(path).arr("results").orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val t = when { type != null -> type; o.s("media_type") == "tv" -> "series"; o.s("media_type") == "movie" -> "movie"; else -> null } ?: return@mapNotNull null
            preview(o, t)
        }.filter { it.poster != null }.distinctBy { it.id }.take(10)
    }.getOrDefault(emptyList())

    /** Grandes sagas du cinéma (Harry Potter, Star Wars, James Bond…). */
    suspend fun sagas(): List<Saga> = coroutineScope {
        SAGA_IDS.map { cid -> async { runCatching {
            val o = get("/collection/$cid")
            // Image d'un film au hasard de la saga : elle change à chaque lancement
            val bd = o.arr("parts")?.mapNotNull { (it as? JsonObject)?.s("backdrop_path")?.takeIf { b -> b.isNotBlank() } }?.randomOrNull() ?: o.s("backdrop_path")
            saga(o, cid.toString()).copy(backdrop = img(bd, "w1280")).takeIf { it.name.isNotBlank() } }.getOrNull() } }
            .awaitAll().filterNotNull()
    }

    private val KIDS_SAGAS = listOf("Toy Story", "Shrek", "L'Âge de glace", "Moi, moche et méchant", "Madagascar", "Kung Fu Panda", "Cars", "Le Monde de Nemo",
        "Les Indestructibles", "Dragons", "Hôtel Transylvanie", "Paddington", "Le Livre de la jungle", "Les Minions", "Les Schtroumpfs", "Garfield", "La Nuit au musée", "Le Monde de Narnia", "Jumanji")

    /** Sagas adaptées aux enfants (page Kids). */
    suspend fun kidsSagas(): List<Saga> = coroutineScope {
        KIDS_SAGAS.map { q -> async { runCatching {
            val o = get("/search/collection", mapOf("query" to q)).arr("results")?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { !it.s("poster_path").isNullOrBlank() && !it.s("backdrop_path").isNullOrBlank() }
            o?.let { r -> r.i("id")?.let { saga(r, it.toString()) } } }.getOrNull() } }
            .awaitAll().filterNotNull().distinctBy { it.id }
    }

    suspend fun searchSagas(q: String): List<Saga> = runCatching {
        get("/search/collection", mapOf("query" to q)).arr("results")?.mapNotNull { it as? JsonObject }
            ?.filter { !it.s("poster_path").isNullOrBlank() }?.take(10)
            ?.mapNotNull { o -> o.i("id")?.let { saga(o, it.toString()) } } ?: emptyList()
    }.getOrDefault(emptyList())

    /** Une saga et ses films, dans l'ordre de sortie. */
    suspend fun collection(cid: String): Pair<Saga, List<MetaPreview>>? = runCatching {
        val o = get("/collection/$cid")
        val parts = o.arr("parts")?.mapNotNull { it as? JsonObject }?.sortedBy { it.s("release_date") ?: "9999" }
            ?.mapNotNull { preview(it, "movie") }?.filter { it.poster != null } ?: emptyList()
        saga(o, cid) to parts
    }.getOrNull()

    /** La saga à laquelle appartient un film (s'il y en a une avec au moins 2 films). */
    suspend fun sagaOf(type: String, id: String): Pair<Saga, List<MetaPreview>>? = runCatching {
        if (type != "movie") return@runCatching null
        val tid = when {
            id.startsWith("tmdb:") -> id.split(":").getOrNull(2)
            id.startsWith("tt") -> (get("/find/${id.substringBefore(":")}", mapOf("external_source" to "imdb_id"))
                .arr("movie_results")?.firstOrNull() as? JsonObject)?.i("id")?.toString()
            else -> null
        } ?: return@runCatching null
        val cid = (get("/movie/$tid")["belongs_to_collection"] as? JsonObject)?.i("id")?.toString() ?: return@runCatching null
        collection(cid)?.takeIf { it.second.size >= 2 }
    }.getOrNull()

    /** Acteurs et actrices du moment (avec photo). */
    suspend fun popularPeople(): List<Person> = runCatching {
        (get("/person/popular", mapOf("page" to "1")).arr("results").orEmpty() + get("/person/popular", mapOf("page" to "2")).arr("results").orEmpty())
            .mapNotNull { it as? JsonObject }.filter { !it.s("profile_path").isNullOrBlank() }
            .mapNotNull { o -> o.i("id")?.let { id ->
                val kf = (o.arr("known_for")?.firstOrNull() as? JsonObject)?.let { it.s("title") ?: it.s("name") }
                Person(id.toString(), o.s("name") ?: "", img(o.s("profile_path"), "w185"), kf) } }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    /** Image fraîche de chaque catégorie : un film très populaire du genre, au hasard parmi les premiers. */
    suspend fun genreBackdrops(names: List<String>): Map<String, String> = coroutineScope {
        names.map { g -> async { runCatching {
            val gid = MOVIE_GENRES[g] ?: return@runCatching null
            val r = get("/discover/movie", mapOf("with_genres" to gid.toString(), "sort_by" to "popularity.desc", "vote_count.gte" to "200"))
                .arr("results")?.mapNotNull { it as? JsonObject }?.filter { !it.s("backdrop_path").isNullOrBlank() }?.take(10).orEmpty()
            r.randomOrNull()?.s("backdrop_path")?.let { g to it }
        }.getOrNull() } }.awaitAll().filterNotNull().toMap()
    }

    /** Image de fond fraîche de chaque plateforme : un titre très populaire chez elle, au hasard parmi les premiers. */
    suspend fun providerBackdrops(ids: List<String>): Map<String, String> = coroutineScope {
        ids.map { pid -> async { runCatching {
            val r = get("/discover/tv", mapOf("watch_region" to "FR", "with_watch_providers" to pid, "with_watch_monetization_types" to "flatrate", "sort_by" to "popularity.desc"))
                .arr("results")?.mapNotNull { it as? JsonObject }?.filter { !it.s("backdrop_path").isNullOrBlank() }?.take(8).orEmpty()
            r.randomOrNull()?.s("backdrop_path")?.let { pid to it }
        }.getOrNull() } }.awaitAll().filterNotNull().toMap()
    }

    suspend fun trending(type: String): List<MetaPreview> = runCatching {
        get("/trending/${kind(type)}/week").arr("results")?.mapNotNull { (it as? JsonObject)?.let { o -> preview(o, type) } }
            ?.filter { it.poster != null } ?: emptyList()
    }.getOrDefault(emptyList())

    /** Clé YouTube de la bande-annonce en français (VF uniquement), ou null s'il n'y en a pas. */
    suspend fun trailerVF(type: String, id: String): String? = runCatching {
        val k = kind(type)
        val tid = when {
            id.startsWith("tmdb:") -> id.split(":").getOrNull(2)
            id.startsWith("tt") -> {
                val f = get("/find/${id.substringBefore(":")}", mapOf("external_source" to "imdb_id"))
                (f.arr(if (k == "tv") "tv_results" else "movie_results")?.firstOrNull() as? JsonObject)?.i("id")?.toString()
            }
            else -> null
        } ?: return@runCatching null
        val vids = get("/$k/$tid/videos", mapOf("language" to "fr-FR")).arr("results")?.mapNotNull { it as? JsonObject } ?: emptyList()
        val fr = vids.filter { it.s("site") == "YouTube" && it.s("iso_639_1") == "fr" && !it.s("key").isNullOrBlank() }
        (fr.firstOrNull { it.s("type") == "Trailer" } ?: fr.firstOrNull { it.s("type") == "Teaser" } ?: fr.firstOrNull())?.s("key")
    }.getOrNull()

    data class TVideo(val key: String, val name: String, val type: String, val year: String?)
    /** Toutes les vidéos YouTube en français (bandes-annonces, teasers, extraits, making-of…). */
    suspend fun videosFr(type: String, id: String): List<TVideo> = runCatching {
        val k = kind(type)
        val tid = when {
            id.startsWith("tmdb:") -> id.split(":").getOrNull(2)
            id.startsWith("tt") -> (get("/find/${id.substringBefore(":")}", mapOf("external_source" to "imdb_id"))
                .arr(if (k == "tv") "tv_results" else "movie_results")?.firstOrNull() as? JsonObject)?.i("id")?.toString()
            else -> null
        } ?: return@runCatching emptyList()
        (get("/$k/$tid/videos", mapOf("language" to "fr-FR")).arr("results")?.mapNotNull { it as? JsonObject } ?: emptyList())
            .filter { it.s("site") == "YouTube" && it.s("iso_639_1") == "fr" && !it.s("key").isNullOrBlank() && !Regex("vost|sous-titr|subtitled|\\bvo\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.s("name") ?: "") }
            .map { TVideo(it.s("key")!!, it.s("name") ?: "Vidéo", it.s("type") ?: "Trailer", it.s("published_at")?.take(4)) }
    }.getOrDefault(emptyList())

    /** French details on top of an add-on's meta (or a TMDB-only meta when [base] is null). */
    suspend fun enrich(type: String, imdb: String?, tmdbId: String?, base: MetaDetail?): MetaDetail? = runCatching {
        val k = kind(type)
        val tid = tmdbId ?: imdb?.let { tt ->
            val f = get("/find/$tt", mapOf("external_source" to "imdb_id"))
            (f.arr(if (k == "tv") "tv_results" else "movie_results")?.firstOrNull() as? JsonObject)?.i("id")?.toString()
        } ?: return@runCatching base
        val d = get("/$k/$tid", mapOf("append_to_response" to "credits,images," + (if (k == "tv") "content_ratings" else "release_dates"), "include_image_language" to "fr,en,null"))
        val logos = d.obj("images")?.arr("logos")?.mapNotNull { it as? JsonObject } ?: emptyList()
        val logo = (logos.firstOrNull { it.s("iso_639_1") == "fr" } ?: logos.firstOrNull { it.s("iso_639_1") == "en" })?.s("file_path")
        val frGenres = d.arr("genres")?.mapNotNull { (it as? JsonObject)?.s("name") } ?: emptyList()
        val castObjs = d.obj("credits")?.arr("cast")?.mapNotNull { it as? JsonObject }?.take(15) ?: emptyList()
        val cast = castObjs.mapNotNull { it.s("name") }.take(6)
        val crewObjs = d.obj("credits")?.arr("crew")?.mapNotNull { it as? JsonObject } ?: emptyList()
        val dirObj = crewObjs.firstOrNull { it.s("job") == "Director" }
        val dirPerson = dirObj?.i("id")?.let { Person(it.toString(), dirObj.s("name") ?: "", img(dirObj.s("profile_path"), "w185"), "Réalisateur") }
        val people = listOfNotNull(dirPerson) + castObjs.mapNotNull { c -> c.i("id")?.let { Person(it.toString(), c.s("name") ?: "", img(c.s("profile_path"), "w185"), c.s("character")) } }
        val runtime = d.i("runtime") ?: (d.arr("episode_run_time")?.firstOrNull() as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
        val date = d.s("release_date") ?: d.s("first_air_date")
        val p0 = base?.preview
        val id = imdb ?: p0?.id ?: "tmdb:$type:$tid"
        val preview = MetaPreview(
            id = id, type = type,
            name = d.s("title") ?: d.s("name") ?: p0?.name ?: "",
            poster = img(d.s("poster_path"), "w500") ?: p0?.poster,
            background = img((d.obj("images")?.arr("backdrops")?.mapNotNull { it as? JsonObject }?.firstOrNull { it.s("iso_639_1") == null })?.s("file_path") ?: d.s("backdrop_path"), "w1280") ?: p0?.background,
            logo = img(logo, "w500") ?: p0?.logo,
            description = d.s("overview"), // français uniquement
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
                v.copy(title = name ?: "Épisode ${v.episode ?: ""}", overview = e.s("overview"),
                    thumbnail = img(e.s("still_path"), "w300") ?: v.thumbnail, released = v.released ?: e.s("air_date"))
            }
        }
        val cert = if (k == "tv") d.obj("content_ratings")?.arr("results")?.mapNotNull { it as? JsonObject }?.firstOrNull { it.s("iso_3166_1") == "FR" }?.s("rating")
            else d.obj("release_dates")?.arr("results")?.mapNotNull { it as? JsonObject }?.firstOrNull { it.s("iso_3166_1") == "FR" }
                ?.arr("release_dates")?.mapNotNull { (it as? JsonObject)?.s("certification") }?.firstOrNull { it.isNotBlank() }
        val status = when (d.s("status")) { "Released" -> "SORTI"; "Returning Series" -> "EN COURS"; "Ended" -> "TERMINÉE"; "Canceled" -> "ANNULÉE"; "Planned", "In Production", "Post Production", "Rumored" -> "À VENIR"; else -> null }
        val ex = buildMap<String, String> {
            cert?.let { put("cert", if (it == "U" || it == "TP") "TP" else it) }
            status?.let { put("status", it) }
            (d.arr("production_countries")?.firstOrNull() as? JsonObject)?.s("name")?.let { put("country", it) }
            d.s("original_language")?.let { put("lang", it.uppercase()) }
            frDate(date)?.let { put("date", it) }
            date?.takeIf { it.length >= 10 }?.let { put("released", it.take(10)) }
            d.s("vote_average")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { put("tmdb", "%.1f".format(java.util.Locale.FRANCE, it * 10)) }
            // Fiche complète (comme le téléphone) : scénaristes, sociétés, pays et langue en toutes lettres, statut en français
            val writers = crewObjs.filter { it.s("job") in setOf("Screenplay", "Writer", "Story", "Novel", "Author", "Teleplay", "Characters") }.mapNotNull { it.s("name") }.distinct().take(3)
            if (writers.isNotEmpty()) put("writers", writers.joinToString(", "))
            val comps = d.arr("production_companies")?.mapNotNull { it as? JsonObject }?.filter { !it.s("logo_path").isNullOrBlank() }?.take(5)
                ?.joinToString(";;") { (it.s("name") ?: "") + "|" + (img(it.s("logo_path"), "w185") ?: "") }
            if (!comps.isNullOrBlank()) put("companies", comps)
            val iso = (d.arr("production_countries")?.firstOrNull() as? JsonObject)?.s("iso_3166_1") ?: d.arr("origin_country")?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            iso?.let { java.util.Locale("", it).getDisplayCountry(java.util.Locale.FRENCH).takeIf { n -> n.isNotBlank() }?.let { n -> put("countryName", n) } }
            d.s("original_language")?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale.FRENCH).takeIf { n -> n.isNotBlank() }?.let { n -> put("langName", n.replaceFirstChar { c -> c.uppercase() }) } }
            when (d.s("status")) { "Released" -> "Sorti"; "Returning Series" -> "En cours"; "Ended" -> "Terminée"; "Canceled" -> "Annulée"; "Planned" -> "À venir"
                "In Production" -> "En production"; "Post Production" -> "En post-production"; "Rumored" -> "Annoncé"; else -> null }?.let { put("statusFr", it) }
            if (k == "tv") { d.i("number_of_seasons")?.let { put("seasons", it.toString()) }; d.i("number_of_episodes")?.let { put("episodes", it.toString()) } }
        }
        MetaDetail(preview, runtime?.let { "$it min" } ?: base?.runtime, videos, cast.ifEmpty { base?.cast ?: emptyList() },
            base?.director?.takeIf { it.isNotEmpty() } ?: listOfNotNull(dirObj?.s("name")), base?.defaultVideoId, people, ex)
    }.getOrElse { base }

    data class PersonPage(val name: String, val photo: String?, val bio: String?, val known: String?, val works: List<MetaPreview>,
        val born: String? = null, val knownFor: List<MetaPreview> = emptyList(), val directed: Set<String> = emptySet())

    private val MONTHS = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
    private fun frDate(d: String?): String? {
        val p = d?.split("-")?.mapNotNull { it.toIntOrNull() } ?: return null
        if (p.size < 3 || p[1] !in 1..12) return null
        return "${if (p[2] == 1) "1er" else p[2].toString()} ${MONTHS[p[1] - 1]} ${p[0]}"
    }

    /** Filmographie d'un acteur (films et séries), en français. */
    suspend fun person(id: String): PersonPage? = runCatching {
        val d = get("/person/$id", mapOf("append_to_response" to "combined_credits"))
        val cc = d.obj("combined_credits")
        val cast = (cc?.arr("cast") ?: emptyList()).mapNotNull { it as? JsonObject }
        val crew = (cc?.arr("crew") ?: emptyList()).mapNotNull { it as? JsonObject }
        fun conv(o: JsonObject): Pair<MetaPreview, Double>? {
            val t = when (o.s("media_type")) { "tv" -> "series"; "movie" -> "movie"; else -> null } ?: return null
            if (t == "series" && (o.s("character")?.contains("Self", true) == true || o.i("episode_count")?.let { it < 2 } == true)) return null
            return preview(o, t)?.let { it to (o.s("popularity")?.toDoubleOrNull() ?: 0.0) }
        }
        val directed = crew.filter { it.s("job") in setOf("Director", "Writer", "Screenplay", "Creator") }.mapNotNull { conv(it)?.first?.id }.toSet()
        val works = (cast + crew).mapNotNull { conv(it) }.distinctBy { it.first.id }.sortedByDescending { it.second }.map { it.first }.filter { it.poster != null }
        val knownFor = cast.mapNotNull { conv(it) }.filter { it.first.poster != null && (it.first.imdbRating?.toDoubleOrNull() ?: 0.0) > 0 }
            .distinctBy { it.first.id }.sortedByDescending { it.second }.take(12).map { it.first }
        var bio = d.s("biography")
        if (bio.isNullOrBlank()) bio = runCatching { get("/person/$id", mapOf("language" to "en-US")).s("biography") }.getOrNull()
        val death = frDate(d.s("deathday")); val birth = frDate(d.s("birthday"))
        val year = d.s("birthday")?.take(4)?.toIntOrNull()
        val age = if (birth != null && year != null) (java.time.LocalDate.now().year - year).let { if (death != null) null else "$it ans" } else null
        val born = listOfNotNull(birth?.let { "Né(e) le $it" }, age, death?.let { "décédé(e) le $it" }, d.s("place_of_birth")).joinToString(" · ").ifBlank { null }
        PersonPage(d.s("name") ?: "", img(d.s("profile_path"), "w342"), bio, d.s("known_for_department"), works, born, knownFor, directed)
    }.getOrNull()
}
