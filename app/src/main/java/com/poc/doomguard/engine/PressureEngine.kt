package com.poc.doomguard.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PressureEngine {
    enum class State { CALM, AWARE, RESIST, BLOCK }
    data class Snapshot(val count: Float, val pressure: Float, val state: State)
    data class ResultOfSession(val reels: Int, val durationMs: Long)

    @Volatile var threshold: Int = 25
    @Volatile var span: Int = 15

    /** Injectable time source so tests don't sleep. */
    internal var clock: () -> Long = System::currentTimeMillis

    private var count = 0f
    private var lastReelAt = clock()
    private var sessionStart = 0L

    private val _snap = MutableStateFlow(Snapshot(0f, 0f, State.CALM))
    val snap: StateFlow<Snapshot> = _snap

    @Synchronized
    fun onReel() {
        val now = clock()
        if (count == 0f) sessionStart = now
        count += 1f
        lastReelAt = now
        publish()
    }

    /** Called every 1000ms by the service. */
    @Synchronized
    fun tick(igForeground: Boolean) {
        if (_snap.value.state == State.BLOCK) return // only reset() clears BLOCK
        if (clock() - lastReelAt > 5000) {
            count = (count - if (igForeground) 0.2f else 1.0f).coerceAtLeast(0f)
        }
        publish()
    }

    @Synchronized
    fun reset(): ResultOfSession {
        val duration = if (sessionStart == 0L) 0L else clock() - sessionStart
        val result = ResultOfSession(count.toInt(), duration)
        count = 0f
        sessionStart = 0L
        publish()
        return result
    }

    private fun publish() {
        val p = ((count - threshold) / span).coerceIn(0f, 1f)
        val s = when {
            p >= 1f -> State.BLOCK
            p > 0f -> State.RESIST
            count > threshold * 0.6f -> State.AWARE
            else -> State.CALM
        }
        _snap.value = Snapshot(count, p, s)
    }
}
