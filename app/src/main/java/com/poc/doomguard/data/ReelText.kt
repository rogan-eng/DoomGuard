package com.poc.doomguard.data

// Instagram's own labels that show up as reel "captions". Pure Kotlin.
object ReelText {
    /** Sponsored-content markers: the reel is an ad, no need to ask any model. */
    fun isAdLabel(s: String): Boolean {
        val t = s.trim()
        return t.equals("Visit Instagram profile", true) ||
            t.startsWith("Test ClipsSingleImageAdsComponent", true)
    }

    /** Chrome that is neither an ad marker nor a caption. */
    fun isNoise(s: String): Boolean = s.trim().equals("AI content", true)
}
