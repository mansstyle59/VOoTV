package app.tvo.tv.data

import kotlinx.serialization.Serializable

@Serializable
data class Profile(val id: String, val name: String, val color: Long, val t: Long = 0)

@Serializable
data class AddonResource(
    val name: String,
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null,
)

@Serializable
data class ExtraDef(val name: String, val isRequired: Boolean = false, val options: List<String> = emptyList())

@Serializable
data class CatalogDef(
    val type: String,
    val id: String,
    val name: String,
    val extra: List<ExtraDef> = emptyList(),
    val extraRequired: List<String> = emptyList(),
    val extraSupported: List<String> = emptyList(),
) {
    val requiresExtra: Boolean get() = extra.any { it.isRequired } || extraRequired.isNotEmpty()
    fun supports(name: String) = extra.any { it.name == name } || extraSupported.contains(name)
    fun options(name: String) = extra.firstOrNull { it.name == name }?.options ?: emptyList()
    /** True if the only required extra is [name]. */
    fun onlyRequires(name: String): Boolean {
        val req = extra.filter { it.isRequired }.map { it.name } + extraRequired
        return req.all { it == name }
    }
}

@Serializable
data class AddonManifest(
    val id: String,
    val name: String,
    val version: String = "",
    val description: String = "",
    val logo: String? = null,
    val types: List<String> = emptyList(),
    val resources: List<AddonResource> = emptyList(),
    val catalogs: List<CatalogDef> = emptyList(),
    val idPrefixes: List<String>? = null,
    val configurable: Boolean = false,
)

@Serializable
data class Addon(val url: String, val manifest: AddonManifest)

@Serializable
data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
)

data class Person(val id: String, val name: String, val photo: String?, val role: String?)

data class Video(
    val id: String,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val released: String?,
    val thumbnail: String?,
    val overview: String?,
)

data class MetaDetail(
    val preview: MetaPreview,
    val runtime: String?,
    val videos: List<Video>,
    val cast: List<String>,
    val director: List<String>,
    val defaultVideoId: String?,
    val people: List<Person> = emptyList(),
) {
    /** Episodes in watching order: regular seasons first, specials (season 0) last. */
    val episodes: List<Video>
        get() = videos.filter { it.season != null }
            .sortedWith(compareBy<Video>({ if ((it.season ?: 0) == 0) Int.MAX_VALUE else it.season }, { it.episode ?: 0 }))
}

@Serializable
data class SubtitleItem(val url: String, val lang: String)

data class StreamItem(
    val addonName: String,
    val name: String,
    val title: String,
    val url: String?,
    val externalUrl: String?,
    val infoHash: String?,
    val headers: Map<String, String>,
    val subtitles: List<SubtitleItem>,
    val bingeGroup: String?,
    val quality: Int,
) {
    val playable: Boolean get() = url != null || externalUrl != null
}

@Serializable
data class LibraryItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val addedAt: Long = 0,
) {
    fun toPreview() = MetaPreview(id = id, type = type, name = name, poster = poster, background = background)
}

@Serializable
data class Progress(
    val videoId: String,
    val metaId: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val position: Long = 0,
    val duration: Long = 0,
    val updatedAt: Long = 0,
) {
    val fraction: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val finished: Boolean get() = fraction >= 0.92f
}

@Serializable
data class PlayRequest(
    val profileId: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleItem> = emptyList(),
    val title: String,
    val subtitle: String? = null,
    val metaId: String,
    val type: String,
    val videoId: String,
    val poster: String? = null,
    val background: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val startPosition: Long = 0,
    val bingeGroup: String? = null,
    val addonName: String? = null,
)

// ---------- sync document (same JSON shape on TV, Android and iPhone) ----------
@Serializable
data class SyncProfile(val id: String, val name: String, val color: String, val t: Long = 0, val del: Boolean = false)

@Serializable
data class SyncLib(val item: LibraryItem, val t: Long = 0, val del: Boolean = false)

@Serializable
data class SyncDoc(
    val v: Int = 1,
    val profiles: Map<String, SyncProfile> = emptyMap(),
    val addons: List<Addon>? = null,
    val addonsT: Long = 0,
    val lib: Map<String, Map<String, SyncLib>> = emptyMap(),
    val prog: Map<String, Map<String, Progress>> = emptyMap(),
    val tmdbKey: String? = null,
    val tmdbT: Long = 0,
    val home: HomeLayout? = null,
)

@Serializable
data class HomeLayout(val order: List<String> = emptyList(), val hidden: List<String> = emptyList(), val t: Long = 0)
