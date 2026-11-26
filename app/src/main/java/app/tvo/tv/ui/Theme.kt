package app.tvo.tv.ui

import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object TvoColors {
    /** Ambiance (même choix que sur le téléphone) : « oled », « nuit », « aurore » ou « profil ». */
    var amb by androidx.compose.runtime.mutableStateOf("oled")
    private fun tint(k: Float, over: Long): Color = androidx.compose.ui.graphics.lerp(Color(over), base, k)
    val Bg: Color get() = when (amb) { "nuit" -> Color(0xFF02060F); "profil" -> tint(0.09f, 0xFF000000); else -> Color(0xFF000000) }
    val Panel: Color get() = when (amb) { "nuit" -> Color(0xFF050C1C); "profil" -> tint(0.11f, 0xFF040406); else -> Color(0xFF07080B) }
    val Surface: Color get() = when (amb) { "nuit" -> Color(0xFF0E1830); "profil" -> tint(0.14f, 0xFF15171C); else -> Color(0xFF17191F) }
    val Surface2: Color get() = when (amb) { "nuit" -> Color(0xFF15223F); "profil" -> tint(0.16f, 0xFF1E2128); else -> Color(0xFF20232A) }
    val Line: Color get() = when (amb) { "nuit" -> Color(0xFF1E2E50); "profil" -> tint(0.2f, 0xFF2A2E36); else -> Color(0xFF2C3038) }
    /** Couleur brute du profil (pour les halos du fond). */
    val Glow: Color get() = base
    val Text = Color(0xFFF5F5F5)
    val Muted = Color(0xFFB3B3B3)
    val Success = Color(0xFF34D399)
    val Danger = Color(0xFFF87171)

    // Couleur d'accent = couleur du profil actif (boutons, focus, liens, barres). Bleu par défaut.
    private var base by androidx.compose.runtime.mutableStateOf(Color(0xFF3D7CFF))
    fun setAccent(c: Long?) { base = if (c == null) Color(0xFF3D7CFF) else Color(c.toInt()).copy(alpha = 1f) }

    private fun lin(v: Float) = if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    private fun lum(c: Color) = 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    private fun ratio(a: Color, b: Color): Float { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f) }
    private fun towards(c: Color, t: Color, k: Float) = androidx.compose.ui.graphics.lerp(c, t, k)

    /** Fond de bouton : assez foncé pour que le texte blanc reste lisible. */
    val Blue: Color get() { var c = base; var i = 0; while (i < 20 && ratio(c, Color.White) < 3.4f) { c = towards(c, Color.Black, 0.08f); i++ }; return c }
    val Violet: Color get() = Blue
    /** Version claire pour textes et icônes sur fond sombre. */
    val Cyan: Color get() { var c = base; var i = 0; while (i < 20 && ratio(c, Bg) < 6f) { c = towards(c, Color.White, 0.1f); i++ }; return c }
    val Accent: Brush get() = Brush.horizontalGradient(listOf(Blue, towards(Blue, Color.White, 0.18f)))
}

/** Police Inter (proche de Netflix Sans) pour tous les textes de la télé. */
val Inter = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(app.tvo.tv.R.font.inter_regular, androidx.compose.ui.text.font.FontWeight.Normal),
    androidx.compose.ui.text.font.Font(app.tvo.tv.R.font.inter_medium, androidx.compose.ui.text.font.FontWeight.Medium),
    androidx.compose.ui.text.font.Font(app.tvo.tv.R.font.inter_semibold, androidx.compose.ui.text.font.FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(app.tvo.tv.R.font.inter_bold, androidx.compose.ui.text.font.FontWeight.Bold),
    androidx.compose.ui.text.font.Font(app.tvo.tv.R.font.inter_extrabold, androidx.compose.ui.text.font.FontWeight.ExtraBold),
    androidx.compose.ui.text.font.Font(app.tvo.tv.R.font.inter_black, androidx.compose.ui.text.font.FontWeight.Black),
)

@Composable
fun TvoTheme(content: @Composable () -> Unit) {
    val prof by app.tvo.tv.Tvo.store.currentProfile.collectAsState()
    androidx.compose.runtime.SideEffect { TvoColors.setAccent(prof?.color) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TvoColors.Violet,
            onPrimary = Color.White,
            secondary = TvoColors.Cyan,
            background = TvoColors.Bg,
            onBackground = TvoColors.Text,
            surface = TvoColors.Surface,
            onSurface = TvoColors.Text,
            surfaceVariant = TvoColors.Surface2,
            onSurfaceVariant = TvoColors.Muted,
            border = TvoColors.Muted,
        ),
        content = {
            // Auto-adaptation : toute l'interface est dessinée pour un écran de référence 960 x 540
            // et mise à l'échelle de la vraie TV (HD, Full HD, 4K, densité exotique des box Android).
            val baseD = androidx.compose.ui.platform.LocalDensity.current
            val baseCfg = androidx.compose.ui.platform.LocalConfiguration.current
            val wPx = baseCfg.screenWidthDp * baseD.density
            val hPx = baseCfg.screenHeightDp * baseD.density
            val scale = if (wPx > 0f && hPx > 0f) minOf(wPx / 960f, hPx / 540f) else baseD.density
            val d = androidx.compose.ui.unit.Density(scale, baseD.fontScale.coerceIn(0.85f, 1.15f))
            UiScale.density = scale
            val cfg = androidx.compose.runtime.remember(baseCfg, scale) {
                android.content.res.Configuration(baseCfg).apply {
                    screenWidthDp = (wPx / scale).toInt(); screenHeightDp = (hPx / scale).toInt()
                    smallestScreenWidthDp = minOf(screenWidthDp, screenHeightDp); densityDpi = (scale * 160).toInt()
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides d,
                androidx.compose.ui.platform.LocalConfiguration provides cfg,
                androidx.tv.material3.LocalTextStyle provides androidx.tv.material3.LocalTextStyle.current.copy(fontFamily = Inter, letterSpacing = (-0.1).sp),
                content = content,
            )
        },
    )
}

