package com.poc.doomguard.data

// In-memory per-session reel category counts. Main thread only (service handler + overlay draw prep).
// Cleared by the service when a new session begins; never persisted.
object CategoryTally {
    private val counts = LinkedHashMap<String, Int>()

    /** Counts one reel in [category]. */
    fun add(category: String) {
        counts[category] = (counts[category] ?: 0) + 1
    }

    /** Top n categories by count, descending; ties keep first-seen order. */
    fun top(n: Int): List<Pair<String, Int>> =
        counts.entries.map { it.key to it.value }.sortedByDescending { it.second }.take(n)

    /** Starts a fresh session tally. */
    fun clear() {
        counts.clear()
    }

    /** Compact overlay line, e.g. "comedy 3 · tech 2 · other 1"; empty when nothing tallied. */
    fun line(n: Int = 3): String = top(n).joinToString(" · ") { "${it.first} ${it.second}" }
}
