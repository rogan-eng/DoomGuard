package com.poc.doomguard.sensor

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.poc.doomguard.engine.Checkpoint
import com.poc.doomguard.engine.PressureEngine
import com.poc.doomguard.overlay.BubbleOverlay
import com.poc.doomguard.ui.BreatheActivity
import com.poc.doomguard.ui.QuoteBreakActivity

// C0 SPIKE (throwaway). Logs under tag "DG" so the reel rule (G0) can be chosen from real
// Instagram events, and exposes a broadcast to test background startActivity (G0b):
//   adb shell am broadcast -a com.poc.doomguard.TEST_BREATHE -p com.poc.doomguard
class ScrollSensorService : AccessibilityService() {
    private lateinit var bubble: BubbleOverlay
    private var igForeground = false
    private var scrollCount = 0
    private var reelCount = 0
    private var lastIndex = -1
    private var lastCheckpointAt = -10_000L
    // The break activity takes a moment to appear; until it does, treat the break as showing.
    private var breakGraceUntil = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val showWhenBreakEnds = object : Runnable {
        override fun run() {
            if (!igForeground || !::bubble.isInitialized) return
            if (breakShowing()) {
                handler.postDelayed(this, 300)
            } else {
                bubble.show()
                bubble.render(PressureEngine.snap.value)
            }
        }
    }

    private fun breakShowing() =
        QuoteBreakActivity.visible || SystemClock.uptimeMillis() < breakGraceUntil

    private val testReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Log.w(TAG, "G0b: startActivity(BreatheActivity) igForeground=$igForeground")
            try {
                startActivity(
                    Intent(this@ScrollSensorService, BreatheActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            } catch (t: Throwable) {
                Log.e(TAG, "G0b: startActivity FAILED", t)
            }
        }
    }

    override fun onServiceConnected() {
        Log.w(TAG, "service connected")
        // MainActivity only restores the saved threshold when it is opened; do it here too.
        PressureEngine.threshold = getSharedPreferences("doomguard_prefs", Context.MODE_PRIVATE)
            .getInt("threshold", PressureEngine.threshold)
        Log.w(TAG, "threshold=${PressureEngine.threshold} checkpoint at ${PressureEngine.threshold + CHECKPOINT_EXTRA}")
        bubble = BubbleOverlay(this)
        ContextCompat.registerReceiver(
            this, testReceiver, IntentFilter(ACTION_TEST), ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString()
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val ig = pkg == IG
                Log.w(TAG, "window pkg=$pkg cls=${e.className} ig=$ig")
                // Ignore transient windows (keyboard, shade) so they don't flip us out of IG.
                if (pkg == null || pkg.startsWith("com.android.systemui") ||
                    pkg.startsWith("com.vivo.systemuiplugin") || pkg.contains("inputmethod") ||
                    pkg == "android" || pkg.startsWith("com.samsung.android.app.cocktailbarservice") ||
                    pkg.startsWith("com.miui.systemui") || pkg.startsWith("com.oplus.") ||
                    pkg.startsWith("com.coloros.")
                ) return
                // Our own bubble window fires a window event too; without this it hides itself.
                if (pkg == packageName && e.className?.toString()?.endsWith("Activity") != true) return
                igForeground = ig
                if (!ig) lastIndex = -1
                if (ig) {
                    // Back in Instagram while the break is still tearing down: show once it is gone.
                    handler.removeCallbacks(showWhenBreakEnds)
                    if (breakShowing()) handler.postDelayed(showWhenBreakEnds, 300)
                    else {
                        bubble.show()
                        bubble.render(PressureEngine.snap.value) // shows the reset count after a checkpoint
                    }
                } else {
                    handler.removeCallbacks(showWhenBreakEnds)
                    bubble.hide()
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (pkg != IG) return
                // Instagram keeps emitting scroll events behind the break; they must neither
                // count nor bring the overlay back.
                if (breakShowing()) return
                // Self-heal: a transient window from another package may have hidden the bubble
                // without IG sending a new window event. IG scrolling proves IG is on screen.
                if (!igForeground && ::bubble.isInitialized) {
                    igForeground = true
                    bubble.show()
                    bubble.render(PressureEngine.snap.value)
                }
                // Reel rule (signal A): Reels is a ViewPager whose fromIndex is the current reel.
                // A swipe fires 2-4 events with the same index, so count index CHANGES, not events.
                if (e.className?.toString() == "androidx.viewpager.widget.ViewPager" && e.fromIndex >= 0) {
                    if (e.fromIndex != lastIndex) {
                        val first = lastIndex == -1
                        lastIndex = e.fromIndex
                        if (!first) {
                            PressureEngine.onReel()
                            reelCount++
                            Log.w(TAG, "REEL #$reelCount index=${e.fromIndex}")
                            val snap = PressureEngine.snap.value
                            if (::bubble.isInitialized) bubble.render(snap)
                            if (snap.count >= PressureEngine.threshold + CHECKPOINT_EXTRA) launchCheckpoint()
                        }
                    }
                }
                scrollCount++
                Log.w(
                    TAG,
                    "scroll#$scrollCount t=${System.currentTimeMillis()} cls=${e.className} " +
                        "from=${e.fromIndex} to=${e.toIndex} cnt=${e.itemCount} " +
                        "dx=${e.scrollDeltaX} dy=${e.scrollDeltaY} sx=${e.scrollX} sy=${e.scrollY}"
                )
            }
        }
    }

    // Checkpoint: threshold + 10 reels -> 10s blocking screen with a quote, then count resets.
    // singleTask brings an existing instance forward, so relaunching while it shows is harmless.
    private fun launchCheckpoint() {
        val now = SystemClock.uptimeMillis()
        if (now - lastCheckpointAt < 3000) return
        lastCheckpointAt = now
        // The break owns the screen now: take the overlay down before it appears.
        breakGraceUntil = now + 2000
        igForeground = false
        lastIndex = -1
        handler.removeCallbacks(showWhenBreakEnds)
        if (::bubble.isInitialized) bubble.hide()
        Log.w(TAG, "CHECKPOINT: count=${PressureEngine.snap.value.count} -> QuoteBreakActivity")
        try {
            startActivity(
                Intent(this, QuoteBreakActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (t: Throwable) {
            Log.e(TAG, "CHECKPOINT: startActivity FAILED", t)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(showWhenBreakEnds)
        try { unregisterReceiver(testReceiver) } catch (_: Throwable) {}
        if (::bubble.isInitialized) bubble.hide()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DG"
        const val IG = "com.instagram.android"
        const val CHECKPOINT_EXTRA = Checkpoint.EXTRA
        const val ACTION_TEST = "com.poc.doomguard.TEST_BREATHE"
    }
}