private val genreFr = mapOf(
    "Action" to "Action", "Adventure" to "Aventure", "Animation" to "Animation", "Biography" to "Biographie",
    "Comedy" to "Comédie", "Crime" to "Policier", "Documentary" to "Documentaire", "Drama" to "Drame",
    "Family" to "Famille", "Fantasy" to "Fantastique", "History" to "Histoire", "Horror" to "Horreur",
    "Music" to "Musique", "Musical" to "Comédie musicale", "Mystery" to "Mystère", "Romance" to "Romance",
    "Sci-Fi" to "Science-fiction", "Sport" to "Sport", "Thriller" to "Thriller", "War" to "Guerre",
    "Western" to "Western", "Reality-TV" to "Téléréalité", "Talk-Show" to "Talk-show", "Game-Show" to "Jeux TV",
    "News" to "Actualités", "Short" to "Court métrage", "Film-Noir" to "Film noir",
)

fun genreLabel(g: String) = genreFr[g] ?: g

fun typeLabel(type: String, plural: Boolean = true) = when (type) {
    "movie" -> if (plural) "Films" else "Film"
    "series" -> if (plural) "Séries" else "Série"
    "anime" -> "Anime"
    "channel" -> "Chaînes"
    "tv" -> "TV en direct"
    else -> type.replaceFirstChar { it.uppercase() }
}

fun catalogTitle(type: String, name: String): String {
    val t = typeLabel(type)
    return when (name.lowercase()) {
        "popular", "top" -> "$t populaires"
        "featured" -> "$t les mieux notés"
        "new" -> "Nouveautés · $t"
        else -> "$name · $t"
    }
}

fun langLabel(code: String): String = when (code.lowercase().take(3)) {
    "fr", "fre", "fra" -> "Français"
    "en", "eng" -> "Anglais"
    "es", "spa" -> "Espagnol"
    "de", "ger", "deu" -> "Allemand"
    "it", "ita" -> "Italien"
    "pt", "por", "pob" -> "Portugais"
    "ar", "ara" -> "Arabe"
    "nl", "dut", "nld" -> "Néerlandais"
    "ja", "jpn" -> "Japonais"
    "tr", "tur" -> "Turc"
    else -> code
}

/** Échelle d'affichage courante (pixels par dp de référence), utilisée hors Compose (défilement). */
object UiScale { @Volatile var density: Float = android.content.res.Resources.getSystem().displayMetrics.density }


/** Fond d'ambiance : halos ronds flous à la couleur du profil et logo TVO en filigrane, très discrets. */
@Composable
fun AmbientBackground() {
    val glow = TvoColors.Glow; val a = TvoColors.amb
    val strong = when (a) { "aurore" -> 0.30f; "profil" -> 0.26f; "nuit" -> 0.20f; else -> 0.16f }
    val drift = if (a == "aurore") androidx.compose.animation.core.rememberInfiniteTransition(label = "amb").animateFloat(0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(24000), androidx.compose.animation.core.RepeatMode.Reverse), label = "d").value else 0f
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(TvoColors.Bg)) {
        androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            fun halo(cx: Float, cy: Float, r: Float, c: Color, k: Float) =
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = k), Color.Transparent), androidx.compose.ui.geometry.Offset(cx, cy), r), r, androidx.compose.ui.geometry.Offset(cx, cy))
            halo(w * (0.08f + 0.12f * drift), h * (-0.05f + 0.1f * drift), w * 0.55f, glow, strong)
            halo(w * (0.95f - 0.1f * drift), h * 1.05f, w * 0.45f, if (a == "aurore") Color(0xFF8B5CF6) else if (a == "nuit") Color(0xFF1E40AF) else glow, strong * 0.6f)
        }
        TvoLogo(androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 40.dp, bottom = 30.dp).size(300.dp)
                .graphicsLayer { rotationZ = -12f; alpha = if (a == "profil") 0.06f else 0.045f }, file = "mobile/www/logo-mark.png", fallback = app.tvo.tv.R.drawable.logo_mark)
    }
}
