package com.poc.doomguard.data

import android.content.Context

/** Whether the user allows reel text (creator, song, caption) to be sent to Google for categorizing. Off by default. */
object AiConsent {
    private const val PREFS = "doomguard_prefs"
    private const val KEY = "ai_consent"

    fun get(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}
