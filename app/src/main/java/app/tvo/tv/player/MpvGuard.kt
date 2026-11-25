package app.tvo.tv.player

import android.content.Context

/** MPV ne se charge pas sur certains boîtiers 32 bits : on s'en souvient et on passe aux autres lecteurs. */
object MpvGuard {
    private const val P = "mpv_guard"
    fun broken(ctx: Context): Boolean = ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getBoolean("broken", false)
    fun markBroken(ctx: Context) { ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putBoolean("broken", true).apply() }
}
