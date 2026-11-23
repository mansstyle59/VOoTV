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

    /** Called after every local change so the sync engine can push it. */
    var onChange: () -> Unit = {}

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

    private fun now() = System.currentTimeMillis()

    // ---- profiles ----
    fun selectProfile(p: Profile) {
        currentProfile.value = p
        library.value = load("library_${p.id}") ?: emptyList()
        progress.value = load("progress_${p.id}") ?: emptyList()
    }

    fun upsertProfile(id: String?, name: String, color: Long): Profile {
        val p = Profile(id ?: UUID.randomUUID().toString(), name.trim().ifBlank { "Profil" }, color, now())
        val list = profiles.value.toMutableList()
        val idx = list.indexOfFirst { it.id == p.id }
        if (idx >= 0) list[idx] = p else list.add(p)
        profiles.value = list; save("profiles", list)
        if (currentProfile.value?.id == p.id) currentProfile.value = p
        onChange()
        return p
    }

    fun deleteProfile(id: String) {
        val list = profiles.value.filterNot { it.id == id }
        profiles.value = list; save("profiles", list)
        save("profdel", (load<Map<String, Long>>("profdel") ?: emptyMap()) + (id to now()))
        prefs.edit().remove("library_$id").remove("progress_$id").remove("libdel_$id").apply()
        if (currentProfile.value?.id == id) currentProfile.value = null
        onChange()
    }

    // ---- library ----
    fun inLibrary(id: String) = library.value.any { it.id == id }

    fun toggleLibrary(item: LibraryItem) {
        val pid = currentProfile.value?.id ?: return
        val del = (load<Map<String, Long>>("libdel_$pid") ?: emptyMap()).toMutableMap()
        val list = if (inLibrary(item.id)) {
            del[item.id] = now()
            library.value.filterNot { it.id == item.id }
        } else {
            del.remove(item.id)
            listOf(item.copy(addedAt = now())) + library.value
        }
        library.value = list; save("library_$pid", list); save("libdel_$pid", del)
        onChange()
    }

    // ---- progress ----
    fun saveProgress(profileId: String, p: Progress) {
        val current = if (currentProfile.value?.id == profileId) progress.value else load("progress_$profileId") ?: emptyList()
        val list = (listOf(p) + current.filterNot { it.videoId == p.videoId }).take(400)
        if (currentProfile.value?.id == profileId) progress.value = list
        save("progress_$profileId", list)
        onChange()
    }

    fun removeFromContinue(metaId: String) {
        val pid = currentProfile.value?.id ?: return
        val t = now()
        val list = progress.value.map { if (it.metaId == metaId) it.copy(position = it.duration, updatedAt = t) else it }
        progress.value = list; save("progress_$pid", list)
        onChange()
    }

    /** One entry per title, most recent first, only unfinished. */
    fun continueWatching(list: List<Progress>): List<Progress> =
        list.sortedByDescending { it.updatedAt }.distinctBy { it.metaId }
            .filter { it.fraction in 0.02f..0.92f }.take(20)

    // ---- addons ----
    fun setAddons(list: List<Addon>, userChange: Boolean = true) {
        addons.value = list; save("addons", list)
        if (userChange) { prefs.edit().putLong("addons_t", now()).apply(); onChange() }
    }

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

    // ---- sync ----
    fun syncId(): String = prefs.getString("sync_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("sync_id", it).apply() }
    fun setSyncId(id: String) { prefs.edit().putString("sync_id", id).apply() }
    var lastSync: Long
        get() = prefs.getLong("last_sync", 0)
        set(v) { prefs.edit().putLong("last_sync", v).apply() }

    private fun hex(c: Long) = "#%06X".format(c and 0xFFFFFF)
    private fun color(h: String) = runCatching { 0xFF000000 or h.removePrefix("#").toLong(16) }.getOrDefault(Defaults.profileColors[0])

    @Synchronized
    fun exportDoc(): SyncDoc {
        val profs = mutableMapOf<String, SyncProfile>()
        profiles.value.forEach { profs[it.id] = SyncProfile(it.id, it.name, hex(it.color), it.t) }
        (load<Map<String, Long>>("profdel") ?: emptyMap()).forEach { (id, t) -> if (id !in profs) profs[id] = SyncProfile(id, "", "#8B5CF6", t, true) }
        val lib = mutableMapOf<String, Map<String, SyncLib>>()
        val prog = mutableMapOf<String, Map<String, Progress>>()
        profiles.value.forEach { p ->
            val m = mutableMapOf<String, SyncLib>()
            (load<List<LibraryItem>>("library_${p.id}") ?: emptyList()).forEach { m[it.id] = SyncLib(it, it.addedAt) }
            (load<Map<String, Long>>("libdel_${p.id}") ?: emptyMap()).forEach { (id, t) ->
                val cur = m[id]
                if (cur == null || cur.t < t) m[id] = SyncLib(cur?.item ?: LibraryItem(id, "movie", ""), t, true)
            }
            lib[p.id] = m
            prog[p.id] = (load<List<Progress>>("progress_${p.id}") ?: emptyList()).associateBy { it.videoId }
        }
        return SyncDoc(1, profs, addons.value, prefs.getLong("addons_t", 0), lib, prog)
    }

    @Synchronized
    fun importDoc(d: SyncDoc) {
        // profiles (keep local order, append new ones)
        val alive = d.profiles.values.filter { !it.del }
        val order = profiles.value.map { it.id }
        val list = alive.sortedBy { order.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .map { Profile(it.id, it.name, color(it.color), it.t) }
        profiles.value = list; save("profiles", list)
        save("profdel", d.profiles.values.filter { it.del }.associate { it.id to it.t })
        list.forEach { p ->
            val l = d.lib[p.id] ?: emptyMap()
            save("library_${p.id}", l.values.filter { !it.del }.map { it.item }.sortedByDescending { it.addedAt })
            save("libdel_${p.id}", l.values.filter { it.del }.associate { it.item.id to it.t })
            save("progress_${p.id}", (d.prog[p.id] ?: emptyMap()).values.sortedByDescending { it.updatedAt }.take(400))
        }
        if (d.addonsT > prefs.getLong("addons_t", 0) && d.addons != null && d.addons.isNotEmpty()) {
            addons.value = d.addons; save("addons", d.addons); prefs.edit().putLong("addons_t", d.addonsT).apply()
        }
        currentProfile.value?.let { cur -> list.firstOrNull { it.id == cur.id }?.let { selectProfile(it) } ?: run { currentProfile.value = null } }
    }
}
