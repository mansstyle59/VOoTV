package app.tvo.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.tvo.tv.Tvo
import app.tvo.tv.data.LibraryItem
import app.tvo.tv.data.MetaDetail
import app.tvo.tv.data.Progress
import app.tvo.tv.data.Video
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private sealed interface Load<out T> { data object Loading : Load<Nothing>; data object Failed : Load<Nothing>; data class Ok<T>(val v: T) : Load<T> }

@Composable
fun DetailScreen(type: String, id: String, nav: Nav) {
    val addons by Tvo.store.addons.collectAsState()
    val state by produceState<Load<MetaDetail>>(Load.Loading, type, id) {
        value = Tvo.client.meta(addons, type, id)?.let { Load.Ok(it) } ?: Load.Failed
    }
    when (val s = state) {
        Load.Loading -> CenterMessage("Chargement…")
        Load.Failed -> CenterMessage("Fiche introuvable", "Aucun add-on n'a pu fournir les informations de ce titre.")
        is Load.Ok -> DetailContent(s.v, nav)
    }
}

/** What "Play" should start: the episode in progress, the next one, or the first one. */
fun resumeTarget(meta: MetaDetail, progress: List<Progress>): Pair<String, Progress?> {
    val p = meta.preview
    if (p.type != "series" || meta.episodes.isEmpty()) {
        val vid = meta.defaultVideoId ?: meta.videos.singleOrNull()?.id ?: p.id
        return vid to progress.firstOrNull { it.videoId == vid }
    }
    val last = progress.filter { it.metaId == p.id }.maxByOrNull { it.updatedAt }
    val eps = meta.episodes
    if (last == null) return (eps.firstOrNull { (it.season ?: 0) > 0 } ?: eps.first()).id to null
    if (!last.finished) return last.videoId to last
    val idx = eps.indexOfFirst { it.id == last.videoId }
    val next = eps.getOrNull(idx + 1) ?: eps[idx.coerceAtLeast(0)]
    return next.id to progress.firstOrNull { it.videoId == next.id }
}

