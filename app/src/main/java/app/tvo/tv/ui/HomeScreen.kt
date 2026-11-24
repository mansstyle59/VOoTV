package app.tvo.tv.ui

import androidx.compose.animation.togetherWith
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

@Composable
fun HomeScreen(nav: Nav) {
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    val profile by Tvo.store.currentProfile.collectAsState()
    var selected by remember { mutableStateOf<MetaPreview?>(null) }
    var shown by remember { mutableStateOf<MetaPreview?>(null) }
    LaunchedEffect(selected) { delay(220); shown = selected }
    BackHandler(enabled = section != Section.HOME) { section = Section.HOME }
    val browse = section == Section.HOME || section == Section.MOVIES || section == Section.SERIES || section == Section.LIVE

    Row(Modifier.fillMaxSize().background(TvoColors.Bg).padding(14.dp)) {
        IconRail(section, onSelect = { section = it; selected = null; shown = null })
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopBar(profile, onSearch = { section = Section.SEARCH }, onSettings = { section = Section.SETTINGS }, onProfile = nav.switchProfile)
            Box(Modifier.fillMaxSize()) {
                androidx.compose.runtime.CompositionLocalProvider(LocalSelect provides { selected = it }) {
                    androidx.compose.animation.AnimatedContent(section, label = "section", transitionSpec = {
                        (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(320)) +
                            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(320)) { it / 14 }) togetherWith
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
                    }) { sec -> when (sec) {
                        Section.HOME -> BrowseSection(null, nav)
                        Section.MOVIES -> BrowseSection("movie", nav)
                        Section.SERIES -> BrowseSection("series", nav)
                        Section.LIVE -> LiveSection(nav)
                        Section.LIST -> LibrarySection(nav)
                        Section.SEARCH -> SearchSection(nav)
                        Section.SETTINGS -> SettingsSection(nav)
                    } }
                }
            }
        }
        if (browse && shown != null) {
            Spacer(Modifier.width(14.dp))
            DetailPanel(shown!!, nav, onClose = { selected = null; shown = null })
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun IconRail(current: Section, onSelect: (Section) -> Unit) {
    val selected = remember { FocusRequester() }
    Column(
        Modifier.fillMaxHeight().width(76.dp).clip(RoundedCornerShape(22.dp)).background(TvoColors.Panel)
            .padding(vertical = 22.dp).focusProperties { enter = { selected } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.logo_mark), "TVO", Modifier.height(40.dp))
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Section.entries.filter { it.inRail }.forEach { s ->
                val on = s == current
                Surface(
                    onClick = { onSelect(s) },
                    modifier = Modifier.size(50.dp).let { if (on) it.focusRequester(selected) else it },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (on) TvoColors.Blue else Color.Transparent,
                        contentColor = if (on) Color.White else TvoColors.Muted,
                        focusedContainerColor = if (on) TvoColors.Blue else Color.White,
                        focusedContentColor = if (on) Color.White else Color.Black,
                    ),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(14.dp))),
                ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (s.icon != null) Icon(s.icon, s.label, Modifier.size(22.dp)) else Icon(painterResource(s.res), s.label, Modifier.size(22.dp)) } }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(current.label, fontSize = 11.sp, color = TvoColors.Muted, maxLines = 1)
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier.size(44.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(containerColor = TvoColors.Surface, contentColor = TvoColors.Text,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black),
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(20.dp)) } }
}

@Composable
private fun TopBar(profile: Profile?, onSearch: () -> Unit, onSettings: () -> Unit, onProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onSearch,
            modifier = Modifier.width(340.dp).height(44.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(containerColor = TvoColors.Surface, contentColor = TvoColors.Muted,
                focusedContainerColor = Color.White, focusedContentColor = Color.Black),
            border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, TvoColors.Line), shape = RoundedCornerShape(14.dp))),
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
                Text("Rechercher…", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        RoundIconButton(Icons.Default.Settings, "Réglages", onSettings)
        Spacer(Modifier.width(12.dp))
        if (profile != null) Surface(
            onClick = onProfile, modifier = Modifier.size(44.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(14.dp))),
        ) { Avatar(profile.name, profile.color, 44.dp) }
    }
}

