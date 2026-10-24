package com.poc.doomguard.data

import android.util.Log
import com.poc.doomguard.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Categorizes a whole session's reels with ONE Gemini request at the break. Only text (creator, song,
// caption) is sent, and only with the user's consent. Blocking: call off the main thread. Callers
// keep the keyword result when this returns null. Prompt/parse/validate/gating logic is pure.
object GeminiBatch {
    // Verified against ai.google.dev (models page): stable Flash-Lite tier, "fastest, most cost-effective".
    const val MODEL = "gemini-3.5-flash-lite"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    private const val TAG = "DG"
    private const val TIMEOUT_MS = 8000 // bigger payload than a single reel
    private const val BACKOFF_MS = 30_000L
    private const val MAX_USER = 60
    private const val MAX_AUDIO = 60
    private const val MAX_CAPTION = 100

    // Same labels the keyword Categorizer returns (see assets/categories.json).
    val CATEGORIES = listOf("sports", "entertainment", "education", "lifestyle", "other")

    class Http(val code: Int, val body: String)

    @Volatile private var blockedUntil = 0L

    /** One category per record in order, or null on any failure (no consent, no key, backoff, network, bad reply). */
    fun categorize(records: List<ReelLog.Record>, consent: Boolean): List<String>? =
        categorizeWith(consent, BuildConfig.GEMINI_API_KEY, records, System::currentTimeMillis) { json ->
            post(json, BuildConfig.GEMINI_API_KEY)
        }

    /** Gate + backoff + parse around an injectable transport. Never throws. */
    internal fun categorizeWith(
        consent: Boolean,
        apiKey: String,
        records: List<ReelLog.Record>,
        now: () -> Long,
        transport: (String) -> Http?
    ): List<String>? {
        if (!consent || apiKey.isBlank() || records.isEmpty()) return null
        if (now() < blockedUntil) return null
        return try {
            val res = transport(buildRequest(records)) ?: return null
            if (res.code == 429) {
                blockedUntil = now() + BACKOFF_MS
                warn("GEMINI 429, pausing ${BACKOFF_MS / 1000}s")
                return null
            }
            if (res.code !in 200..299) {
                warn("GEMINI http ${res.code}")
                return null
            }
            parseResponse(res.body, records.size)
        } catch (t: Throwable) {
            warn("GEMINI failed: ${t.javaClass.simpleName}")
            null
        }
    }

    // Log.w throws on the JVM unit-test classpath; logging must never break categorizing.
    private fun warn(msg: String) {
        try { Log.w(TAG, msg) } catch (_: Throwable) {}
    }

    internal fun resetForTest() {
        blockedUntil = 0L
    }

    private fun clip(s: String?, max: Int): String =
        (s ?: "").replace(Regex("\\s+"), " ").trim().take(max)

    internal fun buildPrompt(records: List<ReelLog.Record>): String {
        val sb = StringBuilder()
        sb.append("Classify each numbered Instagram reel into exactly one category: ${CATEGORIES.joinToString(", ")}.\n")
        sb.append("sports: cricket, football, IPL, athletes, matches, WWE.\n")
        sb.append("entertainment: comedy, music and songs, movies and film clips, romance or mood edits, celebrities, memes, dance, gaming.\n")
        sb.append("education: tech, coding, AI tools, career and jobs, courses, finance and business tips, science, how-to, language learning, motivation.\n")
        sb.append("lifestyle: food, travel, fashion, beauty, fitness and health, home, pets, nature, vlogs.\n")
        sb.append("other: ads, or anything that fits none of these. The text may be Tamil, Hindi or English.\n")
        sb.append("Reply with one category per reel, in the same order, ${records.size} in total.\n")
        records.forEachIndexed { i, r ->
            sb.append("${i + 1}. Creator: ${clip(r.user, MAX_USER)} | Song: ${clip(r.audio, MAX_AUDIO)} | Caption: ${clip(r.caption, MAX_CAPTION)}\n")
        }
        return sb.toString()
    }

    /** generateContent body: one user turn, JSON output constrained to {"categories": [<enum>, ...]}. */
    internal fun buildRequest(records: List<ReelLog.Record>): String {
        val schema = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject().put(
                    "categories",
                    JSONObject().put("type", "ARRAY")
                        .put("items", JSONObject().put("type", "STRING").put("enum", JSONArray(CATEGORIES)))
                )
            )
            .put("required", JSONArray().put("categories"))
        return JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", buildPrompt(records))))
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("maxOutputTokens", 2048) // ~4 tokens per label x 120, plus headroom for thinking
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", schema)
            )
            .toString()
    }

    /**
     * Exactly [n] labels from a generateContent response body: a short array is padded with "other",
     * a long one truncated, unknown labels become "other". Null if the body is malformed/empty.
     */
    internal fun parseResponse(body: String?, n: Int): List<String>? {
        if (body.isNullOrBlank()) return null
        return try {
            val text = JSONObject(body).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            val arr = JSONObject(text).getJSONArray("categories")
            List(n) { i ->
                val c = if (i < arr.length()) arr.optString(i, "").trim().lowercase() else ""
                if (c in CATEGORIES) c else Categorizer.OTHER
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun post(json: String, apiKey: String): Http? {
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(json.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Http(code, body)
        } finally {
            conn.disconnect()
        }
    }
}
