package com.poc.doomguard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CategorizerTest {
    // The real shipped dataset, read from the source tree (JVM tests run with the module dir as cwd).
    private val words = Categorizer.parse(File("src/main/assets/categories.json").readText())

    private fun cat(vararg t: String) = Categorizer.categorize(t.toList(), words)

    @Test fun datasetHasEnoughKeywords() {
        for (c in Categorizer.PRIORITY) assertTrue("$c has ${words[c]?.size}", (words[c]?.size ?: 0) >= 40)
    }

    @Test fun hashtagsMatch() = assertEquals("sports", cat("Last over thriller #cricket #ipl"))

    @Test fun mostHitsWins() = assertEquals("lifestyle", cat("easy recipe #foodie #cooking", "funny"))

    @Test fun wholeLatinTokenOnly() = assertEquals("other", cat("he said hello"))

    @Test fun emptyIsOther() = assertEquals("other", Categorizer.categorize(emptyList(), words))

    @Test fun trainingInstituteIsEducation() =
        assertEquals("education", cat("livewire_training_institute", "Advance your core systems engineering career!"))

    // One hit each ("feelingsong" vs "singapore"); the tie goes to entertainment, which precedes lifestyle.
    @Test fun singaporeFriendsTieGoesToEntertainment() =
        assertEquals("entertainment", cat("singapore_chinna_pasanga", "#singapore #reels #friends #feelingsong"))

    @Test fun tamilLoveCaption() = assertEquals("entertainment", cat("காதல்..🩵🫴 …"))

    @Test fun tamilSuffixMatchesBySubstring() = assertEquals("entertainment", cat("காதலில் விழுந்தேன்"))

    @Test fun hindiCricket() = assertEquals("sports", cat("आज का क्रिकेट मैच"))

    @Test fun adLabelIsOther() = assertEquals("other", cat("microsoftazure", "Test ClipsSingleImageAdsComponent"))

    @Test fun visitProfileIsOtherEvenWithKeywords() = assertEquals("other", cat("tech career", "Visit Instagram profile"))

    @Test fun aiContentLabelIgnored() = assertEquals("other", cat("AI content"))

    @Test fun nonLatinNoiseDoesNotCrash() = assertEquals("other", cat("வணக்கம் 😀🔥…"))

    @Test fun parseReadsKeywordsLowercased() {
        val m = Categorizer.parse("""{"categories":{"Sports":{"keywords":["Cricket"," "]},"other":{"keywords":[]}}}""")
        assertEquals(listOf("cricket"), m["sports"])
    }

    @Test(expected = Exception::class) fun parseRejectsMalformed() { Categorizer.parse("nope") }
}
