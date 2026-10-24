package com.poc.doomguard.engine

/** The checkpoint fires at threshold + EXTRA reels. One definition, shared by service and UI. */
object Checkpoint {
    const val EXTRA = 10

    fun total(threshold: Int): Int = threshold + EXTRA
}
