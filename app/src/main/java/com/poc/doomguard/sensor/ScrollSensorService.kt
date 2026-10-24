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
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.poc.doomguard.data.Categorizer
import com.poc.doomguard.data.CategoryTally
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
    // True while Instagram is the foreground app; the overlay is only shown while this holds.
    private var igForeground = false
    private var scrollCount = 0 // raw scroll events, log-only
    private var reelCount = 0   // reels counted since the service connected, log-only
    // Reels ViewPager index last seen; -1 = unknown, so the first event after (re)entering IG
    // only seeds it and is not counted as a swipe.
    private var lastIndex = -1
    // uptimeMillis of the last checkpoint launch; debounces repeated launches.
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

    // True while the break screen is up or about to be (the grace window covers its launch delay).
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
                            scheduleNodeDump(reelCount)
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
        pendingDump?.let { handler.removeCallbacks(it) }
        pendingDump = null
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

    // ~600ms after a reel is counted (let the new reel's text settle), read the reel's creator,
    // audio and caption and categorize them. A newer reel replaces the pending dump (that reel
    // simply gets no category); a checkpoint cancels it so a late result cannot leak into the
    // next session's tally.
    private var pendingDump: Runnable? = null

    private fun scheduleNodeDump(reel: Int) {
        pendingDump?.let { handler.removeCallbacks(it) }
        val r = Runnable { pendingDump = null; dumpNodes(reel) }
        pendingDump = r
        handler.postDelayed(r, 600)
    }

    // Walks the active window once (capped at MAX_NODES) and picks out three strings by shape,
    // since Instagram's reel UI has no stable ids except the author:
    //   user    = text of the clips_author_username node
    //   audio   = id-less Button whose description contains " · " ("Artist · Track")
    //   caption = first other id-less node with a description and no text, minus button chrome
    private fun dumpNodes(reel: Int) {
        var user: String? = null
        var audio: String? = null
        var caption: String? = null
        try {
            val root = rootInActiveWindow ?: return
            var visited = 0
            fun walk(n: AccessibilityNodeInfo) {
                if (visited++ >= MAX_NODES) return
                val id = n.viewIdResourceName
                val text = n.text?.toString()?.take(300)
                val desc = n.contentDescription?.toString()?.take(300)
                if (DUMP_NODES && (!text.isNullOrBlank() || !desc.isNullOrBlank())) {
                    Log.w(TAG, "NODES reel#$reel id=$id cls=${n.className} text=$text desc=$desc")
                }
                // Home-feed nodes behind the reel: skip the whole subtree.
                if (desc != null && (desc.contains("reels tray container", true) || desc.contains("'s story,", true))) return
                if (id == ID_AUTHOR) {
                    if (user == null && !text.isNullOrBlank()) user = text
                } else if (id == null && !desc.isNullOrBlank()) {
                    if (audio == null && n.className?.toString()?.endsWith("Button") == true && desc.contains(" · ")) {
                        audio = desc
                    } else if (caption == null && text.isNullOrBlank() && !isChrome(desc)) {
                        caption = desc
                    }
                }
                for (i in 0 until n.childCount) {
                    val c = n.getChild(i) ?: continue
                    try { walk(c) } finally { @Suppress("DEPRECATION") c.recycle() }
                }
            }
            try { walk(root) } finally { @Suppress("DEPRECATION") root.recycle() }
        } catch (t: Throwable) {
            Log.e(TAG, "NODES reel#$reel walk failed", t)
        }
        try {
            Log.w(TAG, "REELTEXT reel#$reel user=$user audio=$audio caption=${caption?.take(120)}")
            val texts = listOfNotNull(user, audio, caption)
            val category = Categorizer.categorize(texts)
            // count resets to 0 after a checkpoint: the first reel of a session starts a fresh tally.
            if (PressureEngine.snap.value.count <= 1f) CategoryTally.clear()
            CategoryTally.add(category)
            Log.w(TAG, "CATEGORY reel#$reel -> $category tally=${CategoryTally.line()}")
            if (::bubble.isInitialized) bubble.invalidateTally()
        } catch (t: Throwable) {
            Log.e(TAG, "CATEGORY reel#$reel failed", t)
        }
    }

    // True for button labels and engagement counters, which also show up as id-less nodes with a
    // description and would otherwise be mistaken for the caption.
    private fun isChrome(desc: String): Boolean {
        if (desc.startsWith("Reel by", true)) return true
        if (CHROME_CONTAINS.any { desc.contains(it, true) }) return true
        // Short labels only, so a caption that merely starts with "Like" or "Save" survives.
        return desc.length <= 24 && CHROME_PREFIX.any { desc.startsWith(it, true) }
    }

    // Required by AccessibilityService; there is no feedback to interrupt.
    override fun onInterrupt() {}

    override fun onDestroy() {
        pendingDump?.let { handler.removeCallbacks(it) }
        handler.removeCallbacks(showWhenBreakEnds)
        try { unregisterReceiver(testReceiver) } catch (_: Throwable) {}
        if (::bubble.isInitialized) bubble.hide()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DG"
        const val IG = "com.instagram.android"
        const val CHECKPOINT_EXTRA = Checkpoint.EXTRA
        const val MAX_NODES = 400 // bounds the tree walk so a deep hierarchy cannot stall the main thread
        const val DUMP_NODES = false // flip on to log every text/desc node when re-tuning the rules below
        const val ID_AUTHOR = "com.instagram.android:id/clips_author_username"
        val CHROME_CONTAINS = listOf("Like number", "Comment number", "Reposted", "Reshare", "Save number", "Profile picture of")
        val CHROME_PREFIX = listOf(
            "Like", "Comment", "Repost", "Share", "Save", "More", "Follow", "Back", "Add comment",
            "Instagram Home Feed", "Audio", "Send", "Search", "Camera", "Reels", "Messages"
        )
        const val ACTION_TEST = "com.poc.doomguard.TEST_BREATHE"
    }
}
