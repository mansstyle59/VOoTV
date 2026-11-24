package app.tvo.tv

import android.net.Uri
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.tvo.tv.data.Updater
import app.tvo.tv.ui.AddonAddScreen
import app.tvo.tv.ui.DetailScreen
import app.tvo.tv.ui.GenreScreen
import app.tvo.tv.ui.HomeScreen
import app.tvo.tv.ui.Nav
import app.tvo.tv.ui.ProfileEditScreen
import app.tvo.tv.ui.ProfilesScreen
import app.tvo.tv.ui.StreamsScreen
import app.tvo.tv.ui.TvoButton
import app.tvo.tv.ui.TvoColors
import app.tvo.tv.ui.TvoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        app.tvo.tv.data.Sync.syncInBackground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tvo.init(application)
        setContent {
            TvoTheme {
                Surface(Modifier.fillMaxSize(), colors = SurfaceDefaults.colors(containerColor = TvoColors.Bg, contentColor = TvoColors.Text)) {
                    AppNav()
                    UpdatePrompt()
                }
            }
        }
    }
}

private fun e(s: String) = Uri.encode(s)

@Composable
private fun AppNav() {
    val nc = rememberNavController()
    val nav = remember(nc) { navFor(nc) }
    LaunchedEffect(Unit) { refreshAddons() }
    NavHost(nc, startDestination = "splash") {
        composable("splash") { SplashScreen { nc.navigate("profiles") { popUpTo("splash") { inclusive = true } } } }
        composable("profiles") {
            ProfilesScreen(
                onSelected = { nc.navigate("home") { popUpTo("profiles") { inclusive = true } } },
                onEdit = { nc.navigate("profileEdit/${e(it)}") },
            )
        }
        composable("profileEdit/{id}") { b -> ProfileEditScreen(b.arguments?.getString("id") ?: "new") { nc.popBackStack() } }
        composable("home") { HomeScreen(nav) }
        composable("detail/{type}/{id}") { b ->
            DetailScreen(b.arguments?.getString("type") ?: "movie", b.arguments?.getString("id") ?: "", nav)
        }
        composable("streams/{type}/{metaId}/{videoId}") { b ->
            StreamsScreen(b.arguments?.getString("type") ?: "movie", b.arguments?.getString("metaId") ?: "", b.arguments?.getString("videoId") ?: "")
        }
        composable("genre/{type}/{genre}") { b ->
            GenreScreen(b.arguments?.getString("type") ?: "movie", b.arguments?.getString("genre") ?: "", nav)
        }
        composable("addonAdd") { AddonAddScreen { nc.popBackStack() } }
        composable("person/{id}") { b -> app.tvo.tv.ui.PersonScreen(b.arguments?.getString("id") ?: "", nav) }
        composable("tmdbKey") { app.tvo.tv.ui.TmdbKeyScreen { nc.popBackStack() } }
    }
}

private fun navFor(nc: NavHostController) = Nav(
    openMeta = { nc.navigate("detail/${e(it.type)}/${e(it.id)}") },
    openGenre = { t, g -> nc.navigate("genre/${e(t)}/${e(g)}") },
    openStreams = { t, m, v -> nc.navigate("streams/${e(t)}/${e(m)}/${e(v)}") },
    switchProfile = { nc.navigate("profiles") { popUpTo(nc.graph.id) { inclusive = true } } },
    addAddon = { nc.navigate("addonAdd") },
    editProfile = { nc.navigate("profileEdit/${e(it)}") },
    tmdbKey = { nc.navigate("tmdbKey") },
    openPerson = { nc.navigate("person/${e(it)}") },
)

/** Re-reads every installed add-on manifest so catalogs stay current. */
private suspend fun refreshAddons() {
    if (app.tvo.tv.data.Tmdb.checkProxy()) Tvo.client.clearCaches()
    val store = Tvo.store
    // Les chaînes Free-TV sont retirées (remplacées par Freebox TV).
    if (store.addons.value.any { it.url == app.tvo.tv.data.FreeTv.URL }) store.setAddons(store.addons.value.filterNot { it.url == app.tvo.tv.data.FreeTv.URL })
    val fresh = store.addons.value.map { a -> runCatching { Tvo.client.install(a.url) }.getOrDefault(a) }
    store.setAddons(fresh, userChange = false)
}

@Composable
private fun UpdatePrompt() {
    // À l'ouverture : la nouvelle version se télécharge toute seule, puis l'installateur Android s'ouvre.
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun install() { file?.let { f -> msg = Updater.install(ctx, f) ?: "Valide « Installer » sur l'écran suivant." } }
    val pending by PendingUpdate.release
    LaunchedEffect(pending) {
        val r = pending ?: return@LaunchedEffect
        release = r
        msg = "Téléchargement…"
        file = runCatching { Updater.download(ctx, r.apkUrl) { p -> msg = "Téléchargement ${(p * 100).toInt()} %" } }
            .onFailure { msg = "Échec du téléchargement : ${it.message}" }.getOrNull()
        install()
    }
    val r = release ?: return
    val fr = remember { FocusRequester() }
    Dialog(onDismissRequest = { release = null }) {
        Surface(shape = RoundedCornerShape(16.dp), colors = SurfaceDefaults.colors(containerColor = TvoColors.Surface, contentColor = TvoColors.Text)) {
            Column(Modifier.width(460.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mise à jour de TVO ${r.versionName}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Ta liste, ta progression et tes réglages sont conservés.", fontSize = 15.sp, color = TvoColors.Muted)
                msg?.let { Text(it, fontSize = 15.sp, color = TvoColors.Cyan) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvoButton("Installer", { if (file != null) install() }, Modifier.focusRequester(fr), primary = true)
                    TvoButton("Plus tard", { release = null })
                }
            }
        }
    }
    LaunchedEffect(r) { delay(100); runCatching { fr.requestFocus() } }
    @Suppress("UNUSED_VARIABLE") val keep = scope
}


object PendingUpdate { val release = mutableStateOf<Updater.Release?>(null) }

/** Écran d'accueil : logo + recherche de mise à jour (4 s maximum). */
@Composable
private fun SplashScreen(onDone: () -> Unit) {
    var status by remember { mutableStateOf("Recherche de mise à jour…") }
    LaunchedEffect(Unit) {
        val r = kotlinx.coroutines.withTimeoutOrNull(4000) { Updater.check() }
        if (r != null) { status = "Nouvelle version ${r.versionName} trouvée"; PendingUpdate.release.value = r; delay(600) }
        else { status = "TVO est à jour"; delay(400) }
        onDone()
    }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color(0xFF13245F), TvoColors.Bg), radius = 1100f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo_tvo), "TVO", Modifier.height(90.dp))
            androidx.tv.material3.Text(status, fontSize = 16.sp, color = TvoColors.Muted)
            androidx.compose.foundation.layout.Box(Modifier.width(180.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(TvoColors.Surface2)) {
                val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "s")
                val x: Float by t.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween<Float>(1100)), label = "x")
                androidx.compose.foundation.layout.Box(Modifier.fillMaxHeight().width(60.dp).offset(x = (120f * x).dp).background(TvoColors.Blue))
            }
        }
    }
}
