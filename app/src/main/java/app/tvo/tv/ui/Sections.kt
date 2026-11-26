package app.tvo.tv.ui

import androidx.tv.material3.ClickableSurfaceDefaults

import androidx.tv.material3.Surface

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.tvo.tv.BuildConfig
import app.tvo.tv.Tvo
import app.tvo.tv.data.Addon
import app.tvo.tv.data.Defaults
import app.tvo.tv.data.MetaPreview
import app.tvo.tv.data.RemoteInputServer
import app.tvo.tv.data.Updater
import app.tvo.tv.data.category
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Ma liste

@Composable
fun LibrarySection(nav: Nav) {
    val library by Tvo.store.library.collectAsState()
    val prog by Tvo.store.progress.collectAsState()
    if (library.isEmpty()) {
        CenterMessage("Ta liste est vide", "Ouvre un film ou une série et appuie sur « Ma liste » pour le retrouver ici.")
        return
    }
    // Filtres : Tout, Films, Séries, À voir, Vus ; tri par ajout récent ou A → Z
    var f by rememberSaveable { mutableStateOf("all") }
    var az by rememberSaveable { mutableStateOf(false) }
    val seen = remember(prog) { prog.filter { it.type == "movie" && it.finished }.map { it.metaId }.toSet() }
    fun count(k: String) = library.count { x -> when (k) { "movie", "series" -> x.type == k; "todo" -> x.id !in seen; "seen" -> x.id in seen; else -> true } }
    val list = library.filter { x -> when (f) { "movie", "series" -> x.type == f; "todo" -> x.id !in seen; "seen" -> x.id in seen; else -> true } }
        .let { l -> if (az) l.sortedBy { it.name.lowercase() } else l }
    PosterGrid("Ma liste", list.map { it.toPreview() }, nav, header = {
        androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("all" to "Tout", "movie" to "Films", "series" to "Séries", "todo" to "À voir", "seen" to "Vus").forEach { (k, t) ->
                item { Chip("$t (${count(k)})", f == k, { f = k }) }
            }
            item { Chip(if (az) "Tri : A → Z" else "Tri : ajout récent", false, { az = !az }) }
        }
    })
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PosterGrid(title: String, items: List<MetaPreview>, nav: Nav, onNearEnd: () -> Unit = {}, header: @Composable () -> Unit = {}) {
    val first = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        Text(title, Modifier.padding(start = 48.dp, top = 36.dp, bottom = 8.dp), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
        header()
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides GridCenterSpec) {
        LazyVerticalGrid(
            columns = GridCells.FixedSize(GridCardWidth),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(GridGap),
            verticalArrangement = Arrangement.spacedBy(GridRowGap),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                PosterCard(item, { nav.openMeta(item) }, if (i == 0) Modifier.focusRequester(first) else Modifier, width = GridCardWidth,
                    onFocus = { if (i >= items.size - 14) onNearEnd() })
            }
        }
        }
    }
    LaunchedEffect(items.isNotEmpty()) { if (items.isNotEmpty()) { delay(80); runCatching { first.requestFocus() } } }
}

// ---------------------------------------------------------------- Genre

/** Plateformes : logos officiels (images TMDB, affichés même sans clé). */
val PLATFORMS = listOf(
    Triple("8", "Netflix", 0xFFE50914) to "/rK1KljqmbvO9HQa1PBFLILWah72.png",
    Triple("337", "Disney+", 0xFF113CCF) to "/5eZ872CghnHFLB1j8grszbrx0dx.png",
    Triple("119", "Prime Video", 0xFF00A8E1) to "/gMZdpavHmxFNnLpMHwVxfqeux2g.png",
    Triple("381", "Canal+", 0xFF222222) to "/gliWKhLJVdm7QwL1braBCRko5dx.png",
    Triple("350", "Apple TV+", 0xFF555555) to "/v9SnyvYt0U3KGgBH3Sbh3z4yODe.png",
    Triple("531", "Paramount+", 0xFF0064FF) to "/pkx3klJlwW5JdtaulvDx6hDNtch.png",
    Triple("1899", "Max", 0xFF002BE7) to "/skypuy7SXuugIQeYg0IglmzoKaS.png",
)

