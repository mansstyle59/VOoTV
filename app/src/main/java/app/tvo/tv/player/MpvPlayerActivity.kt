package app.tvo.tv.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Text
import app.tvo.tv.Tvo
import app.tvo.tv.data.PlayRequest
import app.tvo.tv.data.Progress
import app.tvo.tv.data.Sync
import app.tvo.tv.ui.Chip
import app.tvo.tv.ui.TvoButton
import app.tvo.tv.ui.TvoColors
import app.tvo.tv.ui.TvoTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.jdtech.mpv.MPVLib

/**
 * Lecteur MPV intégré (libmpv) : moteur de lecture très complet (MKV, HEVC 10 bits, DTS/TrueHD, sous-titres ASS…),
 * sans ouvrir d'autre appli. Nécessite Android 8 ou plus.
 */
class MpvPlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA = "req"
        fun start(ctx: Context, req: PlayRequest) {
            ctx.startActivity(Intent(ctx, MpvPlayerActivity::class.java).putExtra(EXTRA, Json.encodeToString(req)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private var surfaceView: SurfaceView? = null
    private var mpvReady = false
    private data class Track(val id: String, val type: String, val name: String, val selected: Boolean)
    private lateinit var overlayView: ComposeView
    private lateinit var req: PlayRequest
    private var saveJob: Job? = null

    private var uiVisible by mutableStateOf(true)
    private var options by mutableStateOf(false)
    private var episodes by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var position by mutableLongStateOf(0L)
    private var length by mutableLongStateOf(0L)
    private var playing by mutableStateOf(true)
    private var flash by mutableStateOf<String?>(null)
    private var flashId by mutableIntStateOf(0)
    private var scaleIdx by mutableIntStateOf(0)
    private var rate by mutableStateOf(1f)
    private var startApplied = false
    private var lastInput = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tvo.init(application)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        req = Json.decodeFromString(intent.getStringExtra(EXTRA) ?: run { finish(); return })

        try { MPVLib.create(applicationContext) } catch (e: Throwable) {
            // Bibliothèque MPV incompatible avec ce boîtier : lecteur VLC à la place, et on ne réessaie plus MPV
            MpvGuard.markBroken(applicationContext)
            VlcPlayerActivity.start(this, req)
            finish(); return
        }
        // Réglages repris de Nuvio : sans « opengl-es », l'image ne s'affiche pas sur beaucoup de boîtiers
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("audio-set-media-role", "yes")
        MPVLib.setOptionString("sub-codepage", "auto:utf-8")
        MPVLib.setOptionString("sub-use-margins", "yes")
        MPVLib.setOptionString("hwdec", "mediacodec-copy,no")
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("demuxer-max-bytes", "64MiB")
        MPVLib.setOptionString("demuxer-max-back-bytes", "64MiB")
        MPVLib.setOptionString("demuxer-readahead-secs", "20")
        MPVLib.setOptionString("network-timeout", "30")
        MPVLib.setOptionString("slang", "fr,fre,fra,en")
        MPVLib.setOptionString("alang", "fr,fre,fra,en")
        MPVLib.setOptionString("sub-font-size", "46")
        MPVLib.setOptionString("keep-open", "no")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("idle", "once")
        req.headers["User-Agent"]?.let { MPVLib.setOptionString("user-agent", it) }
        req.headers.filterKeys { it != "User-Agent" }.takeIf { it.isNotEmpty() }?.let { h ->
            MPVLib.setOptionString("http-header-fields", h.entries.joinToString(",") { "${it.key}: ${it.value.replace(",", "\\,")}" })
        }
        if (req.startPosition > 0) MPVLib.setOptionString("start", (req.startPosition / 1000).toString())
        MPVLib.init()
        mpvReady = true
        MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.addObserver(observer)

        val sv = SurfaceView(this).also { surfaceView = it }
        sv.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                MPVLib.attachSurface(holder.surface)
                MPVLib.setOptionString("force-window", "yes")
                if (!loaded) {
                    loaded = true
                    MPVLib.command(arrayOf("loadfile", req.url))
                } else MPVLib.setPropertyString("vo", "gpu")
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
                MPVLib.setPropertyString("android-surface-size", "${w}x$h")
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                MPVLib.setPropertyString("vo", "null")
                MPVLib.setOptionString("force-window", "no")
                MPVLib.detachSurface()
            }
        })
        overlayView = ComposeView(this).apply { setContent { TvoTheme { Overlay() } } }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(sv as android.view.View, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlayView as android.view.View, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })

        saveJob = lifecycleScope.launch {
            while (isActive) {
                delay(5000)
                save()
                if (System.currentTimeMillis() - lastInput > 5000 && playing && !options && !episodes) uiVisible = false
            }
        }
    }

    private var loaded = false
    private var subsAdded = false

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {}
        override fun eventProperty(property: String, value: Long) {}
        override fun eventProperty(property: String, value: String) {}
        override fun eventProperty(property: String, value: Boolean) {
            if (property == "pause") runOnUiThread { playing = !value }
        }
        override fun eventProperty(property: String, value: Double) {
            runOnUiThread {
                when (property) {
                    "time-pos" -> position = (value * 1000).toLong()
                    "duration" -> {
                        length = (value * 1000).toLong()
                        runCatching { MPVLib.getPropertyDouble("container-fps")?.let { f -> runOnUiThread { Afr.apply(this@MpvPlayerActivity, f.toFloat()) } } }
                    }
                }
            }
        }
        override fun event(eventId: Int) {
            when (eventId) {
                MPVLib.MPV_EVENT_FILE_LOADED -> runOnUiThread {
                    playing = true
                    if (!subsAdded) { subsAdded = true
                        req.subtitles.take(20).forEach { s -> runCatching { MPVLib.command(arrayOf("sub-add", s.url, "auto", s.lang ?: "Sous-titres")) } }
                        MPVLib.setPropertyString("sid", "no")
                    }
                }
                MPVLib.MPV_EVENT_END_FILE -> runOnUiThread {
                    if (length > 0 && position >= length - 15_000) { save(finished = true); NextEpisode.playOrFinish(this@MpvPlayerActivity, req) }
                    else if (position < 1000) { Tvo.store.clearLastStream(req.videoId); error = "Cette source ne peut pas être lue avec MPV." }
                }
            }
        }
    }

    private fun tracks(type: String): List<Track> = runCatching {
        val n = MPVLib.getPropertyInt("track-list/count") ?: 0
        (0 until n).mapNotNull { i ->
            if (MPVLib.getPropertyString("track-list/$i/type") != type) return@mapNotNull null
            val id = MPVLib.getPropertyInt("track-list/$i/id")?.toString() ?: return@mapNotNull null
            val title = MPVLib.getPropertyString("track-list/$i/title")
            val lang = MPVLib.getPropertyString("track-list/$i/lang")
            val codec = MPVLib.getPropertyString("track-list/$i/codec")
            val label = listOfNotNull(lang?.let { app.tvo.tv.ui.langLabel(it) }, title, codec?.uppercase()).distinct().joinToString(" · ").ifBlank { "Piste $id" }
            Track(id, type, label, MPVLib.getPropertyBoolean("track-list/$i/selected") == true)
        }
    }.getOrDefault(emptyList())

    private fun save(finished: Boolean = false) {
        val len = length
        if (len <= 0) return
        Tvo.store.saveProgress(req.profileId, Progress(req.videoId, req.metaId, req.type, req.title, req.poster, req.background,
            req.season, req.episode, req.episodeTitle, if (finished) len else position, len, System.currentTimeMillis()))
    }

    private fun seekBy(ms: Long) {
        if (length <= 0) return
        MPVLib.command(arrayOf("seek", (ms / 1000).toString(), "relative"))
        flash = (if (ms > 0) "+" else "−") + "${kotlin.math.abs(ms) / 1000} s"; flashId++
    }

    private val scales = listOf(
        "Ajuster" to 0,
        "Remplir l'écran" to 1,
        "Étirer" to 2,
    )

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastInput = System.currentTimeMillis()
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                // Retour : 1er appui = cacher les boutons, 2e appui = quitter
                when { episodes -> { episodes = false; return true }; options -> { options = false; return true }; uiVisible && error == null -> { uiVisible = false; return true }; else -> { save(); app.tvo.tv.data.Sync.syncInBackground(); finish(); return true } }
            }
            if (!options && !episodes && error == null) {
                val step = if (event.repeatCount > 2) 30_000L else 10_000L
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-step); uiVisible = true; return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(step); uiVisible = true; return true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        MPVLib.setPropertyBoolean("pause", playing); uiVisible = true; return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { options = true; uiVisible = true; overlayView.requestFocus(); return true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { uiVisible = !uiVisible; return true }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun fmt(ms: Long): String {
        val t = (ms / 1000).coerceAtLeast(0); val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    @Composable
    private fun Overlay() {
        Box(Modifier.fillMaxSize()) {
            if (uiVisible && error == null) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xB3000000), 0.25f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color(0xD9000000))))
                Column(Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 28.dp)) {
                    Text(req.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    req.subtitle?.let { Text(it, fontSize = 15.sp, color = TvoColors.Muted) }
                    Text("Lecteur MPV intégré", fontSize = 12.sp, color = TvoColors.Cyan)
                }
                // Heure actuelle et heure de fin, en haut à droite
                Column(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 48.dp), horizontalAlignment = Alignment.End) {
                    val now = System.currentTimeMillis() + 0 * position
                    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)
                    Text(fmt.format(java.util.Date(now)), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (length > 0) Text("Fin à ${fmt.format(java.util.Date(now + ((length - position) / rate).toLong()))}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Cyan)
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 48.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (length > 0) {
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x55FFFFFF))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth((position.toFloat() / length).coerceIn(0f, 1f)).background(TvoColors.Blue))
                        }
                        Row { Text(fmt(position), fontSize = 14.sp, color = Color.White); Spacer(Modifier.weight(1f)); Text("−" + fmt(length - position), fontSize = 14.sp, color = Color.White) }
                    } else Text("● En direct", fontSize = 14.sp, color = Color(0xFFFF5A5A))
                    Text((if (playing) "OK : pause" else "OK : lecture") + "   ◀ ▶ : ±10 s   ▲ : audio, sous-titres, format, vitesse", fontSize = 12.sp, color = Color(0xB3FFFFFF))
                }
            }
            flash?.let { f ->
                LaunchedEffect(flashId) { delay(900); flash = null }
                Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Color(0xAA000000)).padding(horizontal = 26.dp, vertical = 14.dp)) {
                    Text(f, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            if (options) Options()
            if (episodes) TvEpisodesPanel(req, onClose = { episodes = false; overlayView.requestFocus() }) { ep, _ ->
                episodes = false; save(); NextEpisode.play(this@MpvPlayerActivity, req, ep)
            }
            error?.let { msg ->
                val fr = remember { FocusRequester() }
                Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                    Column(Modifier.width(520.dp).clip(RoundedCornerShape(16.dp)).background(TvoColors.Surface).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Lecture impossible", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(msg, fontSize = 16.sp, color = TvoColors.Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (true) TvoButton("Essayer avec VLC", { VlcPlayerActivity.start(this@MpvPlayerActivity, req.copy(startPosition = maxOf(position, req.startPosition))); finish() }, Modifier.focusRequester(fr), primary = true)
                            TvoButton("Retour", { finish() }, if (true) Modifier else Modifier.focusRequester(fr))
                        }
                    }
                }
                LaunchedEffect(msg) { delay(100); runCatching { fr.requestFocus() } }
            }
        }
    }

    @Composable
    private fun Options() {
        val fr = remember { FocusRequester() }
        var v by remember { mutableIntStateOf(0) }
        val audio = remember(v) { tracks("audio") }
        val subs = remember(v) { listOf(Track("no", "sub", "Désactivés", MPVLib.getPropertyString("sid") == "no")) + tracks("sub") }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.45f to Color.Transparent, 1f to Color(0xEE000000)))) {
            LazyColumn(Modifier.align(Alignment.CenterEnd).width(520.dp).fillMaxHeight().padding(end = 40.dp, top = 40.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Options de lecture", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                if (req.type == "series") item { TvoButton("Épisodes", { options = false; episodes = true }, primary = true) }
                item { Text("Audio", fontSize = 15.sp, color = TvoColors.Cyan) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(audio.size) { i -> val t = audio[i]
                            Chip(t.name, t.selected, { MPVLib.setPropertyString("aid", t.id); v++ }, if (i == 0) Modifier.focusRequester(fr) else Modifier) }
                    }
                }
                item { Text("Sous-titres", fontSize = 15.sp, color = TvoColors.Cyan) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subs.size) { i -> val t = subs[i]
                            Chip(t.name, t.selected || (t.id == "no" && subs.drop(1).none { it.selected }), { MPVLib.setPropertyString("sid", t.id); v++ }) }
                        if (subs.isEmpty()) item { Text("Aucun", color = TvoColors.Muted, fontSize = 14.sp) }
                    }
                }
                item { Text("Décalage des sous-titres", fontSize = 15.sp, color = TvoColors.Cyan) }
                item {
                    fun shift(d: Double) { val cur = runCatching { MPVLib.getPropertyDouble("sub-delay") ?: 0.0 }.getOrDefault(0.0); MPVLib.setPropertyDouble("sub-delay", if (d == 0.0) 0.0 else cur + d); v++ }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Chip("− 0,5 s", false, { shift(-0.5) }); Chip("− 0,1 s", false, { shift(-0.1) })
                        Text(if (v >= 0) "%+.1f s".format(java.util.Locale.FRANCE, runCatching { MPVLib.getPropertyDouble("sub-delay") ?: 0.0 }.getOrDefault(0.0)) else "", fontSize = 16.sp, color = Color.White)
                        Chip("+ 0,1 s", false, { shift(0.1) }); Chip("+ 0,5 s", false, { shift(0.5) }); Chip("Réinitialiser", false, { shift(0.0) })
                    }
                }
                item { Text("Taille des sous-titres", fontSize = 15.sp, color = TvoColors.Cyan) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Petite" to 0.8, "Normale" to 1.0, "Grande" to 1.25, "Très grande" to 1.5).forEach { (l, k) -> Chip(l, false, { MPVLib.setPropertyDouble("sub-scale", k); v++ }) } } }
                item { Text("Format de l'image", fontSize = 15.sp, color = TvoColors.Cyan) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { scales.forEachIndexed { i, (l, sc) -> Chip(l, scaleIdx == i, { scaleIdx = i
                    MPVLib.setPropertyDouble("panscan", if (sc == 1) 1.0 else 0.0)
                    MPVLib.setPropertyString("keepaspect", if (sc == 2) "no" else "yes") }) } } }
                item { Text("Vitesse", fontSize = 15.sp, color = TvoColors.Cyan) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { r -> Chip(if (r == 1f) "Normale" else "x${r.toString().removeSuffix(".0")}", rate == r, { rate = r; MPVLib.setPropertyDouble("speed", r.toDouble()) }) } } }
                item { Spacer(Modifier.height(8.dp)); TvoButton("Fermer", { options = false }, primary = true) }
            }
        }
        LaunchedEffect(Unit) { delay(150); runCatching { fr.requestFocus() } }
    }

    override fun onResume() { super.onResume(); app.tvo.tv.data.Sync.playbackActive = true }

    override fun onPause() { super.onPause(); Sync.playbackActive = false; save(); if (mpvReady) MPVLib.setPropertyBoolean("pause", true); Sync.syncInBackground() }

    override fun onDestroy() {
        Afr.reset(this)
        saveJob?.cancel()
        runCatching { MPVLib.removeObserver(observer); MPVLib.destroy() }
        super.onDestroy()
    }
}
