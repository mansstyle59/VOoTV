package app.tvo.tv.data

import app.tvo.tv.Tvo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TV ⇄ téléphone ⇄ iPhone : profils, Ma liste, progression et add-ons.
 * Each device keeps a "space" id; pairing with a 6-digit code makes two devices share one space.
 * Merge rule: for every item the most recent change wins (deletions are kept as tombstones).
 */
object Sync {
    private const val BASE = "https://vfxblubjsxwseacmslnu.supabase.co/rest/v1/rpc/"
    private const val KEY = "sb_publishable_HojETqgwpBMQ_cjDzMNaAg_WGCFMaED"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val status = MutableStateFlow("")
    /** Vrai pendant qu'une vidéo est lue (synchro allégée). */
    @Volatile var playbackActive = false
    @Volatile private var lastPlaySync = 0L
    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        Tvo.store.onChange = { requests.tryEmit(Unit) }
        // Au plus une synchro toutes les 10 s, même pendant la lecture (position envoyée en continu)
        scope.launch { requests.collect {
            kotlinx.coroutines.delay(3_000)
            // Pendant la lecture : une synchro par minute au plus, pour ne jamais faire saccader la vidéo
            if (playbackActive && System.currentTimeMillis() - lastPlaySync < 60_000) return@collect
            if (playbackActive) lastPlaySync = System.currentTimeMillis()
            syncNow(); kotlinx.coroutines.delay(7_000)
        } }
        scope.launch { syncNow() }
    }

    fun requestSoon() { requests.tryEmit(Unit) }

    fun syncInBackground() { scope.launch { syncNow() } }

    private suspend fun rpc(fn: String, body: JsonElement): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(BASE + fn)
            .header("apikey", KEY).header("Authorization", "Bearer $KEY")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        Tvo.http.newCall(req).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            text
        }
    }

    suspend fun syncNow(): Boolean = mutex.withLock {
        runCatching {
            val store = Tvo.store
            val id = store.syncId()
            val raw = rpc("tvo_pull", buildJsonObject { put("p_id", id) }).trim()
            val remote = if (raw.isEmpty() || raw == "null") SyncDoc() else json.decodeFromString<SyncDoc>(raw)
            val merged = merge(store.exportDoc(), remote)
            store.importDoc(merged)
            rpc("tvo_push", buildJsonObject { put("p_id", id); put("p_doc", json.encodeToJsonElement(merged)) })
            store.lastSync = System.currentTimeMillis()
            status.value = "Synchronisé à " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())
        }.onFailure { status.value = "Synchronisation impossible (pas de connexion ?)" }.isSuccess
    }

    /** Shows a 6-digit code (valid 10 min) for another device to join this space. */
    suspend fun createCode(): String? = runCatching {
        syncNow()
        rpc("tvo_pair_create", buildJsonObject { put("p_id", Tvo.store.syncId()) }).trim().trim('"')
    }.getOrNull()

    /** Connexion rapide : la TV affiche un QR code, le téléphone connecté l'approuve. */
    data class LoginRequest(val code: String, val secret: String, val key: String)
    private fun randomToken(): String {
        val b = ByteArray(32); java.security.SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
    /** Deux secrets aléatoires : [secret] reste sur la TV, [key] voyage seulement dans le QR code. */
    suspend fun loginStart(): LoginRequest? = runCatching {
        val secret = randomToken(); val key = randomToken()
        val c = rpc("tvo_login_start", buildJsonObject { put("p_secret", secret); put("p_key", key) }).trim().trim('"')
        if (c.length == 6) LoginRequest(c, secret, key) else null
    }.getOrNull()

    /** Renvoie true quand le téléphone a validé : la TV rejoint alors son compte. */
    suspend fun loginPoll(req: LoginRequest): Boolean = runCatching {
        val raw = rpc("tvo_login_poll", buildJsonObject { put("p_code", req.code); put("p_secret", req.secret) }).trim().trim('"')
        if (raw.isEmpty() || raw == "null") return@runCatching false
        Tvo.store.setSyncId(raw); syncNow(); true
    }.getOrDefault(false)

    /** Statut du compte relié à cette TV : active, pending, disabled, expired, signed_out (null = pas de réseau). */
    suspend fun deviceStatus(): String? = runCatching {
        val raw = rpc("tvo_device_status", buildJsonObject { put("p_id", Tvo.store.syncId()) })
        kotlinx.serialization.json.Json.parseToJsonElement(raw).let { (it as? kotlinx.serialization.json.JsonObject)?.get("status")?.toString()?.trim('"') }
    }.getOrNull()

    /** Joins the space of the device that displays [code]; local data is merged into it. */
    suspend fun claim(code: String): Boolean = runCatching {
        val raw = rpc("tvo_pair_claim", buildJsonObject { put("p_code", code) }).trim().trim('"')
        if (raw.isEmpty() || raw == "null") return@runCatching false
        Tvo.store.setSyncId(raw)
        syncNow()
    }.getOrDefault(false)

    fun merge(a: SyncDoc, b: SyncDoc): SyncDoc {
        fun <V> mergeMap(x: Map<String, V>, y: Map<String, V>, t: (V) -> Long): Map<String, V> {
            val out = x.toMutableMap()
            y.forEach { (k, v) -> val cur = out[k]; if (cur == null || t(v) > t(cur)) out[k] = v }
            return out
        }
        val profiles = mergeMap(a.profiles, b.profiles) { it.t }
        val pids = (a.lib.keys + b.lib.keys + a.prog.keys + b.prog.keys).toSet()
        val lib = pids.associateWith { mergeMap(a.lib[it] ?: emptyMap(), b.lib[it] ?: emptyMap()) { l -> l.t } }
        val prog = pids.associateWith { mergeMap(a.prog[it] ?: emptyMap(), b.prog[it] ?: emptyMap()) { p -> p.updatedAt } }
            .mapValues { (_, m) -> m.values.sortedByDescending { it.updatedAt }.take(400).associateBy { it.videoId } }
        val (addons, addonsT) = if (b.addonsT > a.addonsT && !b.addons.isNullOrEmpty()) b.addons to b.addonsT else a.addons to a.addonsT
        val (tk, tt) = if (b.tmdbT > a.tmdbT) b.tmdbKey to b.tmdbT else a.tmdbKey to a.tmdbT
        return SyncDoc(1, profiles, addons, addonsT, lib.filterKeys { profiles[it]?.del != true }, prog.filterKeys { profiles[it]?.del != true }, tk, tt,
            listOfNotNull(a.home, b.home).maxByOrNull { it.t },
            mergeMap(a.bad, b.bad) { it }, listOfNotNull(a.qpref, b.qpref).maxByOrNull { it.t },
            if (b.porderT > a.porderT) b.porder else a.porder, maxOf(a.porderT, b.porderT))
    }

}