/**
 * Page « univers » commune aux plateformes et aux catégories : en-tête fixe (image, logo, titre),
 * onglets Films / Séries qui changent dès qu'on bouge avec les flèches (pas besoin de cliquer), puis la grille.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun UniversePage(
    title: String, subtitle: String, logo: String?, bg: String?, brand: Color,
    type: String, onType: (String) -> Unit, items: List<MetaPreview>, loading: Boolean, nav: Nav, tabs: Boolean = true, onNearEnd: () -> Unit,
) {
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { tabFocus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (bg != null) coil.compose.AsyncImage(bg, null, Modifier.align(Alignment.TopEnd).fillMaxWidth(0.7f).fillMaxHeight(0.62f),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.7f).fillMaxHeight(0.62f).background(
            androidx.compose.ui.graphics.Brush.horizontalGradient(0f to Color.Black, 0.4f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Transparent, 0.6f to Color.Black)))
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(brand.copy(alpha = 0.35f), Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 1100f)))
        Column(Modifier.fillMaxSize()) {
            // En-tête fixe : jamais coupé, toujours visible
            Row(Modifier.padding(start = 56.dp, top = 30.dp, end = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                if (logo != null) {
                    coil.compose.AsyncImage(logo, title, Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black))
                    Spacer(Modifier.width(16.dp))
                }
                Column {
                    Text(title, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 38.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(subtitle, fontSize = 15.sp, color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            if (tabs) Row(Modifier.padding(start = 56.dp, top = 16.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                TvoButton("Films", { onType("movie") }, Modifier.focusRequester(tabFocus).onFocusChanged { if (it.isFocused) onType("movie") }, primary = type == "movie")
                TvoButton("Séries", { onType("series") }, Modifier.onFocusChanged { if (it.isFocused) onType("series") }, primary = type == "series")
                Text("◀ ▶  pour changer", Modifier.padding(start = 10.dp), fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (items.isEmpty()) Text(if (loading) "Chargement…" else "Aucun titre trouvé pour le moment.",
                    Modifier.padding(start = 56.dp, top = 20.dp), fontSize = 17.sp, color = Color.White.copy(alpha = 0.7f))
                else androidx.compose.runtime.key(type) {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides GridCenterSpec) {
                    LazyVerticalGrid(
                        columns = GridCells.FixedSize(GridCardWidth),
                        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 14.dp, bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(GridGap),
                        verticalArrangement = Arrangement.spacedBy(GridRowGap),
                    ) {
                        itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                            PosterCard(item, { nav.openMeta(item) }, Modifier, width = GridCardWidth, onFocus = { if (i >= items.size - 14) onNearEnd() })
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderGrid(id: String, nav: Nav, initial: String = "movie") {
    val p = PLATFORMS.firstOrNull { it.first.first == id }
    val name = p?.first?.second ?: "Plateforme"
    var type by rememberSaveable { mutableStateOf(if (initial == "series") "series" else "movie") }
    val items = remember { mutableStateListOf<MetaPreview>() }
    var loading by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun more() {
        if (loading || end) return
        loading = true
        val t = type
        scope.launch {
            val page = runCatching { app.tvo.tv.data.Tmdb.catalog(app.tvo.tv.data.CatalogDef(t, "prov_$id", name), mapOf("skip" to items.size.toString())) }.getOrDefault(emptyList())
            if (t != type) return@launch
            val fresh = page.filter { n -> items.none { it.id == n.id } }
            if (fresh.isEmpty()) end = true
            items.addAll(fresh); loading = false
        }
    }
    LaunchedEffect(type) { items.clear(); end = false; loading = false; more() }
    UniversePage(name, "Films et séries disponibles sur $name en France", p?.second?.let { TMDB_IMG + "w300" + it },
        PROV_BG[id]?.let { TMDB_IMG + "w1280" + it }, Color(p?.first?.third ?: 0xFF2F5BFF), type, { type = it }, items, loading, nav) { more() }
}

@Composable
private fun CollectionScreen(id: String, nav: Nav) {
    val data by produceState<Pair<app.tvo.tv.data.Tmdb.Saga, List<MetaPreview>>?>(null, id) { value = app.tvo.tv.data.Tmdb.collection(id) }
    UniversePage(data?.first?.name ?: "Saga", "Tous les films de la saga, dans l'ordre de sortie", null, data?.first?.backdrop,
        Color(0xFFF59E0B), "movie", {}, data?.second ?: emptyList(), data == null, nav, tabs = false, onNearEnd = {})
    LaunchedEffect(data) { if (data != null) delay(150) }
}

@Composable
fun GenreScreen(type: String, genre: String, nav: Nav) {
    if (genre.startsWith("prov:")) return ProviderGrid(genre.removePrefix("prov:"), nav, type)
    if (genre.startsWith("col:")) return CollectionScreen(genre.removePrefix("col:"), nav)
    val rawAddons by Tvo.store.addons.collectAsState()
    val tmdbKey by Tvo.store.tmdbKey.collectAsState()
    val addons = remember(rawAddons, tmdbKey) { app.tvo.tv.data.Tmdb.withTmdb(Tvo.store.allowed(rawAddons), tmdbKey, Tvo.store.currentProfile.value?.kids == true) }
    var t by rememberSaveable { mutableStateOf(if (type == "series") "series" else "movie") }
    val items = remember { mutableStateListOf<MetaPreview>() }
    var loading by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val src = remember(addons, t) {
        addons.firstNotNullOfOrNull { a -> a.manifest.catalogs.firstOrNull { it.type == t && it.supports("genre") && it.onlyRequires("genre") }?.let { a to it } }
    }
    fun more() {
        val sc = src
        if (loading || end || sc == null) return
        loading = true
        scope.launch {
            val extra = buildMap { put("genre", genre); if (items.isNotEmpty() && sc.second.supports("skip")) put("skip", items.size.toString()) }
            val page = runCatching { Tvo.client.catalog(sc.first, sc.second, extra) }.getOrDefault(emptyList())
            if (sc != src) return@launch
            val fresh = page.filter { p -> items.none { it.id == p.id } }
            if (fresh.isEmpty() || !sc.second.supports("skip")) end = true
            items.addAll(fresh); loading = false
        }
    }
    LaunchedEffect(src) { items.clear(); end = false; loading = false; more() }
    val label = genreLabel(genre)
    UniversePage(label, "Les meilleurs films et séries « ${label.lowercase()} »", null,
        GENRE_IMG[genre]?.let { TMDB_IMG + "w1280" + it }, TvoColors.Blue, t, { t = it }, items, loading, nav) { more() }
}

// ---------------------------------------------------------------- Recherche

@Composable
fun SearchSection(nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    val rawAddons by Tvo.store.addons.collectAsState()
    val tmdbKey by Tvo.store.tmdbKey.collectAsState()
    val addons = remember(rawAddons, tmdbKey) { app.tvo.tv.data.Tmdb.withTmdb(Tvo.store.allowed(rawAddons), tmdbKey, Tvo.store.currentProfile.value?.kids == true) }
    val firstKey = remember { FocusRequester() }
    val results by produceState<List<Pair<String, List<MetaPreview>>>?>(emptyList(), query, addons) {
        if (query.trim().length < 2) { value = emptyList(); return@produceState }
        delay(450)
        value = null
        val out = mutableListOf<Pair<String, List<MetaPreview>>>()
        addons.forEach { a ->
            a.manifest.catalogs.filter { it.supports("search") && it.onlyRequires("search") }.forEach { c ->
                val list = runCatching { Tvo.client.catalog(a, c, mapOf("search" to query.trim())) }.getOrDefault(emptyList())
                if (list.isNotEmpty()) out += (if (a.manifest.id == "com.linvo.cinemeta" || a.manifest.id == "tmdb") typeLabel(c.type) else if (a.manifest.id == "tvo.freetv.fr") c.name else "${c.name} · ${a.manifest.name}") to list
            }
        }
        value = out
    }
    val people by produceState<List<app.tvo.tv.data.Person>>(emptyList(), query) {
        value = if (query.trim().length < 2 || !app.tvo.tv.data.Tmdb.enabled) emptyList() else { delay(450); app.tvo.tv.data.Tmdb.searchPeople(query.trim()) }
    }
    val sagas by produceState<List<app.tvo.tv.data.Tmdb.Saga>>(emptyList(), query) {
        value = if (query.trim().length < 2 || !app.tvo.tv.data.Tmdb.enabled) emptyList() else { delay(450); app.tvo.tv.data.Tmdb.searchSagas(query.trim()) }
    }
    val trendMovies by produceState<List<MetaPreview>>(emptyList()) { value = if (app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.trending("movie") else emptyList() }
    val popPeople by produceState<List<app.tvo.tv.data.Person>>(emptyList()) { value = if (app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.popularPeople().take(20) else emptyList() }
    val trendSeries by produceState<List<MetaPreview>>(emptyList()) { value = if (app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.trending("series") else emptyList() }
    Row(Modifier.fillMaxSize().imageBackdrop(TvoColors.Blue)) {
        Column(Modifier.width(500.dp).padding(start = 40.dp, top = 28.dp)) {
            Text("Recherche", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Row(Modifier.padding(vertical = 14.dp).width(444.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x66101A36))
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔍", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(query.ifEmpty { "Film, série, saga ou acteur" }, fontSize = 19.sp,
                    color = if (query.isEmpty()) TvoColors.Muted else TvoColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TvKeyboard(onKey = { query += it }, onBackspace = { query = query.dropLast(1) }, onClear = { query = "" }, firstKey = firstKey)
        }
        Box(Modifier.fillMaxSize()) {
            val r = results
            when {
                r == null -> CenterMessage("Recherche…")
                query.trim().length < 2 -> LazyColumn(contentPadding = PaddingValues(top = 36.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item { Text("Que veux-tu regarder ?", Modifier.padding(start = 48.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text) }
                    if (popPeople.isNotEmpty()) item(key = "popPeople") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle("Acteurs et actrices du moment")
                            androidx.compose.foundation.lazy.LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                items(popPeople, key = { it.id }) { pp -> PersonBubble(pp) { nav.openPerson(pp.id) } }
                            }
                        }
                    }
                    if (trendMovies.isNotEmpty()) item { PosterRow("Films tendance", trendMovies, nav.openMeta, {}) }
                    if (trendSeries.isNotEmpty()) item { PosterRow("Séries tendance", trendSeries, nav.openMeta, {}) }
                }
                r.isEmpty() && people.isEmpty() && sagas.isEmpty() -> CenterMessage("Aucun résultat", "Essaie un autre titre, en anglais ou en français.")
                else -> LazyColumn(contentPadding = PaddingValues(top = 36.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (people.isNotEmpty()) item(key = "people") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle("Acteurs et actrices")
                            androidx.compose.foundation.lazy.LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                items(people, key = { it.id }) { pp -> PersonBubble(pp) { nav.openPerson(pp.id) } }
                            }
                        }
                    }
                    if (sagas.isNotEmpty()) item(key = "sagas") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionTitle("Sagas et collections")
                            androidx.compose.foundation.lazy.LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(sagas, key = { it.id }) { sg -> SagaTile(sg) { nav.openGenre("movie", "col:${sg.id}") } }
                            }
                        }
                    }
                    items(r, key = { it.first }) { (title, list) -> PosterRow(title, list, nav.openMeta, {}) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { firstKey.requestFocus() } }
}

// ---------------------------------------------------------------- Réglages

private data class SetPage(val key: String, val title: String)

/** Réglages façon télé : menu à gauche (comme les blocs du téléphone), contenu à droite. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SettingsSection(nav: Nav) {
    val store = Tvo.store
    val addons by store.addons.collectAsState()
    val external by store.externalPlayer.collectAsState()
    val profile by store.currentProfile.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    val pages = listOf(
        SetPage("profil", "Profil et compte"),
        SetPage("lecteur", "Lecteur"),
        SetPage("addons", "Add-ons"),
        SetPage("accueil", "Page d'accueil"),
        SetPage("app", "Application"),
    )
    var page by rememberSaveable { mutableStateOf("profil") }

    Row(Modifier.fillMaxSize().background(Color(0xFF0A0F1E)).padding(start = 48.dp, end = 48.dp, top = 20.dp)) {
        // Menu : liste simple
        // En revenant du contenu avec ◀, on retombe toujours sur la page ouverte (et pas sur l'entrée la plus proche, qui changerait de page)
        val menuFr = remember { FocusRequester() }
        Column(Modifier.width(280.dp).fillMaxHeight().focusProperties { enter = { menuFr } }) {
            Text("Réglages", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text, modifier = Modifier.padding(bottom = 14.dp))
            pages.forEach { pg ->
                val on = pg.key == page
                Surface(
                    onClick = { page = pg.key },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).let { if (on) it.focusRequester(menuFr) else it }.onFocused { page = pg.key },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (on) Color(0xFF1E2A4F) else Color.Transparent, contentColor = TvoColors.Text,
                        focusedContainerColor = Color.White, focusedContentColor = Color.Black),
                ) {
                    Text(pg.title, Modifier.padding(horizontal = 18.dp, vertical = 14.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.width(32.dp))
        // Contenu : panneau translucide pour que le fond coloré transparaisse
        LazyColumn(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Color(0xFF101731)).padding(horizontal = 28.dp), contentPadding = PaddingValues(top = 24.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (page) {
                "profil" -> {
                    item { Heading("Profil") }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            profile?.let { Avatar(it.name, it.color, 56.dp, icon = it.icon); Text(it.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text, modifier = Modifier.padding(end = 12.dp)) }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvoButton("Changer de profil", nav.switchProfile, icon = Icons.Default.Person)
                            profile?.let { p -> TvoButton("Modifier", { nav.editProfile(p.id) }) }
                        }
                    }
                    item { Heading("Compte et synchronisation") }
                    item { SyncBlock() }
                }
                "lecteur" -> {
                    item { Heading("Lecteur vidéo") }
                    item {
                        val engine by store.engine.collectAsState()
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Chip("Automatique", !external && engine == "auto", { store.setExternalPlayer(false); store.setEngine("auto") })
                            Chip("VLC", !external && engine == "vlc", { store.setExternalPlayer(false); store.setEngine("vlc") })
                            if (android.os.Build.VERSION.SDK_INT >= 26) Chip("MPV", !external && engine == "mpv", { store.setExternalPlayer(false); store.setEngine("mpv") })
                            Chip("Appli externe", external, { store.setExternalPlayer(true) })
                        }
                    }
                    item { Text("Automatique convient à presque tout. VLC ou MPV si un film pose problème.", fontSize = 14.sp, color = TvoColors.Muted) }
                    item { Heading("Qualité des films") }
                    item {
                        val qp by store.qpref.collectAsState()
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("all" to "Toutes", "4k" to "4K", "1080" to "1080p", "720" to "720p et moins").forEach { (k, l) -> Chip(l, qp.v == k, { store.setQpref(k) }) }
                        }
                    }
                    item { Heading("Fréquence d'image auto") }
                    item {
                        val afr by store.afr.collectAsState()
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Chip("Oui", afr, { store.setAfr(true) })
                            Chip("Non", !afr, { store.setAfr(false) })
                        }
                    }
                    item { Text("Adapte la télé à la cadence du film (l'écran peut noircir une seconde).", fontSize = 14.sp, color = TvoColors.Muted) }
                }
                "addons" -> {
                    item { Heading("Add-ons") }
                    item { Text("Compatible Stremio / Nuvio. Le premier de la liste est prioritaire.", fontSize = 14.sp, color = TvoColors.Muted) }
                    item { Text("Service de débridage", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text) }
                    item { AllDebridBlock() }
                    item { TvoButton("Ajouter un add-on", nav.addAddon, icon = Icons.Default.Add, primary = true) }
                    app.tvo.tv.data.ADDON_CATEGORIES.forEach { cat ->
                        val group = addons.filter { it.category() == cat }
                        if (group.isNotEmpty()) {
                            item(key = "cat-$cat") { Text(cat, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text, modifier = Modifier.padding(top = 6.dp)) }
                            items(group, key = { it.url }) { a -> AddonRow(a, addons.indexOf(a)) }
                        }
                    }
                }
                "accueil" -> { item { Heading("Page d'accueil") }; item { HomeEditor() } }
                else -> {
                    item { Heading("Application") }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 16.sp, color = TvoColors.Text)
                            TvoButton("Rechercher une mise à jour", {
                                updateMsg = "Recherche…"
                                scope.launch {
                                    release = Updater.check()
                                    updateMsg = if (release == null) "TVO est à jour." else "Version ${release!!.versionName} disponible."
                                }
                            }, icon = Icons.Default.Refresh)
                            release?.let { r -> TvoButton("Installer ${r.versionName}", {
                                scope.launch {
                                    updateMsg = "Téléchargement…"
                                    updateMsg = runCatching {
                                        val f = Updater.download(ctx, r.apkUrl) { p -> updateMsg = "Téléchargement ${(p * 100).toInt()} %" }
                                        Updater.install(ctx, f) ?: "Installation lancée."
                                    }.getOrElse { "Échec du téléchargement : ${it.message}" }
                                }
                            }, primary = true) }
                        }
                    }
                    updateMsg?.let { m -> item { Text(m, fontSize = 15.sp, color = TvoColors.Cyan) } }
                    item { Heading("Titres et résumés en français (TMDB)") }
                    item { TmdbBlock(nav) }
                    item { Text("TVO réunit tes catalogues et tes add-ons dans une seule appli. Elle n'héberge aucun contenu.", fontSize = 13.sp, color = TvoColors.Muted) }
                }
            }
        }
    }
}

@Composable
private fun Heading(t: String) = Text(t, Modifier.padding(top = 14.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Cyan)

@Composable
private fun AddonRow(a: Addon, index: Int) {
    val store = Tvo.store
    val m = a.manifest
    val kinds = m.resources.map { it.name }.mapNotNull {
        when (it) { "catalog" -> "catalogues"; "meta" -> "fiches"; "stream" -> "vidéos"; "subtitles" -> "sous-titres"; else -> null }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x66101A36)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${m.name}  ${m.version}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
        Text(kinds.joinToString(" · ").ifEmpty { "—" }, fontSize = 13.sp, color = TvoColors.Cyan)
        if (m.description.isNotBlank()) Text(m.description, fontSize = 13.sp, color = TvoColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (m.configurable && a.url.startsWith("http")) Text("Configurer depuis le téléphone : ${a.url}/configure", fontSize = 12.sp, color = TvoColors.Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val prof by store.currentProfile.collectAsState()
        val enabled = prof?.off?.contains(m.id) != true
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            prof?.let { pr -> Chip(if (enabled) "Activé" else "Désactivé", enabled, { store.setAddonForProfile(pr.id, m.id, !enabled); Tvo.client.clearCaches() }) }
            if (index > 0) TvoButton("Monter", { store.moveAddon(a.url, -1) }, icon = Icons.Default.KeyboardArrowUp)
            if (a.url.startsWith("http")) {
                val scope = rememberCoroutineScope()
                TvoButton("Actualiser", { scope.launch { runCatching { Tvo.client.install(a.url) }.onSuccess { store.addAddon(it); Tvo.client.clearCaches() } } }, icon = Icons.Default.Refresh)
            }
            TvoButton("Retirer", { store.removeAddon(a.url) }, icon = Icons.Default.Delete)
        }
    }
}

// ---------------------------------------------------------------- Ajout d'add-on

@Composable
fun AddonAddScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }
    val server = remember { RemoteInputServer { v -> text = v } }
    var port by remember { mutableStateOf<Int?>(null) }
    val ip = remember { RemoteInputServer.localIp() }
    DisposableEffect(Unit) { port = server.start(); onDispose { server.stop() } }
    val phoneUrl = if (ip != null && port != null) "http://$ip:$port" else null
    val qr = remember(phoneUrl) { phoneUrl?.let { RemoteInputServer.qr(it).asImageBitmap() } }

    fun install(url: String) {
        if (busy || url.isBlank()) return
        busy = true; msg = "Installation…"
        scope.launch {
            msg = runCatching { Tvo.client.install(url) }.fold(
                { a -> Tvo.store.addAddon(a); onDone(); null },
                { "Impossible d'installer : vérifie l'adresse (${it.message})." },
            )
            busy = false
        }
    }

    Row(Modifier.fillMaxSize().imageBackdrop(TvoColors.Blue).padding(48.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Ajouter un add-on", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TvoColors.Surface).padding(14.dp)) {
                Text(text.ifEmpty { "https://…/manifest.json" }, fontSize = 17.sp, color = if (text.isEmpty()) TvoColors.Muted else TvoColors.Text, maxLines = 2)
            }
            TvKeyboard(onKey = { text += it }, onBackspace = { text = text.dropLast(1) }, onClear = { text = "" }, url = true, firstKey = firstKey)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvoButton("Installer", { install(text) }, primary = true, icon = Icons.Default.Add)
                TvoButton("Annuler", onDone)
            }
            msg?.let { Text(it, Modifier.padding(top = 12.dp), fontSize = 15.sp, color = TvoColors.Cyan) }
        }
        Column(Modifier.width(340.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Plus simple : depuis ton téléphone", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
            if (qr != null && phoneUrl != null) {
                Image(qr, "QR code", Modifier.padding(vertical = 14.dp).size(220.dp).clip(RoundedCornerShape(12.dp)))
                Text("Scanne ce code (même Wi-Fi) ou ouvre :", fontSize = 14.sp, color = TvoColors.Muted)
                Text(phoneUrl, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TvoColors.Cyan)
                Text("Colle l'adresse, elle s'affiche ici.", fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text("Connecte la TV au Wi-Fi pour utiliser le téléphone.", fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Suggestions compatibles", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                app.tvo.tv.data.Suggestions.groups.forEach { (cat, why) ->
                    val list = app.tvo.tv.data.Suggestions.all.filter { it.category == cat }
                    if (list.isEmpty()) return@forEach
                    item { Column(Modifier.padding(top = 8.dp)) { Text(cat, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TvoColors.Cyan); Text(why, fontSize = 12.sp, color = TvoColors.Muted) } }
                    items(list, key = { it.url }) { sug ->
                        val installed = Tvo.store.addons.value.any { a -> a.url == sug.url.substringBefore("/manifest.json") || a.url == sug.url }
                        TvoButton(if (installed) "✓ ${sug.name}" else sug.name, { if (!installed) install(sug.url) }, Modifier.fillMaxWidth(), wide = true)
                        Text(sug.desc, fontSize = 12.sp, color = TvoColors.Muted)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { firstKey.requestFocus() } }
}


@Composable
private fun SyncBlock() {
    val status by app.tvo.tv.data.Sync.status.collectAsState()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf<String?>(null) }
    var entering by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    val padFr = remember { FocusRequester() }
    var qrCode by remember { mutableStateOf<app.tvo.tv.data.Sync.LoginRequest?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Connecte la TV au compte de ton téléphone (Google ou Apple) : Continuer à regarder, Ma liste, épisodes vus, profils et add-ons sont partagés.",
            fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.width(760.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvoButton("Connexion rapide (QR code)", {
                code = null; entering = false; msg = null
                scope.launch { qrCode = app.tvo.tv.data.Sync.loginStart(); if (qrCode == null) msg = "Pas de connexion." }
            }, primary = true)
            TvoButton("Synchroniser maintenant", { scope.launch { app.tvo.tv.data.Sync.syncNow() } }, icon = Icons.Default.Refresh)
            TvoButton("Afficher un code", {
                entering = false; msg = null; qrCode = null
                scope.launch { code = app.tvo.tv.data.Sync.createCode(); if (code == null) msg = "Pas de connexion." }
            })
            TvoButton("J'ai un code", { code = null; qrCode = null; entering = true; typed = ""; msg = null })
            if (status.isNotBlank()) Text(status, fontSize = 13.sp, color = TvoColors.Cyan)
        }
        qrCode?.let { req ->
            val c = req.code
            val bmp = remember(req) { app.tvo.tv.data.RemoteInputServer.qr("https://mansstyle59.github.io/VOoTV/connect/?code=$c&k=${req.key}", 420).asImageBitmap() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                androidx.compose.foundation.Image(bmp, "QR code", Modifier.size(210.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scanne avec l'appareil photo du téléphone", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                    Text("L'appli TVO s'ouvre et te demande de confirmer.\nOu dans TVO › Profil › Compte › Connecter une TV, saisis :", fontSize = 14.sp, color = TvoColors.Muted)
                    Text(c.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                    Text("En attente du téléphone… (valable 10 minutes)", fontSize = 13.sp, color = TvoColors.Cyan)
                }
            }
            LaunchedEffect(req) {
                repeat(300) {
                    delay(2000)
                    if (app.tvo.tv.data.Sync.loginPoll(req)) { qrCode = null; msg = "TV connectée à ton compte ✓"; return@LaunchedEffect }
                }
                qrCode = null; msg = "Code expiré, recommence."
            }
        }
        code?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(it.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                Text("Sur le téléphone : Réglages › Synchronisation › J'ai un code.\nValable 10 minutes.", fontSize = 14.sp, color = TvoColors.Muted)
            }
        }
        if (entering) {
            Text(if (typed.isEmpty()) "Tape le code affiché sur l'autre appareil" else typed.chunked(3).joinToString(" "),
                fontSize = if (typed.isEmpty()) 15.sp else 32.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            DigitPad({ d -> if (typed.length < 6) typed += d }, { typed = typed.dropLast(1) }, first = padFr)
            TvoButton("Relier", {
                if (typed.length == 6) scope.launch {
                    msg = "Liaison…"
                    msg = if (app.tvo.tv.data.Sync.claim(typed)) { entering = false; "Appareils reliés ✓" } else "Code invalide ou expiré."
                }
            }, primary = true)
            LaunchedEffect(Unit) { delay(80); runCatching { padFr.requestFocus() } }
        }
        msg?.let { Text(it, fontSize = 15.sp, color = TvoColors.Cyan) }
    }
}


@Composable
private fun TmdbBlock(nav: Nav) {
    val key by Tvo.store.tmdbKey.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (key == null) "Ajoute ta clé API TMDB (gratuite sur themoviedb.org) pour avoir les catalogues, titres, résumés, affiches et épisodes en français. Elle passe aussi sur ton téléphone grâce à la synchro."
            else "Clé TMDB active (…${key!!.takeLast(4)}). Catalogues et fiches en français.",
            fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.width(760.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvoButton(if (key == null) "Ajouter la clé TMDB" else "Changer la clé", nav.tmdbKey, primary = key == null)
            if (key != null) TvoButton("Retirer", { Tvo.store.setTmdbKey(null); Tvo.client.clearCaches() }, icon = Icons.Default.Delete)
        }
    }
}

@Composable
fun TmdbKeyScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }
    val server = remember { RemoteInputServer { v -> text = v } }
    var port by remember { mutableStateOf<Int?>(null) }
    val ip = remember { RemoteInputServer.localIp() }
    DisposableEffect(Unit) { port = server.start(); onDispose { server.stop() } }
    val phoneUrl = if (ip != null && port != null) "http://$ip:$port" else null
    val qr = remember(phoneUrl) { phoneUrl?.let { RemoteInputServer.qr(it).asImageBitmap() } }

    fun save() {
        if (busy || text.isBlank()) return
        busy = true; msg = "Vérification de la clé…"
        scope.launch {
            val err = app.tvo.tv.data.Tmdb.test(text)
            if (err == null) { Tvo.store.setTmdbKey(text); Tvo.client.clearCaches(); onDone() } else msg = err
            busy = false
        }
    }

    Row(Modifier.fillMaxSize().imageBackdrop(TvoColors.Blue).padding(48.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Clé API TMDB", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Text("Sur themoviedb.org : Paramètres › API. La « clé d'API » ou le « jeton d'accès en lecture » fonctionnent.",
                fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TvoColors.Surface).padding(14.dp)) {
                Text(text.ifEmpty { "Colle ou tape ta clé" }, fontSize = 16.sp, color = if (text.isEmpty()) TvoColors.Muted else TvoColors.Text, maxLines = 3)
            }
            TvKeyboard(onKey = { text += it }, onBackspace = { text = text.dropLast(1) }, onClear = { text = "" }, url = true, firstKey = firstKey)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvoButton("Enregistrer", { save() }, primary = true, icon = Icons.Default.Add)
                TvoButton("Annuler", onDone)
            }
            msg?.let { Text(it, Modifier.padding(top = 12.dp), fontSize = 15.sp, color = TvoColors.Cyan) }
        }
        Column(Modifier.width(340.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Plus simple : depuis ton téléphone", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
            if (qr != null && phoneUrl != null) {
                Image(qr, "QR code", Modifier.padding(vertical = 14.dp).size(220.dp).clip(RoundedCornerShape(12.dp)))
                Text("Scanne ce code (même Wi-Fi) ou ouvre :", fontSize = 14.sp, color = TvoColors.Muted)
                Text(phoneUrl, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TvoColors.Cyan)
            }
            Text("Ou ajoute la clé dans l'appli du téléphone : elle arrive ici par la synchro.", fontSize = 14.sp,
                color = TvoColors.Muted, modifier = Modifier.padding(top = 14.dp))
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { firstKey.requestFocus() } }
}


@Composable
private fun HomeEditor() {
    val store = Tvo.store
    val raw by store.addons.collectAsState()
    val key by store.tmdbKey.collectAsState()
    val home by store.home.collectAsState()
    val all = remember(raw, key) { buildRows(app.tvo.tv.data.Tmdb.withTmdb(raw, key), null) }
    val special = listOf("continue" to "Continuer à regarder", "library" to "Ma liste", "top10" to "Top 10 de la semaine", "genres" to "Catégories", "collections" to "Sagas et collections")
    val rows = remember(all, home) {
        val ordered = all.sortedBy { r -> home.order.indexOf(r.key).let { if (it < 0) Int.MAX_VALUE else it } }
        special + ordered.map { it.key to it.title }
    }
    // Déplacer : on choisit la rangée, puis l'emplacement où la mettre
    var moving by remember { mutableStateOf<String?>(null) }
    fun placeAt(k: String, target: String) {
        val keys = rows.drop(special.size).map { it.first }.toMutableList()
        val i = keys.indexOf(k); val j = keys.indexOf(target)
        if (i < 0 || j < 0) return
        keys.add(j, keys.removeAt(i)); store.setHome(home.copy(order = keys))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (moving != null) "Choisis l'emplacement : appuie sur « Mettre ici » à la place voulue." else "Choisis les rangées affichées. Pour changer l'ordre : « Déplacer » puis choisis l'emplacement.",
            fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.width(760.dp))
        rows.forEachIndexed { idx, (k, title) ->
            val visible = k !in home.hidden
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TvoColors.Surface).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, Modifier.weight(1f), fontSize = 15.sp, color = if (visible) TvoColors.Text else TvoColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Chip(if (visible) "Affichée" else "Masquée", visible, {
                    store.setHome(home.copy(hidden = if (visible) home.hidden + k else home.hidden - k))
                })
                if (idx >= special.size) {
                    val m = moving
                    when {
                        m == null -> Chip("Déplacer", false, { moving = k })
                        m == k -> Chip("Annuler", true, { moving = null })
                        else -> Chip("Mettre ici (${idx - special.size + 1})", false, { placeAt(m, k); moving = null })
                    }
                }
            }
        }
    }
}


// ---------- AllDebrid : connexion par code PIN (saisi sur le téléphone), jours Premium restants.
// La clé reste sur cette télé uniquement (pas envoyée au serveur de synchronisation).
private object AllDebrid {
    private const val API = "https://api.alldebrid.com"
    private fun http(url: String, post: String? = null, key: String? = null): kotlinx.serialization.json.JsonObject {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        if (key != null) c.setRequestProperty("Authorization", "Bearer $key")
        if (post != null) { c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); c.outputStream.use { it.write(post.toByteArray()) } }
        val txt = (if (c.responseCode in 200..299) c.inputStream else c.errorStream ?: c.inputStream).bufferedReader().use { it.readText() }
        return kotlinx.serialization.json.Json.parseToJsonElement(txt) as kotlinx.serialization.json.JsonObject
    }
    private fun kotlinx.serialization.json.JsonObject.o(k: String) = this[k] as? kotlinx.serialization.json.JsonObject
    private fun kotlinx.serialization.json.JsonObject.t(k: String) = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
    data class Pin(val pin: String, val check: String, val expiresIn: Int)
    data class User(val name: String, val premium: Boolean, val until: Long)
    class AuthError : Exception()
    suspend fun pin(): Pin? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { http("$API/v4.1/pin/get").o("data")?.let { Pin(it.t("pin")!!, it.t("check")!!, it.t("expires_in")?.toIntOrNull() ?: 600) } }.getOrNull()
    }
    /** null = pas encore validé ; "" = code expiré/invalide ; sinon la clé. */
    suspend fun check(p: Pin): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val j = http("$API/v4/pin/check", "pin=${p.pin}&check=${p.check}")
            if (j.t("status") == "error") "" else j.o("data")?.let { d -> if (d.t("activated") == "true") d.t("apikey") else null }
        }.getOrNull()
    }
    suspend fun user(key: String): User = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val j = http("$API/v4/user", key = key)
        if (j.t("status") != "success") { if (j.o("error")?.t("code")?.startsWith("AUTH") == true) throw AuthError() else throw Exception() }
        val u = j.o("data")!!.o("user")!!
        User(u.t("username") ?: "Compte connecté", u.t("isPremium") == "true", (u.t("premiumUntil")?.toLongOrNull() ?: 0L) * 1000)
    }
}

