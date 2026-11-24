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
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Lecteur VLC intégré : lit ce qu'ExoPlayer ne sait pas lire (MKV avec DTS/TrueHD, RTSP de la Freebox, AVI, flux exotiques),
 * sans ouvrir d'autre appli.
 */
class VlcPlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA = "req"
        fun start(ctx: Context, req: PlayRequest) {
            ctx.startActivity(Intent(ctx, VlcPlayerActivity::class.java).putExtra(EXTRA, Json.encodeToString(req)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private lateinit var libVlc: LibVLC
    private lateinit var mp: MediaPlayer
    private lateinit var overlayView: ComposeView
    private lateinit var req: PlayRequest
    private var saveJob: Job? = null

    private var uiVisible by mutableStateOf(true)
    private var options by mutableStateOf(false)
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

        libVlc = LibVLC(this, arrayListOf("--network-caching=2000", "--http-reconnect", "--audio-time-stretch", "--avcodec-hw=any"))
        mp = MediaPlayer(libVlc)
        val vlcLayout = VLCVideoLayout(this)
        overlayView = ComposeView(this).apply { setContent { TvoTheme { Overlay() } } }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(vlcLayout as android.view.View, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlayView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        })
        mp.attachViews(vlcLayout, null, true, false)

        val media = Media(libVlc, Uri.parse(req.url)).apply {
            setHWDecoderEnabled(true, false)
            req.headers["User-Agent"]?.let { addOption(":http-user-agent=$it") }
            req.headers["Referer"]?.let { addOption(":http-referrer=$it") }
        }
        mp.media = media
        media.release()
        req.subtitles.take(20).forEach { s -> runCatching { mp.addSlave(org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle, Uri.parse(s.url), false) } }

        mp.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> {
                    playing = true
                    if (!startApplied && req.startPosition > 0) { startApplied = true; mp.time = req.startPosition }
                }
                MediaPlayer.Event.Paused -> playing = false
                MediaPlayer.Event.LengthChanged -> length = mp.length
                MediaPlayer.Event.TimeChanged -> position = mp.time
                MediaPlayer.Event.EndReached -> { save(finished = true); finish() }
                MediaPlayer.Event.EncounteredError -> error = "Cette source ne peut pas être lue, même avec VLC. Essaie une autre source."
            }
        }
        mp.play()

        saveJob = lifecycleScope.launch {
            while (isActive) {
                delay(5000)
                save()
                if (System.currentTimeMillis() - lastInput > 5000 && playing && !options) uiVisible = false
            }
        }
    }

    private fun save(finished: Boolean = false) {
        val len = mp.length
        if (len <= 0) return
        Tvo.store.saveProgress(req.profileId, Progress(req.videoId, req.metaId, req.type, req.title, req.poster, req.background,
            req.season, req.episode, req.episodeTitle, if (finished) len else mp.time, len, System.currentTimeMillis()))
    }

    private fun seekBy(ms: Long) {
        if (mp.length <= 0) return
        mp.time = (mp.time + ms).coerceIn(0, mp.length - 1000)
        flash = (if (ms > 0) "+" else "−") + "${kotlin.math.abs(ms) / 1000} s"; flashId++
    }

    private val scales = listOf(
        "Ajuster" to MediaPlayer.ScaleType.SURFACE_BEST_FIT,
        "Remplir l'écran" to MediaPlayer.ScaleType.SURFACE_FIT_SCREEN,
        "Étirer" to MediaPlayer.ScaleType.SURFACE_FILL,
    )

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastInput = System.currentTimeMillis()
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                when { options -> { options = false; return true }; else -> { save(); finish(); return true } }
            }
            if (!options && error == null) {
                val step = if (event.repeatCount > 2) 30_000L else 10_000L
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-step); uiVisible = true; return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(step); uiVisible = true; return true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (mp.isPlaying) mp.pause() else mp.play(); uiVisible = true; return true
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
                    Text("Lecteur VLC intégré", fontSize = 12.sp, color = TvoColors.Cyan)
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
            error?.let { msg ->
                val fr = remember { FocusRequester() }
                Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                    Column(Modifier.width(520.dp).clip(RoundedCornerShape(16.dp)).background(TvoColors.Surface).padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Lecture impossible", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(msg, fontSize = 16.sp, color = TvoColors.Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (android.os.Build.VERSION.SDK_INT >= 26) TvoButton("Essayer avec MPV", { MpvPlayerActivity.start(this@VlcPlayerActivity, req.copy(startPosition = maxOf(position, req.startPosition))); finish() }, Modifier.focusRequester(fr), primary = true)
                            TvoButton("Retour", { finish() }, if (android.os.Build.VERSION.SDK_INT >= 26) Modifier else Modifier.focusRequester(fr))
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
        val audio = remember(v) { mp.audioTracks?.toList() ?: emptyList() }
        val subs = remember(v) { mp.spuTracks?.toList() ?: emptyList() }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.45f to Color.Transparent, 1f to Color(0xEE000000)))) {
            LazyColumn(Modifier.align(Alignment.CenterEnd).width(520.dp).fillMaxHeight().padding(end = 40.dp, top = 40.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Options de lecture", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                item { Text("Audio", fontSize = 15.sp, color = TvoColors.Cyan) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(audio.size) { i -> val t = audio[i]
                            Chip(t.name, mp.audioTrack == t.id, { mp.audioTrack = t.id; v++ }, if (i == 0) Modifier.focusRequester(fr) else Modifier) }
                    }
                }
                item { Text("Sous-titres", fontSize = 15.sp, color = TvoColors.Cyan) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subs.size) { i -> val t = subs[i]
                            Chip(if (t.id == -1) "Désactivés" else t.name, mp.spuTrack == t.id, { mp.spuTrack = t.id; v++ }) }
                        if (subs.isEmpty()) item { Text("Aucun", color = TvoColors.Muted, fontSize = 14.sp) }
                    }
                }
                item { Text("Format de l'image", fontSize = 15.sp, color = TvoColors.Cyan) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { scales.forEachIndexed { i, (l, sc) -> Chip(l, scaleIdx == i, { scaleIdx = i; mp.videoScale = sc }) } } }
                item { Text("Vitesse", fontSize = 15.sp, color = TvoColors.Cyan) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { r -> Chip(if (r == 1f) "Normale" else "x${r.toString().removeSuffix(".0")}", rate == r, { rate = r; mp.rate = r }) } } }
                item { Spacer(Modifier.height(8.dp)); TvoButton("Fermer", { options = false }, primary = true) }
            }
        }
        LaunchedEffect(Unit) { delay(150); runCatching { fr.requestFocus() } }
    }

    override fun onPause() { super.onPause(); save(); mp.pause(); Sync.syncInBackground() }

    override fun onDestroy() {
        saveJob?.cancel()
        runCatching { mp.stop(); mp.detachViews(); mp.release(); libVlc.release() }
        super.onDestroy()
    }
}
