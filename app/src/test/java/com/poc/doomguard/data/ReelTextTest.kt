package com.poc.doomguard.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelTextTest {
    @Test fun adLabels() {
        assertTrue(ReelText.isAdLabel("Visit Instagram profile"))
        assertTrue(ReelText.isAdLabel("Test ClipsSingleImageAdsComponent"))
        assertTrue(ReelText.isAdLabel("Test ClipsSingleImageAdsComponent 123"))
        assertFalse(ReelText.isAdLabel("my cricket reel"))
    }

    @Test fun aiContentIsNoiseNotAd() {
        assertTrue(ReelText.isNoise("AI content"))
        assertFalse(ReelText.isAdLabel("AI content"))
        assertFalse(ReelText.isNoise("AI content is the future of tech"))
    }
}
