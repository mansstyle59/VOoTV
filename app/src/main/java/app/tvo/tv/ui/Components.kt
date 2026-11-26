package app.tvo.tv.ui

import androidx.compose.ui.draw.paint
import androidx.compose.runtime.remember

import androidx.compose.runtime.getValue

import androidx.compose.runtime.setValue

import androidx.compose.runtime.produceState

import androidx.compose.runtime.mutableStateOf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.collectAsState
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.tvo.tv.Tvo
import app.tvo.tv.data.MetaPreview
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

val RowPadding = PaddingValues(horizontal = 32.dp, vertical = 10.dp)

fun Modifier.onFocused(block: () -> Unit) = this.onFocusChanged { if (it.isFocused) block() }

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(start = 32.dp, bottom = 2.dp), fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
}

@Composable
fun PosterCard(
    item: MetaPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 128.dp,
    onFocus: () -> Unit = {},
) {
    val shape = RoundedCornerShape(12.dp)
    var menu by remember { mutableStateOf(false) }
    val prog by app.tvo.tv.Tvo.store.progress.collectAsState()
    val seen = remember(prog, item.id) { item.type == "movie" && prog.any { it.metaId == item.id && it.finished } }
    QuickMenuHost(item, onClick, menu) { menu = false }
    Card(
        onClick = onClick,
        onLongClick = { menu = true },
        modifier = modifier.width(width).aspectRatio(2f / 3f).onFocused(onFocus),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = CardDefaults.colors(containerColor = TvoColors.Surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (item.poster == null) {
                Text(item.name, Modifier.align(Alignment.Center).padding(10.dp), textAlign = TextAlign.Center,
                    fontSize = 14.sp, color = TvoColors.Text)
            }
            FbImage(item.poster, item.id, true, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (seen) Text("✓ VU", Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(50))
                .background(TvoColors.Success).padding(horizontal = 7.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

@Composable
fun WideCard(
    image: String?,
    title: String,
    subtitle: String?,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 250.dp,
    onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    badge: String? = null,
    showPlay: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(width).aspectRatio(16f / 9f).onFocused(onFocus),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = CardDefaults.colors(containerColor = TvoColors.Surface2),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(image, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xEE000000))))
            if (showPlay) Box(Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color(0x99FFFFFF)),
                contentAlignment = Alignment.Center) { Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, null, Modifier.size(24.dp), tint = Color.Black) }
            if (badge != null) {
                Text(badge, Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(6.dp))
                    .background(TvoColors.Success).padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 12.sp, color = TvoColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (progress != null && progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x55FFFFFF))) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(TvoColors.Accent))
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PosterRow(
    title: String,
    items: List<MetaPreview>,
    onOpen: (MetaPreview) -> Unit,
    onFocusItem: (MetaPreview) -> Unit,
    requestFocus: Boolean = false,
) {
    val fr = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        LazyRow(Modifier.focusRestorer { fr }, contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                PosterCard(item, { onOpen(item) },
                    modifier = if (i == 0) Modifier.focusRequester(fr) else Modifier,
                    onFocus = { onFocusItem(item) })
            }
        }
    }
    if (requestFocus) LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}

@Composable
fun LoadingRow(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        Row(Modifier.padding(RowPadding), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(7) { Box(Modifier.width(128.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(TvoColors.Surface)) }
        }
    }
}

/** Fond coloré commun aux pages (plus de navy plat) : teinte d'accent en haut à gauche, lueur opposée en bas à droite. */
fun tvoBrush(accent: Color, accent2: Color = TvoColors.Blue): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(accent.copy(alpha = 0.5f), Color(0xFF141414), accent2.copy(alpha = 0.32f)),
        start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(1600f, 1000f))

