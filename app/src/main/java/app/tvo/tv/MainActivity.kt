package app.tvo.tv

import android.net.Uri
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
    NavHost(nc, startDestination = "profiles") {
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
)

/** Re-reads every installed add-on manifest so catalogs stay current. */
private suspend fun refreshAddons() {
    val store = Tvo.store
    val fresh = store.addons.value.map { a -> runCatching { Tvo.client.install(a.url) }.getOrDefault(a) }
    store.setAddons(fresh, userChange = false)
}

@Composable
private fun UpdatePrompt() {
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { delay(3000); release = Updater.check() }
    val r = release ?: return
    val fr = remember { FocusRequester() }
    Dialog(onDismissRequest = { release = null }) {
        Surface(shape = RoundedCornerShape(16.dp), colors = SurfaceDefaults.colors(containerColor = TvoColors.Surface, contentColor = TvoColors.Text)) {
            Column(Modifier.width(460.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mise à jour disponible", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("TVO ${r.versionName} est prête. Ta liste et tes réglages sont conservés.", fontSize = 15.sp, color = TvoColors.Muted)
                msg?.let { Text(it, fontSize = 15.sp, color = TvoColors.Cyan) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvoButton("Installer", {
                        scope.launch {
                            msg = "Téléchargement…"
                            msg = runCatching {
                                val f = Updater.download(ctx, r.apkUrl) { p -> msg = "Téléchargement ${(p * 100).toInt()} %" }
                                Updater.install(ctx, f) ?: "Installation lancée."
                            }.getOrElse { "Échec : ${it.message}" }
                        }
                    }, Modifier.focusRequester(fr), primary = true)
                    TvoButton("Plus tard", { release = null })
                }
            }
        }
    }
    LaunchedEffect(r) { delay(100); runCatching { fr.requestFocus() } }
}
