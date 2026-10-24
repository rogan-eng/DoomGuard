package com.poc.doomguard.data

// The current session's reels, recorded silently as they are counted and categorized once at the
// break (see SessionSummarizer). In memory only; never persisted. Pure Kotlin.
object ReelLog {
    /** One counted reel's text. [ad] reels are counted as "other" and never sent to the network. */
    class Record(val user: String?, val audio: String?, val caption: String?, val ad: Boolean = false)

    const val CAP = 120 // oldest records are dropped beyond this, bounding memory and the request size

    private val records = ArrayList<Record>()

    @Synchronized fun add(r: Record) {
        records.add(r)
        while (records.size > CAP) records.removeAt(0)
    }

    /** Returns the records (oldest first) and empties the log, atomically. */
    @Synchronized fun snapshotAndClear(): List<Record> {
        val copy = ArrayList(records)
        records.clear()
        return copy
    }

    /** Starts a fresh session log. */
    @Synchronized fun clear() = records.clear()

    @Synchronized fun size(): Int = records.size
}
