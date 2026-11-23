package app.tvo.tv.ui

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
    val addons by Tvo.store.addons.collectAsState()
    val meta by produceState<MetaDetail?>(Tvo.client.metaCache["$type/$metaId"], metaId) {
        if (value == null) value = Tvo.client.meta(addons, type, metaId)
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
    val playable = streams.filter { it.playable }.sortedByDescending { it.quality }
    val torrents = streams.count { !it.playable }

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
            Text("Choisis une source", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            val status = when {
                total == 0 -> "Aucun add-on de sources installé."
                done < total -> "Recherche en cours… ($done/$total add-ons)"
                else -> "${playable.size} source(s) trouvée(s)" + if (torrents > 0) " · $torrents torrent(s) masqué(s), non pris en charge" else ""
            }
            Text(status, Modifier.padding(top = 4.dp, bottom = 16.dp), fontSize = 14.sp, color = TvoColors.Muted)
            if (starting) Text("Préparation de la lecture…", fontSize = 16.sp, color = TvoColors.Cyan)
            if (playable.isEmpty() && (done >= total)) {
                Text(
                    if (total == 0) "Ajoute un add-on qui fournit des vidéos dans Réglages > Add-ons (comme dans Nuvio ou Stremio)."
                    else "Aucune source lisible pour ce titre. Essaie un autre add-on.",
                    fontSize = 16.sp, color = TvoColors.Text, modifier = Modifier.width(560.dp),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                itemsIndexed(playable, key = { i, s -> "${s.addonName}|${s.url ?: s.externalUrl}|$i" }) { i, s ->
                    StreamRow(s, if (i == 0) Modifier.focusRequester(firstFr) else Modifier) {
                        if (starting) return@StreamRow
                        val m = meta ?: return@StreamRow
                        starting = true
                        scope.launch {
                            play(ctx, m, s, videoId, addons)
                            starting = false
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(playable.isNotEmpty()) {
        if (playable.isNotEmpty() && !focusedOnce) { focusedOnce = true; delay(60); runCatching { firstFr.requestFocus() } }
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
private fun StreamRow(s: StreamItem, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvoColors.Surface, contentColor = TvoColors.Text,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp).clip(RoundedCornerShape(6.dp)).background(if (s.quality >= 3) TvoColors.Violet else TvoColors.Surface2)
                .padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(AddonClient.qualityLabel(s.quality), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name.replace("\n", " "), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.title.isNotBlank()) Text(s.title, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
                    color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.75f))
            }
            Spacer(Modifier.width(12.dp))
            Text(s.addonName, fontSize = 12.sp, maxLines = 1, color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.6f))
        }
    }
}
