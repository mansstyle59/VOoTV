package app.tvo.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.tvo.tv.Tvo
import app.tvo.tv.ui.Chip
import app.tvo.tv.ui.TvoButton
import app.tvo.tv.ui.TvoColors
import kotlinx.coroutines.delay

/** Menu Épisodes des lecteurs de la télé (principal, MPV, VLC) : saisons en pastilles, épisodes avec image, date et résumé. */
@Composable
fun TvEpisodesPanel(req: app.tvo.tv.data.PlayRequest, onClose: () -> Unit, onPick: (app.tvo.tv.data.Video, String) -> Unit) {
    val meta by produceState<app.tvo.tv.data.MetaDetail?>(null, req.metaId) {
        value = runCatching { Tvo.client.meta(Tvo.store.allowed(Tvo.store.addons.value), req.type, req.metaId) }.getOrNull()
    }
    val all = meta?.episodes ?: emptyList()
    val seasons = remember(all) { all.mapNotNull { it.season }.distinct() }
    var season by remember(seasons, req.season) { mutableStateOf(req.season ?: seasons.firstOrNull()) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(meta) { delay(120); runCatching { fr.requestFocus() } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val eps = remember(all, season) { all.filter { it.season == season } }
    LaunchedEffect(eps) { val i = eps.indexOfFirst { it.id == req.videoId }; if (i > 0) listState.scrollToItem(i) }
    Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
        // Panneau à droite (comme Nuvio) : titre, saisons en pastilles, épisodes avec image
        Column(Modifier.align(Alignment.CenterEnd).width(560.dp).fillMaxHeight()
            .clip(app.tvo.tv.ui.SquircleShape(26.dp)).background(Color(0xFF15181D)).padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Épisodes", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                TvoButton("Fermer", onClose)
            }
            Spacer(Modifier.height(14.dp))
            if (meta == null) Text("Chargement des épisodes…", color = TvoColors.Muted)
            else if (seasons.isEmpty()) Text("Aucun épisode trouvé.", color = TvoColors.Muted)
            else {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(seasons) { sn -> Chip(if (sn == 0) "Bonus" else "Saison $sn", season == sn, { season = sn }) }
                }
                Spacer(Modifier.height(14.dp))
                androidx.compose.foundation.lazy.LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                    items(eps, key = { it.id }) { ep ->
                        val current = ep.id == req.videoId
                        EpisodeCard(ep, meta?.preview?.background, current, if (current) Modifier.focusRequester(fr) else Modifier) {
                            if (current) onClose() else onPick(ep, meta?.preview?.name ?: req.title)
                        }
                    }
                }
            }
        }
    }
}

/** Carte d'épisode : image avec S1E4, titre, date et résumé (deux lignes). */
@Composable
fun EpisodeCard(ep: app.tvo.tv.data.Video, fallback: String?, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = app.tvo.tv.ui.SquircleShape(18.dp)
    androidx.tv.material3.Surface(onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, if (current) TvoColors.Cyan else Color.Transparent), shape = shape),
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White), shape = shape)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Color(0x0FFFFFFF), contentColor = Color.White,
            focusedContainerColor = Color(0x24FFFFFF), focusedContentColor = Color.White)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(190.dp).aspectRatio(16f / 9f).clip(app.tvo.tv.ui.SquircleShape(12.dp)).background(Color(0xFF22262D))) {
                val img = ep.thumbnail ?: fallback
                if (img != null) coil.compose.AsyncImage(img, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Box(Modifier.align(Alignment.BottomStart).padding(8.dp).clip(app.tvo.tv.ui.SquircleShape(7.dp)).background(Color(0xB8000000)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("S${ep.season ?: "?"}E${ep.episode ?: "?"}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(ep.title.ifBlank { "Épisode ${ep.episode ?: ""}" }, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                frDate(ep.released)?.let { Text(it, fontSize = 13.sp, color = Color(0xFF9AA1AD)) }
                ep.overview?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = Color(0xFFB9BFCA), maxLines = 2, lineHeight = 17.sp,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            }
        }
    }
}

private fun frDate(d: String?): String? = runCatching {
    val t = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(d!!.take(10))!!
    java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.FRENCH).format(t)
}.getOrNull()