@Composable
fun TvoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    wide: Boolean = false,
) {
    // Boutons façon Netflix : rectangle aux coins légèrement arrondis, plein (blanc) ou gris translucide, sans effet verre
    val shape = RoundedCornerShape(50)
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp).widthIn(min = 150.dp),
        shape = ButtonDefaults.shape(shape),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) TvoColors.Blue else Color(0xFF6D7691).copy(alpha = 0.45f),
            contentColor = Color.White,
            focusedContainerColor = if (primary) Color.White else Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color(0xFFD9D9D9),
            pressedContentColor = Color.Black,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        border = ButtonDefaults.border(
            border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent), shape = shape),
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent), shape = shape),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        // Contenu toujours centré (même quand le bouton est plus large que son texte)
        // « wide » seulement pour un bouton qui occupe toute la largeur : sinon le bouton s'étirerait (et écraserait ses voisins)
        Row(if (wide) Modifier.fillMaxWidth() else Modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
fun Avatar(name: String, color: Long, size: Dp, modifier: Modifier = Modifier, icon: String? = null) {
    Box(modifier.size(size).clip(RoundedCornerShape(size / 4))
        .background(Brush.linearGradient(listOf(Color(color), Color(color).copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center) {
        Text(icon?.takeIf { it.isNotBlank() } ?: name.take(1).uppercase(), fontSize = (size.value * (if (icon.isNullOrBlank()) 0.45f else 0.5f)).sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvoColors.Blue else TvoColors.Surface2,
            contentColor = TvoColors.Text,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(text, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
fun CenterMessage(title: String, body: String? = null) {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text, textAlign = TextAlign.Center)
        if (body != null) Text(body, Modifier.padding(top = 10.dp).width(560.dp), fontSize = 15.sp,
            color = TvoColors.Muted, textAlign = TextAlign.Center)
    }
}

/** On-screen keyboard: remotes and IMEs on TV boxes are unreliable, this always works with the D-pad. */
@Composable
fun TvKeyboard(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    url: Boolean = false,
    firstKey: FocusRequester? = null,
    fill: Boolean = false,
) {
    val keys = if (url) "abcdefghijklmnopqrstuvwxyz0123456789.:/-_?=&%~".map { it.toString() } + listOf(".com", "https://", "/manifest.json")
    else "abcdefghijklmnopqrstuvwxyz0123456789".map { it.toString() } + listOf("'", "-")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val perRow = if (url) 8 else 9
        val singles = keys.filter { it.length == 1 }.chunked(perRow)
        singles.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { c, k ->
                    // fill = les touches se partagent la largeur disponible (aucune ne peut être écrasée)
                    val base = if (fill) Modifier.weight(1f) else Modifier.width(44.dp)
                    Key(k, { onKey(k) }, base.let { if (r == 0 && c == 0 && firstKey != null) it.focusRequester(firstKey) else it })
                }
                if (fill) repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!url) Key("Espace", { onKey(" ") }, Modifier.width(110.dp))
            Key("⌫", onBackspace, Modifier.width(70.dp))
            Key("Effacer", onClear, Modifier.width(100.dp))
        }
        keys.filter { it.length > 1 }.takeIf { it.isNotEmpty() }?.let { extra ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { extra.forEach { Key(it, { onKey(it) }) } }
        }
    }
}

@Composable
fun DigitPad(onDigit: (String) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier, first: FocusRequester? = null) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..9).map { it.toString() }.plus("0").forEachIndexed { i, d ->
            Key(d, { onDigit(d) }, Modifier.width(44.dp).let { if (i == 0 && first != null) it.focusRequester(first) else it })
        }
        Key("⌫", onBackspace, Modifier.width(60.dp))
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvoColors.Surface2, contentColor = TvoColors.Text,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}


/** Landscape card with title + meta at the bottom (catalog rows). */
@Composable
fun LandscapeCard(
    item: MetaPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 232.dp,
    onFocus: () -> Unit = {},
) {
    val shape = RoundedCornerShape(12.dp)
    var menu by remember { mutableStateOf(false) }
    QuickMenuHost(item, onClick, menu) { menu = false }
    Card(
        onClick = onClick,
        onLongClick = { menu = true },
        modifier = modifier.width(width).aspectRatio(16f / 9f).onFocused(onFocus),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = CardDefaults.colors(containerColor = TvoColors.Surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (item.type == "tv" || item.type == "channel") AsyncImage(item.poster, item.name, Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 14.dp, bottom = 40.dp), contentScale = ContentScale.Fit)
            else FbImage(item.background ?: item.poster, item.id, false, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xF0000000))))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(item.releaseInfo, typeLabel(item.type, plural = false), item.imdbRating?.let { "★ $it" }).joinToString("  ·  ")
                Text(meta, fontSize = 11.sp, color = Color(0xFFCFD4D8), maxLines = 1)
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun LandscapeRow(
    title: String,
    items: List<MetaPreview>,
    onOpen: (MetaPreview) -> Unit,
    onFocusItem: (MetaPreview) -> Unit = {},
    requestFocus: Boolean = false,
) {
    val fr = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        LazyRow(Modifier.focusRestorer { fr }, contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                LandscapeCard(item, { onOpen(item) }, if (i == 0) Modifier.focusRequester(fr) else Modifier, onFocus = { onFocusItem(item) })
            }
        }
    }
    if (requestFocus) LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}


