package app.tvo.tv.player

import android.app.Activity
import android.os.Build
import app.tvo.tv.Tvo
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Fréquence d'image automatique (comme Nuvio) : un film en 23,976 ou 25 images/s affiché en 60 Hz
 * saccade. On demande à la télé le mode de même résolution dont la fréquence est un multiple exact.
 */
object Afr {
    private var applied = false

    fun apply(a: Activity, fps: Float) {
        if (applied || fps < 10f || fps > 121f || Build.VERSION.SDK_INT < 23 || !Tvo.store.afr.value) return
        applied = true
        runCatching {
            @Suppress("DEPRECATION")
            val d = if (Build.VERSION.SDK_INT >= 30) a.display ?: return else a.windowManager.defaultDisplay
            val cur = d.mode
            var best: android.view.Display.Mode? = null
            var bestScore = Float.MAX_VALUE
            for (m in d.supportedModes) {
                if (m.physicalWidth != cur.physicalWidth || m.physicalHeight != cur.physicalHeight) continue
                val k = (m.refreshRate / fps).roundToInt()
                if (k < 1) continue
                val err = abs(m.refreshRate - k * fps)
                if (err > 0.06f) continue
                val score = err * 100 + k // le plus précis, puis le multiple le plus petit
                if (score < bestScore) { bestScore = score; best = m }
            }
            val b = best ?: return
            if (b.modeId == cur.modeId) return
            a.window.attributes = a.window.attributes.apply { preferredDisplayModeId = b.modeId }
        }
    }

    fun reset(a: Activity) {
        if (!applied) return
        applied = false
        runCatching { a.window.attributes = a.window.attributes.apply { preferredDisplayModeId = 0 } }
    }
}
