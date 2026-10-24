package com.poc.doomguard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SessionSummaryTest {
    private val words = Categorizer.parse(File("src/main/assets/categories.json").readText())
    private fun kw(vararg r: ReelLog.Record) = SessionSummarizer.keywordSummary(r.toList(), words)

    @Test fun tallyCountsLocally() {
        val s = SessionSummary.tally(listOf("sports", "sports", "bogus", "education"), SummarySource.GEMINI)
        assertEquals(4, s.total)
        assertEquals(2, s.counts["sports"])
        assertEquals(1, s.counts["other"]) // unknown label
        assertEquals(1, s.counts["education"])
        assertEquals(0, s.counts["lifestyle"])
    }

    @Test fun combineCountsAdsAsOther() {
        val s = SessionSummarizer.combine(2, listOf("sports"))
        assertEquals(3, s.total)
        assertEquals(2, s.counts["other"])
        assertEquals(SummarySource.GEMINI, s.source)
    }

    @Test fun keywordSummaryOnRealRecords() {
        val s = kw(
            ReelLog.Record("livewire_training_institute", null, "Advance your core systems engineering career!"),
            ReelLog.Record(null, null, "காதல்..🩵🫴 …"),
            ReelLog.Record("singapore_chinna_pasanga", null, "#singapore #reels #friends #feelingsong"),
            ReelLog.Record("microsoftazure", null, "Test ClipsSingleImageAdsComponent")
        )
        assertEquals(4, s.total)
        assertEquals(SummarySource.KEYWORDS, s.source)
        assertEquals(1, s.counts["education"])
        assertEquals(2, s.counts["entertainment"]) // Tamil caption + tie goes to entertainment
        assertEquals(1, s.counts["other"])
    }

    @Test fun adFlagIsOtherWhateverTheText() {
        val s = kw(ReelLog.Record("cricket", null, "ipl match", ad = true))
        assertEquals(1, s.counts["other"])
        assertEquals(0, s.counts["sports"])
    }

    @Test fun rowsSortedByCountThenFixedOrder() {
        val s = SessionSummary.tally(
            List(3) { "lifestyle" } + List(5) { "entertainment" } + List(3) { "education" } + listOf("other"),
            SummarySource.KEYWORDS
        )
        assertEquals(
            listOf("entertainment" to 5, "education" to 3, "lifestyle" to 3, "other" to 1),
            SummaryFormat.rows(s)
        )
    }

    @Test fun displayFormatsLineTotalAndSource() {
        val s = SessionSummary.tally(List(18) { "entertainment" } + List(5) { "education" } + List(4) { "lifestyle" }, SummarySource.KEYWORDS)
        val d = SummaryFormat.display(SummaryState.Ready(s))!!
        assertEquals("entertainment 18 · education 5 · lifestyle 4", d.line)
        assertEquals("27 reels", d.totalLabel)
        assertEquals("Sorted on your phone", d.sourceLabel)
        assertEquals("Sorted on your phone · refining…", SummaryFormat.display(SummaryState.Ready(s, refining = true))!!.sourceLabel)
        val g = SessionSummary(s.counts, s.total, SummarySource.GEMINI)
        assertEquals("Sorted by Gemini", SummaryFormat.display(SummaryState.Ready(g))!!.sourceLabel)
    }

    @Test fun singularAndEmptyStates() {
        val one = SessionSummary.tally(listOf("sports"), SummarySource.KEYWORDS)
        assertEquals("1 reel", SummaryFormat.display(SummaryState.Ready(one))!!.totalLabel)
        assertNull(SummaryFormat.display(SummaryState.None))
        assertNull(SummaryFormat.display(SummaryState.Pending))
        assertNull(SummaryFormat.display(SummaryState.Ready(SessionSummary.tally(emptyList(), SummarySource.KEYWORDS))))
    }
}