/** Right-hand panel: details of the focused title. */
@Composable
private fun DetailPanel(m: MetaPreview, nav: Nav, onClose: () -> Unit) {
    val library by Tvo.store.library.collectAsState()
    val inList = library.any { it.id == m.id }
    Column(Modifier.fillMaxHeight().width(330.dp).clip(RoundedCornerShape(22.dp)).background(TvoColors.Panel).padding(18.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(14.dp)).background(TvoColors.Surface)) {
            AsyncImage(m.background ?: m.poster, m.name, Modifier.fillMaxSize(),
                contentScale = if (m.type == "tv") ContentScale.Fit else ContentScale.Crop)
            Box(Modifier.align(Alignment.Center).size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x66FFFFFF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(24.dp), tint = Color.White)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(m.name, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TvoColors.Text, lineHeight = 31.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        val chips = (listOf(typeLabel(m.type, plural = false)) + m.genres.take(2).map { genreLabel(it) })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEachIndexed { i, c ->
                Text(c, Modifier.clip(RoundedCornerShape(8.dp)).background(if (i == 0) TvoColors.Blue else Color(0xFFE8E8EE))
                    .padding(horizontal = 9.dp, vertical = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (i == 0) Color.White else Color.Black, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (m.imdbRating != null) Text("★ ${m.imdbRating}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            if (m.releaseInfo != null) Text(m.releaseInfo, fontSize = 16.sp, color = TvoColors.Muted)
        }
        Spacer(Modifier.height(10.dp))
        Text(m.description ?: "", Modifier.weight(1f), fontSize = 13.sp, color = TvoColors.Muted, lineHeight = 19.sp, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlueButton("Regarder", { nav.openMeta(m) }, filled = true)
            BlueButton(if (inList) "Dans ma liste" else "Ma liste", {
                Tvo.store.toggleLibrary(app.tvo.tv.data.LibraryItem(m.id, m.type, m.name, m.poster, m.background))
            }, filled = false)
        }
    }
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
    val addons = remember(rawAddons, tmdbKey) { app.tvo.tv.data.Tmdb.withTmdb(rawAddons, tmdbKey) }
    val progress by store.progress.collectAsState()
    val library by store.library.collectAsState()
    var focusGiven by remember(type) { mutableStateOf(false) }
    val home by store.home.collectAsState()
    val rows = remember(addons, type, home) { buildRows(addons, type).let { if (type == null) applyHome(it, home) else it } }
    val hidden = if (type == null) home.hidden else emptyList()
    val cont = remember(progress, type) { store.continueWatching(progress).filter { type == null || it.type == type } }
    val lib = remember(library, type) { library.filter { type == null || it.type == type } }
    val heroItems by produceState<List<MetaPreview>>(emptyList(), rows.firstOrNull()?.key) {
        val r = rows.firstOrNull()
        value = r?.let { runCatching { Tvo.client.catalog(it.addon, it.cat, it.extra) }.getOrNull() }?.take(5) ?: emptyList()
    }

    val select = LocalSelect.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (heroItems.isNotEmpty()) item(key = "hero") {
            HeroBanner(heroItems, nav, requestFocus = !focusGiven) { focusGiven = true }
        }
        if (cont.isNotEmpty() && "continue" !in hidden) item(key = "continue") {
            PanelSection("Continuer à regarder") {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(cont, key = { it.metaId }) { p ->
                        WideCard(
                            image = p.background ?: p.poster,
                            title = p.name,
                            subtitle = if (p.season != null) "S${p.season} : É${p.episode} · ${p.episodeTitle ?: ""}" else "Il reste ${((p.duration - p.position) / 60000).coerceAtLeast(1)} min",
                            progress = p.fraction,
                            onClick = { nav.openStreams(p.type, p.metaId, p.videoId) },
                            onLongClick = { store.removeFromContinue(p.metaId) },
                            width = 232.dp,
                            showPlay = true,
                            onFocus = { select(MetaPreview(p.metaId, p.type, p.name, p.poster, p.background)) },
                        )
                    }
                }
            }
        }
        if (lib.isNotEmpty() && "library" !in hidden) item(key = "library") { PortraitPanel("Ma liste", lib.map { it.toPreview() }, nav) }
        if (type == null && "genres" !in hidden) item(key = "genres") { GenreTiles(nav) { } }
        items(rows, key = { it.key }) { row -> CatalogRow(row, nav) }
        if (rows.isEmpty()) item { CenterMessage("Aucun catalogue", "Ajoute un add-on de catalogue dans Réglages.") }
    }
}

@Composable
fun PanelSection(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TvoColors.Panel).padding(vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
            Text("  →", fontSize = 18.sp, color = TvoColors.Text)
            Spacer(Modifier.weight(1f))
            action?.invoke()
        }
        content()
    }
}

