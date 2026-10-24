package com.poc.doomguard.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where a summary's categories came from: one Gemini call, or the keyword list on the phone. */
enum class SummarySource { GEMINI, KEYWORDS }

/** Per-category reel counts for one session. [total] is the number of recorded reels. */
class SessionSummary(val counts: Map<String, Int>, val total: Int, val source: SummarySource) {
    companion object {
        /** Tallies [categories] locally; unknown labels count as "other". Counts never come from the model. */
        fun tally(categories: List<String>, source: SummarySource): SessionSummary {
            val counts = LinkedHashMap<String, Int>()
            for (c in GeminiBatch.CATEGORIES) counts[c] = 0
            for (c in categories) {
                val k = if (c in counts) c else Categorizer.OTHER
                counts[k] = counts.getValue(k) + 1
            }
            return SessionSummary(counts, categories.size, source)
        }
    }
}

/** What the break screen should show. [Ready.refining] is true while a Gemini call may still replace it. */
sealed interface SummaryState {
    /** No records this session: the break shows no breakdown. */
    object None : SummaryState
    object Pending : SummaryState
    class Ready(val summary: SessionSummary, val refining: Boolean = false) : SummaryState
}

/** Latest session summary, written by SessionSummarizer and collected by the break screens. */
object SessionSummaryStore {
    private val flow = MutableStateFlow<SummaryState>(SummaryState.None)
    val state: StateFlow<SummaryState> = flow

    fun set(s: SummaryState) {
        flow.value = s
    }
}

/** Display-ready text for a summary. */
class SummaryDisplay(
    val rows: List<Pair<String, Int>>,
    /** e.g. "entertainment 18 · education 5 · other 3" */
    val line: String,
    /** e.g. "27 reels" */
    val totalLabel: String,
    /** e.g. "Sorted by Gemini" */
    val sourceLabel: String
)

object SummaryFormat {
    /** Non-zero categories by count descending; ties keep the fixed category order. */
    fun rows(s: SessionSummary): List<Pair<String, Int>> =
        GeminiBatch.CATEGORIES.map { it to (s.counts[it] ?: 0) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second } // stable sort

    fun display(state: SummaryState): SummaryDisplay? {
        val r = state as? SummaryState.Ready ?: return null
        val s = r.summary
        if (s.total <= 0) return null
        val rows = rows(s)
        val source = when {
            s.source == SummarySource.GEMINI -> "Sorted by Gemini"
            r.refining -> "Sorted on your phone · refining…"
            else -> "Sorted on your phone"
        }
        return SummaryDisplay(
            rows = rows,
            line = rows.joinToString(" · ") { "${it.first} ${it.second}" },
            totalLabel = if (s.total == 1) "1 reel" else "${s.total} reels",
            sourceLabel = source
        )
    }
}
