package app.tvo.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.tvo.tv.BuildConfig
import app.tvo.tv.Tvo
import app.tvo.tv.data.Addon
import app.tvo.tv.data.Defaults
import app.tvo.tv.data.MetaPreview
import app.tvo.tv.data.RemoteInputServer
import app.tvo.tv.data.Updater
import app.tvo.tv.data.category
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Ma liste

@Composable
fun LibrarySection(nav: Nav) {
    val library by Tvo.store.library.collectAsState()
    if (library.isEmpty()) {
        CenterMessage("Ta liste est vide", "Ouvre un film ou une série et appuie sur « Ma liste » pour le retrouver ici.")
        return
    }
    PosterGrid("Ma liste", library.map { it.toPreview() }, nav)
}

@Composable
fun PosterGrid(title: String, items: List<MetaPreview>, nav: Nav, onNearEnd: () -> Unit = {}) {
    val first = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        Text(title, Modifier.padding(start = 48.dp, top = 36.dp, bottom = 8.dp), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(136.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                PosterCard(item, { nav.openMeta(item) }, if (i == 0) Modifier.focusRequester(first) else Modifier, width = 136.dp,
                    onFocus = { if (i >= items.size - 14) onNearEnd() })
            }
        }
    }
    LaunchedEffect(items.isNotEmpty()) { if (items.isNotEmpty()) { delay(80); runCatching { first.requestFocus() } } }
}

// ---------------------------------------------------------------- Genre

@Composable
fun GenreScreen(type: String, genre: String, nav: Nav) {
    val rawAddons by Tvo.store.addons.collectAsState()
    val tmdbKey by Tvo.store.tmdbKey.collectAsState()
    val addons = remember(rawAddons, tmdbKey) { app.tvo.tv.data.Tmdb.withTmdb(rawAddons, tmdbKey) }
    val items = remember { mutableStateListOf<MetaPreview>() }
    var loading by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val src = remember(addons) {
        addons.firstNotNullOfOrNull { a -> a.manifest.catalogs.firstOrNull { it.type == type && it.supports("genre") && it.onlyRequires("genre") }?.let { a to it } }
    }
    fun more() {
        if (loading || end || src == null) return
        loading = true
        scope.launch {
            val extra = buildMap { put("genre", genre); if (items.isNotEmpty() && src.second.supports("skip")) put("skip", items.size.toString()) }
            val page = runCatching { Tvo.client.catalog(src.first, src.second, extra) }.getOrDefault(emptyList())
            val fresh = page.filter { p -> items.none { it.id == p.id } }
            if (fresh.isEmpty() || !src.second.supports("skip")) end = true
            items.addAll(fresh); loading = false
        }
    }
    LaunchedEffect(src) { more() }
    Box(Modifier.fillMaxSize().background(TvoColors.Bg)) {
        when {
            src == null -> CenterMessage("Catégorie indisponible", "Aucun add-on ne propose de tri par genre.")
            items.isEmpty() && loading -> CenterMessage("Chargement…")
            else -> PosterGrid("${genreLabel(genre)} · ${typeLabel(type)}", items, nav, onNearEnd = { more() })
        }
    }
}

// ---------------------------------------------------------------- Recherche

