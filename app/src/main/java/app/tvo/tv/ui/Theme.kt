package app.tvo.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object TvoColors {
    val Bg = Color(0xFF0E0E10)
    val Panel = Color(0xFF151518)
    val Surface = Color(0xFF1D1D21)
    val Surface2 = Color(0xFF2A2A30)
    val Line = Color(0xFF2E2E35)
    val Blue = Color(0xFF2F5BFF)
    val Violet = Blue
    val Cyan = Color(0xFF7D97FF)
    val Text = Color(0xFFF4F4F6)
    val Muted = Color(0xFF9A9AA6)
    val Success = Color(0xFF34D399)
    val Danger = Color(0xFFF87171)
    val Accent = Brush.horizontalGradient(listOf(Color(0xFF2F5BFF), Color(0xFF4F78FF)))
}

@Composable
fun TvoTheme(content: @Composable () -> Unit) {
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
        content = content,
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