/** Portrait card (poster, play icon, title) with note and year underneath. */
@Composable
fun PortraitCard(item: MetaPreview, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 150.dp, onFocus: () -> Unit = {}) {
    val shape = RoundedCornerShape(16.dp)
    var menu by remember { mutableStateOf(false) }
    QuickMenuHost(item, onClick, menu) { menu = false }
    Column(modifier.width(width)) {
        Card(
            onClick = onClick,
            onLongClick = { menu = true },
            modifier = Modifier.fillMaxWidth().aspectRatio(0.667f).onFocused(onFocus),
            shape = CardDefaults.shape(shape),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvoColors.Blue), shape = shape)),
            colors = CardDefaults.colors(containerColor = TvoColors.Surface),
        ) {
            Box(Modifier.fillMaxSize()) {
                val tv = item.type == "tv" || item.type == "channel"
                FbImage(item.poster ?: item.background, item.id, true, item.name,
                    if (tv) Modifier.fillMaxSize().padding(22.dp) else Modifier.fillMaxSize(),
                    contentScale = if (tv) ContentScale.Fit else ContentScale.Crop)
                // Les affiches portent déjà leur titre : on ne le répète que s'il n'y a pas d'affiche
                if (item.poster == null) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xE6000000))))
                    Text(item.name, Modifier.align(Alignment.BottomStart).padding(10.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.imdbRating?.let { "★ $it" } ?: "—", fontSize = 12.sp, color = TvoColors.Cyan, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(item.releaseInfo ?: typeLabel(item.type, plural = false), fontSize = 12.sp, color = TvoColors.Muted, maxLines = 1)
        }
    }
}


/**
 * Carte « affiche qui s'ouvre » : affiche en portrait, et quand on se place dessus elle s'élargit
 * en grande image paysage avec le logo du titre.
 */
@Composable
fun ExpandCard(item: MetaPreview, onClick: () -> Unit, onFocus: () -> Unit = {}, height: Dp = 180.dp) {
    var focused by remember { mutableStateOf(false) }
    val tv = item.type == "tv" || item.type == "channel"
    val w by androidx.compose.animation.core.animateDpAsState(
        if (focused && !tv && item.background != null) height * 16f / 9f else height * 2f / 3f,
        androidx.compose.animation.core.snap(), label = "w")
    val logo by produceState<String?>(item.logo, item.id, focused) {
        if (focused && value == null && !tv && app.tvo.tv.data.Tmdb.enabled) value = app.tvo.tv.data.Tmdb.logo(item.type, item.id)
    }
    val shape = RoundedCornerShape(12.dp)
    Card(
        onClick = onClick,
        modifier = Modifier.width(w).height(height).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() },
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.03f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = CardDefaults.colors(containerColor = TvoColors.Surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (focused && !tv && item.background != null) {
                AsyncImage(item.background, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xCC000000))))
                if (logo != null) AsyncImage(logo, item.name, Modifier.align(Alignment.BottomStart).padding(12.dp).height(46.dp).fillMaxWidth(0.55f),
                    contentScale = ContentScale.Fit, alignment = Alignment.BottomStart)
                else Text(item.name, Modifier.align(Alignment.BottomStart).padding(12.dp), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                FbImage(item.poster ?: item.background, item.id, true, item.name, if (tv) Modifier.fillMaxSize().padding(16.dp) else Modifier.fillMaxSize(),
                    contentScale = if (tv) ContentScale.Fit else ContentScale.Crop)
                if (item.poster == null || tv) Text(item.name, Modifier.align(Alignment.BottomStart).padding(8.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2)
            }
        }
    }
}


