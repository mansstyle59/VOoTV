package app.tvo.tv.ui

import androidx.compose.foundation.border
import androidx.compose.ui.graphics.compositeOver
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.draw.alpha
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

/** Barre du haut masquée quand on parcourt les rangées (elle revient en remontant). */
val LocalNavHidden = androidx.compose.runtime.staticCompositionLocalOf { androidx.compose.runtime.mutableStateOf(false) }

/** Défilement vertical : la rangée choisie se place toujours en haut de la liste (titre compris), jamais coupée. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
val RowTopSpec = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - 50f * android.content.res.Resources.getSystem().displayMetrics.density
}
/** Défilement horizontal normal (le minimum pour voir la carte) à l'intérieur des rangées. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
val RowSideSpec = object : androidx.compose.foundation.gestures.BringIntoViewSpec {
    // Garde toujours une marge de 48 dp à gauche et à droite : la carte choisie (agrandie) ne dépasse jamais de l'écran
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val pad = 56f * android.content.res.Resources.getSystem().displayMetrics.density
        val end = offset + size
        return when {
            offset < pad -> offset - pad
            end > containerSize - pad -> end - (containerSize - pad)
            else -> 0f
        }
    }
}


/** Mémoire de focus d'une rangée : en descendant/remontant, on arrive sur la dernière carte choisie de la rangée
 *  (ou la première), jamais sur une carte décalée vers la droite. */
class RowFocus {
    var last by androidx.compose.runtime.mutableIntStateOf(0)
    val reqs = mutableMapOf<Int, FocusRequester>()
}
@Composable fun rememberRowFocus() = remember { RowFocus() }
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.rowEnter(rf: RowFocus) = this.focusProperties { enter = { rf.reqs[rf.last] ?: rf.reqs.entries.minByOrNull { it.key }?.value ?: FocusRequester.Default } }
@Composable
fun RowItem(rf: RowFocus, i: Int, content: @Composable () -> Unit) {
    val r = remember(i) { FocusRequester() }
    androidx.compose.runtime.DisposableEffect(i) { rf.reqs[i] = r; onDispose { if (rf.reqs[i] === r) rf.reqs.remove(i) } }
    Box(Modifier.focusRequester(r).onFocusChanged { if (it.hasFocus) rf.last = i }) { content() }
}

/** Hauteur de la barre de navigation du haut (façon Netflix). */
val NavBarHeight = 76.dp

