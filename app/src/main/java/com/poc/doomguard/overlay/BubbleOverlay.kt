package com.poc.doomguard.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
import com.poc.doomguard.engine.PressureEngine

/**
 * Owns the full-screen overlay window that hosts [BubbleView]. The window is not touchable by
 * default so Instagram keeps receiving swipes; [setTouchable] flips that for the break.
 */
class BubbleOverlay(private val ctx: Context) {
    private val wm: WindowManager? = ctx.getSystemService(WindowManager::class.java)
    private val view = BubbleView(ctx)
    private var shown = false // our belief only; attached() is the real answer
    private var touchable = false

    // Accessibility overlays need no "Display over other apps" grant and OEM overlay
    // managers (vivo/Xiaomi/Oppo/Huawei) leave them alone; fall back to the app overlay type.
    private var useA11yType = true

    private fun params(touchable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (useA11yType) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT
    )

    // The window manager is the source of truth, not our own flag: removeView is asynchronous
    // and a failed addView can leave the view attached while `shown` says otherwise.
    private fun attached() = view.parent != null || view.isAttachedToWindow

    /** Adds the overlay if it is not already attached. Falls back to the app overlay type once. */
    fun show() {
        if (attached()) {
            shown = true
            return
        }
        view.refreshTemplate()
        try {
            wm?.addView(view, params(touchable))
            shown = true
        } catch (e: IllegalStateException) {
            // "already added": it is on screen, which is all we wanted.
            shown = attached()
        } catch (e: Exception) {
            Log.w("DG", "overlay addView failed (a11yType=$useA11yType)", e)
            if (useA11yType) {
                useA11yType = false
                show()
            }
        }
    }

    /** Removes the overlay if attached; safe to call repeatedly. */
    fun hide() {
        if (!attached()) {
            shown = false
            return
        }
        try {
            // Immediate, so a show() right after cannot collide with a pending removal.
            wm?.removeViewImmediate(view)
        } catch (_: Exception) {
            // Window already gone.
        }
        shown = false
    }

    /** Switches between pass-through (false) and touch-blocking (true) without re-adding the view. */
    fun setTouchable(t: Boolean) {
        touchable = t
        if (!shown) return
        try {
            wm?.updateViewLayout(view, params(t))
        } catch (_: Exception) {
            // Window may be gone.
        }
    }

    /** Pushes a new pressure snapshot to the view, which animates toward it. */
    fun render(s: PressureEngine.Snapshot) {
        view.render(s)
    }

    /** Redraws the per-category tally line only (no count animation restart). */
    fun invalidateTally() {
        view.refreshTally()
    }
}