/** Carte d'une saga / collection : fond de la saga, voile et nom. */
fun sagaName(n: String) = n.replace(" - Saga", "").replace(" Collection", "").replace("— Saga", "").trim()

@Composable
fun SagaTile(sg: app.tvo.tv.data.Tmdb.Saga, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.width(240.dp).height(135.dp).onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF333333), focusedContainerColor = Color(0xFF333333)),
    ) {
        Box(Modifier.fillMaxSize()) {
            (sg.backdrop ?: sg.poster)?.let { AsyncImage(it.replace("/w1280/", "/w500/"), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color(0xF2000000))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 12.dp, end = 10.dp)) {
                Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFF59E0B)))
                Spacer(Modifier.height(6.dp))
                Text(sagaName(sg.name), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
            }
        }
    }
}


/** Image de fond commune des pages (un film tendance), gardée pour toute la session. */
object PageImg { var cached: String? = null }

@Composable
fun rememberPageImage(): String? {
    val v by androidx.compose.runtime.produceState(PageImg.cached) {
        if (PageImg.cached == null && app.tvo.tv.data.Tmdb.enabled)
            PageImg.cached = app.tvo.tv.data.Tmdb.trending("movie").mapNotNull { it.background }.randomOrNull()
        value = PageImg.cached
    }
    return v
}

/** Fond de page : photo de film voilée et teintée (la couleur seule sert de secours). */
@Composable
fun Modifier.imageBackdrop(accent: Color, url: String? = rememberPageImage()): Modifier {
    val painter = coil.compose.rememberAsyncImagePainter(url)
    return this.background(tvoBrush(accent))
        .paint(painter, contentScale = ContentScale.Crop, alpha = 0.5f)
        .background(Brush.horizontalGradient(0f to Color(0xE60A0F1E), 0.55f to Color(0xA60A0F1E), 1f to Color(0x4D0A0F1E)))
        .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xD90A0F1E)))
        .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 1200f))
}


/** Accès à la navigation pour le menu rapide (appui long sur une affiche). */
object QuickMenu { var nav: Nav? = null }

/** Petit menu d'appui long : regarder, ouvrir la fiche, ajouter / retirer de ma liste. */
/** Une ligne du menu façon Nuvio : libellé à gauche, icône à droite ; rouge pour « Supprimer ». */
class NvItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * Menu d'appui long façon Nuvio, adapté à la télé : fond sombre avec l'image du titre, aperçu de la carte à gauche,
 * titre, sous-titre et liste d'actions groupée à droite. Retour = fermer.
 */
