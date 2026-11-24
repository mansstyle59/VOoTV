package app.tvo.tv.ui

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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
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
    val shape = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
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
            AsyncImage(item.poster, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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

@Composable
fun PosterRow(
    title: String,
    items: List<MetaPreview>,
    onOpen: (MetaPreview) -> Unit,
    onFocusItem: (MetaPreview) -> Unit,
    requestFocus: Boolean = false,
) {
    val fr = FocusRequester()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
            repeat(7) { Box(Modifier.width(128.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(TvoColors.Surface)) }
        }
    }
}

/** Fond coloré commun aux pages (plus de navy plat) : teinte d'accent en haut à gauche, lueur opposée en bas à droite. */
fun tvoBrush(accent: Color, accent2: Color = Color(0xFF0EA5E9)): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(accent.copy(alpha = 0.5f), Color(0xFF0A0F1E), accent2.copy(alpha = 0.32f)),
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
    // Boutons 2.0 : pilule, verre dépoli au repos, blanc éclatant + léger zoom quand on est dessus
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = ButtonDefaults.shape(RoundedCornerShape(50)),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) Color.White else Color.White.copy(alpha = 0.12f),
            contentColor = if (primary) Color(0xFF0B0A12) else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF0B0A12),
            pressedContainerColor = Color(0xFFE6E6EE),
            pressedContentColor = Color(0xFF0B0A12),
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.07f, pressedScale = 0.97f),
        border = ButtonDefaults.border(
            border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (primary) 0f else 0.22f)), shape = RoundedCornerShape(50)),
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.55f)), inset = (-4).dp, shape = RoundedCornerShape(50)),
        ),
        contentPadding = PaddingValues(start = if (icon != null) 20.dp else 26.dp, end = 26.dp),
    ) {
        // Contenu toujours centré (même quand le bouton est plus large que son texte)
        // « wide » seulement pour un bouton qui occupe toute la largeur : sinon le bouton s'étirerait (et écraserait ses voisins)
        Row(if (wide) Modifier.fillMaxWidth() else Modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvoColors.Violet else TvoColors.Surface2,
            contentColor = TvoColors.Text,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(text, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
) {
    val keys = if (url) "abcdefghijklmnopqrstuvwxyz0123456789.:/-_?=&%~".map { it.toString() } + listOf(".com", "https://", "/manifest.json")
    else "abcdefghijklmnopqrstuvwxyz0123456789".map { it.toString() } + listOf("'", "-")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val singles = keys.filter { it.length == 1 }.chunked(if (url) 8 else 9)
        singles.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { c, k ->
                    Key(k, { onKey(k) }, Modifier.width(44.dp).let { if (r == 0 && c == 0 && firstKey != null) it.focusRequester(firstKey) else it })
                }
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
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
    Card(
        onClick = onClick,
        modifier = modifier.width(width).aspectRatio(16f / 9f).onFocused(onFocus),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = CardDefaults.colors(containerColor = TvoColors.Surface),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (item.type == "tv" || item.type == "channel") AsyncImage(item.poster, item.name, Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 14.dp, bottom = 40.dp), contentScale = ContentScale.Fit)
            else AsyncImage(item.background ?: item.poster, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xF0000000))))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(item.releaseInfo, typeLabel(item.type, plural = false), item.imdbRating?.let { "★ $it" }).joinToString("  ·  ")
                Text(meta, fontSize = 11.sp, color = Color(0xFFCFD4D8), maxLines = 1)
            }
        }
    }
}

@Composable
fun LandscapeRow(
    title: String,
    items: List<MetaPreview>,
    onOpen: (MetaPreview) -> Unit,
    onFocusItem: (MetaPreview) -> Unit = {},
    requestFocus: Boolean = false,
) {
    val fr = FocusRequester()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.78f).onFocused(onFocus),
            shape = CardDefaults.shape(shape),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvoColors.Blue), shape = shape)),
            colors = CardDefaults.colors(containerColor = TvoColors.Surface),
        ) {
            Box(Modifier.fillMaxSize()) {
                val tv = item.type == "tv" || item.type == "channel"
                AsyncImage(item.poster ?: item.background, item.name,
                    if (tv) Modifier.fillMaxSize().padding(22.dp) else Modifier.fillMaxSize(),
                    contentScale = if (tv) ContentScale.Fit else ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xE6000000))))
                Box(Modifier.align(Alignment.Center).size(40.dp).clip(RoundedCornerShape(11.dp)).background(Color(0x55FFFFFF)), contentAlignment = Alignment.Center) {
                    Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, null, Modifier.size(22.dp), tint = Color.White)
                }
                Text(item.name, Modifier.align(Alignment.BottomStart).padding(10.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
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
        androidx.compose.animation.core.tween(220), label = "w")
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
                AsyncImage(item.poster ?: item.background, item.name, if (tv) Modifier.fillMaxSize().padding(16.dp) else Modifier.fillMaxSize(),
                    contentScale = if (tv) ContentScale.Fit else ContentScale.Crop)
                if (item.poster == null || tv) Text(item.name, Modifier.align(Alignment.BottomStart).padding(8.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2)
            }
        }
    }
}


/** Carte d'une saga / collection : fond de la saga, voile et nom. */
@Composable
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
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1E2A4F), focusedContainerColor = Color(0xFF1E2A4F)),
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