@Composable
private fun AllDebridBlock() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("tvo_alldebrid", android.content.Context.MODE_PRIVATE) }
    var key by remember { mutableStateOf(prefs.getString("key", null)) }
    var pin by remember { mutableStateOf<AllDebrid.Pin?>(null) }
    var user by remember { mutableStateOf<AllDebrid.User?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun save(k: String?) { prefs.edit().putString("key", k).apply(); key = k; user = null }
    LaunchedEffect(key, retry) {
        val k = key ?: return@LaunchedEffect
        err = null
        try { user = AllDebrid.user(k) } catch (e: AllDebrid.AuthError) { save(null); err = "Connexion expirée, reconnecte-toi." } catch (e: Exception) { err = "Impossible de joindre AllDebrid pour le moment." }
    }
    LaunchedEffect(pin) {
        val p = pin ?: return@LaunchedEffect
        val end = System.currentTimeMillis() + p.expiresIn * 1000L
        while (System.currentTimeMillis() < end) {
            kotlinx.coroutines.delay(5000)
            when (val r = AllDebrid.check(p)) { null -> {}; "" -> break; else -> { pin = null; save(r); return@LaunchedEffect } }
        }
        pin = null; err = "Code expiré, recommence."
    }
    Column(Modifier.width(620.dp).clip(RoundedCornerShape(18.dp)).background(TvoColors.Surface).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val p = pin; val u = user
        when {
            p != null -> {
                Text("Sur ton téléphone, va sur alldebrid.com/pin et saisis ce code :", fontSize = 16.sp, color = TvoColors.Text)
                Text(p.pin, fontSize = 56.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 12.sp)
                Text("En attente de ta validation…", fontSize = 14.sp, color = TvoColors.Muted)
                TvoButton("Annuler", { pin = null })
            }
            key == null -> {
                Text("Connecte ton compte AllDebrid pour voir tes jours Premium restants.", fontSize = 16.sp, color = TvoColors.Text)
                err?.let { Text(it, fontSize = 14.sp, color = Color(0xFFF59E0B)) }
                TvoButton("Connecter AllDebrid", { scope.launch { val r = AllDebrid.pin(); if (r == null) err = "AllDebrid ne répond pas, réessaie." else { err = null; pin = r } } }, primary = true)
            }
            u != null -> {
                val days = if (u.until > 0) maxOf(0L, (u.until - System.currentTimeMillis() + 86_399_999L) / 86_400_000L) else 0L
                val col = if (!u.premium || days <= 3) Color(0xFFEF4444) else if (days <= 15) Color(0xFFF59E0B) else Color(0xFF22C55E)
                Text(u.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                if (u.premium) Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("$days", fontSize = 48.sp, fontWeight = FontWeight.Black, color = col)
                    Text(if (days > 1) "jours Premium restants" else "jour Premium restant", Modifier.padding(bottom = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
                } else Text("Pas d'abonnement Premium actif", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = col)
                if (u.premium && u.until > 0) Text("Jusqu'au " + java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.FRENCH).format(java.util.Date(u.until)), fontSize = 15.sp, color = TvoColors.Muted)
                TvoButton("Déconnecter AllDebrid", { save(null) })
            }
            else -> {
                Text(err ?: "Chargement…", fontSize = 16.sp, color = TvoColors.Muted)
                if (err != null) TvoButton("Réessayer", { retry++ })
            }
        }
    }
}
