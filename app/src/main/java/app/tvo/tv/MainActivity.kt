package app.tvo.tv

import app.tvo.tv.ui.QuickMenu
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.heightIn
import androidx.lifecycle.lifecycleScope
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
    private var lastCheck = System.currentTimeMillis()
    private var leftAt = 0L
    override fun onStop() { super.onStop(); leftAt = System.currentTimeMillis() }
    override fun onResume() {
        super.onResume()
        // Absent depuis plus d'une heure : on vide les caches et les catalogues se rechargent tout seuls (dernières nouveautés)
        if (leftAt > 0 && System.currentTimeMillis() - leftAt > 60 * 60_000) { Tvo.client.clearCaches(); Tvo.refreshEpoch.intValue++ }
        app.tvo.tv.data.Sync.syncInBackground()
        // Retour dans l'appli : nouvelle recherche de mise à jour (au plus une fois toutes les 20 minutes)
        if (System.currentTimeMillis() - lastCheck > 20 * 60_000 && PendingUpdate.release.value == null) {
            lastCheck = System.currentTimeMillis()
            lifecycleScope.launch { Updater.check()?.let { PendingUpdate.release.value = it } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tvo.init(application)
        val crashFile = java.io.File(filesDir, "crash.txt")
        val lastCrash = runCatching { crashFile.takeIf { it.exists() }?.readText() }.getOrNull()
        if (lastCrash != null) runCatching { crashFile.delete() }
        // Version de TEST uniquement (debug) : ouvre directement une fiche, sans connexion ni profil, pour les tests automatiques
        val testTarget = if (BuildConfig.DEBUG) intent?.let { i ->
            val t = i.getStringExtra("tvo_test_type"); val id = i.getStringExtra("tvo_test_id")
            i.getStringExtra("tvo_test_tmdb")?.takeIf { it.isNotBlank() }?.let { k -> Tvo.store.tmdbKey.value = k }
            if (t != null && id != null) t to id else null
        } else null
        TvoColors.amb = getSharedPreferences("tvo_ui", MODE_PRIVATE).getString("amb", "oled") ?: "oled"
        setContent {
            var crash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) } // plus d'écran d'erreur : l'appli repart simplement
            if (crash != null) {
                // Écran de diagnostic : la photo de cet écran suffit pour corriger le plantage
                TvoTheme {
                    androidx.compose.foundation.layout.Column(
                        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).padding(40.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                    ) {
                        androidx.tv.material3.Text("L'application s'est fermée toute seule", fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                        androidx.tv.material3.Text("Voici l'erreur (montre cet écran en photo) :", fontSize = 16.sp, color = androidx.compose.ui.graphics.Color(0xFF8FC2FF))
                        androidx.tv.material3.Text(crash!!.lines().take(14).joinToString("\n"), fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFFFFB4B4), maxLines = 16, modifier = Modifier.weight(1f))
                        app.tvo.tv.ui.TvoButton("Continuer", { crash = null }, primary = true)
                    }
                }
                return@setContent
            }
            TvoTheme {
                Surface(Modifier.fillMaxSize(), colors = SurfaceDefaults.colors(containerColor = TvoColors.Bg, contentColor = TvoColors.Text)) {
                    app.tvo.tv.ui.AmbientBackground()
                    AppNav(testTarget)
                    if (testTarget == null) { UpdatePrompt(); WhatsNew(); AllDebridAlert() }
                }
            }
        }
    }
}

private fun e(s: String) = Uri.encode(s)