@Composable
private fun DetailContent(meta: MetaDetail, nav: Nav) {
    val store = Tvo.store
    val p = meta.preview
    val library by store.library.collectAsState()
    val progress by store.progress.collectAsState()
    val inList = library.any { it.id == p.id }
    val (target, targetProgress) = remember(meta, progress) { resumeTarget(meta, progress) }
    val targetVideo = meta.videos.firstOrNull { it.id == target }
    val playLabel = when {
        targetProgress != null && !targetProgress.finished && targetProgress.fraction > 0.02f ->
            if (targetVideo?.season != null) "Reprendre S${targetVideo.season} : É${targetVideo.episode}" else "Reprendre"
        targetVideo?.season != null -> "Lecture S${targetVideo.season} : É${targetVideo.episode}"
        else -> "Lecture"
    }
    val seasons = remember(meta) { meta.episodes.mapNotNull { it.season }.distinct() }
    var season by remember(meta) { mutableStateOf(targetVideo?.season ?: seasons.firstOrNull()) }
    val playFr = remember { FocusRequester() }

    Box(Modifier.fillMaxSize().background(TvoColors.Bg)) {
        Backdrop(p.background ?: p.poster)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 48.dp, bottom = 56.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Column(Modifier.padding(start = 56.dp).width(620.dp)) {
                    if (p.logo != null) AsyncImage(p.logo, p.name, Modifier.height(100.dp).fillMaxWidth(0.85f), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                    else Text(p.name, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                    Spacer(Modifier.height(14.dp))
                    MetaLine(p.type, p.releaseInfo, p.imdbRating, p.genres, meta.runtime)
                    if (!p.description.isNullOrBlank()) Text(p.description, Modifier.padding(top = 14.dp), fontSize = 16.sp,
                        color = TvoColors.Text.copy(alpha = 0.88f), maxLines = 5, overflow = TextOverflow.Ellipsis, lineHeight = 23.sp)
                    if (p.description.isNullOrBlank() && !app.tvo.tv.data.Tmdb.enabled) Text("Ajoute ta clé TMDB (Réglages) pour les résumés en français.", Modifier.padding(top = 10.dp), fontSize = 13.sp, color = TvoColors.Cyan)
                    if (meta.cast.isNotEmpty() && meta.people.isEmpty()) Text("Avec : " + meta.cast.joinToString(", "), Modifier.padding(top = 10.dp),
                        fontSize = 14.sp, color = TvoColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (meta.director.isNotEmpty()) Text("Réalisation : " + meta.director.joinToString(", "), fontSize = 14.sp, color = TvoColors.Muted, maxLines = 1)
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvoButton(playLabel, { nav.openStreams(p.type, p.id, target) }, Modifier.focusRequester(playFr), Icons.Default.PlayArrow, primary = true)
                        TvoButton(if (inList) "Dans ma liste" else "Ma liste", {
                            store.toggleLibrary(LibraryItem(p.id, p.type, p.name, p.poster, p.background))
                        }, icon = if (inList) Icons.Default.Check else Icons.Default.Add)
                    }
                    if (targetProgress != null && !targetProgress.finished && targetProgress.fraction > 0.02f) {
                        val left = ((targetProgress.duration - targetProgress.position) / 60000).coerceAtLeast(1)
                        Text("Il reste $left min", Modifier.padding(top = 10.dp), fontSize = 13.sp, color = TvoColors.Muted)
                    }
                }
            }
            if (meta.people.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Distribution", Modifier.padding(start = 56.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
                    LazyRow(contentPadding = PaddingValues(horizontal = 56.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(meta.people, key = { it.id }) { person -> PersonBubble(person) { nav.openPerson(person.id) } }
                    }
                }
            }
            if (seasons.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 56.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(seasons) { s -> Chip(if (s == 0) "Bonus" else "Saison $s", s == season, { season = s }) }
                    }
                }
                item {
                    val eps = meta.episodes.filter { it.season == season }
                    val startIndex = eps.indexOfFirst { it.id == target }.coerceAtLeast(0)
                    val listState = rememberLazyListState(startIndex)
                    LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 56.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(eps, key = { it.id }) { v -> EpisodeCard(v, p.background ?: p.poster, progress.firstOrNull { it.videoId == v.id }) { nav.openStreams(p.type, p.id, v.id) } }
                    }
                    val current = eps.getOrNull(startIndex)
                    if (current?.overview != null) Text(current.overview, Modifier.padding(start = 56.dp, top = 4.dp).width(700.dp),
                        fontSize = 14.sp, color = TvoColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(100); runCatching { playFr.requestFocus() } }
}

@Composable
private fun EpisodeCard(v: Video, fallback: String?, pr: Progress?, onClick: () -> Unit) {
    Column(Modifier.width(260.dp)) {
        WideCard(
            image = v.thumbnail ?: fallback,
            title = "É${v.episode ?: "?"} · ${v.title.ifBlank { "Épisode ${v.episode ?: ""}" }}",
            subtitle = v.released?.take(10)?.let { d -> d.split("-").takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}/${it[0]}" } },
            progress = pr?.fraction,
            onClick = onClick,
            width = 260.dp,
            badge = if (pr?.finished == true) "VU" else null,
        )
    }
}


@Composable
fun PersonBubble(person: app.tvo.tv.data.Person, onClick: () -> Unit) {
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.tv.material3.Surface(
            onClick = onClick, modifier = Modifier.size(84.dp),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.CircleShape),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = androidx.tv.material3.ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(3.dp, TvoColors.Blue), shape = androidx.compose.foundation.shape.CircleShape)),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = TvoColors.Surface2, focusedContainerColor = TvoColors.Surface2),
        ) {
            if (person.photo != null) AsyncImage(person.photo, person.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(person.name.take(1), fontSize = 28.sp, color = TvoColors.Text) }
        }
        Text(person.name, Modifier.padding(top = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        person.role?.let { Text(it, fontSize = 11.sp, color = TvoColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun PersonScreen(id: String, nav: Nav) {
    val page by produceState<app.tvo.tv.data.Tmdb.PersonPage?>(null, id) { value = app.tvo.tv.data.Tmdb.person(id) }
    val p = page ?: return CenterMessage("Chargement…")
    val first = remember { FocusRequester() }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize().background(TvoColors.Bg),
        contentPadding = PaddingValues(48.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(p.photo, p.name, Modifier.size(140.dp).clip(androidx.compose.foundation.shape.CircleShape).background(TvoColors.Surface), contentScale = ContentScale.Crop)
                Column(Modifier.width(720.dp)) {
                    Text(p.name, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = TvoColors.Text)
                    Text("${p.works.size} films et séries", fontSize = 15.sp, color = TvoColors.Cyan)
                    if (!p.bio.isNullOrBlank()) Text(p.bio, Modifier.padding(top = 8.dp), fontSize = 14.sp, color = TvoColors.Muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        items(p.works.size) { i ->
            val m = p.works[i]
            PortraitCard(m, { nav.openMeta(m) }, if (i == 0) Modifier.focusRequester(first) else Modifier)
        }
    }
    LaunchedEffect(p) { kotlinx.coroutines.delay(120); runCatching { first.requestFocus() } }
}
