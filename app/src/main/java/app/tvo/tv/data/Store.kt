package app.tvo.tv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

object Defaults {
    val GENRES = listOf("Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family",
        "Fantasy", "History", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller", "War", "Western")

    val cinemeta = Addon(
        url = "https://v3-cinemeta.strem.io",
        manifest = AddonManifest(
            id = "com.linvo.cinemeta", name = "Cinemeta", version = "3.0",
            description = "Catalogues et fiches des films et séries",
            types = listOf("movie", "series"),
            resources = listOf(AddonResource("catalog"), AddonResource("meta")),
            catalogs = listOf(
                CatalogDef("movie", "top", "Popular", listOf(ExtraDef("genre", options = GENRES), ExtraDef("search"), ExtraDef("skip"))),
                CatalogDef("series", "top", "Popular", listOf(ExtraDef("genre", options = GENRES), ExtraDef("search"), ExtraDef("skip"))),
                CatalogDef("movie", "imdbRating", "Featured", listOf(ExtraDef("genre", options = GENRES), ExtraDef("skip"))),
                CatalogDef("series", "imdbRating", "Featured", listOf(ExtraDef("genre", options = GENRES), ExtraDef("skip"))),
            ),
            idPrefixes = listOf("tt"),
        ),
    )

    val suggested = listOf(
        "https://opensubtitles-v3.strem.io/manifest.json" to "Sous-titres OpenSubtitles (gratuit)",
    )

    val profileColors = listOf(0xFF8B5CF6, 0xFF22D3EE, 0xFFF97316, 0xFF10B981, 0xFFEC4899, 0xFFEAB308, 0xFF3B82F6, 0xFFEF4444)
}

class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("tvo", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private inline fun <reified T> load(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> save(key: String, value: T) =
        prefs.edit().putString(key, json.encodeToString(value)).apply()

    val profiles = MutableStateFlow(load<List<Profile>>("profiles") ?: emptyList())
    val addons = MutableStateFlow(load<List<Addon>>("addons") ?: listOf(Defaults.cinemeta))
    val currentProfile = MutableStateFlow<Profile?>(null)
    val library = MutableStateFlow<List<LibraryItem>>(emptyList())
    val progress = MutableStateFlow<List<Progress>>(emptyList())
    val externalPlayer = MutableStateFlow(prefs.getBoolean("externalPlayer", false))

    // ---- profiles ----
    fun selectProfile(p: Profile) {
        currentProfile.value = p
        library.value = load("library_${p.id}") ?: emptyList()
        progress.value = load("progress_${p.id}") ?: emptyList()
    }

    fun upsertProfile(id: String?, name: String, color: Long): Profile {
        val p = Profile(id ?: UUID.randomUUID().toString(), name.trim().ifBlank { "Profil" }, color)
        val list = profiles.value.toMutableList()
        val idx = list.indexOfFirst { it.id == p.id }
        if (idx >= 0) list[idx] = p else list.add(p)
        profiles.value = list; save("profiles", list)
        if (currentProfile.value?.id == p.id) currentProfile.value = p
        return p
    }

    fun deleteProfile(id: String) {
        val list = profiles.value.filterNot { it.id == id }
        profiles.value = list; save("profiles", list)
        prefs.edit().remove("library_$id").remove("progress_$id").apply()
        if (currentProfile.value?.id == id) currentProfile.value = null
    }

    // ---- library ----
    fun inLibrary(id: String) = library.value.any { it.id == id }

    fun toggleLibrary(item: LibraryItem) {
        val pid = currentProfile.value?.id ?: return
        val list = if (inLibrary(item.id)) library.value.filterNot { it.id == item.id }
        else listOf(item.copy(addedAt = System.currentTimeMillis())) + library.value
        library.value = list; save("library_$pid", list)
    }

    // ---- progress ----
    fun saveProgress(profileId: String, p: Progress) {
        val current = if (currentProfile.value?.id == profileId) progress.value else load("progress_$profileId") ?: emptyList()
        val list = (listOf(p) + current.filterNot { it.videoId == p.videoId }).take(400)
        if (currentProfile.value?.id == profileId) progress.value = list
        save("progress_$profileId", list)
    }

    fun removeFromContinue(metaId: String) {
        val pid = currentProfile.value?.id ?: return
        val list = progress.value.map { if (it.metaId == metaId) it.copy(position = it.duration) else it }
        progress.value = list; save("progress_$pid", list)
    }

    /** One entry per title, most recent first, only unfinished. */
    fun continueWatching(list: List<Progress>): List<Progress> =
        list.sortedByDescending { it.updatedAt }.distinctBy { it.metaId }
            .filter { it.fraction in 0.02f..0.92f }.take(20)

    // ---- addons ----
    fun setAddons(list: List<Addon>) { addons.value = list; save("addons", list) }

    fun addAddon(a: Addon) {
        val list = addons.value.toMutableList()
        val idx = list.indexOfFirst { it.manifest.id == a.manifest.id || it.url == a.url }
        if (idx >= 0) list[idx] = a else list.add(a)
        setAddons(list)
    }

    fun removeAddon(url: String) = setAddons(addons.value.filterNot { it.url == url })

    fun moveAddon(url: String, delta: Int) {
        val list = addons.value.toMutableList()
        val i = list.indexOfFirst { it.url == url }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i)); setAddons(list)
    }

    fun setExternalPlayer(v: Boolean) { externalPlayer.value = v; prefs.edit().putBoolean("externalPlayer", v).apply() }
}
