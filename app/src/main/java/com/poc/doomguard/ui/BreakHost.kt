package com.poc.doomguard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.poc.doomguard.data.Quotes
import com.poc.doomguard.data.SessionSummaryStore
import com.poc.doomguard.data.SummaryDisplay
import com.poc.doomguard.data.SummaryFormat
import com.poc.doomguard.engine.Checkpoint
import com.poc.doomguard.engine.PressureEngine

const val BREAK_SECONDS = 10

/** What every template's break screen needs to draw itself. */
class BreakState(
    /** Whole seconds still to run (10 down to 0). */
    val remaining: Int,
    /** 0..1 progress through the current second, for smooth animation. */
    val second: Float,
    val finished: Boolean,
    val reelsRun: Int,
    /** Frames in a full run: threshold + 10. */
    val total: Int,
    val quote: String,
    /** Category breakdown of the run; null until there is one (or when nothing was recorded). */
    val summary: SummaryDisplay? = null
) {
    /** Fraction of the break still to run, smooth within each second. */
    val left: Float
        get() = if (finished) 0f else ((remaining - second) / BREAK_SECONDS).coerceIn(0f, 1f)
}

/**
 * The break's behaviour, shared by every template: a 10s countdown that restarts if the screen
 * is left, ticks a haptic each second, and resets the reel count when it ends.
 */
@Composable
fun BreakHost(onDone: () -> Unit, content: @Composable (BreakState, () -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val quote = rememberSaveable { Quotes.next(ctx) }
    // Captured before the countdown ends and the engine resets.
    val reelsRun = rememberSaveable { PressureEngine.snap.value.count.toInt() }
    val total = rememberSaveable { Checkpoint.total(PressureEngine.threshold) }

    // Restart token: bumped on every ON_START so the countdown LaunchedEffect restarts.
    var runToken by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(BREAK_SECONDS) }
    var finished by remember { mutableStateOf(false) }
    val sweep = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (!finished) {
                        remaining = BREAK_SECONDS
                        runToken = -1 // cancels the running effect so it can't finish in the background
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (!finished) {
                        remaining = BREAK_SECONDS
                        runToken = runToken.coerceAtLeast(0) + 1
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(runToken) {
        if (runToken <= 0 || finished) return@LaunchedEffect
        remaining = BREAK_SECONDS
        while (remaining > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(durationMillis = 1000, easing = LinearEasing))
            remaining -= 1
        }
        if (!finished) {
            finished = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            PressureEngine.reset()
        }
    }

    BackHandler(enabled = true) { }

    // Keyword counts arrive at once; a Gemini result (if enabled) replaces them while the break runs.
    val summaryState by SessionSummaryStore.state.collectAsState()
    val summary = SummaryFormat.display(summaryState)

    content(BreakState(remaining, sweep.value, finished, reelsRun, total, quote, summary), onDone)
}