@Composable
fun HomeScreen(nav: Nav) {
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    val profile by Tvo.store.currentProfile.collectAsState()
    BackHandler(enabled = section != Section.HOME) { section = Section.HOME }
    val immersive = section == Section.HOME || section == Section.MOVIES || section == Section.SERIES || section == Section.LIVE

    val navHidden = remember { mutableStateOf(false) }
    LaunchedEffect(section) { navHidden.value = false }
    Box(Modifier.fillMaxSize().background(TvoColors.Bg)) {
      androidx.compose.runtime.CompositionLocalProvider(LocalNavHidden provides navHidden) {
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
        TopNav(section, profile, onSelect = { section = it }, onProfile = nav.switchProfile, hidden = navHidden.value && immersive)
      }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TopNav(current: Section, profile: Profile?, onSelect: (Section) -> Unit, onProfile: () -> Unit, hidden: Boolean = false) {
    val currentFr = remember { FocusRequester() }
    Row(
        Modifier.fillMaxWidth().height(NavBarHeight).alpha(if (hidden) 0f else 1f)
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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

    // Les cartes « Continuer à regarder » n'ont pas le résumé : on va chercher la fiche complète (genre, année, note, résumé)
    val full by produceState<MetaPreview?>(null, shown?.id) {
        val s0 = shown
        value = if (s0 != null && s0.description.isNullOrBlank() && s0.type !in listOf("tv", "channel", "genre", "platform"))
            runCatching { Tvo.client.metaCache["${s0.type}/${s0.id}"]?.preview ?: Tvo.client.meta(Tvo.store.allowed(Tvo.store.addons.value), s0.type, s0.id)?.preview }.getOrNull()
        else null
    }
    val m = shown?.let { sh -> full?.takeIf { it.id == sh.id }?.let { f -> sh.copy(description = f.description, genres = sh.genres.ifEmpty { f.genres },
        releaseInfo = sh.releaseInfo ?: f.releaseInfo, imdbRating = sh.imdbRating ?: f.imdbRating, logo = sh.logo ?: f.logo, background = sh.background ?: f.background) } ?: sh }
    val navHidden = LocalNavHidden.current
    LaunchedEffect(inRows) { navHidden.value = inRows }
    val logo by produceState<String?>(m?.logo, m?.id) {
        val mm = m
        value = mm?.logo ?: if (mm != null && mm.type !in listOf("tv", "channel", "genre", "platform") && app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.logo(mm.type, mm.id) else null
    }
    Box(Modifier.fillMaxSize()) {
        if (m != null) {
            val tv = m.type == "tv" || m.type == "channel"
            // Grande image à droite, fondue vers la gauche (façon Nuvio / Netflix)
            AsyncImage(m.background ?: m.poster, null,
                Modifier.fillMaxWidth(0.64f).fillMaxHeight(0.66f).align(Alignment.TopEnd).let { if (tv) it.padding(60.dp) else it },
                contentScale = if (tv) ContentScale.Fit else ContentScale.Crop)
            Box(Modifier.fillMaxWidth(0.64f).fillMaxHeight(0.66f).align(Alignment.TopEnd)
                .background(Brush.horizontalGradient(0f to TvoColors.Bg, 0.35f to Color(0x990A0F1E), 1f to Color.Transparent)))
            Box(Modifier.fillMaxWidth(0.64f).fillMaxHeight(0.66f).align(Alignment.TopEnd)
                .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to TvoColors.Bg)))
        }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 48.dp, top = if (inRows) 22.dp else NavBarHeight + 4.dp).width(470.dp).height(if (inRows) 250.dp else 240.dp)) {
                if (m != null) {
                    if (logo != null) AsyncImage(logo, m.name, Modifier.height(if (inRows) 88.dp else 70.dp).fillMaxWidth(0.75f), contentScale = ContentScale.Fit, alignment = Alignment.BottomStart)
                    else Text(m.name, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 36.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(if (m.type == "genre") "Catégorie  •  Films et séries" else if (m.type == "platform") "Plateforme  •  Films et séries"
                        else listOfNotNull(typeLabel(m.type, plural = false), m.genres.firstOrNull()?.let { genreLabel(it) }, m.releaseInfo).joinToString("  •  "),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text.copy(alpha = 0.9f), maxLines = 1)
                    if (m.imdbRating != null) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("TMDB", Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF01B4E4)).padding(horizontal = 5.dp, vertical = 1.dp), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0D253F))
                        Text("  ★ ${m.imdbRating}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFACC15))
                    }
                    if (!m.description.isNullOrBlank()) Text(m.description, Modifier.padding(top = 8.dp), fontSize = 13.sp,
                        color = TvoColors.Text.copy(alpha = 0.85f), maxLines = if (inRows) 4 else 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                    val inList = library.any { it.id == m.id }
                    if (!inRows) Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BlueButton("▶  Regarder", { nav.openMeta(m) }, filled = true, modifier = Modifier.focusRequester(watchFr))
                        BlueButton(if (inList) "✓  Dans ma liste" else "+  Ma liste", {
                            Tvo.store.toggleLibrary(app.tvo.tv.data.LibraryItem(m.id, m.type, m.name, m.poster, m.background))
                        }, filled = false)
                    }
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalSelect provides { selected = it }, androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides RowTopSpec) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val upScope = rememberCoroutineScope()
                    val fm = androidx.compose.ui.platform.LocalFocusManager.current
                    LazyColumn(
                        // Haut : remonte d'une seule rangée à la fois ; l'en-tête n'est atteint qu'en étant sur la première rangée
                        Modifier.fillMaxSize().onFocusChanged { inRows = it.hasFocus }.onPreviewKeyEvent { e ->
                            if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.DirectionUp && listState.firstVisibleItemIndex > 0) {
                                upScope.launch { listState.scrollToItem(listState.firstVisibleItemIndex - 1); delay(30); fm.moveFocus(androidx.compose.ui.focus.FocusDirection.Up) }
                                true
                            } else false
                        },
                        state = listState,
                        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = rows,
                    )
                    // Fondu doux en bas : la rangée suivante apparaît en transparence au lieu d'être coupée net
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(70.dp)
                        .background(Brush.verticalGradient(0f to Color.Transparent, 1f to TvoColors.Bg.copy(alpha = 0.92f))))
                }
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
    LaunchedEffect(Unit) { app.tvo.tv.data.Sync.syncInBackground() } // récupère la position exacte venant des autres appareils
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
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
                val rf = rememberRowFocus()
                LazyRow(Modifier.rowEnter(rf), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(cont, key = { _, it -> it.metaId }) { idx, p -> RowItem(rf, idx) {
                        WideCard(
                            image = p.background ?: p.poster,
                            title = p.name,
                            subtitle = (if (p.season != null) "S${p.season} : É${p.episode} · " else "") + "Reprendre à ${fmtTime(p.position)} · reste ${((p.duration - p.position) / 60000).coerceAtLeast(1)} min",
                            progress = p.fraction,
                            onClick = { resumeFast(ctx, scope, nav, p.type, p.metaId, p.videoId) },
                            onLongClick = { store.removeFromContinue(p.metaId) },
                            width = 240.dp,
                            showPlay = true,
                            onFocus = { select(MetaPreview(p.metaId, p.type, p.name, p.poster, p.background)) },
                        )
                    } }
                }
            }
        }
        if (lib.isNotEmpty() && "library" !in hidden) item(key = "library") { PortraitPanel("Ma liste", lib.map { it.toPreview() }, nav) }
        if (type == null && "genres" !in hidden) item(key = "genres") { GenreTiles(nav) { } }
        if (type == null) item(key = "platforms") { PlatformTiles(nav) }
        if (type == null || type == "movie") item(key = "sagas") { SagaTiles(nav) }
        if (type == null) item(key = "actors") { ActorRow(nav) }
        items(rows, key = { it.key }) { row -> CatalogRow(row, nav) }
        if (rows.isEmpty()) item { CenterMessage("Aucun catalogue", "Ajoute un add-on de catalogue dans Réglages.") }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PanelSection(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(36.dp).padding(start = 48.dp, end = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text, letterSpacing = (-0.2).sp)
            Spacer(Modifier.weight(1f))
            action?.invoke()
        }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides RowSideSpec) { content() }
    }
}

