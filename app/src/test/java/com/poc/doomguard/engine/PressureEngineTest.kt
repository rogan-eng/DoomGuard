package com.poc.doomguard.engine

import com.poc.doomguard.engine.PressureEngine.State
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PressureEngineTest {
    private var now = 1_000_000L

    @Before
    fun setUp() {
        now = 1_000_000L
        PressureEngine.clock = { now }
        PressureEngine.threshold = 25
        PressureEngine.span = 15
        PressureEngine.reset()
    }

    @After
    fun tearDown() {
        PressureEngine.reset()
        PressureEngine.clock = System::currentTimeMillis
    }

    private fun reels(n: Int) = repeat(n) { PressureEngine.onReel() }
    private val s get() = PressureEngine.snap.value

    @Test fun startsCalm() {
        assertEquals(State.CALM, s.state)
        assertEquals(0f, s.pressure, 0f)
    }

    @Test fun awareAboveSixtyPercent() {
        reels(15)
        assertEquals(State.CALM, s.state)
        reels(1)
        assertEquals(State.AWARE, s.state)
    }

    @Test fun resistAboveThreshold() {
        reels(25)
        assertEquals(State.AWARE, s.state)
        reels(1)
        assertEquals(State.RESIST, s.state)
        assertEquals(1f / 15f, s.pressure, 1e-4f)
    }

    @Test fun blockAtThresholdPlusSpan() {
        reels(39)
        assertEquals(State.RESIST, s.state)
        reels(1)
        assertEquals(State.BLOCK, s.state)
        assertEquals(1f, s.pressure, 0f)
    }

    @Test fun pressureClamped() {
        reels(100)
        assertEquals(1f, s.pressure, 0f)
        PressureEngine.reset()
        assertEquals(0f, s.pressure, 0f)
    }

    @Test fun noDecayWithinFiveSeconds() {
        reels(10)
        now += 5000
        PressureEngine.tick(false)
        assertEquals(10f, s.count, 0f)
    }

    @Test fun decayForegroundSlow() {
        reels(10)
        now += 5001
        PressureEngine.tick(true)
        assertEquals(9.8f, s.count, 1e-4f)
    }

    @Test fun decayBackgroundFast() {
        reels(10)
        now += 5001
        PressureEngine.tick(false)
        assertEquals(9f, s.count, 1e-4f)
    }

    @Test fun decayFloorsAtZero() {
        reels(1)
        now += 10_000
        PressureEngine.tick(false)
        PressureEngine.tick(false)
        assertEquals(0f, s.count, 0f)
    }

    @Test fun noDecayInBlock() {
        reels(40)
        now += 60_000
        PressureEngine.tick(false)
        assertEquals(40f, s.count, 0f)
        assertEquals(State.BLOCK, s.state)
    }

    @Test fun resetReturnsReelsAndDuration() {
        reels(3)
        now += 12_000
        val r = PressureEngine.reset()
        assertEquals(3, r.reels)
        assertEquals(12_000L, r.durationMs)
        assertEquals(0f, s.count, 0f)
        assertEquals(State.CALM, s.state)
    }

    @Test fun sessionStartsAtFirstReel() {
        now += 50_000
        PressureEngine.onReel()
        now += 4_000
        PressureEngine.onReel()
        assertEquals(4_000L, PressureEngine.reset().durationMs)
    }

    @Test fun customThreshold() {
        PressureEngine.threshold = 5
        PressureEngine.span = 5
        reels(3)
        assertEquals(State.CALM, s.state)
        reels(1)
        assertEquals(State.AWARE, s.state)
        reels(2)
        assertEquals(State.RESIST, s.state)
        reels(4)
        assertEquals(State.BLOCK, s.state)
    }
}
