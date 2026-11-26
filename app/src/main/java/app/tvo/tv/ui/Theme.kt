package app.tvo.tv.ui

import androidx.compose.ui.unit.sp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object TvoColors {
    val Bg = Color(0xFF141414)
    val Panel = Color(0xFF181818)
    val Surface = Color(0xFF2B2B2B)
    val Surface2 = Color(0xFF333333)
    val Line = Color(0xFF404040)
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
