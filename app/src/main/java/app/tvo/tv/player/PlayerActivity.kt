package app.tvo.tv.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import app.tvo.tv.Tvo
import app.tvo.tv.data.PlayRequest
import app.tvo.tv.data.Progress
import app.tvo.tv.data.StreamItem
import app.tvo.tv.data.USER_AGENT
import app.tvo.tv.ui.TvoButton
import app.tvo.tv.ui.TvoColors
import app.tvo.tv.ui.TvoTheme
import app.tvo.tv.ui.langLabel
import app.tvo.tv.ui.openExternal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@UnstableApi
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA = "req"
        fun start(ctx: Context, req: PlayRequest) {
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java).putExtra(EXTRA, Json.encodeToString(req)))
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var overlayView: ComposeView
    private lateinit var req: PlayRequest

    // Overlay state
    private var controllerVisible by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var nextUp by mutableStateOf<NextUp?>(null)
    private var nextDismissed = false
    private var progressJob: Job? = null

    data class NextUp(val label: String, val loading: Boolean = false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tvo.init(application)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        req = Json.decodeFromString(intent.getStringExtra(EXTRA) ?: run { finish(); return })

        player = ExoPlayer.Builder(
            this,
            DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
        ).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages("fr", "fre", "fra")
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onPlayerError(e: PlaybackException) {
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "La source ne répond pas. Essaie une autre source."
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Format non pris en charge par cette TV. Essaie le lecteur externe ou une autre source."
                    else -> "Lecture impossible (${e.errorCodeName})."
                }
                showOverlayFocus()
            }
        })

        playerView = PlayerView(this).apply {
            player = this@PlayerActivity.player
            useController = true
            controllerShowTimeoutMs = 4500
            setShowSubtitleButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
            setShowFastForwardButton(true)
            setShowRewindButton(true)
            keepScreenOn = true
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> controllerVisible = v == View.VISIBLE })
            isFocusable = true
            isFocusableInTouchMode = true
        }
        overlayView = ComposeView(this).apply { setContent { TvoTheme { Overlay() } } }
        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(playerView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlayView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)
        playerView.requestFocus()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    nextUp != null -> { nextUp = null; nextDismissed = true; playerView.requestFocus() }
                    playerView.isControllerFullyVisible && error == null -> playerView.hideController()
                    else -> finish()
                }
            }
        })
        load(req)
    }

    private fun load(r: PlayRequest) {
        req = r
        error = null; nextUp = null; nextDismissed = false
        val dsf = OkHttpDataSource.Factory(Tvo.http).setUserAgent(r.headers["User-Agent"] ?: USER_AGENT)
            .setDefaultRequestProperties(r.headers.filterKeys { it != "User-Agent" })
        val subs = r.subtitles.map { s ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                .setMimeType(subtitleMime(s.url))
                .setLanguage(s.lang)
                .setLabel(langLabel(s.lang))
                .setSelectionFlags(0)
                .build()
        }
        val item = MediaItem.Builder()
            .setUri(r.url)
            .setSubtitleConfigurations(subs)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(r.title).build())
            .build()
        player.setMediaSource(DefaultMediaSourceFactory(dsf).createMediaSource(item))
        if (r.startPosition > 0) player.seekTo(r.startPosition)
        player.prepare()
        player.playWhenReady = true
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                delay(5_000)
                saveProgress()
                checkNextUp()
            }
        }
        playerView.requestFocus()
    }

    private fun subtitleMime(url: String): String {
        val u = url.lowercase().substringBefore('?')
        return when {
            u.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            u.endsWith(".ass") || u.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            u.endsWith(".ttml") || u.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun saveProgress(finished: Boolean = false) {
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        val pos = if (finished) dur else player.currentPosition
        Tvo.store.saveProgress(
            req.profileId,
            Progress(req.videoId, req.metaId, req.type, req.title, req.poster, req.background, req.season, req.episode,
                req.episodeTitle, pos, dur, System.currentTimeMillis()),
        )
    }

    // ------------------------------------------------------------ next episode

    private fun checkNextUp() {
        if (req.type != "series" || nextUp != null || nextDismissed) return
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        if (dur - player.currentPosition in 1..30_000) {
            nextUp = NextUp("Épisode suivant")
            showOverlayFocus()
        }
    }

    private fun onEnded() {
        saveProgress(finished = true)
        if (req.type == "series" && !nextDismissed) playNext() else finish()
    }

    private fun showOverlayFocus() {
        overlayView.requestFocus()
    }

    private fun playNext() {
        nextUp = NextUp("Recherche de l'épisode suivant…", loading = true)
        lifecycleScope.launch {
            saveProgress(finished = true)
            val addons = Tvo.store.addons.value
            val meta = Tvo.client.meta(addons, req.type, req.metaId)
            val eps = meta?.episodes ?: emptyList()
            val idx = eps.indexOfFirst { it.id == req.videoId }
            val next = eps.getOrNull(idx + 1)
            if (meta == null || next == null) { finish(); return@launch }
            val all = withTimeoutOrNull(20_000) {
                Tvo.client.streams(addons, req.type, next.id).toList().flatMap { it.items }
            } ?: emptyList()
            val playable = all.filter { it.url != null }
            val pick: StreamItem? = playable.firstOrNull { req.bingeGroup != null && it.bingeGroup == req.bingeGroup }
                ?: playable.filter { it.addonName == req.addonName }.maxByOrNull { it.quality }
                ?: playable.maxByOrNull { it.quality }
            if (pick?.url == null) {
                nextUp = null
                error = "Aucune source trouvée pour l'épisode suivant."
                showOverlayFocus()
                return@launch
            }
            if (Tvo.store.externalPlayer.value) { openExternal(this@PlayerActivity, Uri.parse(pick.url), meta.preview.name); finish(); return@launch }
            val subs = runCatching { Tvo.client.subtitles(addons, req.type, next.id) }.getOrDefault(emptyList())
            load(req.copy(
                url = pick.url, headers = pick.headers,
                subtitles = (pick.subtitles + subs).distinctBy { it.url }.take(40),
                videoId = next.id, season = next.season, episode = next.episode, episodeTitle = next.title,
                subtitle = "S${next.season} : É${next.episode} · ${next.title}",
                startPosition = 0, bingeGroup = pick.bingeGroup, addonName = pick.addonName,
            ))
        }
    }

    // ------------------------------------------------------------ overlayView UI

    @Composable
    private fun Overlay() {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(controllerVisible && error == null, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))).padding(start = 48.dp, top = 28.dp, end = 48.dp, bottom = 40.dp)) {
                    Column {
                        Text(req.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        req.subtitle?.let { Text(it, fontSize = 16.sp, color = TvoColors.Muted) }
                        val remaining = (player.duration - player.currentPosition).takeIf { player.duration > 0 }
                        if (remaining != null) {
                            val end = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(System.currentTimeMillis() + remaining))
                            Text("Se termine à $end", fontSize = 14.sp, color = TvoColors.Cyan, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            nextUp?.let { n -> NextUpCard(n) }
            error?.let { ErrorCard(it) }
        }
    }

    @Composable
    private fun NextUpCard(n: NextUp) {
        val fr = remember { FocusRequester() }
        var count by remember(n) { mutableIntStateOf(15) }
        if (!n.loading) LaunchedEffect(n) {
            while (count > 0) { delay(1000); count-- }
            playNext()
        }
        Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.BottomEnd) {
            Column(Modifier.width(380.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xEE161622)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (n.loading) n.label else "${n.label} dans $count s", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (!n.loading) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvoButton("Lire maintenant", { playNext() }, Modifier.focusRequester(fr), primary = true)
                    TvoButton("Annuler", { nextUp = null; nextDismissed = true; playerView.requestFocus() })
                }
            }
        }
        if (!n.loading) LaunchedEffect(n) { delay(100); runCatching { fr.requestFocus() } }
    }

    @Composable
    private fun ErrorCard(msg: String) {
        val fr = remember { FocusRequester() }
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(520.dp).clip(RoundedCornerShape(16.dp)).background(TvoColors.Surface).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Oups", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(msg, fontSize = 16.sp, color = TvoColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvoButton("Retour", { finish() }, Modifier.focusRequester(fr), primary = true)
                    TvoButton("Lecteur externe", { openExternal(this@PlayerActivity, Uri.parse(req.url), req.title); finish() })
                }
            }
        }
        LaunchedEffect(msg) { delay(100); runCatching { fr.requestFocus() } }
    }

    // ------------------------------------------------------------ lifecycle

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Media keys always go to the player, even when an overlayView has focus.
        if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (playerView.dispatchMediaKeyEvent(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
        player.pause()
        app.tvo.tv.data.Sync.syncInBackground()
    }

    override fun onDestroy() {
        progressJob?.cancel()
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