@Composable
private fun AppNav(testTarget: Pair<String, String>? = null) {
    val nc = rememberNavController()
    val nav = remember(nc) { navFor(nc) }
    QuickMenu.nav = nav
    LaunchedEffect(Unit) { refreshAddons() }
    // Transitions entre pages : fondu court et calme (par défaut, Android fait un fondu lent de 0,7 s)
    NavHost(nc, startDestination = if (testTarget != null) "test" else "splash",
        enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(170)) },
        exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(110)) },
        popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(170)) },
        popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(110)) }) {
        composable("test") {
            LaunchedEffect(Unit) { testTarget?.let { (t, id) ->
                // « home » : parcours client depuis l'accueil (premier profil choisi d'office)
                if (t == "home") { (Tvo.store.profiles.value.firstOrNull() ?: Tvo.store.upsertProfile(null, "Test", 0xFF3B82F6)).let { Tvo.store.selectProfile(it) }; nc.navigate("home") { popUpTo("test") { inclusive = true } } }
                else nc.navigate("detail/${e(t)}/${e(id)}") { popUpTo("test") { inclusive = true } }
            } }
        }
        composable("splash") { SplashScreen { nc.navigate("gate") { popUpTo("splash") { inclusive = true } } } }
        composable("gate") { AccountGate {
            // À chaque ouverture, on demande qui regarde (enfants, adultes…)
            nc.navigate("profiles") { popUpTo("gate") { inclusive = true } }
        } }
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
    app.tvo.tv.data.Tmdb.refreshRemote() // nouveaux catalogues publiés (silencieux, visibles à la prochaine ouverture d'une page)
    val store = Tvo.store
    // Les chaînes Free-TV sont retirées (remplacées par Freebox TV).
    if (store.addons.value.any { app.tvo.tv.data.NoTv.isTv(it) }) store.setAddons(store.addons.value)
    val fresh = store.addons.value.map { a -> runCatching { Tvo.client.install(a.url) }.getOrDefault(a) }
    store.setAddons(fresh, userChange = false)
}

/** Bannière commune (mise à jour, AllDebrid) en haut à droite : icône de couleur, titre, détail, lignes, barre de progression, boutons. */
@Composable
private fun NoticeCard(tone: Color, icon: String, title: String, sub: String?, lines: List<String> = emptyList(), progress: Float? = null, buttons: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(top = 22.dp, end = 36.dp), contentAlignment = androidx.compose.ui.Alignment.TopEnd) {
        Column(Modifier.width(500.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF16171D))
            .border(1.dp, tone.copy(alpha = 0.45f), RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(tone), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(icon, fontSize = 20.sp, fontWeight = FontWeight.Black, color = if (tone == NoticeWarn) Color(0xFF111111) else Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (!sub.isNullOrBlank()) Text(sub, fontSize = 14.sp, color = Color(0xFFB9C0CF))
                }
            }
            lines.forEach { Text("•  $it", Modifier.padding(start = 56.dp), fontSize = 14.sp, color = Color(0xFFDFE4EE), maxLines = 2) }
            if (progress != null) androidx.compose.foundation.layout.Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f))) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(5.dp).background(tone))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.End)) { buttons() }
        }
    }
}
private val NoticeBlue = Color(0xFF3D7CFF)
private val NoticeWarn = Color(0xFFF59E0B)
private val NoticeDanger = Color(0xFFDC2626)

