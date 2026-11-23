package app.tvo.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

val RowPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp)

fun Modifier.onFocused(block: () -> Unit) = this.onFocusChanged { if (it.isFocused) block() }

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(start = 48.dp, bottom = 2.dp), fontSize = 19.sp,
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
) {
    val shape = RoundedCornerShape(10.dp)
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

@Composable
fun TvoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) Color.White else TvoColors.Surface2,
            contentColor = if (primary) Color.Black else TvoColors.Text,
            focusedContainerColor = if (primary) TvoColors.Cyan else Color.White,
            focusedContentColor = Color.Black,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Avatar(name: String, color: Long, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size / 4))
        .background(Brush.linearGradient(listOf(Color(color), Color(color).copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold, color = Color.White)
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
        val singles = keys.filter { it.length == 1 }.chunked(if (url) 8 else 6)
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
