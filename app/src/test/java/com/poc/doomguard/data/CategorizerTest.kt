package com.poc.doomguard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizerTest {
    @Test fun hashtagsMatch() =
        assertEquals("fitness", Categorizer.categorize(listOf("Leg day #gym #workout")))

    @Test fun mostHitsWins() =
        assertEquals("food", Categorizer.categorize(listOf("easy recipe #foodie", "funny")))

    @Test fun wholeTokenOnly() =
        assertEquals("other", Categorizer.categorize(listOf("he said hello")))

    @Test fun emptyIsOther() = assertEquals("other", Categorizer.categorize(emptyList()))

    @Test fun adsComponentDoesNotCrash() {
        val c = Categorizer.categorize(listOf("scaler_official", "Test ClipsSingleImageAdsComponent"))
        assertTrue(c.isNotEmpty())
    }

    @Test fun trainingInstituteIsTech() = assertEquals(
        "tech",
        Categorizer.categorize(listOf("livewire_training_institute", "Advance your core systems engineering career!"))
    )

    @Test fun singaporeFriendsIsTravelOrMusic() {
        val c = Categorizer.categorize(listOf("singapore_chinna_pasanga", "#singapore #reels #friends #feelingsong"))
        assertTrue(c == "travel" || c == "music")
    }

    @Test fun tamilComedy() =
        assertEquals("comedy", Categorizer.categorize(listOf("Vadivelu comedy scene #trolls")))

    @Test fun romanisedLoveSong() =
        assertEquals("music", Categorizer.categorize(listOf("kadhal love song whatsapp status")))

    @Test fun nonLatinDoesNotCrash() =
        assertEquals("other", Categorizer.categorize(listOf("வணக்கம் 😀🔥…")))
}