/** Rangée de grandes cartes paysage qui s'agrandissent au focus. */
@Composable
fun PortraitPanel(title: String, items: List<MetaPreview>, nav: Nav) {
    val select = LocalSelect.current
    PanelSection(title) {
        val rf = rememberRowFocus()
        LazyRow(Modifier.rowEnter(rf), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(items, key = { _, it -> it.id }) { i, m -> RowItem(rf, i) { ExpandCard(m, { nav.openMeta(m) }, onFocus = { select(m) }) } }
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
internal val GENRE_IMG = mapOf(
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
/** Couleur de chaque catégorie (teinte du voile et du liseré). */
internal val GENRE_COLOR = mapOf(
    "Action" to 0xFFE5484D, "Adventure" to 0xFFF59E0B, "Comedy" to 0xFFFACC15, "Drama" to 0xFF8B5CF6, "Thriller" to 0xFF64748B,
    "Crime" to 0xFFB91C1C, "Sci-Fi" to 0xFF06B6D4, "Fantasy" to 0xFFA855F7, "Horror" to 0xFF7F1D1D, "Romance" to 0xFFEC4899,
    "Animation" to 0xFF22C55E, "Family" to 0xFF38BDF8, "Mystery" to 0xFF6366F1, "History" to 0xFFB45309, "War" to 0xFF65A30D,
    "Western" to 0xFFD97706, "Documentary" to 0xFF14B8A6,
)
internal const val TMDB_IMG = "https://image.tmdb.org/t/p/"
/** Image de fond de chaque plateforme (série populaire chez elle). */
internal val PROV_BG = mapOf(
    "8" to "/9P4IIMYY3HifqeruZq0ZZ9g7YUi.jpg",
    "337" to "/9zcbqSxdsRMZWHYtyCd1nXPr2xq.jpg",
    "119" to "/bq28ajZaoMyzEIm6REelqyqtEDZ.jpg",
    "381" to "/3ZIWMs9vRjuss8RJWn2ijGRuwtE.jpg",
    "350" to "/ixgFmf1X59PUZam2qbAfskx2gQr.jpg",
    "531" to "/zW0v2YT74C6tRafzqqBkfSqLAN0.jpg",
    "1899" to "/577eXC8wFQT0eUrJcgznSiFPRmk.jpg",
)

private val GENRE_TXT = mapOf(
    "Action" to "Poursuites, combats et héros : les films qui ne laissent pas souffler.",
    "Comedy" to "De quoi rire seul ou en famille : comédies françaises et internationales.",
    "Animation" to "Pixar, Disney, Ghibli et toutes les pépites animées, pour petits et grands.",
    "Sci-Fi" to "Voyages dans l'espace, futurs possibles et mondes imaginaires.",
    "Horror" to "Frissons garantis : épouvante, suspense et nuits blanches.",
    "Thriller" to "Tension, enquêtes et retournements jusqu'à la dernière minute.",
    "Drama" to "Des histoires fortes et émouvantes, portées par de grands acteurs.",
    "Documentary" to "Le monde réel raconté : nature, histoire, société, sport.",
)

private fun GenreTilesSelect(g: String) = MetaPreview("genre:$g", "genre", genreLabel(g), null,
    GENRE_IMG[g]?.let { TMDB_IMG + "w1280" + it }, null, GENRE_TXT[g] ?: "Les meilleurs titres de la catégorie ${genreLabel(g)}.")

@Composable
private fun GenreTiles(nav: Nav, onFocus: () -> Unit) {
    val select = LocalSelect.current
    PanelSection("Catégories") {
        val rf = rememberRowFocus()
        LazyRow(Modifier.rowEnter(rf), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(homeGenres) { gi, g -> RowItem(rf, gi) {
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    onClick = { nav.openGenre("movie", g) },
                    modifier = Modifier.width(240.dp).height(135.dp).onFocused { onFocus(); select(GenreTilesSelect(g)) },
                    shape = ClickableSurfaceDefaults.shape(shape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = TvoColors.Surface, focusedContainerColor = TvoColors.Surface),
                ) {
                    val gc = Color(GENRE_COLOR[g] ?: 0xFF3D7CFF)
                    Box(Modifier.fillMaxSize().background(gc.copy(alpha = 0.35f))) {
                        GENRE_IMG[g]?.let { AsyncImage(TMDB_IMG + "w500" + it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.85f) }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 1f to Color(0xF2000000))))
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(0f to gc.copy(alpha = 0.55f), 0.6f to Color.Transparent)))
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 12.dp, end = 10.dp)) {
                            Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(gc))
                            Spacer(Modifier.height(6.dp))
                            Text(genreLabel(g), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } }
        }
    }
}

