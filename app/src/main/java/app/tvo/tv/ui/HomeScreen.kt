package app.tvo.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.tvo.tv.R
import app.tvo.tv.Tvo
import app.tvo.tv.data.Addon
import app.tvo.tv.data.CatalogDef
import app.tvo.tv.data.MetaPreview
import app.tvo.tv.data.Profile
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** Menu identique sur TV et téléphone : Accueil, Films, Séries, Live TV, Ma liste (recherche et réglages en haut). */
enum class Section(val label: String, val icon: ImageVector?, val res: Int = 0, val inRail: Boolean = true) {
    HOME("Accueil", Icons.Default.Home),
    MOVIES("Films", null, R.drawable.ic_movie),
    SERIES("Séries", null, R.drawable.ic_series),
    LIVE("Live TV", null, R.drawable.ic_live),
    LIST("Ma liste", Icons.Default.Favorite),
    SEARCH("Recherche", Icons.Default.Search, inRail = false),
    SETTINGS("Réglages", Icons.Default.Settings, inRail = false),
}

class Nav(
    val openMeta: (MetaPreview) -> Unit,
    val openGenre: (type: String, genre: String) -> Unit,
    val openStreams: (type: String, metaId: String, videoId: String) -> Unit,
    val switchProfile: () -> Unit,
    val addAddon: () -> Unit,
    val editProfile: (String) -> Unit,
    val tmdbKey: () -> Unit,
    val openPerson: (String) -> Unit = {},
)

/** Item shown in the right-hand detail panel (follows the focused card). */
val LocalSelect = androidx.compose.runtime.staticCompositionLocalOf<(MetaPreview) -> Unit> { {} }

/** Hauteur de la barre de navigation du haut (façon Netflix). */
val NavBarHeight = 76.dp

@Composable
fun HomeScreen(nav: Nav) {
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    val profile by Tvo.store.currentProfile.collectAsState()
    BackHandler(enabled = section != Section.HOME) { section = Section.HOME }
    val immersive = section == Section.HOME || section == Section.MOVIES || section == Section.SERIES || section == Section.LIVE

    Box(Modifier.fillMaxSize().background(TvoColors.Bg)) {
        Box(Modifier.fillMaxSize().padding(top = if (immersive) 0.dp else NavBarHeight)) {
            when (section) {
                Section.HOME -> BrowseSection(null, nav)
                Section.MOVIES -> BrowseSection("movie", nav)
                Section.SERIES -> BrowseSection("series", nav)
                Section.LIVE -> LiveSection(nav)
                Section.LIST -> LibrarySection(nav)
                Section.SEARCH -> SearchSection(nav)
                Section.SETTINGS -> SettingsSection(nav)
            }
        }
        TopNav(section, profile, onSelect = { section = it }, onProfile = nav.switchProfile)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TopNav(current: Section, profile: Profile?, onSelect: (Section) -> Unit, onProfile: () -> Unit) {
    val currentFr = remember { FocusRequester() }
    Row(
        Modifier.fillMaxWidth().height(NavBarHeight)
            .background(Brush.verticalGradient(0f to Color(0xF20A0F1E), 0.7f to Color(0x990A0F1E), 1f to Color.Transparent))
            .padding(horizontal = 48.dp).focusProperties { enter = { currentFr } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.logo_tvo), "TVO", Modifier.height(30.dp))
        Spacer(Modifier.width(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Section.entries.filter { it.inRail }.forEach { s ->
                val on = s == current
                Surface(
                    onClick = { onSelect(s) },
                    modifier = if (on) Modifier.focusRequester(currentFr) else Modifier,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = if (on) Color.White else TvoColors.Muted,
                        focusedContainerColor = Color.White, focusedContentColor = Color.Black,
                    ),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.label, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                        Box(Modifier.padding(top = 3.dp).size(width = 18.dp, height = 3.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (on) TvoColors.Blue else Color.Transparent))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        RoundIconButton(Icons.Default.Search, "Recherche", if (current == Section.SEARCH) Modifier.focusRequester(currentFr) else Modifier) { onSelect(Section.SEARCH) }
        Spacer(Modifier.width(10.dp))
        RoundIconButton(Icons.Default.Settings, "Réglages", if (current == Section.SETTINGS) Modifier.focusRequester(currentFr) else Modifier) { onSelect(Section.SETTINGS) }
        Spacer(Modifier.width(12.dp))
        if (profile != null) Surface(
            onClick = onProfile, modifier = Modifier.size(40.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp))),
        ) { Avatar(profile.name, profile.color, 40.dp, icon = profile.icon) }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.size(40.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = TvoColors.Text,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black),
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(22.dp)) } }
}