@Composable
private fun UpdatePrompt() {
    // Une nouvelle version est proposée : ce qui change sur la TV, « Installer » (barre de progression), « Plus tard » (redemande dans 12 h)
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var prog by remember { mutableStateOf<Float?>(null) }
    var file by remember { mutableStateOf<java.io.File?>(null) }
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("tvo_ui", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    fun install() { file?.let { f -> prog = null; msg = Updater.install(ctx, f) ?: "Valide « Installer » sur l'écran suivant." } }
    val pending by PendingUpdate.release
    LaunchedEffect(pending) {
        val r = pending ?: return@LaunchedEffect
        val snoozed = prefs.getInt("upd_snooze_code", -1) == r.versionCode && System.currentTimeMillis() < prefs.getLong("upd_snooze_until", 0)
        if (!snoozed) { release = r; msg = null; file = null; prog = null }
    }
    fun later(r: Updater.Release) {
        prefs.edit().putInt("upd_snooze_code", r.versionCode).putLong("upd_snooze_until", System.currentTimeMillis() + 12 * 3_600_000L).apply()
        release = null
    }
    fun start() {
        val r = release ?: return
        if (file != null) { install(); return }
        scope.launch {
            msg = "Téléchargement…"; prog = 0f
            file = runCatching { Updater.download(ctx, r.apkUrl) { p -> prog = p; msg = "Téléchargement ${(p * 100).toInt()} %" } }
                .onFailure { prog = null; msg = "Échec du téléchargement, vérifie la connexion puis réessaie." }.getOrNull()
            install()
        }
    }
    val r = release ?: return
    val fr = remember { FocusRequester() }
    androidx.activity.compose.BackHandler { later(r) }
    val tvLines = remember(r) {
        r.notes.lines().map { it.trim().removePrefix("- ") }
            .filter { it.isNotBlank() && !Regex("^(Mobile|Lecteur mobile|iPhone|Téléphone|Mac)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            .map { it.replace(Regex("^(TV|Android TV)\\s*:?\\s*", RegexOption.IGNORE_CASE), "") }.take(2)
    }
    NoticeCard(NoticeBlue, "⬆", "Mise à jour disponible · TVO ${r.versionName}", msg ?: "Tes réglages et ta liste sont conservés", if (msg == null) tvLines else emptyList(), prog) {
        TvoButton(if (file != null) "Installer" else if (prog != null) "Téléchargement…" else "Installer", { start() }, Modifier.focusRequester(fr), primary = true)
        TvoButton("Plus tard", { later(r) })
    }
    LaunchedEffect(r) { delay(100); runCatching { fr.requestFocus() } }
}

/** À l'ouverture : AllDebrid expire aujourd'hui/demain ou a expiré → bannière (une fois par jour). */
@Composable
private fun AllDebridAlert() {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf<Pair<Boolean, Long>?>(null) } // (expiré, date de fin)
    LaunchedEffect(Unit) {
        val key = ctx.getSharedPreferences("tvo_alldebrid", android.content.Context.MODE_PRIVATE).getString("key", null) ?: return@LaunchedEffect
        val prefs = ctx.getSharedPreferences("tvo_ui", android.content.Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        if (prefs.getString("ad_warn_day", "") == today) return@LaunchedEffect
        delay(5000) // après l'arrivée sur l'accueil
        val u = runCatching { app.tvo.tv.ui.AllDebrid.user(key) }.getOrNull() ?: return@LaunchedEffect
        val days = if (u.until > 0) kotlin.math.ceil((u.until - System.currentTimeMillis()) / 86_400_000.0).toInt() else 0
        if (u.premium && days > 1) return@LaunchedEffect
        prefs.edit().putString("ad_warn_day", today).apply()
        state = (!u.premium || days <= 0) to u.until
    }
    val st = state ?: return
    val fr = remember { FocusRequester() }
    androidx.activity.compose.BackHandler { state = null }
    val (expired, until) = st
    NoticeCard(if (expired) NoticeDanger else NoticeWarn, "!",
        if (expired) "Ton abonnement AllDebrid a expiré" else "AllDebrid expire demain",
        if (expired) "Les sources débridées ne se lancent plus. Prolonge-le sur alldebrid.com depuis ton téléphone."
        else "Fin le " + java.text.SimpleDateFormat("d MMMM", java.util.Locale.FRENCH).format(java.util.Date(until)) + " : pense à le prolonger sur alldebrid.com.") {
        TvoButton("Compris", { state = null }, Modifier.focusRequester(fr), primary = true)
    }
    LaunchedEffect(st) { delay(100); runCatching { fr.requestFocus() } }
}


/** Accès à TVO : la TV doit être reliée à un compte validé par l'administrateur. */
@Composable
private fun AccountGate(onOk: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("tvo_ui", android.content.Context.MODE_PRIVATE) }
    var status by remember { mutableStateOf<String?>("checking") }
    var req by remember { mutableStateOf<app.tvo.tv.data.Sync.LoginRequest?>(null) }
    var tick by remember { mutableStateOf(0) }
    var entering by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var codeMsg by remember { mutableStateOf<String?>(null) }
    val padFr = remember { FocusRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(tick) {
        status = "checking"
        val s = app.tvo.tv.data.Sync.deviceStatus()
        val okBefore = System.currentTimeMillis() - prefs.getLong("gate_ok", 0) < 14 * 86_400_000L
        when {
            s == "active" -> { prefs.edit().putLong("gate_ok", System.currentTimeMillis()).apply(); onOk() }
            s == null && okBefore -> onOk() // hors connexion : dernier statut valide conservé 14 jours
            else -> { status = s ?: "offline"; if (s == "signed_out") req = app.tvo.tv.data.Sync.loginStart() }
        }
    }
    LaunchedEffect(req) {
        val r = req ?: return@LaunchedEffect
        repeat(300) { delay(2000); if (app.tvo.tv.data.Sync.loginPoll(r)) { req = null; tick++; return@LaunchedEffect } }
        req = app.tvo.tv.data.Sync.loginStart()
    }
    val fr = remember { FocusRequester() }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color(0xFF1F1F1F), TvoColors.Bg), radius = 1300f)),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                app.tvo.tv.ui.TvoLogo(Modifier.height(70.dp))
                val (t, d) = when (status) {
                    "checking" -> "Vérification du compte…" to ""
                    "signed_out" -> "Bienvenue sur TVO" to "Avec ton téléphone, c'est le plus simple : scanne le QR code avec l'appareil photo.\n• Tu as déjà un compte : connecte-toi, la télé se connecte toute seule.\n• Pas encore de compte : crée-le en une minute sur la page qui s'ouvre.\n• Ou choisis « J'ai un code » et tape le code affiché dans l'appli du téléphone."
                    "pending" -> "Compte en attente de validation" to "L'administrateur doit autoriser ton compte avant que tu puisses regarder."
                    "disabled" -> "Compte désactivé" to "L'accès de ce compte a été suspendu par l'administrateur."
                    "expired" -> "Accès terminé" to "La période d'accès de ce compte est terminée. Contacte l'administrateur."
                    else -> "Pas de connexion internet" to "Vérifie le Wi-Fi de la TV puis réessaie."
                }
                Text(t, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                if (d.isNotEmpty()) Text(d, fontSize = 17.sp, color = TvoColors.Muted, lineHeight = 26.sp)
                if (entering) {
                    Text(if (typed.isEmpty()) "Tape le code à 6 chiffres affiché sur le téléphone" else typed.chunked(3).joinToString(" "),
                        fontSize = if (typed.isEmpty()) 15.sp else 32.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                    app.tvo.tv.ui.DigitPad({ d -> if (typed.length < 6) typed += d }, { typed = typed.dropLast(1) }, first = padFr)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvoButton("Valider", { if (typed.length == 6) scope.launch { codeMsg = "Connexion…"
                            if (app.tvo.tv.data.Sync.claim(typed)) { entering = false; codeMsg = null; tick++ } else { codeMsg = "Code invalide ou expiré."; typed = "" } } }, primary = true)
                        TvoButton("Retour", { entering = false; codeMsg = null })
                    }
                    codeMsg?.let { Text(it, fontSize = 15.sp, color = TvoColors.Cyan) }
                    LaunchedEffect(Unit) { delay(80); runCatching { padFr.requestFocus() } }
                } else if (status != "checking") Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (status == "signed_out") TvoButton("J'ai un code", { entering = true; typed = ""; codeMsg = null })
                    TvoButton("Réessayer", { tick++ }, Modifier.focusRequester(fr), primary = true)
                    if (status != "signed_out" && status != "offline") TvoButton("Changer de compte", { Tvo.store.setSyncId(java.util.UUID.randomUUID().toString()); tick++ })
                }
                Text("En utilisant TVO, tu acceptes les conditions d'utilisation et la politique de confidentialité (consultables dans l'appli du téléphone).", fontSize = 12.sp, color = TvoColors.Muted)
            }
            if (!entering) req?.let { r ->
                val bmp = remember(r) { app.tvo.tv.data.RemoteInputServer.qr("https://mansstyle59.github.io/VOoTV/connect/?code=${r.code}&k=${r.key}", 460).asImageBitmap() }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.foundation.Image(bmp, "QR code", Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.White).padding(10.dp))
                    Text(r.code.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                    Text("Scanne avec ton téléphone", fontSize = 15.sp, color = TvoColors.Cyan)
                    Text("Connexion ou création de compte", fontSize = 13.sp, color = TvoColors.Muted)
                }
            }
        }
    }
    LaunchedEffect(status) { delay(150); runCatching { fr.requestFocus() } }
}