@Composable
private fun ActorRow(nav: Nav) {
    val people by produceState<List<app.tvo.tv.data.Person>>(emptyList()) { value = if (app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.popularPeople() else emptyList() }
    if (people.isEmpty()) return
    PanelSection("Acteurs et actrices du moment") {
        val rf = rememberRowFocus()
        LazyRow(Modifier.rowEnter(rf), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(people, key = { _, pp -> pp.id }) { i, pp -> RowItem(rf, i) { PersonBubble(pp) { nav.openPerson(pp.id) } } }
        }
    }
}

@Composable
private fun SagaTiles(nav: Nav) {
    val sagas by produceState<List<app.tvo.tv.data.Tmdb.Saga>>(emptyList()) { value = if (app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.sagas() else emptyList() }
    if (sagas.isEmpty()) return
    PanelSection("Sagas et collections") {
        val rf = rememberRowFocus()
        LazyRow(Modifier.rowEnter(rf), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(sagas, key = { _, sg -> sg.id }) { i, sg -> RowItem(rf, i) { SagaTile(sg) { nav.openGenre("movie", "col:${sg.id}") } } }
        }
    }
}

@Composable
private fun PlatformTiles(nav: Nav) {
    val select = LocalSelect.current
    val fresh by produceState<Map<String, String>>(emptyMap()) { value = if (app.tvo.tv.data.Tmdb.enabled) app.tvo.tv.data.Tmdb.providerBackdrops(PLATFORMS.map { it.first.first }) else emptyMap() }
    PanelSection("Plateformes") {
        val rf = rememberRowFocus()
        LazyRow(Modifier.rowEnter(rf), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(PLATFORMS) { pi, (pl, logo) -> RowItem(rf, pi) {
                val (id, name, color) = pl
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    onClick = { nav.openGenre("all", "prov:$id") },
                    modifier = Modifier.width(250.dp).height(124.dp).onFocused {
                        select(MetaPreview("platform:$id", "platform", name, null, (fresh[id] ?: PROV_BG[id])?.let { TMDB_IMG + "w1280" + it }, TMDB_IMG + "w300" + logo,
                            "Les nouveautés films et séries disponibles sur $name, en France."))
                    },
                    shape = ClickableSurfaceDefaults.shape(shape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(color), focusedContainerColor = Color(color)),
                ) {
                    // Carte de marque : dégradé de la couleur de la plateforme, grand logo officiel, nom
                    val bc = Color(color)
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(0f to bc, 1f to bc.copy(alpha = 0.55f).compositeOver(Color(0xFF0A0F1E))))) {
                        (fresh[id] ?: PROV_BG[id])?.let { AsyncImage(TMDB_IMG + "w500" + it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.45f) }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0x99000000))))
                        Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(TMDB_IMG + "w154" + logo, name, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).border(2.dp, Color(0x55FFFFFF), RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text("Films et séries", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xCCFFFFFF))
                            }
                        }
                    }
                }
            } }
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
        Column(Modifier.fillMaxSize().padding(top = NavBarHeight + 24.dp)) {
            Text("Live TV", Modifier.padding(start = 48.dp), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Text("Choisis des chaînes à ajouter : elles apparaîtront ici avec le programme en cours.", Modifier.padding(start = 48.dp, top = 6.dp, bottom = 8.dp), fontSize = 15.sp, color = TvoColors.Muted)
            LiveSuggestions()
        }
        return
    }
    val hero by produceState<List<MetaPreview>>(emptyList(), rows.firstOrNull()?.key) {
        val r = rows.firstOrNull()
        value = r?.let { runCatching { Tvo.client.catalog(it.addon, it.cat, it.extra) }.getOrNull() }?.take(1) ?: emptyList()
    }
    Immersive(hero, nav, key = "live") {
        items(rows, key = { it.key }) { row -> CatalogRow(row, nav) }
        item(key = "sugg") { PanelSection("Ajouter des chaînes") { LiveSuggestions() } }
    }
}


