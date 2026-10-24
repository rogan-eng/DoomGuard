package com.poc.doomguard.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiBatchTest {
    @Before fun reset() = GeminiBatch.resetForTest()

    private fun rec(u: String? = "u", a: String? = "a", c: String? = "c") = ReelLog.Record(u, a, c)

    private fun reply(vararg cats: String): String {
        val inner = """{"categories":[${cats.joinToString(",") { "\"$it\"" }}]}""".replace("\"", "\\\"")
        return """{"candidates":[{"content":{"parts":[{"text":"$inner"}]}}]}"""
    }

    @Test fun promptNumbersRecordsAndTruncates() {
        val p = GeminiBatch.buildPrompt(listOf(rec("creator", "song", "x".repeat(300)), rec(null, null, null)))
        assertTrue(p.contains("1. Creator: creator | Song: song | Caption: ${"x".repeat(100)}\n"))
        assertFalse(p.contains("x".repeat(101)))
        assertTrue(p.contains("2. Creator:  | Song:  | Caption: \n"))
        assertTrue(p.contains("2 in total"))
        GeminiBatch.CATEGORIES.forEach { assertTrue(p.contains(it)) }
    }

    @Test fun audioTruncatedToSixty() {
        val p = GeminiBatch.buildPrompt(listOf(rec(a = "a".repeat(100))))
        assertTrue(p.contains("Song: ${"a".repeat(60)} |"))
    }

    @Test fun requestShape() {
        val j = JSONObject(GeminiBatch.buildRequest(listOf(rec())))
        val cfg = j.getJSONObject("generationConfig")
        assertEquals("application/json", cfg.getString("responseMimeType"))
        val items = cfg.getJSONObject("responseSchema").getJSONObject("properties")
            .getJSONObject("categories").getJSONObject("items")
        assertEquals(GeminiBatch.CATEGORIES.size, items.getJSONArray("enum").length())
    }

    @Test fun parseValid() =
        assertEquals(listOf("sports", "education"), GeminiBatch.parseResponse(reply("sports", "education"), 2))

    @Test fun parseShortArrayPadded() =
        assertEquals(listOf("sports", "other", "other"), GeminiBatch.parseResponse(reply("sports"), 3))

    @Test fun parseLongArrayTruncated() =
        assertEquals(listOf("sports"), GeminiBatch.parseResponse(reply("sports", "education", "lifestyle"), 1))

    @Test fun parseInvalidLabelIsOther() =
        assertEquals(listOf("other", "education"), GeminiBatch.parseResponse(reply("cooking", " Education "), 2))

    @Test fun parseMalformedIsNull() {
        assertNull(GeminiBatch.parseResponse("not json", 2))
        assertNull(GeminiBatch.parseResponse("", 2))
        assertNull(GeminiBatch.parseResponse(null, 2))
        assertNull(GeminiBatch.parseResponse("""{"candidates":[]}""", 2))
    }

    @Test fun consentOffOrBlankKeyNeverCallsNetwork() {
        var called = false
        val t = { _: String -> called = true; GeminiBatch.Http(200, reply("sports")) }
        assertNull(GeminiBatch.categorizeWith(false, "key", listOf(rec()), { 0L }, t))
        assertNull(GeminiBatch.categorizeWith(true, " ", listOf(rec()), { 0L }, t))
        assertNull(GeminiBatch.categorizeWith(true, "key", emptyList(), { 0L }, t))
        assertFalse(called)
    }

    @Test fun success() {
        val r = GeminiBatch.categorizeWith(true, "key", listOf(rec(), rec()), { 0L }) { GeminiBatch.Http(200, reply("sports", "lifestyle")) }
        assertEquals(listOf("sports", "lifestyle"), r)
    }

    @Test fun http429BacksOffThirtySeconds() {
        var calls = 0
        var now = 1000L
        val t = { _: String -> calls++; GeminiBatch.Http(429, "") }
        assertNull(GeminiBatch.categorizeWith(true, "key", listOf(rec()), { now }, t))
        now += 29_000
        assertNull(GeminiBatch.categorizeWith(true, "key", listOf(rec()), { now }, t))
        assertEquals(1, calls)
        now += 2_000
        GeminiBatch.categorizeWith(true, "key", listOf(rec()), { now }, t)
        assertEquals(2, calls)
    }

    @Test fun transportFailureIsNull() {
        val rs = listOf(rec())
        assertNull(GeminiBatch.categorizeWith(true, "key", rs, { 0L }) { throw RuntimeException("boom") })
        assertNull(GeminiBatch.categorizeWith(true, "key", rs, { 0L }) { GeminiBatch.Http(500, "") })
        assertNull(GeminiBatch.categorizeWith(true, "key", rs, { 0L }) { GeminiBatch.Http(200, "junk") })
    }
}
