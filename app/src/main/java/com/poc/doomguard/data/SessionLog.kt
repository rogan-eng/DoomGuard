package com.poc.doomguard.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object SessionLog {
    private const val PREFS = "doomguard_log"
    private const val KEY = "entries"

    data class Entry(val ts: Long, val reels: Int, val durationMs: Long, val mood: Int)

    @Volatile private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
    }

    private fun prefs(): SharedPreferences? =
        try {
            appCtx?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }

    @Synchronized
    fun add(reels: Int, durationMs: Long, mood: Int) {
        try {
            val p = prefs() ?: return
            val arr = try {
                JSONArray(p.getString(KEY, "[]") ?: "[]")
            } catch (_: Exception) {
                JSONArray()
            }
            val o = JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("reels", reels)
                .put("durationMs", durationMs)
                .put("mood", mood)
            arr.put(o.toString())
            p.edit().putString(KEY, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun all(): List<Entry> {
        return try {
            val p = prefs() ?: return emptyList()
            val arr = JSONArray(p.getString(KEY, "[]") ?: "[]")
            val out = ArrayList<Entry>()
            for (i in 0 until arr.length()) {
                try {
                    val o = JSONObject(arr.getString(i))
                    out.add(
                        Entry(
                            o.getLong("ts"),
                            o.getInt("reels"),
                            o.getLong("durationMs"),
                            o.getInt("mood")
                        )
                    )
                } catch (_: Exception) {
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}