/**
 * Écran « immersif » façon Netflix : grande image en fond qui suit la carte sélectionnée,
 * infos et boutons en haut, rangées de cartes paysage en dessous.
 */
@Composable
fun Immersive(hero: List<MetaPreview>, nav: Nav, key: Any?, rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    var selected by remember(key) { mutableStateOf<MetaPreview?>(null) }
    var heroIndex by remember(hero) { mutableStateOf(0) }
    var shown by remember(key) { mutableStateOf<MetaPreview?>(null) }
    val target = selected ?: hero.getOrNull(heroIndex)
    LaunchedEffect(target) { if (shown == null) shown = target else { delay(200); shown = target } }
    LaunchedEffect(hero, selected) { if (selected == null && hero.size > 1) while (true) { delay(9000); heroIndex = (heroIndex + 1) % hero.size } }
    val watchFr = remember(key) { FocusRequester() }
    val library by Tvo.store.library.collectAsState()
    var inRows by remember(key) { mutableStateOf(false) }

    val m = shown
    Box(Modifier.fillMaxSize()) {
        if (m != null) {
            val tv = m.type == "tv" || m.type == "channel"
            AsyncImage(m.background ?: m.poster, null,
                Modifier.fillMaxWidth(0.74f).fillMaxHeight(0.7f).align(Alignment.TopEnd).let { if (tv) it.padding(80.dp) else it },
                contentScale = if (tv) ContentScale.Fit else ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to TvoColors.Bg, 0.3f to TvoColors.Bg, 0.58f to Color(0x990A0F1E), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color(0x880A0F1E), 0.62f to TvoColors.Bg)))
        Column(Modifier.fillMaxSize()) {
            // Infos du titre : version complète avec boutons en haut de page, version compacte quand on parcourt les rangées
            // (les rangées gagnent de la place et rien ne se chevauche).
            Column(Modifier.padding(start = 48.dp, top = NavBarHeight + 4.dp).width(620.dp).height(if (inRows) 150.dp else 240.dp)) {
                if (m != null) {
                    if (m.logo != null) AsyncImage(m.logo, m.name, Modifier.height(if (inRows) 52.dp else 70.dp).fillMaxWidth(0.7f), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                    else Text(m.name, fontSize = if (inRows) 28.sp else 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    MetaLine(m.type, m.releaseInfo, m.imdbRating, m.genres, null)
                    if (!m.description.isNullOrBlank()) Text(m.description, Modifier.padding(top = 8.dp), fontSize = 14.sp,
                        color = TvoColors.Text.copy(alpha = 0.86f), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                    val inList = library.any { it.id == m.id }
                    if (!inRows) Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BlueButton("▶  Regarder", { nav.openMeta(m) }, filled = true, modifier = Modifier.focusRequester(watchFr))
                        BlueButton(if (inList) "✓  Dans ma liste" else "+  Ma liste", {
                            Tvo.store.toggleLibrary(app.tvo.tv.data.LibraryItem(m.id, m.type, m.name, m.poster, m.background))
                        }, filled = false)
                    }
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalSelect provides { selected = it }) {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).onFocusChanged { inRows = it.hasFocus },
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = rows,
                )
            }
        }
    }
    LaunchedEffect(key, m != null) { if (m != null) { delay(150); runCatching { watchFr.requestFocus() } } }
}