@Composable
fun NvMenu(image: String?, wide: Boolean, title: String, sub: String?, items: List<NvItem>, onDismiss: () -> Unit, progress: Float? = null) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { first.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF00B0B10))) {
            if (image != null) AsyncImage(image, null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.22f }, contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x660B0B10), Color(0xF20B0B10)), radius = 1400f)))
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                if (image != null) Box(Modifier.width(if (wide) 360.dp else 210.dp).aspectRatio(if (wide) 16f / 9f else 2f / 3f)
                    .clip(RoundedCornerShape(22.dp)).background(TvoColors.Surface)) {
                    AsyncImage(image, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (progress != null && progress > 0f) Box(Modifier.align(Alignment.BottomStart).padding(14.dp).fillMaxWidth(progress.coerceIn(0.03f, 1f)).height(5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(TvoColors.Blue))
                }
                Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (!sub.isNullOrBlank()) Text(sub, fontSize = 16.sp, color = TvoColors.Muted, maxLines = 1)
                    }
                    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF1C1C22)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items.forEachIndexed { i, it ->
                            androidx.tv.material3.Surface(
                                onClick = { onDismiss(); it.onClick() },
                                modifier = Modifier.fillMaxWidth().height(52.dp).let { m -> if (i == 0) m.focusRequester(first) else m },
                                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent, contentColor = if (it.danger) Color(0xFFF472B6) else Color.White,
                                    focusedContainerColor = if (it.danger) Color(0xFFE11D48) else Color.White, focusedContentColor = if (it.danger) Color.White else Color.Black),
                            ) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(it.label, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = androidx.tv.material3.LocalContentColor.current)
                                    androidx.tv.material3.Icon(it.icon, null, Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Appui long sur une carte : bibliothèque, vu, fiche (comme Nuvio). */
@Composable
fun QuickMenuHost(item: MetaPreview, onOpen: () -> Unit, show: Boolean, onDismiss: () -> Unit) {
    if (!show) return
    val lib by Tvo.store.library.collectAsState()
    val prog by Tvo.store.progress.collectAsState()
    val inList = lib.any { it.id == item.id }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ic = androidx.compose.material.icons.Icons.Filled
    val seen = item.type == "movie" && prog.any { it.metaId == item.id && it.finished }
    val items = buildList {
        add(NvItem("Voir les détails", ic.Info) { onOpen() })
        if (item.type == "movie" || item.type == "series") {
            add(NvItem(if (inList) "Retirer de la bibliothèque" else "Ajouter à la bibliothèque", if (inList) ic.Check else ic.Add) {
                Tvo.store.toggleLibrary(app.tvo.tv.data.LibraryItem(item.id, item.type, item.name, item.poster, item.background))
                android.widget.Toast.makeText(ctx, if (inList) "Retiré de ma liste" else "Ajouté à ma liste", android.widget.Toast.LENGTH_SHORT).show()
            })
        }
        if (item.type == "movie") add(NvItem(if (seen) "Marquer comme non vu" else "Marquer comme vu", ic.CheckCircle) {
            val pid = Tvo.store.currentProfile.value?.id
            if (pid != null) Tvo.store.saveProgress(pid, app.tvo.tv.data.Progress(item.id, item.id, item.type, item.name, item.poster, item.background,
                position = if (seen) 0 else 1, duration = 1, updatedAt = System.currentTimeMillis()))
            android.widget.Toast.makeText(ctx, if (seen) "Marqué non vu" else "Marqué comme vu", android.widget.Toast.LENGTH_SHORT).show()
        })
    }
    val wide = item.poster == null
    NvMenu(item.poster ?: item.background, wide, item.name,
        listOfNotNull(when (item.type) { "movie" -> "Film"; "series" -> "Série"; else -> null }, item.releaseInfo).joinToString(" · ").ifBlank { null }, items, onDismiss)
}


/** Appui long sur « Continuer à regarder » : détails, reprendre, recommencer, supprimer (comme Nuvio). */
@Composable
fun ContinueMenuHost(p: app.tvo.tv.data.Progress, show: Boolean, nav: Nav, ctx: android.content.Context,
                     scope: kotlinx.coroutines.CoroutineScope, onDismiss: () -> Unit) {
    if (!show) return
    val lib by Tvo.store.library.collectAsState()
    val inList = lib.any { it.id == p.metaId }
    val ic = androidx.compose.material.icons.Icons.Filled
    val left = if (p.duration > 1) ((p.duration - p.position) / 60000).coerceAtLeast(1) else 0
    val sub = listOfNotNull(if (p.season != null) "Saison ${p.season} · Épisode ${p.episode}" else if (p.type == "series") "Série" else "Film",
        if (left > 0) "$left min restantes" else null).joinToString(" · ")
    NvMenu(p.background ?: p.poster, p.background != null, p.name, sub, listOf(
        NvItem("Voir les détails", ic.Info) { nav.openMeta(MetaPreview(p.metaId, p.type, p.name, p.poster, p.background)) },
        NvItem("Reprendre la lecture", ic.PlayArrow) { resumeFast(ctx, scope, nav, p.type, p.metaId, p.videoId) },
        NvItem("Recommencer depuis le début", ic.Refresh) {
            val pid = Tvo.store.currentProfile.value?.id
            if (pid != null) Tvo.store.saveProgress(pid, p.copy(position = 0, updatedAt = System.currentTimeMillis()))
            nav.openStreams(p.type, p.metaId, p.videoId)
        },
        NvItem(if (inList) "Retirer de la bibliothèque" else "Ajouter à la bibliothèque", if (inList) ic.Check else ic.Add) {
            Tvo.store.toggleLibrary(app.tvo.tv.data.LibraryItem(p.metaId, p.type, p.name, p.poster, p.background))
        },
        NvItem("Supprimer", ic.Delete, danger = true) { Tvo.store.removeFromContinue(p.metaId) },
    ), onDismiss, progress = if (p.duration > 1) p.position.toFloat() / p.duration else null)
}


/** Les logos de titre TMDB sont parfois noirs : illisibles sur fond sombre. On mesure leur luminosité (une seule fois par logo)
 *  et on les passe en blanc quand ils sont sombres. */
private val logoToneCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
suspend fun logoIsDark(ctx: android.content.Context, url: String): Boolean {
    logoToneCache[url]?.let { return it }
    val dark = runCatching {
        val res = coil.Coil.imageLoader(ctx).execute(coil.request.ImageRequest.Builder(ctx).data(url).allowHardware(false).size(96).build())
        val bmp = (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@runCatching false
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        var sum = 0.0; var n = 0
        for (c in px) {
            val a = (c ushr 24) and 0xFF
            if (a > 128) { sum += (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)) / 255.0; n++ }
        }
        n > 20 && sum / n < 0.45
    }.getOrDefault(false)
    logoToneCache[url] = dark
    return dark
}
val WhiteLogoFilter: androidx.compose.ui.graphics.ColorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)

