package app.tvo.tv.ui

import androidx.compose.foundation.focusable

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import app.tvo.tv.data.MetaPreview
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.focusGroup
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
        value = runCatching { Tvo.client.meta(addons, type, id) }.getOrNull()?.let { Load.Ok(it) } ?: Load.Failed
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    // Bande-annonce VF : jamais en fond de l'affiche ; le bouton la lance en plein écran avec le son (Retour = fermer).
    val trailer by produceState<String?>(null, p.id) { value = if (app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.trailerVF(p.type, p.id) else null }
    var trailerMode by remember(p.id) { mutableStateOf(0) }
    var videoKey by remember(p.id) { mutableStateOf<String?>(null) }
    val videos by produceState(emptyList<app.tvo.tv.data.Tmdb.TVideo>(), p.id) { value = if (app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.videosFr(p.type, p.id) else emptyList() }
    val trailerWin = trailerMode == 2
    androidx.activity.compose.BackHandler(enabled = trailerMode != 0) { trailerMode = 0; videoKey = null }
    val similar by produceState(emptyList<MetaPreview>(), p.id) { value = if (app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.similar(p.type, p.id) else emptyList() }
    val saga by produceState<Pair<app.tvo.tv.data.Tmdb.Saga, List<MetaPreview>>?>(null, p.id) {
        value = if (p.type == "movie" && app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.sagaOf(p.type, p.id) else null
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val logoDark by produceState(false, p.logo) { value = p.logo?.let { logoIsDark(ctx, it) } ?: false }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val mainState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Tant que le focus est sur les boutons du haut, la page reste calée tout en haut (titre jamais coupé)
    var heroHasFocus by remember { mutableStateOf(true) }
    // Retour sur les boutons depuis les rangées : un seul retour en haut, en douceur (plus de « combat » de défilement)
    LaunchedEffect(heroHasFocus) {
        if (heroHasFocus && (mainState.firstVisibleItemIndex > 0 || mainState.firstVisibleItemScrollOffset > 0)) mainState.animateScrollToItem(0)
    }
    // Défilement de la page : quand le focus est sur les boutons du haut, la page ne bouge JAMAIS (cause du clignotement).
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val heroFullPx = (cfg.screenHeightDp - 175).coerceIn(320, 390) * density
    val widthPx = cfg.screenWidthDp * density
    // Une seule règle de défilement pour toute la fiche, basée sur la POSITION de l'élément choisi :
    //  - boutons du haut : la page ne bouge pas ;
    //  - retour vers le haut : la page remonte d'un coup tout en haut (synopsis + boutons entiers) ;
    //  - rangée du bas : elle se cale en haut de l'écran, titre compris (comme sur l'accueil) ;
    //  - défilement horizontal dans une rangée : marge normale à gauche et à droite.
    val pageSpec = remember(widthPx, heroFullPx, density) {
        object : androidx.compose.foundation.gestures.BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                if (kotlin.math.abs(containerSize - widthPx) < 8f) return RowSideSpec.calculateScrollDistance(offset, size, containerSize)
                val idx = mainState.firstVisibleItemIndex
                val so = mainState.firstVisibleItemScrollOffset
                if (idx == 0 && so == 0 && offset >= 0f && offset + size <= heroFullPx + 40f * density) return 0f
                if (offset < 0f && idx <= 1) return -(if (idx == 0) so.toFloat() else heroFullPx + 10f * density + so)
                return offset - 50f * density
            }
        }
    }
    // Basé sur le focus (et non sur le défilement) : plus de clignotement du voile quand on passe d'un bouton à l'autre
    val heroFocused = heroHasFocus
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    // Hauteur du bloc d'infos : toujours assez courte pour que la 1re rangée reste visible (sinon elle n'est pas atteignable)
    val heroFull = (screenH - 175).coerceIn(320, 390).dp
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Visuel plein écran (image fixe), voilé à gauche et en bas pour garder le texte lisible
        Box(Modifier.fillMaxSize()) {
            FbImage(p.background ?: p.poster, p.id, false, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                0.0f to Color.Black.copy(alpha = 0.94f), 0.5f to Color.Black.copy(alpha = 0.7f), 0.85f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.0f to Color.Black.copy(alpha = 0.35f), 0.18f to Color.Transparent,
                0.55f to Color.Transparent, 0.8f to Color.Black.copy(alpha = 0.75f), 1f to Color.Black.copy(alpha = 0.92f))))
        }
        // Quand on parcourt les rangées : fond sombre progressif pour que visages et affiches restent nets
        val rowsScrim by androidx.compose.animation.core.animateFloatAsState(if (heroFocused) 0f else 1f, androidx.compose.animation.core.snap(), label = "scrim")
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = rowsScrim }.background(Brush.verticalGradient(0.2f to Color.Transparent, 0.38f to Color.Black.copy(alpha = 0.72f), 1f to Color.Black.copy(alpha = 0.94f))))
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides pageSpec) {
        LazyColumn(Modifier.fillMaxSize(), state = mainState, contentPadding = PaddingValues(top = 0.dp, bottom = 56.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "hero") {
        Box(Modifier.fillMaxWidth().height(heroFull).padding(top = 40.dp).onFocusChanged { heroHasFocus = it.hasFocus }) {
                    Column(Modifier.padding(start = 48.dp).width(640.dp)) {
                        if (p.logo != null) AsyncImage(p.logo, p.name, Modifier.height(72.dp).fillMaxWidth(0.85f), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, colorFilter = if (logoDark) WhiteLogoFilter else null)
                        else Text(p.name, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        // Boutons d'abord (pilule « Regarder » + boutons ronds), comme sur la maquette
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PillButton(if (targetProgress != null && !targetProgress.finished && targetProgress.fraction > 0.02f) "Reprendre" else "Regarder", {
                                if (targetProgress != null && !targetProgress.finished && targetProgress.fraction > 0.02f) resumeFast(ctx, scope, nav, p.type, p.id, target)
                                else nav.openStreams(p.type, p.id, target)
                            }, Modifier.focusRequester(playFr))
                            RoundButton(if (inList) Icons.Default.Check else Icons.Default.Add) {
                                store.toggleLibrary(LibraryItem(p.id, p.type, p.name, p.poster, p.background))
                                android.widget.Toast.makeText(ctx, if (inList) "Retiré de ma liste" else "Ajouté à ma liste", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            // Bande-annonce : plein écran avec le son (Retour pour fermer)
                            if (trailer != null) RoundButton(TvIcon) { videoKey = null; trailerMode = 2 }
                            if (p.type == "movie") {
                                val seen = targetProgress?.finished == true
                                RoundButton(Icons.Default.CheckCircle, active = seen) {
                                    val pid = store.currentProfile.value?.id
                                    if (pid != null) store.saveProgress(pid, Progress(target, p.id, p.type, p.name, p.poster, p.background,
                                        position = if (seen) 0 else 1, duration = 1, updatedAt = System.currentTimeMillis()))
                                    android.widget.Toast.makeText(ctx, if (seen) "Marqué non vu" else "Marqué comme vu", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        if (meta.director.isNotEmpty()) Text("Réalisateur : " + meta.director.joinToString(", "), Modifier.padding(top = 6.dp), fontSize = 13.sp, color = TvoColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        meta.extra["writers"]?.let { w -> Text("Scénariste : $w", Modifier.padding(top = 2.dp), fontSize = 13.sp, color = TvoColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        val tm = meta.extra["tmdb"]
                        if (tm != null || !p.imdbRating.isNullOrBlank()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (tm != null) Text("TMDB  " + tm.substringBefore(",") + "%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TvoColors.Cyan)
                            if (!p.imdbRating.isNullOrBlank()) Text("★ " + p.imdbRating, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TvoColors.Cyan)
                        }
                        if (!p.description.isNullOrBlank()) Text(p.description, Modifier.padding(top = 6.dp), fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.92f), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                        if (p.description.isNullOrBlank() && !app.tvo.tv.data.Tmdb.enabled) Text("Ajoute ta clé TMDB (Réglages) pour les résumés en français.", Modifier.padding(top = 10.dp), fontSize = 13.sp, color = TvoColors.Cyan)
                        val gl = p.genres.take(3).joinToString(" • ") { genreLabel(it) }
                        val dl = meta.extra["date"] ?: p.releaseInfo
                        if (gl.isNotBlank() || dl != null) Text(listOfNotNull(gl.ifBlank { null }, dl).joinToString(" • "), Modifier.padding(top = 4.dp), fontSize = 12.sp, color = TvoColors.Muted, maxLines = 1)
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            meta.extra["cert"]?.let { Badge(it) }
                            val info = listOfNotNull(meta.extra["status"], meta.runtime, meta.extra["country"], meta.extra["lang"]).joinToString(" • ")
                            if (info.isNotBlank()) Text(info, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Muted, maxLines = 1)
                        }
                        if (targetProgress != null && !targetProgress.finished && targetProgress.fraction > 0.02f) {
                            val left = ((targetProgress.duration - targetProgress.position) / 60000).coerceAtLeast(1)
                            Text("Il reste $left min", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = TvoColors.Muted)
                        }
                    }
        }
        }
            if (seasons.isNotEmpty()) {
                item(key = "epsTitle") { Text("Épisodes", Modifier.padding(start = 48.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text) }
                item {
                    val fr = remember { FocusRequester() }
                    LazyRow(Modifier.focusRestorer { fr }, contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(seasons) { i, s -> Box(if (i == 0) Modifier.focusRequester(fr).focusGroup() else Modifier) { Chip(if (s == 0) "Bonus" else "Saison $s", s == season, { season = s }) } }
                    }
                }
                item {
                    val eps = meta.episodes.filter { it.season == season }.distinctBy { it.id }
                    val startIndex = eps.indexOfFirst { it.id == target }.coerceAtLeast(0)
                    val listState = rememberLazyListState(startIndex)
                    val fr = remember { FocusRequester() }
                    LazyRow(Modifier.focusRestorer { fr }, state = listState, contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        itemsIndexed(eps, key = { _, it -> it.id }) { i, v -> Box(if (i == startIndex) Modifier.focusRequester(fr).focusGroup() else Modifier) { EpisodeCard(v, p.background ?: p.poster, progress.firstOrNull { it.videoId == v.id }) { nav.openStreams(p.type, p.id, v.id) } } }
                    }
                    val current = eps.getOrNull(startIndex)
                    if (current?.overview != null) Text(current.overview, Modifier.padding(start = 48.dp, top = 4.dp).width(700.dp),
                        fontSize = 14.sp, color = TvoColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (meta.people.isNotEmpty()) item(key = "cast") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Casting", Modifier.padding(start = 48.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                    val fr = remember { FocusRequester() }
                    LazyRow(Modifier.focusRestorer { fr }, contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        itemsIndexed(meta.people.distinctBy { it.id }, key = { _, it -> it.id }) { i, person -> Box(if (i == 0) Modifier.focusRequester(fr).focusGroup() else Modifier) { PersonBubble(person) { nav.openPerson(person.id) } } }
                    }
                }
            }
            // Vidéos en français par type (bandes-annonces, teasers, extraits…) : OK = plein écran avec le son
            if (videos.isNotEmpty()) item(key = "videos") {
                val types = VTYPES.keys.filter { t -> videos.any { it.type == t } }
                var cur by remember(videos) { mutableStateOf(types.firstOrNull() ?: "Trailer") }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Vidéos", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                        if (types.size > 1) types.forEach { t -> Chip(VTYPES[t] ?: t, t == cur, { cur = t }) }
                    }
                    val list = videos.filter { it.type == cur }
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides RowSideSpec) {
                        val fr = remember { FocusRequester() }
                        LazyRow(Modifier.focusRestorer { fr }, contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(list, key = { _, v -> v.key }) { i, v ->
                                Column(Modifier.width(260.dp).let { if (i == 0) it.focusRequester(fr).focusGroup() else it }) {
                                    WideCard(image = "https://i.ytimg.com/vi/${v.key}/hqdefault.jpg", title = v.name, subtitle = v.year ?: "", progress = null,
                                        onClick = { videoKey = v.key; trailerMode = 2 }, width = 260.dp, showPlay = true)
                                    Text(v.name, Modifier.padding(top = 6.dp), fontSize = 13.sp, color = TvoColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            // Détails : tableau clé / valeur
            item(key = "details") {
                val ser = p.type == "series"
                val rows = listOfNotNull(
                    meta.extra["statusFr"]?.let { "Statut" to it },
                    (meta.extra["date"] ?: p.releaseInfo)?.let { (if (ser) "Première diffusion" else "Date de sortie") to it },
                    if (ser) meta.extra["seasons"]?.let { "Saisons" to it } else meta.runtime?.let { "Durée" to it },
                    if (ser) meta.extra["episodes"]?.let { "Épisodes" to it } else null,
                    meta.extra["cert"]?.let { "Classification" to it },
                    (meta.extra["countryName"] ?: meta.extra["country"])?.let { "Pays d'origine" to it },
                    (meta.extra["langName"] ?: meta.extra["lang"])?.let { "Langue originale" to it },
                )
                if (rows.isNotEmpty()) Column(Modifier.padding(horizontal = 48.dp).width(640.dp).focusable()) {
                    Text(if (ser) "Détails de la série" else "Détails du film", Modifier.padding(bottom = 6.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                    rows.forEachIndexed { i, (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, fontSize = 15.sp, color = TvoColors.Muted, fontWeight = FontWeight.SemiBold)
                            Text(v, fontSize = 15.sp, color = TvoColors.Text)
                        }
                        if (i < rows.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
                    }
                }
            }
            saga?.let { sg -> item(key = "saga") {
                val nm = sg.first.name.replace(" - Saga", "").replace(" Collection", "").trim()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("$nm - Saga", Modifier.padding(start = 48.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                    val fr = remember { FocusRequester() }
                    LazyRow(Modifier.focusRestorer { fr }, contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(sg.second.distinctBy { it.id }, key = { _, m -> m.id }) { i, m ->
                            Column(Modifier.width(280.dp).let { if (i == 0) it.focusRequester(fr).focusGroup() else it }) {
                                WideCard(image = m.background ?: m.poster, title = m.name,
                                    subtitle = listOfNotNull("SAGA ${i + 1}/${sg.second.size}", m.genres.firstOrNull()?.let { genreLabel(it) }, m.imdbRating?.let { "★ $it" }, m.releaseInfo).joinToString(" • "),
                                    progress = null, onClick = { nav.openMeta(m) }, width = 280.dp)
                                Text(m.name + (m.releaseInfo?.let { " ($it)" } ?: ""), Modifier.padding(top = 6.dp), fontSize = 13.sp, color = TvoColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } }
            if (similar.isNotEmpty()) item(key = "similar") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("À voir aussi", Modifier.padding(start = 48.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                    val fr = remember { FocusRequester() }
                    LazyRow(Modifier.focusRestorer { fr }, contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(similar.distinctBy { it.id }, key = { _, it -> it.id }) { i, m -> Box(if (i == 0) Modifier.focusRequester(fr).focusGroup() else Modifier) { PosterCard(m, { nav.openMeta(m) }, width = 136.dp) } }
                    }
                }
            }
        }
        }
    }
    // Bande-annonce plein écran avec le son. Retour pour fermer ; à la fin, retour automatique à la fiche.
    val fullKey = videoKey ?: trailer
    if (trailerWin && fullKey != null) Box(Modifier.fillMaxSize().background(Color.Black)) {
        TrailerView(fullKey, Modifier.fillMaxSize(), cover = false, loop = false, onEnded = { trailerMode = 0; videoKey = null })
        var hint by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { delay(3500); hint = false }
        if (hint) Text("Bande-annonce VF · ${p.name}  —  Retour pour fermer", Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
            .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 18.dp, vertical = 8.dp), fontSize = 15.sp, color = Color.White)
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PersonScreen(id: String, nav: Nav) {
    val page by produceState<app.tvo.tv.data.Tmdb.PersonPage?>(null, id) { value = app.tvo.tv.data.Tmdb.person(id) }
    val p = page ?: return CenterMessage("Chargement…")
    val first = remember { FocusRequester() }
    var kind by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("new") }
    val works = remember(p, kind, sort) {
        val f = p.works.distinctBy { it.id }.filter { (kind == "all" || it.type == kind || (kind == "dir" && it.id in p.directed)) }
        val y = { m: app.tvo.tv.data.MetaPreview -> m.releaseInfo?.take(4)?.toIntOrNull() ?: 0 }
        when (sort) { "new" -> f.sortedByDescending(y); "old" -> f.filter { y(it) > 0 }.sortedBy(y) + f.filter { y(it) == 0 }; else -> f }
    }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val gridScope = androidx.compose.runtime.rememberCoroutineScope()
    // Quand le focus remonte sur « Connu pour » (1re zone cliquable), on remonte la page tout en haut : bio et photo visibles
    var topFocus by remember { mutableStateOf(false) }
    val backToTop = Modifier.onFocusChanged { topFocus = it.hasFocus }
    // On attend la fin du défilement automatique de la télé, puis on remonte tout en haut (sinon les deux se battent et l'en-tête reste coupé)
    LaunchedEffect(topFocus) { if (topFocus) { kotlinx.coroutines.delay(250); runCatching { gridState.animateScrollToItem(0) } } }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides GridCenterSpec) {
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        state = gridState,
        columns = androidx.compose.foundation.lazy.grid.GridCells.FixedSize(GridCardWidth),
        modifier = Modifier.fillMaxSize().imageBackdrop(Color(0xFF8B5CF6), p.works.firstNotNullOfOrNull { it.background }),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
        verticalArrangement = Arrangement.spacedBy(GridRowGap),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(p.photo, p.name, Modifier.size(120.dp).clip(androidx.compose.foundation.shape.CircleShape).background(TvoColors.Surface), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = TvoColors.Text)
                    Text("${p.works.size} films et séries", fontSize = 15.sp, color = TvoColors.Cyan)
                    p.born?.let { Text(it, Modifier.padding(top = 2.dp), fontSize = 13.sp, color = TvoColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    if (!p.bio.isNullOrBlank()) Text(p.bio, Modifier.padding(top = 8.dp), fontSize = 14.sp, color = TvoColors.Muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (p.knownFor.size >= 3) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(backToTop, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Connu pour", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                val kfFr = remember { FocusRequester() }
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides RowSideSpec) {
                androidx.compose.foundation.lazy.LazyRow(Modifier.focusRestorer { kfFr }, horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    items(p.knownFor.distinctBy { it.id }.size, key = { p.knownFor.distinctBy { it.id }[it].id }) { i -> val m = p.knownFor.distinctBy { it.id }[i]; PortraitCard(m, { nav.openMeta(m) }, if (i == 0) Modifier.focusRequester(kfFr) else Modifier) }
                }
                }
            }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            androidx.compose.foundation.layout.FlowRow((if (p.knownFor.size < 3) backToTop else Modifier).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Tout (${p.works.size})", kind == "all", { kind = "all" }, Modifier.focusRequester(first))
                Chip("Films (${p.works.count { it.type == "movie" }})", kind == "movie", { kind = "movie" })
                Chip("Séries (${p.works.count { it.type == "series" }})", kind == "series", { kind = "series" })
                if (p.directed.isNotEmpty()) Chip("Réalisation (${p.works.count { it.id in p.directed }})", kind == "dir", { kind = "dir" })
                Chip("Plus récents", sort == "new", { sort = "new" })
                Chip("Plus anciens", sort == "old", { sort = "old" })
                Chip("Populaires", sort == "pop", { sort = "pop" })
            }
        }
        items(works.size, key = { works[it].id }) { i ->
            val m = works[i]
            PortraitCard(m, { nav.openMeta(m) }, width = GridCardWidth)
        }
    }
    }
    LaunchedEffect(p) { kotlinx.coroutines.delay(120); runCatching { first.requestFocus() } }
}


@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    // Bouton principal : couleur du profil (texte blanc) ; au focus il devient blanc avec un liseré de la couleur du profil
    androidx.tv.material3.Button(
        onClick = onClick, modifier = modifier.height(48.dp), shape = androidx.tv.material3.ButtonDefaults.shape(shape),
        contentPadding = PaddingValues(horizontal = 26.dp),
        colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = TvoColors.Blue, contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
        scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1.06f),
        border = androidx.tv.material3.ButtonDefaults.border(focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, TvoColors.Cyan), shape = shape)),
    ) {
        androidx.tv.material3.Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, null, Modifier.size(22.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean = false, onClick: () -> Unit) {
    val shape = androidx.compose.foundation.shape.CircleShape
    androidx.tv.material3.Button(
        onClick = onClick, modifier = Modifier.size(48.dp), shape = androidx.tv.material3.ButtonDefaults.shape(shape),
        contentPadding = PaddingValues(0.dp),
        colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = if (active) TvoColors.Blue else Color(0x66FFFFFF).copy(alpha = 0.22f), contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color.Black),
        scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1.08f),
        border = androidx.tv.material3.ButtonDefaults.border(border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), shape = shape)),
    ) { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.tv.material3.Icon(icon, null, Modifier.size(22.dp)) } }
}

@Composable
private fun Badge(text: String) {
    Text(text, Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    androidx.tv.material3.Surface(
        onClick = onClick, shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.14f)),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else TvoColors.Muted)
            Box(Modifier.padding(top = 4.dp).height(3.dp).fillMaxWidth().background(if (selected) TvoColors.Blue else Color.Transparent))
        }
    }
}

/** Icône « télé » (la bibliothèque d'icônes de base n'en a pas). */
private val TvIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("Tv", 24.dp, 24.dp, 24f, 24f).addPath(
        androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M21,3H3C1.9,3 1,3.9 1,5v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM21,17H3V5h18v12z"
        ).toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.White),
    ).build()
}

/** Types de vidéos TMDB, dans l'ordre d'affichage, avec leur nom en français. */
private val VTYPES = linkedMapOf("Trailer" to "Bandes-annonces", "Teaser" to "Teasers", "Clip" to "Extraits", "Featurette" to "Making-of",
    "Behind the Scenes" to "Coulisses", "Bloopers" to "Bêtisier", "Opening Credits" to "Génériques")
