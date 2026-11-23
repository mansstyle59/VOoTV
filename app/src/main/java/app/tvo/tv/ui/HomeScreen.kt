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
)

@Composable
fun HomeScreen(nav: Nav) {
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    val profile by Tvo.store.currentProfile.collectAsState()
    BackHandler(enabled = section != Section.HOME) { section = Section.HOME }

    Box(Modifier.fillMaxSize().background(TvoColors.Bg)) {
        Box(Modifier.fillMaxSize().padding(start = 84.dp)) {
            when (section) {
                Section.HOME -> BrowseSection(null, nav)
                Section.MOVIES -> BrowseSection("movie", nav)
                Section.SERIES -> BrowseSection("series", nav)
                Section.LIST -> LibrarySection(nav)
                Section.SEARCH -> SearchSection(nav)
                Section.SETTINGS -> SettingsSection(nav)
            }
        }
        SideRail(section, profile, onSelect = { section = it }, onProfile = nav.switchProfile)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SideRail(current: Section, profile: Profile?, onSelect: (Section) -> Unit, onProfile: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 230.dp else 84.dp, label = "rail")
    val selected = remember { FocusRequester() }
    Box(
        Modifier.fillMaxHeight().width(width)
            .background(
                if (expanded) Brush.horizontalGradient(listOf(Color(0xF20B0B12), Color(0xE60B0B12), Color(0x000B0B12)))
                else Brush.horizontalGradient(listOf(Color(0xCC0B0B12), Color(0x000B0B12)))
            )
            .onFocusChanged { expanded = it.hasFocus }
    ) {
        Column(
            Modifier.fillMaxHeight().padding(vertical = 26.dp, horizontal = 14.dp)
                .focusProperties { enter = { selected } },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (expanded) Image(painterResource(R.drawable.logo_tvo), "TVO", Modifier.padding(start = 8.dp, bottom = 10.dp).height(28.dp))
            else Spacer(Modifier.height(38.dp))
            if (profile != null) {
                RailItem(expanded, false, profile.name, onClick = onProfile) {
                    Avatar(profile.name, profile.color, 26.dp)
                }
                Spacer(Modifier.height(14.dp))
            }
            Section.entries.forEach { s ->
                RailItem(expanded, s == current, s.label,
                    modifier = if (s == current) Modifier.focusRequester(selected) else Modifier,
                    onClick = { onSelect(s) }) {
                    Icon(s.icon, s.label, Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    expanded: Boolean,
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) TvoColors.Text else TvoColors.Muted,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) { leading() }
            if (expanded) {
                Spacer(Modifier.width(14.dp))
                Text(label, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
        if (selected && !expanded) Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(22.dp).background(TvoColors.Accent))
    }
}

// ---------------------------------------------------------------- Browse

data class RowSource(val key: String, val title: String, val addon: Addon, val cat: CatalogDef, val extra: Map<String, String> = emptyMap())

private val homeGenres = listOf("Action", "Comedy", "Animation", "Sci-Fi", "Horror", "Thriller", "Drama", "Documentary")

fun buildRows(addons: List<Addon>, type: String?): List<RowSource> {
    val rows = mutableListOf<RowSource>()
    addons.forEach { a ->
        a.manifest.catalogs.filter { !it.requiresExtra && (type == null || it.type == type) }.forEach { c ->
            val title = if (a.manifest.id == "com.linvo.cinemeta") catalogTitle(c.type, c.name) else "${c.name} · ${a.manifest.name}"
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
    val addons by store.addons.collectAsState()
    val progress by store.progress.collectAsState()
    val library by store.library.collectAsState()
    var focused by remember(type) { mutableStateOf<MetaPreview?>(null) }
    var hero by remember(type) { mutableStateOf<MetaPreview?>(null) }
    var focusGiven by remember(type) { mutableStateOf(false) }
    val rows = remember(addons, type) { buildRows(addons, type) }
    val cont = remember(progress, type) { store.continueWatching(progress).filter { type == null || it.type == type } }
    val lib = remember(library, type) { library.filter { type == null || it.type == type } }

    LaunchedEffect(focused) { delay(280); if (focused != null) hero = focused }

    Box(Modifier.fillMaxSize()) {
        Backdrop(hero?.background ?: hero?.poster)
        Column(Modifier.fillMaxSize()) {
            HeroInfo(hero, Modifier.fillMaxWidth().height(250.dp))
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (cont.isNotEmpty()) item(key = "continue") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Continuer à regarder")
                        val fr = remember { FocusRequester() }
                        LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(cont, key = { _, p -> p.metaId }) { i, p ->
                                val preview = MetaPreview(p.metaId, p.type, p.name, p.poster, p.background)
                                WideCard(
                                    image = p.background ?: p.poster,
                                    title = p.name,
                                    subtitle = if (p.season != null) "S${p.season} : É${p.episode} · ${p.episodeTitle ?: ""}" else "Reprendre",
                                    progress = p.fraction,
                                    onClick = { nav.openStreams(p.type, p.metaId, p.videoId) },
                                    onLongClick = { store.removeFromContinue(p.metaId) },
                                    onFocus = { focused = preview },
                                    modifier = if (i == 0) Modifier.focusRequester(fr) else Modifier,
                                )
                            }
                        }
                        if (!focusGiven) LaunchedEffect(Unit) { focusGiven = true; delay(100); runCatching { fr.requestFocus() } }
                    }
                }
                if (lib.isNotEmpty()) item(key = "library") {
                    PosterRow("Ma liste", lib.map { it.toPreview() }, nav.openMeta, { focused = it })
                }
                if (type == null) item(key = "genres") { GenreTiles(nav) { } }
                items(rows, key = { it.key }) { row ->
                    CatalogRow(row, nav, onFocus = { focused = it }, requestFocus = !focusGiven, onFocusTaken = { focusGiven = true })
                }
                if (rows.isEmpty()) item { CenterMessage("Aucun catalogue", "Ajoute un add-on de catalogue dans Réglages.") }
            }
        }
    }
}

@Composable
private fun CatalogRow(row: RowSource, nav: Nav, onFocus: (MetaPreview) -> Unit, requestFocus: Boolean, onFocusTaken: () -> Unit) {
    val state by produceState<List<MetaPreview>?>(null, row.key) {
        value = runCatching { Tvo.client.catalog(row.addon, row.cat, row.extra) }.getOrDefault(emptyList())
    }
    when {
        state == null -> LoadingRow(row.title)
        state!!.isEmpty() -> Spacer(Modifier.height(0.dp))
        else -> {
            val take = requestFocus
            if (take) LaunchedEffect(Unit) { onFocusTaken() }
            PosterRow(row.title, state!!, nav.openMeta, onFocus, requestFocus = take)
        }
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