@Composable
fun BlueButton(text: String, onClick: () -> Unit, filled: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    androidx.tv.material3.Button(
        onClick = onClick, modifier = modifier,
        shape = androidx.tv.material3.ButtonDefaults.shape(shape),
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = if (filled) TvoColors.Blue else Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = if (filled) Color(0xFF5A7DFF) else Color.White,
            focusedContentColor = if (filled) Color.White else Color.Black,
        ),
        border = androidx.tv.material3.ButtonDefaults.border(
            border = Border(BorderStroke(1.5.dp, if (filled) Color.Transparent else TvoColors.Blue), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

fun applyHome(rows: List<RowSource>, h: app.tvo.tv.data.HomeLayout): List<RowSource> =
    rows.filter { it.key !in h.hidden }.sortedBy { r -> h.order.indexOf(r.key).let { if (it < 0) Int.MAX_VALUE else it } }

data class RowSource(val key: String, val title: String, val addon: Addon, val cat: CatalogDef, val extra: Map<String, String> = emptyMap())

private val homeGenres = listOf("Action", "Comedy", "Animation", "Sci-Fi", "Horror", "Thriller", "Drama", "Documentary")

fun buildRows(addons: List<Addon>, type: String?): List<RowSource> {
    val rows = mutableListOf<RowSource>()
    addons.forEach { a ->
        a.manifest.catalogs.filter { !it.requiresExtra && (type == null || it.type == type) }.forEach { c ->
            val title = when (a.manifest.id) {
                "com.linvo.cinemeta" -> catalogTitle(c.type, c.name)
                "tmdb", "tvo.freetv.fr" -> c.name
                else -> "${c.name} · ${a.manifest.name}"
            }
            rows += RowSource("${a.url}|${c.type}|${c.id}", title, a, c)
        }
    }
    if (type != null) {
        // Genre rows for the Films / Séries pages.
        val src = addons.firstNotNullOfOrNull { a -> a.manifest.catalogs.firstOrNull { it.type == type && it.supports("genre") && it.onlyRequires("genre") }?.let { a to it } }
        if (src != null) {
            val opts = src.second.options("genre").ifEmpty { homeGenres }
            homeGenres.filter { it in opts }.forEach { g ->
                rows += RowSource("${src.first.url}|$type|${src.second.id}|$g", "${genreLabel(g)} · ${typeLabel(type)}", src.first, src.second, mapOf("genre" to g))
            }
        }
    }
    return rows
}

@Composable
fun BrowseSection(type: String?, nav: Nav) {
    val store = Tvo.store
    val rawAddons by store.addons.collectAsState()
    val tmdbKey by store.tmdbKey.collectAsState()
    val prof by store.currentProfile.collectAsState()
    val addons = remember(rawAddons, tmdbKey, prof) { app.tvo.tv.data.Tmdb.withTmdb(store.allowed(rawAddons), tmdbKey, prof?.kids == true) }
    val progress by store.progress.collectAsState()
    val library by store.library.collectAsState()
    val home by store.home.collectAsState()
    val rows = remember(addons, type, home) { buildRows(addons, type).let { if (type == null) applyHome(it, home) else it } }
    val hidden = if (type == null) home.hidden else emptyList()
    val cont = remember(progress, type) { store.continueWatching(progress).filter { type == null || it.type == type } }
    val lib = remember(library, type) { library.filter { type == null || it.type == type } }
    val heroItems by produceState<List<MetaPreview>>(emptyList(), rows.firstOrNull()?.key) {
        val r = rows.firstOrNull()
        value = r?.let { runCatching { Tvo.client.catalog(it.addon, it.cat, it.extra) }.getOrNull() }?.take(5) ?: emptyList()
    }

    Immersive(heroItems, nav, key = type) {
        if (cont.isNotEmpty() && "continue" !in hidden) item(key = "continue") {
            val select = LocalSelect.current
            PanelSection("Continuer à regarder") {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(cont, key = { it.metaId }) { p ->
                        WideCard(
                            image = p.background ?: p.poster,
                            title = p.name,
                            subtitle = if (p.season != null) "S${p.season} : É${p.episode} · ${p.episodeTitle ?: ""}" else "Il reste ${((p.duration - p.position) / 60000).coerceAtLeast(1)} min",
                            progress = p.fraction,
                            onClick = { nav.openStreams(p.type, p.metaId, p.videoId) },
                            onLongClick = { store.removeFromContinue(p.metaId) },
                            width = 240.dp,
                            showPlay = true,
                            onFocus = { select(MetaPreview(p.metaId, p.type, p.name, p.poster, p.background)) },
                        )
                    }
                }
            }
        }
        if (lib.isNotEmpty() && "library" !in hidden) item(key = "library") { PortraitPanel("Ma liste", lib.map { it.toPreview() }, nav) }
        if (type == null && "genres" !in hidden) item(key = "genres") { GenreTiles(nav) { } }
        if (type == null) item(key = "platforms") { PlatformTiles(nav) }
        items(rows, key = { it.key }) { row -> CatalogRow(row, nav) }
        if (rows.isEmpty()) item { CenterMessage("Aucun catalogue", "Ajoute un add-on de catalogue dans Réglages.") }
    }
}

@Composable
fun PanelSection(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Spacer(Modifier.weight(1f))
            action?.invoke()
        }
        content()
    }
}

