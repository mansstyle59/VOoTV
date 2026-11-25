package app.tvo.tv.ui

import androidx.compose.material.icons.filled.PlayArrow

import androidx.compose.ui.draw.alpha

import androidx.compose.runtime.mutableStateMapOf
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.tvo.tv.Tvo
import app.tvo.tv.data.AddonClient
import app.tvo.tv.data.MetaDetail
import app.tvo.tv.data.PlayRequest
import app.tvo.tv.data.StreamItem
import app.tvo.tv.player.PlayerActivity
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StreamsScreen(type: String, metaId: String, videoId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rawA by Tvo.store.addons.collectAsState()
    val addons = remember(rawA) { Tvo.store.allowed(rawA) }
    val meta by produceState<MetaDetail?>(Tvo.client.metaCache["$type/$metaId"], metaId) {
        if (value == null) value = runCatching { Tvo.client.meta(addons, type, metaId) }.getOrNull()
    }
    val video = meta?.videos?.firstOrNull { it.id == videoId }
    val streams = remember(videoId) { mutableStateListOf<StreamItem>() }
    val total = remember(addons, videoId) { Tvo.client.streamAddonCount(addons, type, videoId) }
    var done by remember(videoId) { mutableIntStateOf(0) }
    var starting by remember { mutableStateOf(false) }
    val firstFr = remember { FocusRequester() }
    var focusedOnce by remember(videoId) { mutableStateOf(false) }

    LaunchedEffect(videoId, addons) {
        Tvo.client.streams(addons, type, videoId).collect { res ->
            streams.addAll(res.items)
            done++
        }
    }
    // Test rapide de chaque lien (en-têtes seulement, sans télécharger la vidéo) :
    // temps de réponse + détection des liens qui ne mènent pas à une vraie vidéo.
    data class T(val ok: Boolean, val ms: Long, val lock: String? = null)
    val tests = remember(videoId) { mutableStateMapOf<String, T>() }
    val sem = remember { kotlinx.coroutines.sync.Semaphore(6) }
    val testHttp = remember { Tvo.http.newBuilder().connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).readTimeout(5, java.util.concurrent.TimeUnit.SECONDS).followRedirects(true).build() }
    LaunchedEffect(streams.size) {
        streams.filter { it.url?.startsWith("http") == true && it.url !in tests }.forEach { st ->
            val u = st.url!!; tests[u] = T(false, -1L)
            launch(kotlinx.coroutines.Dispatchers.IO) {
                sem.acquire()
                try {
                    val t0 = System.currentTimeMillis()
                    val req = okhttp3.Request.Builder().url(u).head().apply { st.headers.forEach { (k, v) -> header(k, v) } }.build()
                    val res = runCatching { testHttp.newCall(req).execute().use { r ->
                        val ct = r.header("Content-Type").orEmpty().lowercase()
                        val len = r.header("Content-Length")?.toLongOrNull() ?: -1L
                        val ok = r.isSuccessful || r.code == 405 || r.code == 206
                        val hls = ".m3u8" in u || "mpegurl" in ct
                        val lock = when {
                            !ok -> "Hors ligne"
                            ct.startsWith("text/html") || "json" in ct -> "Demande un service payant"
                            !hls && len in 1..60_000_000L -> "Vidéo d'attente (service payant requis)"
                            else -> null
                        }
                        T(ok && lock == null, System.currentTimeMillis() - t0, lock)
                    } }.getOrDefault(T(false, 0, "Hors ligne"))
                    tests[u] = res
                } finally { sem.release() }
            }
        }
    }
    fun langOf(s: StreamItem): String { val t = (s.name + " " + s.title).lowercase()
        return when { Regex("\\bmulti\\b").containsMatchIn(t) -> "MULTI"; Regex("\\b(vff|vfq|vf2?|french|truefrench)\\b").containsMatchIn(t) -> "VF"; "vostfr" in t -> "VOSTFR"; else -> "" } }
    /** Raison du blocage (cadenas) ou null si la source est utilisable. */
    fun lockOf(s: StreamItem): String? {
        val t = (s.name + " " + s.title).lowercase()
        if ("⬇" in t || "uncached" in t || "non en cache" in t || "[dl]" in t) return "Pas disponible tout de suite (service payant)"
        if (s.url == null) return null
        return tests[s.url]?.lock
    }
    fun score(s: StreamItem): Long { val r = s.url?.let { tests[it] }
        val alive = when { r == null -> 1; r.ms < 0 -> 1; r.ok -> 2; else -> 0 }
        return alive * 10_000_000L + (if (langOf(s).isNotEmpty()) 1_000_000L else 0L) + s.quality * 100_000L - (if (r != null && r.ok) r.ms.coerceAtMost(99_999) else 50_000) }
    var qFilter by remember(videoId) { mutableStateOf<Int?>(null) }
    var showLocked by remember(videoId) { mutableStateOf(false) }
    var reportFor by remember { mutableStateOf<StreamItem?>(null) }
    val badMap by Tvo.store.badSources.collectAsState()
    val qprefV by Tvo.store.qpref.collectAsState()
    val usableAll = streams.filter { it.playable && !Tvo.store.isBad(it.url) }.sortedByDescending { score(it) }
    val qOk = usableAll.filter { Tvo.store.qpass(it.quality) }
    val qFallback = qprefV.v != "all" && qOk.isEmpty() && usableAll.isNotEmpty()
    val sorted = if (qFallback) usableAll else qOk
    val all = sorted.filter { lockOf(it) == null }
    val locked = sorted.filter { lockOf(it) != null }
    val playable = all.filter { qFilter == null || it.quality == qFilter }
    val torrents = streams.count { !it.playable }
    fun start(s: StreamItem) {
        if (starting) return
        val m = meta ?: return
        starting = true
        scope.launch { play(ctx, m, s, videoId, addons); starting = false }
    }

    Row(Modifier.fillMaxSize().background(TvoColors.Bg)) {
        Column(Modifier.width(320.dp).padding(start = 48.dp, top = 48.dp, end = 24.dp)) {
            val p = meta?.preview
            AsyncImage(p?.poster, p?.name, Modifier.width(220.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(TvoColors.Surface), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(16.dp))
            Text(p?.name ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text, maxLines = 2)
            if (video?.season != null) Text("Saison ${video.season} · Épisode ${video.episode}", fontSize = 15.sp, color = TvoColors.Cyan)
            if (video != null && video.title.isNotBlank()) Text(video.title, fontSize = 15.sp, color = TvoColors.Muted, maxLines = 2)
        }
        Column(Modifier.fillMaxSize().padding(top = 48.dp, end = 48.dp)) {
            Text("Choisis une source", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            val status = when {
                total == 0 -> "Aucun add-on de sources installé."
                done < total -> "Recherche en cours… ($done/$total add-ons)"
                else -> "${all.size} source(s) utilisable(s)" + (if (locked.isNotEmpty()) " · ${locked.size} bloquée(s) 🔒" else "") + if (torrents > 0) " · $torrents torrent(s) non pris en charge" else ""
            }
            Text(status + (if (qprefV.v != "all") " · qualité : " + (mapOf("4k" to "4K", "1080" to "1080p", "720" to "720p et moins")[qprefV.v] ?: "") + (if (qFallback) " (aucune source, toutes affichées)" else "") else ""), Modifier.padding(top = 4.dp, bottom = 4.dp), fontSize = 14.sp, color = TvoColors.Muted)
            Text("Appui long sur une source pour la signaler si elle ne marche pas", Modifier.padding(bottom = 12.dp), fontSize = 12.sp, color = TvoColors.Muted)
            if (starting) Text("Préparation de la lecture…", fontSize = 16.sp, color = TvoColors.Cyan)
            if (all.isNotEmpty()) {
                val pending = tests.values.count { it.ms < 0 }
                Text(if (pending > 0) "Test des liens… ($pending en cours) · classés par langue, qualité et vitesse" else "Liens testés · classés par langue, qualité et vitesse",
                    fontSize = 13.sp, color = TvoColors.Cyan, modifier = Modifier.padding(bottom = 8.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    item { Chip("Tout (${all.size})", qFilter == null, { qFilter = null }) }
                    listOf(4, 3, 2, 1, 0).forEach { q -> val n = all.count { it.quality == q }
                        if (n > 0) item { Chip("${if (q == 0) "Autres" else AddonClient.qualityLabel(q)} ($n)", qFilter == q, { qFilter = q }) } }
                }
            }
            if (playable.isEmpty() && (done >= total)) {
                Text(
                    if (total == 0) "Ajoute un add-on qui fournit des vidéos dans Réglages > Add-ons (comme dans Nuvio ou Stremio)."
                    else if (locked.isNotEmpty()) "Toutes les sources trouvées sont bloquées 🔒 : elles demandent un service payant ou ne répondent pas. Essaie un autre add-on."
                    else "Aucune source lisible pour ce titre. Essaie un autre add-on.",
                    fontSize = 16.sp, color = TvoColors.Text, modifier = Modifier.width(560.dp),
                )
            }
            if (all.isNotEmpty()) {
                val b = all.first()
                TvoButton("Lecture rapide · ${AddonClient.qualityLabel(b.quality)}${langOf(b).let { if (it.isNotEmpty()) " $it" else "" }}", { start(b) },
                    Modifier.focusRequester(firstFr).padding(bottom = 12.dp), androidx.compose.material.icons.Icons.Default.PlayArrow, primary = true)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                itemsIndexed(playable, key = { i, s -> "${s.addonName}|${s.url ?: s.externalUrl}|$i" }) { i, s ->
                    val r = s.url?.let { tests[it] }
                    val badge = when { s.url == null -> "Lien externe"; r == null || r.ms < 0 -> "Test…"
                        r.ms < 1000 -> "⚡ ${r.ms} ms"; else -> "⚡ ${"%.1f".format(r.ms / 1000.0)} s" }
                    StreamRow(s, badge, langOf(s), i == 0 && qFilter == null, Modifier, onLongClick = { if (s.url != null) reportFor = s }) { start(s) }
                }
                if (locked.isNotEmpty()) item(key = "lockhead") {
                    Chip(if (showLocked) "🔒 Masquer les sources bloquées (${locked.size})" else "🔒 Sources bloquées (${locked.size}) · voir pourquoi", showLocked, { showLocked = !showLocked })
                }
                if (showLocked) itemsIndexed(locked, key = { i, s -> "L|${s.addonName}|${s.url}|$i" }) { _, s ->
                    StreamRow(s, "🔒 " + (lockOf(s) ?: "Indisponible"), langOf(s), false, Modifier, locked = true) {
                        android.widget.Toast.makeText(ctx, "Source bloquée : ${lockOf(s)}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    reportFor?.let { rs ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { reportFor = null }) {
            Column(Modifier.width(480.dp).clip(RoundedCornerShape(16.dp)).background(TvoColors.Surface).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Signaler cette source ?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(rs.name.replace("\n", " "), fontSize = 14.sp, color = Color(0xCCFFFFFF), maxLines = 2)
                Text("Elle disparaît de la liste (sur la télé et le téléphone) jusqu'à sa mise à jour.", fontSize = 14.sp, color = Color(0xCCFFFFFF))
                val fr = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                TvoButton("Signaler", { rs.url?.let { Tvo.store.reportSource(it) }
                    android.widget.Toast.makeText(ctx, "Source signalée : retirée de la liste", android.widget.Toast.LENGTH_SHORT).show(); reportFor = null },
                    Modifier.focusRequester(fr).fillMaxWidth(), primary = true, wide = true)
                TvoButton("Annuler", { reportFor = null }, Modifier.fillMaxWidth(), wide = true)
            }
        }
    }
    LaunchedEffect(all.isNotEmpty()) {
        if (all.isNotEmpty() && !focusedOnce) { focusedOnce = true; delay(60); runCatching { firstFr.requestFocus() } }
    }
}

suspend fun play(ctx: Context, meta: MetaDetail, s: StreamItem, videoId: String, addons: List<app.tvo.tv.data.Addon>) {
    val store = Tvo.store
    val p = meta.preview
    if (s.url == null && s.externalUrl != null) {
        openExternal(ctx, Uri.parse(s.externalUrl), null)
        return
    }
    val url = s.url ?: return
    store.saveLastStream(videoId, s)
    val video = meta.videos.firstOrNull { it.id == videoId }
    val prog = store.progress.value.firstOrNull { it.videoId == videoId }
    val start = if (prog != null && !prog.finished && prog.fraction > 0.02f) prog.position else 0L
    if (store.externalPlayer.value) {
        openExternal(ctx, Uri.parse(url), if (video?.season != null) "${p.name} S${video.season}E${video.episode}" else p.name)
        return
    }
    val extraSubs = runCatching { Tvo.client.subtitles(addons, p.type, videoId) }.getOrDefault(emptyList())
    val req = PlayRequest(
        profileId = store.currentProfile.value?.id ?: "default",
        url = url,
        headers = s.headers,
        subtitles = (s.subtitles + extraSubs).distinctBy { it.url }.take(40),
        title = p.name,
        subtitle = video?.let { if (it.season != null) "S${it.season} : É${it.episode} · ${it.title}" else null },
        metaId = p.id,
        type = p.type,
        videoId = videoId,
        poster = p.poster,
        background = p.background,
        season = video?.season,
        episode = video?.episode,
        episodeTitle = video?.title,
        startPosition = start,
        bingeGroup = s.bingeGroup,
        addonName = s.addonName,
    )
    PlayerActivity.start(ctx, req)
}

/** Reprise rapide : relance directement la dernière source utilisée (pas de nouvelle recherche). Sinon, écran des sources. */
fun resumeFast(ctx: Context, scope: kotlinx.coroutines.CoroutineScope, nav: Nav, type: String, metaId: String, videoId: String) {
    val ls = Tvo.store.lastStream(videoId) ?: return nav.openStreams(type, metaId, videoId)
    scope.launch {
        val addons = Tvo.store.allowed(Tvo.store.addons.value)
        val m = Tvo.client.metaCache["$type/$metaId"] ?: runCatching { Tvo.client.meta(addons, type, metaId) }.getOrNull()
        if (m == null) { nav.openStreams(type, metaId, videoId); return@launch }
        play(ctx, m, StreamItem(ls.addon, ls.name, "", ls.url, null, null, ls.headers, emptyList(), ls.bingeGroup, 0), videoId, addons)
    }
}

fun openExternal(ctx: Context, uri: Uri, title: String?) {
    val i = Intent(Intent.ACTION_VIEW).apply {
        if (uri.scheme?.startsWith("http") == true && uri.host?.contains("youtube") != true) setDataAndType(uri, "video/*") else data = uri
        if (title != null) putExtra("title", title)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { ctx.startActivity(Intent.createChooser(i, "Ouvrir avec").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: ActivityNotFoundException) { Toast.makeText(ctx, "Aucune appli ne peut ouvrir cette vidéo.", Toast.LENGTH_LONG).show() }
}

@Composable
private fun StreamRow(s: StreamItem, badge: String, lang: String, best: Boolean, modifier: Modifier, locked: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().then(if (locked) Modifier.alpha(0.55f) else Modifier),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvoColors.Surface, contentColor = TvoColors.Text,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp).clip(RoundedCornerShape(50)).background(if (s.quality >= 3) TvoColors.Blue else TvoColors.Surface2)
                .padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(AddonClient.qualityLabel(s.quality), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (best) Text("MEILLEURE SOURCE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TvoColors.Blue)
                    val bc = when { badge.startsWith("🔒") -> Color(0xFFF87171); badge.startsWith("⚡") -> Color(0xFF4ADE80); else -> TvoColors.Muted }
                    Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bc)
                    if (lang.isNotEmpty()) Text(lang, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TvoColors.Cyan)
                }
                Text(s.name.replace("\n", " "), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.title.isNotBlank()) Text(s.title, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
                    color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.75f))
            }
            Spacer(Modifier.width(12.dp))
            Text(s.addonName, fontSize = 12.sp, maxLines = 1, color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.6f))
        }
    }
}
