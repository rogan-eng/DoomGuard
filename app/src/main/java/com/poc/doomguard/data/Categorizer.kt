package com.poc.doomguard.data

import android.content.Context
import org.json.JSONObject

// Keyword fallback for reel text (creator, audio, caption), used when Gemini is off or fails.
// Keywords live in assets/categories.json. Latin keywords match whole tokens (so "ai" doesn't hit
// "said"); keywords in other scripts (Tamil, Devanagari) match as substrings because those languages
// attach suffixes (a trailing virama is dropped so stems match inflections). Highest hit count wins, ties go to the earlier category in [PRIORITY].
object Categorizer {
    const val OTHER = "other"
    const val ASSET = "categories.json"
    /** Tie-break order; "other" is never matched, only returned. */
    val PRIORITY = listOf("sports", "entertainment", "education", "lifestyle")

    // Used when the asset is missing or malformed so nothing crashes.
    private val BUILT_IN: Map<String, List<String>> = mapOf(
        "sports" to listOf("cricket", "ipl", "football", "match", "wwe"),
        "entertainment" to listOf("comedy", "song", "movie", "music", "dance", "meme", "love"),
        "education" to listOf("tech", "coding", "career", "course", "ai", "learn"),
        "lifestyle" to listOf("food", "recipe", "travel", "fashion", "fitness", "nature")
    )

    @Volatile private var dataset: Map<String, List<String>> = BUILT_IN
    @Volatile private var loaded = false

    /** Parses {"categories":{"sports":{"keywords":[...]},...}} into lowercase keyword lists. Throws on bad JSON. */
    fun parse(json: String): Map<String, List<String>> {
        val cats = JSONObject(json).getJSONObject("categories")
        val out = LinkedHashMap<String, List<String>>()
        for (name in cats.keys()) {
            val arr = cats.getJSONObject(name).getJSONArray("keywords")
            out[name.lowercase()] = (0 until arr.length()).map { arr.getString(it).trim().lowercase() }
                .filter { it.isNotEmpty() }
        }
        return out
    }

    /** Reads the asset once and caches it; keeps the built-in list if it is missing or malformed. */
    fun load(ctx: Context) {
        if (loaded) return
        try {
            val json = ctx.assets.open(ASSET).bufferedReader().use { it.readText() }
            val parsed = parse(json)
            if (parsed.values.any { it.isNotEmpty() }) dataset = parsed
        } catch (_: Throwable) {
            // keep BUILT_IN
        }
        loaded = true
    }

    /** Returns the best-matching category for the reel's [texts], or [OTHER] when nothing matches. */
    fun categorize(texts: List<String>): String = categorize(texts, dataset)

    fun categorize(texts: List<String>, words: Map<String, List<String>>): String {
        // Instagram ad labels mean the reel is an ad, whatever else the text says.
        if (texts.any { ReelText.isAdLabel(it) }) return OTHER
        val clean = texts.filterNot { ReelText.isNoise(it) }
        // Letters, marks (Tamil vowel signs) and digits survive; emoji and punctuation become spaces.
        val tokens = clean.joinToString(" ").lowercase()
            .split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return OTHER
        // Space-padded so `" $it "` below matches whole tokens and phrases only.
        val joined = " " + tokens.joinToString(" ") + " "
        var best = OTHER
        var bestScore = 0
        val order = PRIORITY + words.keys.filter { it !in PRIORITY && it != OTHER }
        for (category in order) {
            val kws = words[category] ?: continue
            val score = kws.count {
                if (isLatin(it)) joined.contains(" $it ")
                // Drop a trailing virama so the stem also matches inflected forms (காதல் -> காதலில்).
                else joined.contains(it.trimEnd('\u0BCD', '\u094D'))
            }
            if (score > bestScore) {
                best = category
                bestScore = score
            }
        }
        return best
    }

    // Latin keyword = no letter outside ASCII/Latin script.
    private fun isLatin(s: String) = s.none { it.isLetter() && Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN }
}