/** Image de titre avec secours : si l'adresse échoue (ou manque), on tente l'image Metahub par id IMDb. */
@Composable
fun FbImage(model: String?, id: String, poster: Boolean, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit, alignment: Alignment = Alignment.Center) {
    var url by remember(model) { mutableStateOf(model ?: metahubUrl(poster, id)) }
    AsyncImage(url, contentDescription, modifier, contentScale = contentScale, alignment = alignment, onError = {
        val fb = metahubUrl(poster, id)
        if (fb != null && url != fb) url = fb
    })
}

private fun metahubUrl(poster: Boolean, id: String): String? {
    val rid = id.substringBefore(":")
    return if (Regex("tt\\d+").matches(rid)) "https://images.metahub.space/${if (poster) "poster" else "background"}/medium/$rid/img" else null
}

/** Taille unique des cartes dans toutes les grilles (plateformes, catégories, sagas, Ma liste, acteurs). */
val GridCardWidth = 112.dp
val GridGap = 14.dp
val GridRowGap = 18.dp

/** Titre « logo » quand l'image du logo manque : gras, serré, avec une ombre douce pour rester lisible sur n'importe quelle image. */
@Composable
fun StyledTitle(name: String, size: Int) {
    Text(name, fontSize = size.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 2,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, lineHeight = (size * 1.05f).sp, letterSpacing = (-0.5).sp,
        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xCC000000), androidx.compose.ui.geometry.Offset(0f, 3f), 14f)))
}