/** Rangée de grandes cartes paysage qui s'agrandissent au focus. */
@Composable
fun PortraitPanel(title: String, items: List<MetaPreview>, nav: Nav) {
    val select = LocalSelect.current
    PanelSection(title) {
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items, key = { it.id }) { m -> LandscapeCard(m, { nav.openMeta(m) }, width = 240.dp, onFocus = { select(m) }) }
        }
    }
}

@Composable
private fun CatalogRow(row: RowSource, nav: Nav) {
    val state by produceState<List<MetaPreview>?>(null, row.key) {
        value = runCatching { Tvo.client.catalog(row.addon, row.cat, row.extra) }.getOrDefault(emptyList())
    }
    when {
        state == null -> LoadingRow(row.title)
        state!!.isEmpty() -> Spacer(Modifier.height(0.dp))
        else -> PortraitPanel(row.title, state!!, nav)
    }
}

/** Images des catégories (photos TMDB, visibles même sans clé). */
private val GENRE_IMG = mapOf(
    "Action" to "/cKvDv2LpwVEqbdXWoQl4XgGN6le.jpg",
    "Adventure" to "/c7btGYblBnfAaw713WvFM0Kimc7.jpg",
    "Comedy" to "/sMmG82PtjK8A9rVc34gwKQtCm3A.jpg",
    "Drama" to "/bybAHw5fenKXRFI4phanPhpljlT.jpg",
    "Thriller" to "/yE766yhgg98tKwTKd8CyNvAtmn6.jpg",
    "Crime" to "/axRYPjeLD4zjJNaIw4TPopbKVfs.jpg",
    "Sci-Fi" to "/subj7knTn4DeS6KS7qI66EQKe9i.jpg",
    "Fantasy" to "/hofnlIyF6bePkgQOpcuRWLvzf15.jpg",
    "Horror" to "/3icyRAqgakNcQn6aDVz9libFmBA.jpg",
    "Romance" to "/sCzcYW9h55WcesOqA12cgEr9Exw.jpg",
    "Animation" to "/qjTqY5coNiz6sVtPng40IzltsoN.jpg",
    "Family" to "/rueyXwlewVPAP5NkqCUJx4RjhqB.jpg",
    "Mystery" to "/qqHFLg90IUSoTmSMpKOOW5RyqN3.jpg",
    "History" to "/neeNHeXjMF5fXoCJRsOmkNGC7q.jpg",
    "War" to "/zb6fM1CX41D9rF9hdgclu0peUmy.jpg",
    "Western" to "/2oZklIzUbvZXXzIFzv7Hi68d6xf.jpg",
    "Documentary" to "/dUbP1HNdI0aCq1zgRJw28PWSqmk.jpg",
)
private const val TMDB_IMG = "https://image.tmdb.org/t/p/"

@Composable
private fun GenreTiles(nav: Nav, onFocus: () -> Unit) {
    PanelSection("Catégories") {
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(homeGenres) { g ->
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    onClick = { nav.openGenre("movie", g) },
                    modifier = Modifier.width(240.dp).height(135.dp).onFocused(onFocus),
                    shape = ClickableSurfaceDefaults.shape(shape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = TvoColors.Surface, focusedContainerColor = TvoColors.Surface),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        GENRE_IMG[g]?.let { AsyncImage(TMDB_IMG + "w500" + it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE6000000))))
                        Text(genreLabel(g), Modifier.align(Alignment.BottomStart).padding(12.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformTiles(nav: Nav) {
    PanelSection("Plateformes") {
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(PLATFORMS) { (pl, logo) ->
                val (id, name, color) = pl
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    onClick = { nav.openGenre("all", "prov:$id") },
                    modifier = Modifier.width(240.dp).height(110.dp),
                    shape = ClickableSurfaceDefaults.shape(shape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(color), focusedContainerColor = Color(color)),
                ) {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(color), Color(0xFF0A0F1E)))).padding(horizontal = 16.dp)) {
                        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(TMDB_IMG + "w154" + logo, name, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(14.dp))
                            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Backdrop(url: String?) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(url, null, Modifier.fillMaxWidth(0.78f).fillMaxHeight(0.72f).align(Alignment.TopEnd), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.0f to TvoColors.Bg, 0.25f to TvoColors.Bg, 0.6f to Color(0x660B0B12), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.0f to Color.Transparent, 0.45f to Color(0x990B0B12), 0.7f to TvoColors.Bg)))
    }
}

