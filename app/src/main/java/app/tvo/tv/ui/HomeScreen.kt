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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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

enum class Section(val label: String, val icon: ImageVector) {
    HOME("Accueil", Icons.Default.Home),
    MOVIES("Films", Icons.Default.PlayArrow),
    SERIES("Séries", Icons.AutoMirrored.Filled.List),
    LIST("Ma liste", Icons.Default.Favorite),
    SEARCH("Recherche", Icons.Default.Search),
    SETTINGS("Réglages", Icons.Default.Settings),
}

class Nav(
    val openMeta: (MetaPreview) -> Unit,
    val openGenre: (type: String, genre: String) -> Unit,
    val openStreams: (type: String, metaId: String, videoId: String) -> Unit,
    val switchProfile: () -> Unit,
    val addAddon: () -> Unit,
    val editProfile: (String) -> Unit,
    val tmdbKey: () -> Unit,
)

@Composable
fun HomeScreen(nav: Nav) {
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    val profile by Tvo.store.currentProfile.collectAsState()
    BackHandler(enabled = section != Section.HOME) { section = Section.HOME }

    Row(Modifier.fillMaxSize().background(TvoColors.Bg)) {
        Sidebar(section, onSelect = { section = it }, nav = nav)
        Column(Modifier.fillMaxSize()) {
            TopBar(profile, onSearch = { section = Section.SEARCH }, onProfile = nav.switchProfile)
            Box(Modifier.fillMaxSize()) {
                when (section) {
                    Section.HOME -> BrowseSection(null, nav)
                    Section.MOVIES -> BrowseSection("movie", nav)
                    Section.SERIES -> BrowseSection("series", nav)
                    Section.LIST -> LibrarySection(nav)
                    Section.SEARCH -> SearchSection(nav)
                    Section.SETTINGS -> SettingsSection(nav)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Sidebar(current: Section, onSelect: (Section) -> Unit, nav: Nav) {
    val selected = remember { FocusRequester() }
    val rawAddons by Tvo.store.addons.collectAsState()
    val tmdbKey by Tvo.store.tmdbKey.collectAsState()
    val addons = remember(rawAddons, tmdbKey) { app.tvo.tv.data.Tmdb.withTmdb(rawAddons, tmdbKey) }
    val src = remember(addons) { buildRows(addons, "series").firstOrNull() ?: buildRows(addons, null).getOrNull(1) }
    val news by produceState<List<MetaPreview>>(emptyList(), src?.key) {
        value = src?.let { runCatching { Tvo.client.catalog(it.addon, it.cat, it.extra) }.getOrNull() }?.take(3) ?: emptyList()
    }
    Column(
        Modifier.fillMaxHeight().width(248.dp).background(TvoColors.Panel)
            .padding(start = 22.dp, end = 22.dp, top = 28.dp)
            .focusProperties { enter = { selected } },
    ) {
        Image(painterResource(R.drawable.logo_tvo), "TVO", Modifier.padding(start = 6.dp, bottom = 26.dp).height(30.dp))
        Section.entries.forEach { s ->
            RailItem(s == current, s.label,
                modifier = if (s == current) Modifier.focusRequester(selected) else Modifier,
                onClick = { onSelect(s) }) { Icon(s.icon, s.label, Modifier.size(20.dp)) }
        }
        if (news.isNotEmpty()) {
            Text("Nouveautés", Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                news.forEach { m ->
                    WideCard(m.background ?: m.poster, m.name, null, null, { nav.openMeta(m) }, width = 204.dp, showPlay = false)
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvoColors.Surface else Color.Transparent,
            contentColor = if (selected) TvoColors.Text else TvoColors.Muted,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(TvoColors.Accent))
            else Spacer(Modifier.size(8.dp))
            Spacer(Modifier.width(10.dp))
            leading()
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun TopBar(profile: Profile?, onSearch: () -> Unit, onProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onSearch,
            modifier = Modifier.width(360.dp).height(44.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(containerColor = TvoColors.Surface, contentColor = TvoColors.Muted,
                focusedContainerColor = Color.White, focusedContentColor = Color.Black),
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Text("Rechercher un film, une série…", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        if (profile != null) Surface(
            onClick = onProfile,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
            colors = ClickableSurfaceDefaults.colors(containerColor = TvoColors.Surface, contentColor = TvoColors.Text,
                focusedContainerColor = Color.White, focusedContentColor = Color.Black),
        ) {
            Row(Modifier.padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile.name, profile.color, 34.dp); Spacer(Modifier.width(10.dp))
                Column {
                    Text(profile.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Changer de profil", fontSize = 11.sp, color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.65f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Browse

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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (heroItems.isNotEmpty()) item(key = "hero") {
            HeroBanner(heroItems, nav, requestFocus = !focusGiven) { focusGiven = true }
        }
        if (cont.isNotEmpty() && "continue" !in hidden) item(key = "continue") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Continuer à regarder")
                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        )
                    }
                }
            }
        }
        if (lib.isNotEmpty() && "library" !in hidden) item(key = "library") { LandscapeRow("Ma liste", lib.map { it.toPreview() }, nav.openMeta) }
        if (type == null && "genres" !in hidden) item(key = "genres") { GenreTiles(nav) { } }
        items(rows, key = { it.key }) { row -> CatalogRow(row, nav) }
        if (rows.isEmpty()) item { CenterMessage("Aucun catalogue", "Ajoute un add-on de catalogue dans Réglages.") }
    }
}

@Composable
private fun HeroBanner(items: List<MetaPreview>, nav: Nav, requestFocus: Boolean, onFocusTaken: () -> Unit) {
    var index by remember(items) { mutableStateOf(0) }
    val item = items[index.coerceIn(items.indices)]
    val inList by remember(item.id) { derivedStateOf { Tvo.store.library.value.any { it.id == item.id } } }
    val watchFr = remember { FocusRequester() }
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.padding(horizontal = 32.dp).fillMaxWidth().height(270.dp).clip(shape).background(TvoColors.Surface)) {
        AsyncImage(item.background ?: item.poster, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF2141619), 0.45f to Color(0x99141619), 0.75f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0xAA000000))))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 32.dp).width(440.dp)) {
            if (item.logo != null) AsyncImage(item.logo, item.name, Modifier.height(78.dp).fillMaxWidth(0.85f), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
            else Text(item.name, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            val meta = listOfNotNull(item.releaseInfo, item.genres.firstOrNull()?.let { genreLabel(it) }, item.imdbRating?.let { "★ $it" }).joinToString("  ·  ")
            Text(meta, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD5DADE))
            if (!item.description.isNullOrBlank()) Text(item.description, Modifier.padding(top = 8.dp), fontSize = 13.sp,
                color = Color(0xFFC2C8CD), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TvoButton("Regarder", { nav.openMeta(item) }, Modifier.focusRequester(watchFr), Icons.Default.PlayArrow, primary = true)
                TvoButton(if (inList) "✓" else "+", {
                    Tvo.store.toggleLibrary(app.tvo.tv.data.LibraryItem(item.id, item.type, item.name, item.poster, item.background))
                })
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEachIndexed { i, m ->
                val s = RoundedCornerShape(8.dp)
                Surface(
                    onClick = { nav.openMeta(m) },
                    modifier = Modifier.width(92.dp).aspectRatio(16f / 10f).onFocused { index = i },
                    shape = ClickableSurfaceDefaults.shape(s),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(2.dp, if (i == index) TvoColors.Cyan else Color(0x55FFFFFF)), shape = s),
                        focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = s)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = TvoColors.Surface2, focusedContainerColor = TvoColors.Surface2),
                ) { AsyncImage(m.background ?: m.poster, m.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
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
        else -> LandscapeRow(row.title, state!!, nav.openMeta)
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
