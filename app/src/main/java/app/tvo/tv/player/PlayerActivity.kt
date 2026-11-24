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
import androidx.compose.runtime.produceState
import androidx.compose.foundation.lazy.items
import app.tvo.tv.data.MetaDetail
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
import app.tvo.tv.ui.Chip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
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
            // RTSP (Freebox), AVI, WMV, TS… : directement le lecteur VLC intégré
            val engine = app.tvo.tv.Tvo.store.engine.value
            if (engine == "mpv" && android.os.Build.VERSION.SDK_INT >= 26) { MpvPlayerActivity.start(ctx, req); return }
            if (engine == "vlc" || Regex("^rtsp:|\\.(avi|wmv|flv|ts)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(req.url)) {
                VlcPlayerActivity.start(ctx, req); return
            }
            ctx.startActivity(Intent(ctx, PlayerActivity::class.java).putExtra(EXTRA, Json.encodeToString(req)))
        }
    }

    /** Lecteur de secours : MPV (Android 8+), sinon VLC. */
    private fun fallbackPlayer(r: PlayRequest) {
        if (android.os.Build.VERSION.SDK_INT >= 26) MpvPlayerActivity.start(this, r) else VlcPlayerActivity.start(this, r)
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
    private var options by mutableStateOf(false)
    private var audioMenu by mutableStateOf(false)
    private var subsMenu by mutableStateOf(false)
    private var ctrlTick by mutableIntStateOf(0)
    private val anyPanel get() = options || audioMenu || subsMenu || episodesMenu
    private var episodesMenu by mutableStateOf(false)
    private var seekFlash by mutableStateOf<String?>(null)
    private var seekFlashId by mutableIntStateOf(0)
    private var resizeIdx by mutableIntStateOf(0)
    private var speed by mutableStateOf(1f)
    private var tracksVersion by mutableIntStateOf(0)
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
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
        ).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages("fr", "fre", "fra")
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    watchdog.removeCallbacks(stuck)
                    player.videoFormat?.frameRate?.takeIf { it > 0f }?.let { Afr.apply(this@PlayerActivity, it) }
                    val d = player.duration
                    // Lien « de démonstration » (quelques secondes) : la source demande un service payant (débrideur).
                    if (req.type != "tv" && req.type != "channel" && d in 1..45_000) {
                        player.stop(); Tvo.store.clearLastStream(req.videoId)
                        error = "Cette source ne fournit qu'une vidéo d'attente : elle demande un service payant. Choisis une autre source."
                        showOverlayFocus(); return
                    }
                }
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                tracksVersion++
                val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                if (audio.isNotEmpty() && audio.none { g -> (0 until g.length).any { g.isTrackSupported(it) } }) {
                    // Son illisible (DTS, TrueHD…) : MPV intégré le décode (comme Nuvio), sinon VLC.
                    fallbackPlayer(req.copy(startPosition = player.currentPosition))
                    finish()
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                // Format non pris en charge par ExoPlayer : bascule automatique sur MPV intégré (comme Nuvio), au même endroit.
                if (e.errorCode in listOf(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODING_FAILED,
                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED)) {
                    fallbackPlayer(req.copy(startPosition = player.currentPosition.coerceAtLeast(req.startPosition)))
                    finish(); return
                }
                Tvo.store.clearLastStream(req.videoId)
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
            useController = false
            keepScreenOn = true
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
        getSharedPreferences("tvo_ui", MODE_PRIVATE).let { subSize = it.getFloat("sub_size", 1f); subBox = it.getBoolean("sub_box", false); subColor = it.getInt("sub_color", android.graphics.Color.WHITE) }
        applySubStyle()

        // Retour : ferme le menu ouvert, sinon quitte le lecteur (un seul appui, jamais de boucle).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    options -> closeOptions()
                    audioMenu || subsMenu -> closePanels()
                    episodesMenu -> closeEpisodes()
                    nextUp != null -> { nextUp = null; nextDismissed = true; playerView.requestFocus() }
                    // 1er appui : cacher les boutons ; 2e appui (boutons déjà cachés) : quitter
                    controllerVisible && error == null -> hideControls()
                    else -> { saveProgress(); app.tvo.tv.data.Sync.syncInBackground(); finish() }
                }
            }
        })
        load(req)
    }

    private val watchdog = android.os.Handler(android.os.Looper.getMainLooper())
    private val stuck = Runnable {
        if (!isFinishing && player.playbackState != Player.STATE_READY && error == null) {
            player.stop()
            Tvo.store.clearLastStream(req.videoId)
            error = "La source ne répond pas (rien après 25 secondes). Choisis une autre source."
            showOverlayFocus()
        }
    }

    private fun load(r: PlayRequest) {
        req = r
        error = null; nextUp = null; nextDismissed = false
        watchdog.removeCallbacks(stuck); watchdog.postDelayed(stuck, 25_000)
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
            val addons = Tvo.store.allowed(Tvo.store.addons.value)
            val meta = Tvo.client.meta(addons, req.type, req.metaId)
            val eps = meta?.episodes ?: emptyList()
            val idx = eps.indexOfFirst { it.id == req.videoId }
            val next = eps.getOrNull(idx + 1)
            if (meta == null || next == null) { finish(); return@launch }
            playEpisode(next, meta.preview.name)
        }
    }

    /** Lance un épisode choisi (épisode suivant ou menu Épisodes) sans quitter le lecteur. */
    private fun playEpisode(next: app.tvo.tv.data.Video, showName: String) {
        nextUp = NextUp("Recherche de la source…", loading = true)
        lifecycleScope.launch {
            saveProgress(finished = true)
            val addons = Tvo.store.allowed(Tvo.store.addons.value)
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
            if (Tvo.store.externalPlayer.value) { openExternal(this@PlayerActivity, Uri.parse(pick.url), showName); finish(); return@launch }
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
            AnimatedVisibility(controllerVisible && error == null && !anyPanel, enter = fadeIn(), exit = fadeOut()) { Controls() }
            // Heure actuelle et heure de fin (vitesse comprise), en haut à droite, mises à jour chaque seconde
            AnimatedVisibility(controllerVisible && error == null && !anyPanel, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)) {
                var tick by remember { mutableStateOf(0L) }
                LaunchedEffect(Unit) { while (true) { tick = System.currentTimeMillis(); delay(1000) } }
                val fmt = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
                val now = if (tick > 0) tick else System.currentTimeMillis()
                val remaining = (player.duration - player.currentPosition).takeIf { player.duration > 0 }?.let { (it / player.playbackParameters.speed).toLong() }
                Column(Modifier.padding(top = 28.dp, end = 48.dp), horizontalAlignment = Alignment.End) {
                    Text(fmt.format(Date(now)), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (remaining != null) Text("Fin à ${fmt.format(Date(now + remaining))}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Cyan)
                }
            }
            seekFlash?.let { f ->
                LaunchedEffect(seekFlashId) { delay(900); seekFlash = null }
                Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Color(0xAA000000)).padding(horizontal = 26.dp, vertical = 14.dp)) {
                    Text(f, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            if (options) OptionsPanel()
            if (audioMenu) AudioPanel()
            if (subsMenu) SubsPanel()
            if (episodesMenu) EpisodesPanel()
            nextUp?.let { n -> NextUpCard(n) }
            error?.let { ErrorCard(it) }
        }
    }

    // ------------------------------------------------------------ options (audio, sous-titres, format, vitesse)

    private data class TrackChoice(val label: String, val group: androidx.media3.common.TrackGroup?, val index: Int, val selected: Boolean, val lang: String = "")

    private fun trackChoices(type: Int): List<TrackChoice> {
        val out = mutableListOf<TrackChoice>()
        player.currentTracks.groups.filter { it.type == type }.forEach { g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                val lang = f.language?.let { langLabel(it) } ?: f.label?.takeIf { it.isNotBlank() } ?: "Piste ${out.size + 1}"
                val tags = mutableListOf<String>()
                if (type == C.TRACK_TYPE_AUDIO) {
                    when { f.channelCount >= 8 -> tags += "7.1"; f.channelCount >= 6 -> tags += "5.1"; f.channelCount == 2 -> tags += "Stéréo"; f.channelCount == 1 -> tags += "Mono" }
                    codecName(f.sampleMimeType)?.let { tags += it }
                    if (f.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) tags += "Audiodescription"
                } else {
                    if (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) tags += "Forcés"
                    if (f.roleFlags and (C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND or C.ROLE_FLAG_CAPTION) != 0) tags += "Sourds et malentendants"
                    val l = f.label.orEmpty().lowercase()
                    if ("forc" in l && "Forcés" !in tags) tags += "Forcés"
                    if (("sdh" in l || "malentend" in l) && "Sourds et malentendants" !in tags) tags += "Sourds et malentendants"
                    if (f.id?.contains(":") == true || f.sampleMimeType?.contains("vtt") == true) tags += "externe"
                }
                val label = (listOf(lang) + tags).joinToString(" · ")
                out += TrackChoice(label, g.mediaTrackGroup, i, g.isTrackSelected(i), f.language ?: "")
            }
        }
        // Français d'abord, puis anglais, puis le reste
        return out.sortedBy { c -> when (c.lang.lowercase().take(2)) { "fr" -> 0; "en" -> 1; else -> 2 } }
    }

    private fun codecName(m: String?): String? = when {
        m == null -> null
        "eac3" in m || "ec-3" in m -> "Dolby Digital Plus"; "ac3" in m || "ac-3" in m -> "Dolby Digital"
        "truehd" in m -> "Dolby TrueHD"; "dts" in m -> "DTS"; "aac" in m || "mp4a" in m -> "AAC"; "opus" in m -> "Opus"; "mpeg" in m -> "MP3"
        else -> null
    }

    private var subSize by mutableStateOf(1f)
    private var subBox by mutableStateOf(false)
    private var subColor by mutableIntStateOf(android.graphics.Color.WHITE)
    private fun applySubStyle() {
        val sv = playerView.subtitleView ?: return
        sv.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subSize)
        sv.setStyle(androidx.media3.ui.CaptionStyleCompat(subColor,
            if (subBox) 0xB3000000.toInt() else android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            if (subBox) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            android.graphics.Color.BLACK, null))
        getSharedPreferences("tvo_ui", MODE_PRIVATE).edit().putFloat("sub_size", subSize).putBoolean("sub_box", subBox).putInt("sub_color", subColor).apply()
    }

    private fun selectTrack(type: Int, c: TrackChoice?) {
        val b = player.trackSelectionParameters.buildUpon()
        if (c?.group == null) b.setTrackTypeDisabled(type, true)
        else b.setTrackTypeDisabled(type, false).setOverrideForType(androidx.media3.common.TrackSelectionOverride(c.group, c.index))
        player.trackSelectionParameters = b.build()
    }

    private val resizeModes = listOf(
        "Ajuster" to androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
        "Remplir l'écran" to androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        "Étirer" to androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,
    )

    private fun showControls() { controllerVisible = true; ctrlTick++; overlayView.requestFocus() }
    private fun hideControls() { controllerVisible = false; playerView.requestFocus() }
    private fun openOptions() { hideControls(); options = true; overlayView.requestFocus() }
    private fun openAudio() { hideControls(); audioMenu = true; overlayView.requestFocus() }
    private fun openSubs() { hideControls(); subsMenu = true; overlayView.requestFocus() }
    private fun closePanels() { audioMenu = false; subsMenu = false; showControls() }
    private fun openEpisodes() { if (req.type != "series") return; hideControls(); episodesMenu = true; options = false; overlayView.requestFocus() }
    private fun closeEpisodes() { episodesMenu = false; showControls() }

    /** Menu Épisodes : saisons + liste de la saison, pour passer d'un épisode à l'autre pendant une série. */
    @Composable
    private fun EpisodesPanel() {
        val meta by produceState<app.tvo.tv.data.MetaDetail?>(null, req.metaId) {
            value = runCatching { Tvo.client.meta(Tvo.store.allowed(Tvo.store.addons.value), req.type, req.metaId) }.getOrNull()
        }
        val all = meta?.episodes ?: emptyList()
        val seasons = remember(all) { all.mapNotNull { it.season }.distinct() }
        var season by remember(seasons, req.season) { mutableStateOf(req.season ?: seasons.firstOrNull()) }
        val fr = remember { FocusRequester() }
        LaunchedEffect(meta) { delay(120); runCatching { fr.requestFocus() } }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.4f to Color.Transparent, 1f to Color(0xF2000000)))) {
            Column(Modifier.align(Alignment.CenterEnd).width(560.dp).fillMaxHeight().padding(end = 40.dp, top = 40.dp, bottom = 40.dp)) {
                Text(meta?.preview?.name ?: req.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Spacer(Modifier.height(12.dp))
                if (meta == null) Text("Chargement des épisodes…", color = TvoColors.Muted)
                else if (seasons.isEmpty()) Text("Aucun épisode trouvé.", color = TvoColors.Muted)
                else {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons) { sn -> Chip(if (sn == 0) "Bonus" else "Saison $sn", season == sn, { season = sn }) }
                    }
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(all.filter { it.season == season }, key = { it.id }) { ep ->
                            val current = ep.id == req.videoId
                            TvoButton(
                                (if (current) "▶ " else "") + "É${ep.episode ?: "?"} · ${ep.title ?: "Épisode"}",
                                { closeEpisodes(); if (!current) playEpisode(ep, meta?.preview?.name ?: req.title) },
                                (if (current) Modifier.focusRequester(fr) else Modifier).fillMaxWidth(),
                                primary = current,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TvoButton("Fermer", { closeEpisodes() })
            }
        }
    }
    private fun closeOptions() { options = false; showControls() }

    @Composable
    private fun OptionsPanel() {
        val fr = remember { FocusRequester() }
        @Suppress("UNUSED_VARIABLE") val v = tracksVersion
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.45f to Color.Transparent, 1f to Color(0xEE000000)))) {
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.align(Alignment.CenterEnd).width(520.dp).fillMaxHeight().padding(end = 40.dp, top = 40.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Text("Plus d'options", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                item { OptTitle("Format de l'image") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        resizeModes.forEachIndexed { i, (label, mode) ->
                            Chip(label, resizeIdx == i, { resizeIdx = i; playerView.resizeMode = mode }, if (i == 0) Modifier.focusRequester(fr) else Modifier)
                        }
                    }
                }
                item { OptTitle("Vitesse") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                            Chip(if (sp == 1f) "Normale" else "x${sp.toString().removeSuffix(".0")}", speed == sp, { speed = sp; player.setPlaybackSpeed(sp) })
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)); TvoButton("Fermer", { closeOptions() }, primary = true) }
            }
        }
        LaunchedEffect(Unit) { delay(120); runCatching { fr.requestFocus() } }
    }

    @Composable
    private fun OptTitle(t: String) = Text(t, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Cyan, modifier = Modifier.padding(top = 8.dp))

    private fun seekBy(ms: Long) {
        val dur = player.duration.takeIf { it > 0 } ?: return
        player.seekTo((player.currentPosition + ms).coerceIn(0, dur))
        seekFlash = (if (ms > 0) "+" else "−") + "${kotlin.math.abs(ms) / 1000} s"
        seekFlashId++
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


    // ------------------------------------------------------------ commandes façon Nuvio (barre en bas, icônes rondes)

    private object PIcons {
        private fun icon(name: String, d: String) = androidx.compose.ui.graphics.vector.ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black)).build()
        val Pause by lazy { icon("pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z") }
        val Audio by lazy { icon("audio", "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z") }
        val Subs by lazy { icon("subs", "M19,4H5c-1.11,0 -2,0.9 -2,2v12c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM11,11H9.5v-0.5h-2v3h2V13H11v1c0,0.55 -0.45,1 -1,1H7c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1v1zM18,11h-1.5v-0.5h-2v3h2V13H18v1c0,0.55 -0.45,1 -1,1h-3c-0.55,0 -1,-0.45 -1,-1v-4c0,-0.55 0.45,-1 1,-1h3c0.55,0 1,0.45 1,1v1z") }
        val More by lazy { icon("more", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z") }
    }

    private fun fmtTime(ms: Long): String {
        val t = (ms / 1000).coerceAtLeast(0); val h = t / 3600; val m = (t % 3600) / 60; val sec = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    @Composable
    private fun CtlBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, size: androidx.compose.ui.unit.Dp = 46.dp, big: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
        androidx.tv.material3.Surface(
            onClick = onClick, modifier = modifier.size(size),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.CircleShape),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = if (big) Color.White else Color(0xFF6D7691).copy(alpha = 0.40f), contentColor = if (big) Color.Black else Color.White,
                focusedContainerColor = Color.White, focusedContentColor = Color.Black),
            border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.7f)), inset = (-3).dp, shape = androidx.compose.foundation.shape.CircleShape)),
        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.tv.material3.Icon(icon, desc, Modifier.size(size * 0.5f)) } }
    }

    @Composable
    private fun Controls() {
        val playFr = remember { FocusRequester() }
        var pos by remember { mutableStateOf(player.currentPosition) }
        var dur by remember { mutableStateOf(player.duration.coerceAtLeast(0)) }
        var playing by remember { mutableStateOf(player.isPlaying) }
        LaunchedEffect(Unit) { while (true) { pos = player.currentPosition; dur = player.duration.coerceAtLeast(0); playing = player.isPlaying; delay(400) } }
        LaunchedEffect(Unit) { delay(80); runCatching { playFr.requestFocus() } }
        // masque tout seul après 5 s sans appui (seulement pendant la lecture)
        LaunchedEffect(ctrlTick) { delay(5000); if (player.isPlaying && !anyPanel) hideControls() }
        var barFocused by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent))))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 52.dp, end = 52.dp, top = 70.dp, bottom = 36.dp)) {
                Text(req.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                req.subtitle?.let { Text(it, fontSize = 16.sp, color = Color(0xCCFFFFFF), maxLines = 1) }
                Spacer(Modifier.height(14.dp))
                // barre de progression (◀ ▶ quand elle est sélectionnée : ±10 s)
                val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(22.dp)
                    .onFocusChanged { barFocused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (e.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) when (e.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(if (e.nativeKeyEvent.repeatCount > 2) -30_000L else -10_000L); pos = player.currentPosition; true }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(if (e.nativeKeyEvent.repeatCount > 2) 30_000L else 10_000L); pos = player.currentPosition; true }
                            else -> false
                        } else false
                    }.focusable(), contentAlignment = Alignment.CenterStart) {
                    val h = if (barFocused) 10.dp else 6.dp
                    Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.28f))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(TvoColors.Cyan))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CtlBtn(if (playing) PIcons.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Lecture", 58.dp, big = true, modifier = Modifier.focusRequester(playFr)) {
                        if (player.isPlaying) player.pause() else player.play(); playing = player.isPlaying; ctrlTick++ }
                    CtlBtn(PIcons.Subs, "Sous-titres") { openSubs() }
                    CtlBtn(PIcons.Audio, "Audio") { openAudio() }
                    if (req.type == "series") CtlBtn(Icons.AutoMirrored.Filled.List, "Épisodes") { openEpisodes() }
                    CtlBtn(PIcons.More, "Plus") { openOptions() }
                    Spacer(Modifier.weight(1f))
                    Text("${fmtTime(pos)} / ${fmtTime(dur)}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }

    // ------------------------------------------------------------ fenêtres Audio et Sous-titres

    @Composable
    private fun PanelRow(title: String, selected: Boolean, detail: String? = null, badge: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
        androidx.tv.material3.Surface(
            onClick = onClick, modifier = modifier.fillMaxWidth(),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = if (selected) TvoColors.Blue.copy(alpha = 0.85f) else Color.Transparent, contentColor = Color.White,
                focusedContainerColor = Color.White, focusedContentColor = Color.Black),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    detail?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp)) }
                }
                badge?.let { Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(TvoColors.Blue).padding(horizontal = 9.dp, vertical = 3.dp)) { Text(it, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) } }
                if (selected) androidx.tv.material3.Icon(Icons.Filled.Check, null, Modifier.size(24.dp).padding(start = 6.dp))
            }
        }
    }

    @Composable
    private fun PanelScrim(title: String, content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color(0xE6050810)).padding(horizontal = 64.dp, vertical = 36.dp)) {
            Column(Modifier.fillMaxSize()) {
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(18.dp))
                content()
            }
        }
    }

    @Composable
    private fun AudioPanel() {
        @Suppress("UNUSED_VARIABLE") val v = tracksVersion
        val audio = trackChoices(C.TRACK_TYPE_AUDIO)
        val fr = remember { FocusRequester() }
        PanelScrim("Audio") {
            if (audio.isEmpty()) Text("Une seule piste audio dans cette vidéo.", color = TvoColors.Muted, fontSize = 17.sp)
            androidx.compose.foundation.lazy.LazyColumn(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(audio.size) { i ->
                    val c = audio[i]; val parts = c.label.split(" · ")
                    PanelRow(parts.first() + parts.getOrNull(1)?.let { " ($it)" }.orEmpty(), c.selected, parts.drop(1).joinToString(" | "), null,
                        if (i == 0) Modifier.focusRequester(fr) else Modifier) { selectTrack(C.TRACK_TYPE_AUDIO, c); tracksVersion++ }
                }
            }
        }
        LaunchedEffect(Unit) { delay(120); runCatching { fr.requestFocus() } }
    }

    @Composable
    private fun SubsPanel() {
        @Suppress("UNUSED_VARIABLE") val v = tracksVersion
        val text = trackChoices(C.TRACK_TYPE_TEXT)
        val off = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || text.none { it.selected }
        val langs = text.map { it.lang.lowercase() }.distinct()
        var sel by remember { mutableStateOf(text.firstOrNull { it.selected }?.lang?.lowercase() ?: langs.firstOrNull()) }
        val fr = remember { FocusRequester() }
        PanelScrim("Sous-titres") {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(0.8f)) {
                    Text("Langues", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Muted, modifier = Modifier.padding(bottom = 8.dp))
                    androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { PanelRow("Aucun", off, modifier = Modifier.focusRequester(fr)) { selectTrack(C.TRACK_TYPE_TEXT, null); tracksVersion++ } }
                        items(langs.size) { i ->
                            val l = langs[i]
                            PanelRow(if (l.isBlank()) "Autre" else langLabel(l), sel == l && !off, null, text.count { it.lang.lowercase() == l }.toString()) { sel = l }
                        }
                    }
                }
                Column(Modifier.weight(1.2f)) {
                    Text("Sous-titres", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Muted, modifier = Modifier.padding(bottom = 8.dp))
                    androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val list = text.filter { it.lang.lowercase() == sel }
                        items(list.size) { i ->
                            val c = list[i]; val parts = c.label.split(" · ")
                            PanelRow(parts.first(), c.selected && !off, parts.drop(1).joinToString(" · ").replace("externe", "Externe")) { selectTrack(C.TRACK_TYPE_TEXT, c); tracksVersion++ }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Style des sous-titres", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TvoColors.Muted)
                    Text("Taille de la police", fontSize = 15.sp, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Chip("−", false, { subSize = (subSize - 0.1f).coerceAtLeast(0.6f); applySubStyle() })
                        Text("${(subSize * 100).toInt()}%", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Chip("+", false, { subSize = (subSize + 0.1f).coerceAtMost(2f); applySubStyle() })
                    }
                    Text("Fond noir", fontSize = 15.sp, color = Color.White)
                    Chip(if (subBox) "Activé" else "Désactivé", subBox, { subBox = !subBox; applySubStyle() })
                    Text("Couleur du texte", fontSize = 15.sp, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(0xFFFFFFFF, 0xFFFFE14D, 0xFF5CD6FF, 0xFFFF8FA3, 0xFF7CF29A).forEach { col ->
                            val ci = col.toInt()
                            androidx.tv.material3.Surface(onClick = { subColor = ci; applySubStyle() }, Modifier.size(36.dp),
                                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.CircleShape),
                                scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
                                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Color(col), focusedContainerColor = Color(col)),
                                border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                                    border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(if (subColor == ci) 3.dp else 0.dp, TvoColors.Blue), shape = androidx.compose.foundation.shape.CircleShape),
                                    focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White), shape = androidx.compose.foundation.shape.CircleShape)),
                            ) {}
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { delay(120); runCatching { fr.requestFocus() } }
    }

    // ------------------------------------------------------------ lifecycle

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) ctrlTick++
        // Commandes cachées : ◀ ▶ avancent/reculent directement, tout autre bouton affiche les commandes (comme Nuvio).
        if (event.action == KeyEvent.ACTION_DOWN && !anyPanel && nextUp == null && error == null && !controllerVisible) {
            val step = if (event.repeatCount > 2) 30_000L else 10_000L
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-step); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(step); return true }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { openOptions(); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { showControls(); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (player.isPlaying) player.pause() else player.play()
                    showControls(); return true
                }
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && options && event.keyCode == KeyEvent.KEYCODE_MENU) { closeOptions(); return true }
        // Media keys always go to the player, even when an overlayView has focus.
        if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when {
                    event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE -> player.pause()
                    event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY -> player.play()
                    player.isPlaying -> player.pause()
                    else -> player.play()
                }
                showControls()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() { super.onResume(); app.tvo.tv.data.Sync.playbackActive = true }

    override fun onPause() {
        super.onPause()
        app.tvo.tv.data.Sync.playbackActive = false
        saveProgress()
        app.tvo.tv.data.Sync.syncInBackground()
        player.pause()
        app.tvo.tv.data.Sync.syncInBackground()
    }

    override fun onDestroy() {
        Afr.reset(this)
        watchdog.removeCallbacks(stuck)
        progressJob?.cancel()
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }
}
