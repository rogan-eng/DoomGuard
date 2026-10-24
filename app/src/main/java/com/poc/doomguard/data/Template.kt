package com.poc.doomguard.data

import android.content.Context

/** The look the user picked: it styles the overlay over Instagram and the 10-second break. */
enum class Template(val label: String, val blurb: String) {
    BUBBLE("Bubble", "A bubble that grows and warms up as you scroll"),
    REEL("Reel", "A film reel and a huge countdown"),
    DEPTH("Depth", "A dive computer and a safety stop")
}

object TemplateStore {
    private const val PREFS = "doomguard_prefs"
    private const val KEY = "template"

    fun get(ctx: Context): Template {
        val name = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return Template.entries.firstOrNull { it.name == name } ?: Template.REEL
    }

    fun set(ctx: Context, t: Template) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, t.name).apply()
    }
}