@Composable
fun HeroInfo(item: MetaPreview?, modifier: Modifier = Modifier) {
    Box(modifier.padding(start = 48.dp, top = 34.dp, end = 48.dp)) {
        AnimatedVisibility(item != null, enter = fadeIn(), exit = fadeOut()) {
            if (item != null) Column(Modifier.width(560.dp)) {
                if (item.logo != null) {
                    AsyncImage(item.logo, item.name, Modifier.height(80.dp).fillMaxWidth(0.8f), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                } else {
                    Text(item.name, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
                MetaLine(item.type, item.releaseInfo, item.imdbRating, item.genres, null)
                if (!item.description.isNullOrBlank()) {
                    Text(item.description, Modifier.padding(top = 10.dp), fontSize = 15.sp, color = TvoColors.Text.copy(alpha = 0.85f),
                        maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
                }
            }
        }
    }
}

@Composable
fun MetaLine(type: String, year: String?, rating: String?, genres: List<String>, runtime: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(typeLabel(type, plural = false), Modifier.clip(RoundedCornerShape(5.dp)).background(TvoColors.Accent)
            .padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (!year.isNullOrBlank()) Text(year, fontSize = 15.sp, color = TvoColors.Text)
        if (!rating.isNullOrBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = Color(0xFFFACC15))
            Text(" $rating", fontSize = 15.sp, color = TvoColors.Text)
        }
        if (!runtime.isNullOrBlank()) Text(runtime, fontSize = 15.sp, color = TvoColors.Text)
        if (genres.isNotEmpty()) Text(genres.take(3).joinToString(" · ") { genreLabel(it) }, fontSize = 15.sp, color = TvoColors.Muted, maxLines = 1)
    }
}


/** Live TV : rangées de chaînes (Freebox TV, listes M3U, add-ons de chaînes). */
@Composable
fun LiveSection(nav: Nav) {
    val raw0 by Tvo.store.addons.collectAsState()
    val raw = remember(raw0) { Tvo.store.allowed(raw0) }
    val rows = remember(raw) { buildRows(raw, "tv") + buildRows(raw, "channel") }
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    if (rows.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = NavBarHeight + 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Freebox TV", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Text("Abonné Free ? Connecte la TV à ta Freebox (Wi-Fi ou câble) pour ajouter toutes tes chaînes. Tu peux aussi ajouter ta propre liste M3U dans Réglages › Add-ons.",
                fontSize = 15.sp, color = TvoColors.Muted, modifier = Modifier.width(700.dp))
            BlueButton("Ajouter les chaînes Freebox", {
                msg = "Recherche de la Freebox…"
                scope.launch {
                    msg = runCatching { Tvo.client.install("m3u:http://mafreebox.freebox.fr/freeboxtv/playlist.m3u") }
                        .fold({ Tvo.store.addAddon(it); null }, { it.message })
                }
            }, filled = true)
            msg?.let { Text(it, fontSize = 15.sp, color = TvoColors.Cyan) }
        }
        return
    }
    val hero by produceState<List<MetaPreview>>(emptyList(), rows.firstOrNull()?.key) {
        val r = rows.firstOrNull()
        value = r?.let { runCatching { Tvo.client.catalog(it.addon, it.cat, it.extra) }.getOrNull() }?.take(1) ?: emptyList()
    }
    Immersive(hero, nav, key = "live") {
        items(rows, key = { it.key }) { row -> CatalogRow(row, nav) }
    }
}
