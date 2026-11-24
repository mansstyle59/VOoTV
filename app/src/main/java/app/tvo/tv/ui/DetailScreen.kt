package app.tvo.tv.ui

import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.aspectRatio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import app.tvo.tv.data.MetaPreview
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
    val rawA by Tvo.store.addons.collectAsState()
    val addons = remember(rawA) { Tvo.store.allowed(rawA) }
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // Bande-annonce VF : 1er appui = elle démarre en fond d'écran, 2e appui = fenêtre centrée. Retour = on revient au fond.
    val trailer by produceState<String?>(null, p.id) { value = if (app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.trailerVF(p.type, p.id) else null }
    var trailerMode by remember(p.id) { mutableStateOf(0) }
    val trailerOn = trailerMode == 1
    val trailerWin = trailerMode == 2
    androidx.activity.compose.BackHandler(enabled = trailerMode != 0) { trailerMode = if (trailerMode == 2) 1 else 0 }
    val similar by produceState(emptyList<MetaPreview>(), p.id) { value = if (app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.similar(p.type, p.id) else emptyList() }
    val saga by produceState<Pair<app.tvo.tv.data.Tmdb.Saga, List<MetaPreview>>?>(null, p.id) {
        value = if (p.type == "movie" && app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.sagaOf(p.type, p.id) else null
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val mainState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Tant que le focus est sur les boutons du haut, la page reste calée tout en haut (titre jamais coupé)
    var heroHasFocus by remember { mutableStateOf(true) }
    LaunchedEffect(heroHasFocus) {
        if (heroHasFocus) androidx.compose.runtime.snapshotFlow { mainState.firstVisibleItemIndex to mainState.firstVisibleItemScrollOffset }
            .collect { (i, o) -> if (i > 0 || o > 0) mainState.scrollToItem(0) }
    }
    // Basé sur le focus (et non sur le défilement) : plus de clignotement du voile quand on passe d'un bouton à l'autre
    val heroFocused = heroHasFocus
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    // Hauteur du bloc d'infos : toujours assez courte pour que la 1re rangée reste visible (sinon elle n'est pas atteignable)
    val heroFull = (screenH - 100).coerceIn(300, 480).dp
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Visuel plein écran (image puis bande-annonce au même endroit), voilé à gauche et en bas pour garder le texte lisible
        Box(Modifier.fillMaxSize()) {
            AsyncImage(p.background ?: p.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (trailerOn && trailer != null) TrailerView(trailer!!, Modifier.fillMaxSize(), cover = true)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                0.0f to Color.Black.copy(alpha = if (trailerOn) 0.75f else 0.94f), 0.5f to Color.Black.copy(alpha = if (trailerOn) 0.4f else 0.7f), 0.85f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.0f to Color.Black.copy(alpha = 0.35f), 0.18f to Color.Transparent,
                0.55f to Color.Transparent, 0.8f to Color.Black.copy(alpha = 0.75f), 1f to Color.Black.copy(alpha = 0.92f))))
        }
        // Quand on parcourt les rangées : fond sombre progressif pour que visages et affiches restent nets
        val rowsScrim by androidx.compose.animation.core.animateFloatAsState(if (heroFocused) 0f else 1f, label = "scrim")
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = rowsScrim }.background(Brush.verticalGradient(0.2f to Color.Transparent, 0.38f to Color.Black.copy(alpha = 0.72f), 1f to Color.Black.copy(alpha = 0.94f))))
        LazyColumn(Modifier.fillMaxSize(), state = mainState, contentPadding = PaddingValues(top = 0.dp, bottom = 56.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item(key = "hero") {
        // Les boutons du haut ne font jamais défiler la page (sinon : petit saut puis retour = clignotement)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides NoScrollSpec) {
        Box(Modifier.fillMaxWidth().height(heroFull).padding(top = 48.dp).onFocusChanged { heroHasFocus = it.hasFocus }) {
                    Column(Modifier.padding(start = 56.dp).width(640.dp)) {
                        if (p.logo != null) AsyncImage(p.logo, p.name, Modifier.height(84.dp).fillMaxWidth(0.85f), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                        else Text(p.name, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text, maxLines = 1)
                        Spacer(Modifier.height(10.dp))
                        MetaLine(p.type, p.releaseInfo, p.imdbRating, p.genres, meta.runtime)
                        if (!p.description.isNullOrBlank()) Text(p.description, Modifier.padding(top = 12.dp), fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.92f), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                        if (p.description.isNullOrBlank() && !app.tvo.tv.data.Tmdb.enabled) Text("Ajoute ta clé TMDB (Réglages) pour les résumés en français.", Modifier.padding(top = 10.dp), fontSize = 13.sp, color = TvoColors.Cyan)
                        if (meta.cast.isNotEmpty() && meta.people.isEmpty()) Text("Avec : " + meta.cast.joinToString(", "), Modifier.padding(top = 10.dp),
                            fontSize = 14.sp, color = TvoColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (meta.director.isNotEmpty()) Text("Réalisation : " + meta.director.joinToString(", "), Modifier.padding(top = 6.dp), fontSize = 13.sp, color = TvoColors.Muted, maxLines = 1)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvoButton(playLabel, {
                                if (targetProgress != null && !targetProgress.finished && targetProgress.fraction > 0.02f) resumeFast(ctx, scope, nav, p.type, p.id, target)
                                else nav.openStreams(p.type, p.id, target)
                            }, Modifier.focusRequester(playFr), Icons.Default.PlayArrow, primary = true)
                            TvoButton(if (inList) "Dans ma liste" else "Ma liste", {
                                store.toggleLibrary(LibraryItem(p.id, p.type, p.name, p.poster, p.background))
                                android.widget.Toast.makeText(ctx, if (inList) "Retiré de ma liste" else "Ajouté à ma liste", android.widget.Toast.LENGTH_SHORT).show()
                            }, icon = if (inList) Icons.Default.Check else Icons.Default.Add)
                            if (trailer != null) TvoButton(when (trailerMode) { 0 -> "Bande-annonce"; else -> "Agrandir" },
                                { trailerMode = if (trailerMode == 0) 1 else 2 }, icon = if (trailerMode == 0) Icons.Default.PlayArrow else null)
                        }
                        if (targetProgress != null && !targetProgress.finished && targetProgress.fraction > 0.02f) {
                            val left = ((targetProgress.duration - targetProgress.position) / 60000).coerceAtLeast(1)
                            Text("Il reste $left min", Modifier.padding(top = 8.dp), fontSize = 13.sp, color = TvoColors.Muted)
                        }
                    }
        }
        }
        }
            if (meta.people.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Distribution", Modifier.padding(start = 56.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
            saga?.let { sg -> item(key = "saga") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Saga : " + sg.first.name.replace(" - Saga", "").replace(" Collection", "").trim(), Modifier.padding(start = 56.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    LazyRow(contentPadding = PaddingValues(horizontal = 56.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(sg.second, key = { it.id }) { m -> PosterCard(m, { nav.openMeta(m) }, width = 136.dp) }
                    }
                }
            } }
            if (similar.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (p.type == "series") "Séries similaires" else "Films similaires", Modifier.padding(start = 56.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    LazyRow(contentPadding = PaddingValues(horizontal = 56.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(similar, key = { it.id }) { m -> PosterCard(m, { nav.openMeta(m) }, width = 136.dp) }
                    }
                }
            }
        }
    }
    // Fenêtre de bande-annonce : vidéo entière au centre, fond assombri. Retour pour fermer.
    if (trailerWin && trailer != null) Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth(0.6f).aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))) {
                TrailerView(trailer!!, Modifier.fillMaxSize(), cover = false)
            }
            Text("Bande-annonce VF · ${p.name}  —  Retour pour réduire", Modifier.padding(top = 14.dp), fontSize = 15.sp, color = TvoColors.Muted)
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
fun PersonBubble(person: app.tvo.tv.data.Person, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.tv.material3.Surface(
            onClick = onClick, modifier = Modifier.size(84.dp).onFocusChanged { if (it.isFocused) onFocus() },
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.CircleShape),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = androidx.tv.material3.ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(
                androidx.compose.foundation.BorderStroke(3.dp, Color.White), shape = androidx.compose.foundation.shape.CircleShape),
                border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), shape = androidx.compose.foundation.shape.CircleShape)),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = TvoColors.Surface2, focusedContainerColor = TvoColors.Surface2),
        ) {
            if (person.photo != null) AsyncImage(person.photo, person.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(person.name.take(1), fontSize = 28.sp, color = TvoColors.Text) }
        }
        Text(person.name, Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        person.role?.let { Text(it, fontSize = 11.sp, color = TvoColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun PersonScreen(id: String, nav: Nav) {
    val page by produceState<app.tvo.tv.data.Tmdb.PersonPage?>(null, id) { value = app.tvo.tv.data.Tmdb.person(id) }
    val p = page ?: return CenterMessage("Chargement…")
    val first = remember { FocusRequester() }
    var kind by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("new") }
    val works = remember(p, kind, sort) {
        val f = p.works.filter { kind == "all" || it.type == kind }
        val y = { m: app.tvo.tv.data.MetaPreview -> m.releaseInfo?.take(4)?.toIntOrNull() ?: 0 }
        when (sort) { "new" -> f.sortedByDescending(y); "old" -> f.filter { y(it) > 0 }.sortedBy(y) + f.filter { y(it) == 0 }; else -> f }
    }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize().imageBackdrop(Color(0xFF8B5CF6), p.works.firstNotNullOfOrNull { it.background }),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 48.dp),
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
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("Tout (${p.works.size})", kind == "all", { kind = "all" }, Modifier.focusRequester(first))
                Chip("Films (${p.works.count { it.type == "movie" }})", kind == "movie", { kind = "movie" })
                Chip("Séries (${p.works.count { it.type == "series" }})", kind == "series", { kind = "series" })
                Spacer(Modifier.width(24.dp))
                Chip("Plus récents", sort == "new", { sort = "new" })
                Chip("Plus anciens", sort == "old", { sort = "old" })
                Chip("Populaires", sort == "pop", { sort = "pop" })
            }
        }
        items(works.size, key = { works[it].id }) { i ->
            val m = works[i]
            PortraitCard(m, { nav.openMeta(m) })
        }
    }
    LaunchedEffect(p) { kotlinx.coroutines.delay(120); runCatching { first.requestFocus() } }
}
