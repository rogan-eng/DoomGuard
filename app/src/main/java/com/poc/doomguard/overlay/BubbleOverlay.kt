package com.poc.doomguard.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
import com.poc.doomguard.engine.PressureEngine

class BubbleOverlay(private val ctx: Context) {
    private val wm: WindowManager? = ctx.getSystemService(WindowManager::class.java)
    private val view = BubbleView(ctx)
    private var shown = false
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

    fun show() {
        if (shown && view.isAttachedToWindow) return
        shown = false
        view.refreshTemplate()
        try {
            wm?.addView(view, params(touchable))
            shown = true
        } catch (e: Exception) {
            Log.w("DG", "overlay addView failed (a11yType=$useA11yType)", e)
            if (useA11yType) {
                useA11yType = false
                show()
            }
        }
    }

    fun hide() {
        if (!shown) return
        try {
            wm?.removeView(view)
        } catch (_: Exception) {
            // Window already gone.
        }
        shown = false
    }

    fun setTouchable(t: Boolean) {
        touchable = t
        if (!shown) return
        try {
            wm?.updateViewLayout(view, params(t))
        } catch (_: Exception) {
            // Window may be gone.
        }
    }

    fun render(s: PressureEngine.Snapshot) {
        view.render(s)
    }
}