/** Listes de chaînes gratuites proposées (comme sur mobile), avec un visuel par liste. */
private val LIVE_SUGG = listOf(
    listOf("https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_france.m3u8", "TNT gratuite", "France TV, Arte, LCP, franceinfo…", "🇫🇷", "FF2F5BFF", "FFE11D48"),
    listOf("https://iptv-org.github.io/iptv/countries/fr.m3u", "France · chaînes libres", "Nationales, régionales et thématiques", "📺", "FF1D4ED8", "FF0EA5E9"),
    listOf("https://iptv-org.github.io/iptv/categories/news.m3u", "Info du monde", "Information en continu", "🌍", "FF0F766E", "FF22C55E"),
    listOf("https://iptv-org.github.io/iptv/categories/kids.m3u", "Enfants", "Dessins animés et jeunesse", "🧸", "FFF59E0B", "FFEC4899"),
    listOf("https://iptv-org.github.io/iptv/categories/music.m3u", "Musique", "Clips et concerts", "🎵", "FF7C3AED", "FFDB2777"),
    listOf("https://iptv-org.github.io/iptv/countries/be.m3u", "Belgique", "RTBF et chaînes belges", "🇧🇪", "FF111827", "FFEAB308"),
    listOf("https://iptv-org.github.io/iptv/countries/ch.m3u", "Suisse", "RTS et chaînes suisses", "🇨🇭", "FFDC2626", "FF7F1D1D"),
    listOf("http://mafreebox.freebox.fr/freeboxtv/playlist.m3u", "Freebox TV", "Abonnés Free, depuis le Wi-Fi de la Freebox", "📡", "FF991B1B", "FF111827"),
)

@Composable
private fun LiveSuggestions() {
    val addons by Tvo.store.addons.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(LIVE_SUGG, key = { it[0] }) { sg ->
                val (url, name, desc, ic, c1) = sg; val c2 = sg[5]
                val added = addons.any { it.url.endsWith(url) }
                val shape = RoundedCornerShape(14.dp)
                Surface(
                    onClick = {
                        if (added || busy != null) return@Surface
                        busy = url; msg = "Ajout de « $name »…"
                        scope.launch {
                            msg = runCatching { Tvo.client.install("m3u:$url") }.fold({ Tvo.store.addAddon(it); "« $name » ajoutée ✓" }, { "Impossible d'ajouter « $name » : ${it.message}" })
                            busy = null
                        }
                    },
                    modifier = Modifier.width(250.dp).height(140.dp),
                    shape = ClickableSurfaceDefaults.shape(shape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                ) {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(c1.toLong(16)), Color(c2.toLong(16))))).padding(14.dp)) {
                        Text(ic, fontSize = 28.sp)
                        Column(Modifier.align(Alignment.BottomStart)) {
                            Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(desc, fontSize = 12.sp, color = Color(0xDDFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (added) "✓ Ajoutée" else if (busy == url) "…" else "+ Ajouter", Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(8.dp))
                            .background(if (added) Color(0x5522C55E) else Color(0x33FFFFFF)).padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
        msg?.let { Text(it, Modifier.padding(start = 48.dp), fontSize = 14.sp, color = TvoColors.Cyan) }
    }
}


/** 4213000 ms → « 1:10:13 », 133000 → « 2:13 ». */
fun fmtTime(ms: Long): String { val t = ms / 1000; val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec) }
