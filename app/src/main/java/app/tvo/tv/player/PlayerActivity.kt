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
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
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
                        player.stop()
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
                    // Son illisible (DTS, TrueHD…) : VLC intégré le décode.
                    VlcPlayerActivity.start(this@PlayerActivity, req.copy(startPosition = player.currentPosition))
                    finish()
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                // Format non pris en charge par ExoPlayer : bascule automatique sur VLC intégré, au même endroit.
                if (e.errorCode in listOf(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODING_FAILED,
                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED)) {
                    VlcPlayerActivity.start(this@PlayerActivity, req.copy(startPosition = player.currentPosition.coerceAtLeast(req.startPosition)))
                    finish(); return
                }
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
            controllerAutoShow = false
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
        getSharedPreferences("tvo_ui", MODE_PRIVATE).let { subSize = it.getFloat("sub_size", 1f); subBox = it.getBoolean("sub_box", false) }
        applySubStyle()

        // Retour : ferme le menu ouvert, sinon quitte le lecteur (un seul appui, jamais de boucle).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    options -> closeOptions()
                    nextUp != null -> { nextUp = null; nextDismissed = true; playerView.requestFocus() }
                    // 1er appui : cacher les boutons ; 2e appui (boutons déjà cachés) : quitter
                    controllerVisible && error == null -> { playerView.hideController(); controllerVisible = false; playerView.requestFocus() }
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
            AnimatedVisibility(controllerVisible && error == null, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))).padding(start = 48.dp, top = 28.dp, end = 48.dp, bottom = 40.dp)) {
                    Column {
                        Text(req.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        req.subtitle?.let { Text(it, fontSize = 16.sp, color = TvoColors.Muted) }
                        Text("▲ Options (audio, sous-titres, format, vitesse)   ◀ ▶ reculer / avancer de 10 s" + if (req.type == "series") "   ▼ Épisodes" else "", fontSize = 12.sp,
                            color = Color(0xB3FFFFFF), modifier = Modifier.padding(top = 6.dp))
                        if (req.type == "series") Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvoButton("Épisodes", { openEpisodes() })
                            TvoButton("Options", { openOptions() })
                        }
                    }
                }
            }
            // Heure actuelle et heure de fin (vitesse comprise), en haut à droite, mises à jour chaque seconde
            AnimatedVisibility(controllerVisible && error == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)) {
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
    private fun applySubStyle() {
        val sv = playerView.subtitleView ?: return
        sv.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subSize)
        sv.setStyle(androidx.media3.ui.CaptionStyleCompat(android.graphics.Color.WHITE,
            if (subBox) 0xB3000000.toInt() else android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT,
            if (subBox) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            android.graphics.Color.BLACK, null))
        getSharedPreferences("tvo_ui", MODE_PRIVATE).edit().putFloat("sub_size", subSize).putBoolean("sub_box", subBox).apply()
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

    private fun openOptions() { playerView.hideController(); options = true; overlayView.requestFocus() }
    private fun openEpisodes() { if (req.type != "series") return; playerView.hideController(); episodesMenu = true; options = false; overlayView.requestFocus() }
    private fun closeEpisodes() { episodesMenu = false; playerView.requestFocus() }

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
    private fun closeOptions() { options = false; playerView.requestFocus() }

    @Composable
    private fun OptionsPanel() {
        val fr = remember { FocusRequester() }
        @Suppress("UNUSED_VARIABLE") val v = tracksVersion
        val audio = trackChoices(C.TRACK_TYPE_AUDIO)
        val text = trackChoices(C.TRACK_TYPE_TEXT)
        val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || text.none { it.selected }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.45f to Color.Transparent, 1f to Color(0xEE000000)))) {
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.align(Alignment.CenterEnd).width(520.dp).fillMaxHeight().padding(end = 40.dp, top = 40.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Text("Options de lecture", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                item { OptTitle("Audio") }
                item {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (audio.isEmpty()) item { Text("Piste unique", color = TvoColors.Muted, fontSize = 14.sp) }
                        items(audio.size) { i ->
                            val c = audio[i]
                            Chip(c.label, c.selected, { selectTrack(C.TRACK_TYPE_AUDIO, c); tracksVersion++ },
                                if (i == 0) Modifier.focusRequester(fr) else Modifier)
                        }
                    }
                }
                item { OptTitle("Sous-titres") }
                item {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Chip("Désactivés", textOff, { selectTrack(C.TRACK_TYPE_TEXT, null); tracksVersion++ },
                            if (audio.isEmpty()) Modifier.focusRequester(fr) else Modifier) }
                        items(text.size) { i -> val c = text[i]; Chip(c.label, c.selected && !textOff, { selectTrack(C.TRACK_TYPE_TEXT, c); tracksVersion++ }) }
                    }
                }
                item { OptTitle("Taille des sous-titres") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Petite" to 0.8f, "Normale" to 1f, "Grande" to 1.25f, "Très grande" to 1.5f).forEach { (l, k) ->
                            Chip(l, subSize == k, { subSize = k; applySubStyle() }) }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Texte avec ombre", !subBox, { subBox = false; applySubStyle() })
                        Chip("Fond noir (plus lisible)", subBox, { subBox = true; applySubStyle() })
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sous-titres en avance ou en retard ?", fontSize = 13.sp, color = TvoColors.Muted)
                        Chip("Régler le décalage (VLC)", false, {
                            VlcPlayerActivity.start(this@PlayerActivity, req.copy(startPosition = player.currentPosition)); finish() })
                    }
                }
                item { OptTitle("Format de l'image") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        resizeModes.forEachIndexed { i, (label, mode) ->
                            Chip(label, resizeIdx == i, { resizeIdx = i; playerView.resizeMode = mode })
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

    // ------------------------------------------------------------ lifecycle

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Netflix-like remote: ◀ ▶ seek without opening the controls, ▲ or Menu opens options.
        if (event.action == KeyEvent.ACTION_DOWN && !options && !episodesMenu && nextUp == null && error == null && !playerView.isControllerFullyVisible) {
            val step = if (event.repeatCount > 2) 30_000L else 10_000L
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-step); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(step); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { openOptions(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> if (req.type == "series") { openEpisodes(); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (player.isPlaying) player.pause() else player.play()
                    playerView.showController(); return true
                }
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && options && event.keyCode == KeyEvent.KEYCODE_MENU) { closeOptions(); return true }
        // Media keys always go to the player, even when an overlayView has focus.
        if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (playerView.dispatchMediaKeyEvent(event)) return true
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
