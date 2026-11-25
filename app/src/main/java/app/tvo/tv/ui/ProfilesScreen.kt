package app.tvo.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.tvo.tv.R
import app.tvo.tv.Tvo
import app.tvo.tv.data.Defaults
import app.tvo.tv.data.Profile
import kotlinx.coroutines.delay

@Composable
fun ProfilesScreen(onSelected: () -> Unit, onEdit: (String) -> Unit) {
    val store = Tvo.store
    val profiles by store.profiles.collectAsState()
    var manage by rememberSaveable { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    LaunchedEffect(profiles.isEmpty()) { if (profiles.isEmpty()) onEdit("new") }
    val ctxP = androidx.compose.ui.platform.LocalContext.current
    var pinFor by remember { mutableStateOf<Pair<app.tvo.tv.data.Profile, Boolean>?>(null) }
    var manageAuth by remember { mutableStateOf<app.tvo.tv.data.Profile?>(null) }
    manageAuth?.let { adm ->
        PinDialog("Code administrateur (${adm.name})", onCancel = { manageAuth = null }) { code ->
            if (pinHash(code) == adm.pin) { manageAuth = null; manage = true; true } else false
        }
        return
    }
    pinFor?.let { (p, forEdit) ->
        PinDialog("Code de ${p.name}", onCancel = { pinFor = null }) { code ->
            if (pinHash(code) == p.pin) { pinFor = null; if (forEdit) onEdit(p.id) else { store.selectProfile(p); onSelected() } ; true } else false
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1F1F1F), TvoColors.Bg), radius = 1400f))) {
        Image(painterResource(R.drawable.logo_tvo), "TVO", Modifier.padding(start = 48.dp, top = 32.dp).height(40.dp))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (manage) "Gérer les profils" else "Qui regarde ?", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                profiles.forEachIndexed { i, p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ProfileTile(p, manage, Modifier.let { if (i == 0) it.focusRequester(first) else it }) {
                        val blocked = !store.isAdmin(p) && (p.disabled || (p.trialUntil != null && System.currentTimeMillis() > p.trialUntil))
                        if (blocked && !manage) android.widget.Toast.makeText(ctxP, if (p.disabled) "Ce profil est désactivé par l'administrateur." else "La période d'essai de ce profil est terminée.", android.widget.Toast.LENGTH_LONG).show()
                        else if (p.pin != null) pinFor = p to manage
                        else if (manage) onEdit(p.id) else { store.selectProfile(p); onSelected() }
                    }
                    if (manage) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (i > 0) TvoButton("◀", { store.moveProfile(p.id, -1) })
                        if (i < profiles.size - 1) TvoButton("▶", { store.moveProfile(p.id, 1) })
                    }
                    }
                }
                if (profiles.size < 6) AddTile { onEdit("new") }
            }
            Spacer(Modifier.height(44.dp))
            TvoButton(if (manage) "Terminé" else "Gérer les profils", {
                // Gérer / réordonner : réservé à l'administrateur (son code s'il en a un)
                val adm = profiles.firstOrNull { store.isAdmin(it) }
                if (!manage && adm?.pin != null) manageAuth = adm else manage = !manage
            },
                icon = if (manage) Icons.Default.Check else Icons.Default.Edit)
        }
    }
    LaunchedEffect(profiles.size) { delay(80); runCatching { first.requestFocus() } }
}

@Composable
private fun ProfileTile(p: Profile, manage: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(132.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(30.dp)),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(4.dp, Color.White), shape = RoundedCornerShape(30.dp))),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        ) {
            Avatar(p.name, p.color, 132.dp, icon = p.icon)
            if (manage) Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Edit, "Modifier", Modifier.size(40.dp), tint = Color.White)
            }
        }
        Text(p.name, Modifier.padding(top = 12.dp), fontSize = 18.sp, color = TvoColors.Muted)
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(132.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(30.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            colors = ClickableSurfaceDefaults.colors(containerColor = TvoColors.Surface2, contentColor = TvoColors.Muted,
                focusedContainerColor = Color.White, focusedContentColor = Color.Black),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Ajouter", Modifier.size(56.dp)) }
        }
        Text("Ajouter", Modifier.padding(top = 12.dp), fontSize = 18.sp, color = TvoColors.Muted)
    }
}

@Composable
fun ProfileEditScreen(id: String, onDone: () -> Unit) {
    val store = Tvo.store
    val existing = store.profiles.value.firstOrNull { it.id == id }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var color by rememberSaveable { mutableLongStateOf(existing?.color ?: Defaults.profileColors[store.profiles.value.size % Defaults.profileColors.size]) }
    androidx.compose.runtime.LaunchedEffect(color) { TvoColors.setAccent(color) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { TvoColors.setAccent(store.currentProfile.value?.color) } }
    val firstKey = remember { FocusRequester() }

    Row(Modifier.fillMaxSize().background(TvoColors.Bg).padding(56.dp), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        Column(Modifier.width(380.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (existing == null) "Nouveau profil" else "Modifier le profil", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Spacer(Modifier.height(28.dp))
            Avatar(name.ifBlank { "?" }, color, 140.dp)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(320.dp).clip(RoundedCornerShape(12.dp)).background(TvoColors.Surface).padding(14.dp)) {
                Text(name.ifEmpty { "Prénom" }, fontSize = 22.sp, color = if (name.isEmpty()) TvoColors.Muted else TvoColors.Text)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Defaults.profileColors.forEach { c ->
                    Surface(
                        onClick = { color = c },
                        modifier = Modifier.size(34.dp),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(c), focusedContainerColor = Color(c)),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(BorderStroke(if (c == color) 3.dp else 0.dp, Color.White), shape = RoundedCornerShape(50)),
                            focusedBorder = Border(BorderStroke(3.dp, TvoColors.Cyan), shape = RoundedCornerShape(50))),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
                    ) {}
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvoButton("Enregistrer", {
                    if (name.isNotBlank()) { store.upsertProfile(existing?.id, name, color); onDone() }
                }, primary = true, icon = Icons.Default.Check)
                if (existing != null) TvoButton("Supprimer", { store.deleteProfile(existing.id); onDone() }, icon = Icons.Default.Delete)
            }
        }
        Column {
            Text("Tape le prénom", fontSize = 16.sp, color = TvoColors.Muted, modifier = Modifier.padding(bottom = 12.dp))
            TvKeyboard(
                onKey = { if (name.length < 16) name = if (name.isEmpty()) it.uppercase() else name + it },
                onBackspace = { name = name.dropLast(1) },
                onClear = { name = "" },
                firstKey = firstKey,
            )
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { firstKey.requestFocus() } }
}


/** Même empreinte que sur le téléphone (SHA-256 de "tvo-pin:" + code). */
fun pinHash(pin: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest("tvo-pin:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

@Composable
private fun PinDialog(title: String, onCancel: () -> Unit, onSubmit: (String) -> Boolean) {
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(TvoColors.Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TvoColors.Text)
            Text("●".repeat(typed.length) + "○".repeat(4 - typed.length), fontSize = 34.sp, color = TvoColors.Text)
            if (wrong) Text("Code incorrect", fontSize = 15.sp, color = Color(0xFFFF6B6B))
            DigitPad({ d ->
                if (typed.length < 4) typed += d
                if (typed.length == 4) { if (!onSubmit(typed)) { wrong = true; typed = "" } }
            }, { typed = typed.dropLast(1) }, first = fr)
            TvoButton("Annuler", onCancel)
        }
    }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(100); runCatching { fr.requestFocus() } }
}