/** Après une mise à jour : la liste des nouveautés s'affiche une fois. */
@Composable
private fun WhatsNew() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("tvo_ui", android.content.Context.MODE_PRIVATE) }
    var notes by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        val seen = prefs.getInt("seen_code", -1)
        val cur = app.tvo.tv.BuildConfig.VERSION_CODE
        prefs.edit().putInt("seen_code", cur).apply()
        // Nouvelle version installée : on efface le fichier d'installation de l'ancienne mise à jour
        if (seen != cur) runCatching { java.io.File(ctx.cacheDir, "updates").deleteRecursively() }
        if (seen in 0 until cur) { delay(3000); notes = Updater.notesSince(seen, cur) }
    }
    if (notes.isEmpty()) return
    // Bannière discrète, sans prendre le focus : disparaît seule après quelques secondes
    LaunchedEffect(notes) { delay(9000); notes = emptyList() }
    // Seulement ce qui concerne la TV (les lignes « Mobile : … » sont pour le téléphone)
    val tvLines = notes.flatMap { it.second.lines() }.map { it.trim().removePrefix("- ") }
        .filter { it.isNotBlank() && !Regex("^(Mobile|Lecteur mobile|iPhone|Téléphone|Mac)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        .map { it.replace(Regex("^(TV|Android TV)\\s*:?\\s*", RegexOption.IGNORE_CASE), "") }
    if (tvLines.isEmpty()) return
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(top = 20.dp, end = 40.dp), contentAlignment = androidx.compose.ui.Alignment.TopEnd) {
        Column(Modifier.width(440.dp).clip(RoundedCornerShape(16.dp)).background(TvoColors.Surface).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text("📺  Cette TV est à jour · TVO ${app.tvo.tv.BuildConfig.VERSION_NAME}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Text("${tvLines.size} nouveauté${if (tvLines.size > 1) "s" else ""} pour la TV", fontSize = 12.sp, color = TvoColors.Cyan)
            tvLines.take(3).forEach { Text("• $it", fontSize = 13.sp, color = TvoColors.Muted, maxLines = 1) }
        }
    }
}

object PendingUpdate { val release = mutableStateOf<Updater.Release?>(null) }

/** Écran d'accueil : logo + recherche de mise à jour (4 s maximum). */
@Composable
private fun SplashScreen(onDone: () -> Unit) {
    var status by remember { mutableStateOf("Recherche de mise à jour…") }
    LaunchedEffect(Unit) {
        val t0 = System.currentTimeMillis()
        val r = kotlinx.coroutines.withTimeoutOrNull(5000) { Updater.check() }
        if (r != null) { status = "Mise à jour ${r.versionName} trouvée"; PendingUpdate.release.value = r }
        else status = "TVO est à jour ✓"
        delay((2400 - (System.currentTimeMillis() - t0)).coerceAtLeast(500))
        onDone()
    }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color(0xFF1F1F1F), TvoColors.Bg), radius = 1100f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            app.tvo.tv.ui.TvoLogo(Modifier.height(110.dp))
            androidx.tv.material3.Text("Films, séries et TV en direct", fontSize = 18.sp, color = androidx.compose.ui.graphics.Color(0xFFC9D8FF))
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