@Composable
fun PortraitPanel(title: String, items: List<MetaPreview>, nav: Nav) {
    val select = LocalSelect.current
    PanelSection(title) {
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items, key = { it.id }) { m -> PortraitCard(m, { nav.openMeta(m) }, onFocus = { select(m) }) }
        }
    }
}

@Composable
private fun HeroBanner(items: List<MetaPreview>, nav: Nav, requestFocus: Boolean, onFocusTaken: () -> Unit) {
    var index by remember(items) { mutableStateOf(0) }
    val item = items[index.coerceIn(items.indices)]
    val select = LocalSelect.current
    val library by Tvo.store.library.collectAsState()
    val inList = library.any { it.id == item.id }
    val watchFr = remember { FocusRequester() }
    var heroFocused by remember { mutableStateOf(false) }
    LaunchedEffect(items) { while (true) { delay(9000); if (!heroFocused) index = (index + 1) % items.size } }
    Box(Modifier.fillMaxWidth().height(236.dp).onFocusChanged { heroFocused = it.hasFocus }.clip(RoundedCornerShape(22.dp)).background(TvoColors.Surface)) {
        AsyncImage(item.background ?: item.poster, item.name, Modifier.fillMaxHeight().fillMaxWidth(0.72f).align(Alignment.CenterEnd), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to TvoColors.Surface, 0.34f to TvoColors.Surface, 0.62f to Color(0x881D1D21), 1f to Color.Transparent)))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 30.dp).width(430.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, Modifier.weight(1f, fill = false), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val badge = item.imdbRating?.let { "★ $it" } ?: item.releaseInfo
                if (badge != null) Text(badge, Modifier.padding(start = 12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE8E8EE))
                    .padding(horizontal = 9.dp, vertical = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            if (!item.description.isNullOrBlank()) Text(item.description, Modifier.padding(top = 8.dp), fontSize = 13.sp,
                color = TvoColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BlueButton("Regarder", { nav.openMeta(item) }, filled = true, modifier = Modifier.focusRequester(watchFr).onFocused { select(item) })
                BlueButton(if (inList) "Dans ma liste" else "Ma liste", {
                    Tvo.store.toggleLibrary(app.tvo.tv.data.LibraryItem(item.id, item.type, item.name, item.poster, item.background))
                }, filled = false)
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.indices.forEach { i -> Box(Modifier.size(width = if (i == index) 20.dp else 7.dp, height = 7.dp).clip(RoundedCornerShape(4.dp)).background(if (i == index) TvoColors.Blue else Color(0x66FFFFFF))) }
        }
    }
    if (requestFocus) LaunchedEffect(Unit) { onFocusTaken(); delay(120); runCatching { watchFr.requestFocus() } }
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

@Composable
private fun GenreTiles(nav: Nav, onFocus: () -> Unit) {
    val palette = listOf(
        listOf(Color(0xFF7C3AED), Color(0xFF2563EB)), listOf(Color(0xFFDB2777), Color(0xFFF97316)),
        listOf(Color(0xFF059669), Color(0xFF22D3EE)), listOf(Color(0xFF1E3A8A), Color(0xFF7C3AED)),
        listOf(Color(0xFF7F1D1D), Color(0xFF111827)), listOf(Color(0xFF0F766E), Color(0xFF1E293B)),
        listOf(Color(0xFFB45309), Color(0xFF7C2D12)), listOf(Color(0xFF334155), Color(0xFF0EA5E9)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Explorer par catégorie")
        LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(homeGenres) { i, g ->
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    onClick = { nav.openGenre("movie", g) },
                    modifier = Modifier.width(200.dp).height(100.dp).onFocused(onFocus),
                    shape = ClickableSurfaceDefaults.shape(shape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                ) {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(palette[i % palette.size])), contentAlignment = Alignment.Center) {
                        Text(genreLabel(g), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
    val raw by Tvo.store.addons.collectAsState()
    val rows = remember(raw) { buildRows(raw, "tv") + buildRows(raw, "channel") }
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    if (rows.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        items(rows, key = { it.key }) { row -> CatalogRow(row, nav) }
    }
}
