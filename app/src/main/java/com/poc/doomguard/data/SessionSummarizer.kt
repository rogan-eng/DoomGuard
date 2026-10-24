package com.poc.doomguard.data

import android.util.Log
import com.poc.doomguard.BuildConfig
import java.util.concurrent.Executors

// Turns the session's ReelLog into a SessionSummary at the break: keyword counts at once, then one
// Gemini call (if the user consented and a key is set) that replaces them on success.
object SessionSummarizer {
    private const val TAG = "DG"

    // The Gemini call blocks for up to 8s, so it runs here, never on the service/main thread.
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "dg-summary").apply { isDaemon = true } }
    // Bumped per checkpoint; a Gemini result from an older checkpoint is dropped.
    private val lock = Any()
    private var generation = 0

    /** Call at the start of the checkpoint, on the service thread. Never throws, never blocks on the network. */
    fun begin(consent: Boolean) {
        try {
            val records = ReelLog.snapshotAndClear()
            val gen = synchronized(lock) { ++generation }
            if (records.isEmpty()) {
                SessionSummaryStore.set(SummaryState.None)
                return
            }
            SessionSummaryStore.set(SummaryState.Pending)
            val keywords = keywordSummary(records)
            val toSend = records.filter { !it.ad }
            val useAi = consent && BuildConfig.GEMINI_API_KEY.isNotBlank() && toSend.isNotEmpty()
            SessionSummaryStore.set(SummaryState.Ready(keywords, refining = useAi))
            Log.w(TAG, "SUMMARY keywords total=${keywords.total} ai=$useAi")
            if (!useAi) return
            executor.execute {
                try {
                    val cats = GeminiBatch.categorize(toSend, consent)
                    if (cats == null) {
                        Log.w(TAG, "SUMMARY gemini failed, keeping keywords")
                        publish(gen, SummaryState.Ready(keywords, refining = false))
                    } else {
                        val s = combine(records.size - toSend.size, cats)
                        Log.w(TAG, "SUMMARY gemini ok total=${s.total}")
                        publish(gen, SummaryState.Ready(s, refining = false))
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "SUMMARY gemini crashed", t)
                    publish(gen, SummaryState.Ready(keywords, refining = false))
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "SUMMARY begin failed", t)
        }
    }

    private fun publish(gen: Int, s: SummaryState) {
        synchronized(lock) {
            if (gen == generation) SessionSummaryStore.set(s)
        }
    }

    /** Keyword counts for every record; ad-flagged records are "other". */
    fun keywordSummary(records: List<ReelLog.Record>, words: Map<String, List<String>>? = null): SessionSummary {
        val cats = records.map {
            val texts = listOfNotNull(it.user, it.audio, it.caption)
            when {
                it.ad -> Categorizer.OTHER
                words != null -> Categorizer.categorize(texts, words)
                else -> Categorizer.categorize(texts)
            }
        }
        return SessionSummary.tally(cats, SummarySource.KEYWORDS)
    }

    /** Gemini's labels for the non-ad records plus [adCount] ads (counted as other), tallied locally. */
    fun combine(adCount: Int, geminiCategories: List<String>): SessionSummary =
        SessionSummary.tally(List(adCount) { Categorizer.OTHER } + geminiCategories, SummarySource.GEMINI)
}