@Composable
fun SearchSection(nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    val rawAddons by Tvo.store.addons.collectAsState()
    val tmdbKey by Tvo.store.tmdbKey.collectAsState()
    val addons = remember(rawAddons, tmdbKey) { app.tvo.tv.data.Tmdb.withTmdb(rawAddons, tmdbKey) }
    val firstKey = remember { FocusRequester() }
    val results by produceState<List<Pair<String, List<MetaPreview>>>?>(emptyList(), query, addons) {
        if (query.trim().length < 2) { value = emptyList(); return@produceState }
        delay(450)
        value = null
        val out = mutableListOf<Pair<String, List<MetaPreview>>>()
        addons.forEach { a ->
            a.manifest.catalogs.filter { it.supports("search") && it.onlyRequires("search") }.forEach { c ->
                val list = runCatching { Tvo.client.catalog(a, c, mapOf("search" to query.trim())) }.getOrDefault(emptyList())
                if (list.isNotEmpty()) out += (if (a.manifest.id == "com.linvo.cinemeta" || a.manifest.id == "tmdb") typeLabel(c.type) else if (a.manifest.id == "tvo.freetv.fr") c.name else "${c.name} · ${a.manifest.name}") to list
            }
        }
        value = out
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(380.dp).padding(start = 40.dp, top = 36.dp)) {
            Text("Recherche", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Box(Modifier.padding(vertical = 16.dp).width(330.dp).clip(RoundedCornerShape(12.dp)).background(TvoColors.Surface).padding(14.dp)) {
                Text(query.ifEmpty { "Titre d'un film ou d'une série" }, fontSize = 19.sp,
                    color = if (query.isEmpty()) TvoColors.Muted else TvoColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TvKeyboard(onKey = { query += it }, onBackspace = { query = query.dropLast(1) }, onClear = { query = "" }, firstKey = firstKey)
        }
        Box(Modifier.fillMaxSize()) {
            val r = results
            when {
                r == null -> CenterMessage("Recherche…")
                query.trim().length < 2 -> CenterMessage("Que veux-tu regarder ?", "Tape au moins deux lettres.")
                r.isEmpty() -> CenterMessage("Aucun résultat", "Essaie un autre titre, en anglais ou en français.")
                else -> LazyColumn(contentPadding = PaddingValues(top = 36.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(r, key = { it.first }) { (title, list) -> PosterRow(title, list, nav.openMeta, {}) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { firstKey.requestFocus() } }
}

// ---------------------------------------------------------------- Réglages

@Composable
fun SettingsSection(nav: Nav) {
    val store = Tvo.store
    val addons by store.addons.collectAsState()
    val external by store.externalPlayer.collectAsState()
    val profile by store.currentProfile.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<Updater.Release?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 36.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Réglages", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text) }

        item { Heading("Profil") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                profile?.let { Avatar(it.name, it.color, 44.dp); Text(it.name, fontSize = 18.sp, color = TvoColors.Text, modifier = Modifier.padding(end = 12.dp)) }
                TvoButton("Changer de profil", nav.switchProfile, icon = Icons.Default.Person)
                profile?.let { p -> TvoButton("Modifier", { nav.editProfile(p.id) }) }
            }
        }

        item { Heading("Synchronisation TV ⇄ téléphone") }
        item { SyncBlock() }

        item { Heading("TMDB (titres et résumés en français)") }
        item { TmdbBlock(nav) }

        item { Heading("Add-ons") }
        item { Text("Compatible avec les add-ons Stremio / Nuvio existants : colle l'adresse de leur manifest.json. Catalogues, fiches, vidéos et sous-titres viennent des add-ons ; le premier de la liste est prioritaire. Les sources torrent seules ne sont pas lisibles.",
            fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.width(760.dp)) }
        app.tvo.tv.data.ADDON_CATEGORIES.forEach { cat ->
            val group = addons.filter { it.category() == cat }
            if (group.isNotEmpty()) {
                item(key = "cat-$cat") { Text(cat, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text, modifier = Modifier.padding(top = 6.dp)) }
                items(group, key = { it.url }) { a -> AddonRow(a, addons.indexOf(a)) }
            }
        }
        item { TvoButton("Ajouter un add-on", nav.addAddon, icon = Icons.Default.Add, primary = true) }

        item { Heading("Page d'accueil") }
        item { HomeEditor() }

        item { Heading("Lecture") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Lecteur TVO", !external, { store.setExternalPlayer(false) })
                Chip("Lecteur externe (VLC, Just Player…)", external, { store.setExternalPlayer(true) })
            }
        }
        item { Text("Le lecteur externe aide si un film n'a pas de son (formats DTS, TrueHD…).", fontSize = 14.sp, color = TvoColors.Muted) }

        item { Heading("Mise à jour") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 16.sp, color = TvoColors.Text)
                TvoButton("Rechercher une mise à jour", {
                    updateMsg = "Recherche…"
                    scope.launch {
                        release = Updater.check()
                        updateMsg = if (release == null) "TVO est à jour." else "Version ${release!!.versionName} disponible."
                    }
                }, icon = Icons.Default.Refresh)
                release?.let { r -> TvoButton("Installer ${r.versionName}", {
                    scope.launch {
                        updateMsg = "Téléchargement…"
                        updateMsg = runCatching {
                            val f = Updater.download(ctx, r.apkUrl) { p -> updateMsg = "Téléchargement ${(p * 100).toInt()} %" }
                            Updater.install(ctx, f) ?: "Installation lancée."
                        }.getOrElse { "Échec du téléchargement : ${it.message}" }
                    }
                }, primary = true) }
            }
        }
        updateMsg?.let { m -> item { Text(m, fontSize = 15.sp, color = TvoColors.Cyan) } }
        item { Spacer(Modifier.height(10.dp)); Text("TVO réunit tes catalogues et tes add-ons dans une seule appli. Elle n'héberge aucun contenu.", fontSize = 13.sp, color = TvoColors.Muted) }
    }
}

@Composable
private fun Heading(t: String) = Text(t, Modifier.padding(top = 14.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Cyan)

@Composable
private fun AddonRow(a: Addon, index: Int) {
    val store = Tvo.store
    val m = a.manifest
    val kinds = m.resources.map { it.name }.mapNotNull {
        when (it) { "catalog" -> "catalogues"; "meta" -> "fiches"; "stream" -> "vidéos"; "subtitles" -> "sous-titres"; else -> null }
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TvoColors.Surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("${m.name}  ${m.version}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
            Text(kinds.joinToString(" · ").ifEmpty { "—" }, fontSize = 13.sp, color = TvoColors.Cyan)
            if (m.description.isNotBlank()) Text(m.description, fontSize = 13.sp, color = TvoColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (m.configurable && a.url.startsWith("http")) Text("Configurer depuis le téléphone : ${a.url}/configure", fontSize = 12.sp, color = TvoColors.Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (index > 0) TvoButton("Monter", { store.moveAddon(a.url, -1) }, icon = Icons.Default.KeyboardArrowUp)
        if (a.url.startsWith("http")) {
            val scope = rememberCoroutineScope()
            TvoButton("Actualiser", { scope.launch { runCatching { Tvo.client.install(a.url) }.onSuccess { store.addAddon(it); Tvo.client.clearCaches() } } }, icon = Icons.Default.Refresh)
        }
        TvoButton("Retirer", { store.removeAddon(a.url) }, icon = Icons.Default.Delete)
    }
}

// ---------------------------------------------------------------- Ajout d'add-on

@Composable
fun AddonAddScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }
    val server = remember { RemoteInputServer { v -> text = v } }
    var port by remember { mutableStateOf<Int?>(null) }
    val ip = remember { RemoteInputServer.localIp() }
    DisposableEffect(Unit) { port = server.start(); onDispose { server.stop() } }
    val phoneUrl = if (ip != null && port != null) "http://$ip:$port" else null
    val qr = remember(phoneUrl) { phoneUrl?.let { RemoteInputServer.qr(it).asImageBitmap() } }

    fun install(url: String) {
        if (busy || url.isBlank()) return
        busy = true; msg = "Installation…"
        scope.launch {
            msg = runCatching { Tvo.client.install(url) }.fold(
                { a -> Tvo.store.addAddon(a); onDone(); null },
                { "Impossible d'installer : vérifie l'adresse (${it.message})." },
            )
            busy = false
        }
    }

    Row(Modifier.fillMaxSize().background(TvoColors.Bg).padding(48.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Ajouter un add-on", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TvoColors.Surface).padding(14.dp)) {
                Text(text.ifEmpty { "https://…/manifest.json" }, fontSize = 17.sp, color = if (text.isEmpty()) TvoColors.Muted else TvoColors.Text, maxLines = 2)
            }
            TvKeyboard(onKey = { text += it }, onBackspace = { text = text.dropLast(1) }, onClear = { text = "" }, url = true, firstKey = firstKey)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvoButton("Installer", { install(text) }, primary = true, icon = Icons.Default.Add)
                TvoButton("Annuler", onDone)
            }
            msg?.let { Text(it, Modifier.padding(top = 12.dp), fontSize = 15.sp, color = TvoColors.Cyan) }
        }
        Column(Modifier.width(340.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Plus simple : depuis ton téléphone", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
            if (qr != null && phoneUrl != null) {
                Image(qr, "QR code", Modifier.padding(vertical = 14.dp).size(220.dp).clip(RoundedCornerShape(12.dp)))
                Text("Scanne ce code (même Wi-Fi) ou ouvre :", fontSize = 14.sp, color = TvoColors.Muted)
                Text(phoneUrl, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TvoColors.Cyan)
                Text("Colle l'adresse, elle s'affiche ici.", fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text("Connecte la TV au Wi-Fi pour utiliser le téléphone.", fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Suggestions compatibles", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                app.tvo.tv.data.Suggestions.all.groupBy { it.category }.forEach { (cat, list) ->
                    item { Text(cat, fontSize = 14.sp, color = TvoColors.Cyan, modifier = Modifier.padding(top = 6.dp)) }
                    items(list, key = { it.url }) { sug ->
                        val installed = Tvo.store.addons.value.any { a -> a.url == sug.url.substringBefore("/manifest.json") || a.url == sug.url }
                        TvoButton(if (installed) "✓ ${sug.name}" else sug.name, { if (!installed) install(sug.url) }, Modifier.fillMaxWidth())
                        Text(sug.desc, fontSize = 12.sp, color = TvoColors.Muted)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { firstKey.requestFocus() } }
}


@Composable
private fun SyncBlock() {
    val status by app.tvo.tv.data.Sync.status.collectAsState()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf<String?>(null) }
    var entering by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    val padFr = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Continuer à regarder, Ma liste, épisodes vus, profils et add-ons sont partagés automatiquement entre tes appareils reliés.",
            fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.width(760.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvoButton("Synchroniser maintenant", { scope.launch { app.tvo.tv.data.Sync.syncNow() } }, icon = Icons.Default.Refresh)
            TvoButton("Afficher un code", {
                entering = false; msg = null
                scope.launch { code = app.tvo.tv.data.Sync.createCode(); if (code == null) msg = "Pas de connexion." }
            })
            TvoButton("J'ai un code", { code = null; entering = true; typed = ""; msg = null })
            if (status.isNotBlank()) Text(status, fontSize = 13.sp, color = TvoColors.Cyan)
        }
        code?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(it.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
                Text("Sur le téléphone : Réglages › Synchronisation › J'ai un code.\nValable 10 minutes.", fontSize = 14.sp, color = TvoColors.Muted)
            }
        }
        if (entering) {
            Text(if (typed.isEmpty()) "Tape le code affiché sur l'autre appareil" else typed.chunked(3).joinToString(" "),
                fontSize = if (typed.isEmpty()) 15.sp else 32.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            DigitPad({ d -> if (typed.length < 6) typed += d }, { typed = typed.dropLast(1) }, first = padFr)
            TvoButton("Relier", {
                if (typed.length == 6) scope.launch {
                    msg = "Liaison…"
                    msg = if (app.tvo.tv.data.Sync.claim(typed)) { entering = false; "Appareils reliés ✓" } else "Code invalide ou expiré."
                }
            }, primary = true)
            LaunchedEffect(Unit) { delay(80); runCatching { padFr.requestFocus() } }
        }
        msg?.let { Text(it, fontSize = 15.sp, color = TvoColors.Cyan) }
    }
}


@Composable
private fun TmdbBlock(nav: Nav) {
    val key by Tvo.store.tmdbKey.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (key == null) "Ajoute ta clé API TMDB (gratuite sur themoviedb.org) pour avoir les catalogues, titres, résumés, affiches et épisodes en français. Elle passe aussi sur ton téléphone grâce à la synchro."
            else "Clé TMDB active (…${key!!.takeLast(4)}). Catalogues et fiches en français.",
            fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.width(760.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvoButton(if (key == null) "Ajouter la clé TMDB" else "Changer la clé", nav.tmdbKey, primary = key == null)
            if (key != null) TvoButton("Retirer", { Tvo.store.setTmdbKey(null); Tvo.client.clearCaches() }, icon = Icons.Default.Delete)
        }
    }
}

@Composable
fun TmdbKeyScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }
    val server = remember { RemoteInputServer { v -> text = v } }
    var port by remember { mutableStateOf<Int?>(null) }
    val ip = remember { RemoteInputServer.localIp() }
    DisposableEffect(Unit) { port = server.start(); onDispose { server.stop() } }
    val phoneUrl = if (ip != null && port != null) "http://$ip:$port" else null
    val qr = remember(phoneUrl) { phoneUrl?.let { RemoteInputServer.qr(it).asImageBitmap() } }

    fun save() {
        if (busy || text.isBlank()) return
        busy = true; msg = "Vérification de la clé…"
        scope.launch {
            val err = app.tvo.tv.data.Tmdb.test(text)
            if (err == null) { Tvo.store.setTmdbKey(text); Tvo.client.clearCaches(); onDone() } else msg = err
            busy = false
        }
    }

    Row(Modifier.fillMaxSize().background(TvoColors.Bg).padding(48.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Clé API TMDB", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Text("Sur themoviedb.org : Paramètres › API. La « clé d'API » ou le « jeton d'accès en lecture » fonctionnent.",
                fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TvoColors.Surface).padding(14.dp)) {
                Text(text.ifEmpty { "Colle ou tape ta clé" }, fontSize = 16.sp, color = if (text.isEmpty()) TvoColors.Muted else TvoColors.Text, maxLines = 3)
            }
            TvKeyboard(onKey = { text += it }, onBackspace = { text = text.dropLast(1) }, onClear = { text = "" }, url = true, firstKey = firstKey)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvoButton("Enregistrer", { save() }, primary = true, icon = Icons.Default.Add)
                TvoButton("Annuler", onDone)
            }
            msg?.let { Text(it, Modifier.padding(top = 12.dp), fontSize = 15.sp, color = TvoColors.Cyan) }
        }
        Column(Modifier.width(340.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Plus simple : depuis ton téléphone", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Text)
            if (qr != null && phoneUrl != null) {
                Image(qr, "QR code", Modifier.padding(vertical = 14.dp).size(220.dp).clip(RoundedCornerShape(12.dp)))
                Text("Scanne ce code (même Wi-Fi) ou ouvre :", fontSize = 14.sp, color = TvoColors.Muted)
                Text(phoneUrl, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TvoColors.Cyan)
            }
            Text("Ou ajoute la clé dans l'appli du téléphone : elle arrive ici par la synchro.", fontSize = 14.sp,
                color = TvoColors.Muted, modifier = Modifier.padding(top = 14.dp))
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { firstKey.requestFocus() } }
}


@Composable
private fun HomeEditor() {
    val store = Tvo.store
    val raw by store.addons.collectAsState()
    val key by store.tmdbKey.collectAsState()
    val home by store.home.collectAsState()
    val all = remember(raw, key) { buildRows(app.tvo.tv.data.Tmdb.withTmdb(raw, key), null) }
    val special = listOf("continue" to "Continuer à regarder", "library" to "Ma liste", "genres" to "Explorer par catégorie")
    val rows = remember(all, home) {
        val ordered = all.sortedBy { r -> home.order.indexOf(r.key).let { if (it < 0) Int.MAX_VALUE else it } }
        special + ordered.map { it.key to it.title }
    }
    fun move(k: String, d: Int) {
        val keys = rows.drop(special.size).map { it.first }.toMutableList()
        val i = keys.indexOf(k); val j = i + d
        if (i < 0 || j !in keys.indices) return
        keys.add(j, keys.removeAt(i)); store.setHome(home.copy(order = keys))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Choisis les rangées affichées sur l'accueil et leur ordre. Chaque catalogue d'add-on peut devenir une rangée.",
            fontSize = 14.sp, color = TvoColors.Muted, modifier = Modifier.width(760.dp))
        rows.forEachIndexed { idx, (k, title) ->
            val visible = k !in home.hidden
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TvoColors.Surface).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, Modifier.weight(1f), fontSize = 15.sp, color = if (visible) TvoColors.Text else TvoColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Chip(if (visible) "Affichée" else "Masquée", visible, {
                    store.setHome(home.copy(hidden = if (visible) home.hidden + k else home.hidden - k))
                })
                if (idx >= special.size) {
                    TvoButton("▲", { move(k, -1) })
                    TvoButton("▼", { move(k, 1) })
                }
            }
        }
    }
}
