package com.poc.doomguard.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.poc.doomguard.engine.PressureEngine
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Plain View that draws the pressure bubble. No allocations in onDraw. */
class BubbleView(ctx: Context) : View(ctx) {

    private val density = ctx.resources.displayMetrics.density
    private val scaledDensity = ctx.resources.displayMetrics.scaledDensity
    private val minRadius = 48f * density
    private val inset = 24f * density
    private val statusBarH: Float = run {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) ctx.resources.getDimensionPixelSize(id).toFloat() else 24f * density
    }

    // Current animated values.
    private var p = 0f
    private var hue = HUE_CALM

    // Animation start/target.
    private var fromP = 0f
    private var toP = 0f
    private var fromHue = HUE_CALM
    private var toHue = HUE_CALM

    private var countText = "0"
    private var lastCount = 0

    private val hsv = FloatArray(3)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Unit-radius highlight gradient, scaled via matrix at draw time.
    private val glossMatrix = Matrix()
    private val glossShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255)),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
    )
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = glossShader
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 450L
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            val t = it.animatedValue as Float
            p = fromP + (toP - fromP) * t
            hue = fromHue + (toHue - fromHue) * t
            invalidate()
        }
    }

    fun render(s: PressureEngine.Snapshot) {
        val newP = s.pressure.coerceIn(0f, 1f)
        val threshold = PressureEngine.threshold
        val aware = newP == 0f && (s.state == PressureEngine.State.AWARE ||
            s.count > 0.6f * threshold)
        val newHue = when {
            newP > 0f -> HUE_CALM * (1f - newP)
            aware -> HUE_AMBER
            else -> HUE_CALM
        }

        val c = s.count.roundToInt()
        if (c != lastCount) {
            lastCount = c
            countText = c.toString()
        }

        fromP = p
        fromHue = hue
        toP = newP
        toHue = newHue
        animator.cancel()
        animator.start()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val maxRadius = hypot(w, h) / 2f
        val r = minRadius + (maxRadius - minRadius) * p

        // Parked top-end corner at p==0; centred as soon as pressure builds (eased in).
        val parkX = w - inset - minRadius
        val parkY = statusBarH + inset + minRadius
        val c = min(1f, p * 8f)
        val cx = parkX + (w / 2f - parkX) * c
        val cy = parkY + (h / 2f - parkY) * c

        hsv[0] = hue
        hsv[1] = 0.85f
        hsv[2] = 0.95f
        val alpha = (0.35f + 0.5f * p).coerceIn(0f, 1f)
        fillPaint.color = Color.HSVToColor((alpha * 255f).roundToInt(), hsv)
        canvas.drawCircle(cx, cy, r, fillPaint)

        glossMatrix.setScale(r, r)
        glossMatrix.postTranslate(cx - r * 0.25f, cy - r * 0.25f)
        glossShader.setLocalMatrix(glossMatrix)
        canvas.drawCircle(cx, cy, r, glossPaint)

        textPaint.textSize = (22f + 22f * p) * scaledDensity
        val baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(countText, cx, baseline, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = true

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val HUE_CALM = 210f
        const val HUE_AMBER = 40f
    }
}
