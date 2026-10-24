package com.poc.doomguard.sensor

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.poc.doomguard.engine.PressureEngine
import com.poc.doomguard.overlay.BubbleOverlay
import com.poc.doomguard.ui.BreatheActivity

// C0 SPIKE (throwaway). Logs under tag "DG" so the reel rule (G0) can be chosen from real
// Instagram events, and exposes a broadcast to test background startActivity (G0b):
//   adb shell am broadcast -a com.poc.doomguard.TEST_BREATHE -p com.poc.doomguard
class ScrollSensorService : AccessibilityService() {
    private lateinit var bubble: BubbleOverlay
    private var igForeground = false
    private var scrollCount = 0
    private var reelCount = 0
    private var lastIndex = -1

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
                    pkg.startsWith("com.vivo.systemuiplugin") || pkg.contains("inputmethod")
                ) return
                // Our own bubble window fires a window event too; without this it hides itself.
                if (pkg == packageName && e.className?.toString()?.endsWith("Activity") != true) return
                igForeground = ig
                if (!ig) lastIndex = -1
                if (ig) {
                    bubble.show()
                    bubble.render(PressureEngine.Snapshot(0f, 0f, PressureEngine.State.CALM))
                } else {
                    bubble.hide()
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (pkg != IG) return
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
                            if (::bubble.isInitialized) bubble.render(PressureEngine.snap.value)
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

    override fun onInterrupt() {}

    override fun onDestroy() {
        try { unregisterReceiver(testReceiver) } catch (_: Throwable) {}
        if (::bubble.isInitialized) bubble.hide()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DG"
        const val IG = "com.instagram.android"
        const val ACTION_TEST = "com.poc.doomguard.TEST_BREATHE"
    }
}
