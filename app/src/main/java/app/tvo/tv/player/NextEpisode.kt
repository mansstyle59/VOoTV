package app.tvo.tv.player

import android.app.Activity
import app.tvo.tv.Tvo
import app.tvo.tv.data.PlayRequest
import app.tvo.tv.data.StreamItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Épisode suivant pour les lecteurs MPV et VLC : même choix de source que le lecteur principal. */
object NextEpisode {
    fun playOrFinish(act: Activity, req: PlayRequest) = play(act, req, null)

    /** Lance l'épisode choisi (menu Épisodes) ou, sans choix, l'épisode suivant. */
    fun play(act: Activity, req: PlayRequest, target: app.tvo.tv.data.Video?) {
        if (req.type != "series") { act.finish(); return }
        android.widget.Toast.makeText(act, if (target == null) "Épisode suivant…" else "Recherche de la source…", android.widget.Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            val next = runCatching {
                val addons = Tvo.store.allowed(Tvo.store.addons.value)
                val meta = Tvo.client.meta(addons, req.type, req.metaId) ?: return@runCatching null
                val eps = meta.episodes
                val ep = target ?: eps.getOrNull(eps.indexOfFirst { it.id == req.videoId } + 1) ?: return@runCatching null
                val all = withTimeoutOrNull(20_000) { Tvo.client.streams(addons, req.type, ep.id).toList().flatMap { it.items } } ?: emptyList()
                val playable = all.filter { it.url != null }
                val pick: StreamItem = playable.firstOrNull { req.bingeGroup != null && it.bingeGroup == req.bingeGroup }
                    ?: playable.filter { it.addonName == req.addonName }.maxByOrNull { it.quality }
                    ?: playable.maxByOrNull { it.quality } ?: return@runCatching null
                val subs = runCatching { Tvo.client.subtitles(addons, req.type, ep.id) }.getOrDefault(emptyList())
                req.copy(url = pick.url!!, headers = pick.headers, subtitles = (pick.subtitles + subs).distinctBy { it.url }.take(40),
                    videoId = ep.id, season = ep.season, episode = ep.episode, episodeTitle = ep.title,
                    subtitle = "S${ep.season} : É${ep.episode} · ${ep.title}", startPosition = 0, bingeGroup = pick.bingeGroup, addonName = pick.addonName)
            }.getOrNull()
            if (next != null && !act.isFinishing) PlayerActivity.start(act, next)
            act.finish()
        }
    }
}
