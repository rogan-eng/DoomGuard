package com.poc.doomguard.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.poc.doomguard.data.CategoryTally
import com.poc.doomguard.data.Template
import com.poc.doomguard.data.TemplateStore
import com.poc.doomguard.engine.Checkpoint
import com.poc.doomguard.engine.PressureEngine
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Plain View that draws the overlay in the user's chosen template (Bubble, Reel or Depth).
 * One animator drives every template's values. No allocations in onDraw.
 */
class BubbleView(ctx: Context) : View(ctx) {

    private val density = ctx.resources.displayMetrics.density
    private val fontScale = ctx.resources.configuration.fontScale
    private val inset = 20f * density
    private val statusBarH: Float = run {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) ctx.resources.getDimensionPixelSize(id).toFloat() else 24f * density
    }

    private var template = Template.REEL

    // Animated values: current / start / target, indexed by the constants below.
    private val cur = FloatArray(N).also { it[HUE] = HUE_CALM }
    private val from = FloatArray(N)
    private val to = FloatArray(N)

    private var countText = "0"
    private var depthText = "0 m"
    private var leftText = ""
    private var tallyText = ""
    private var framesLeft = Checkpoint.total(PressureEngine.threshold)

    // Reel
    private val painter = ReelPainter(density)
    private val reelR = 30f * density
    // A film leader, not a black bar: reels often have black frames of their own, so the warning
    // must look like nothing a reel contains. Solid red, bone sprocket holes, a dark inner edge.
    private val barPaint = Paint().apply { color = RED }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BONE }
    private val edgePaint = Paint().apply { color = Color.rgb(17, 17, 17) }
    private val reelBack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BONE }

    // Bubble
    private val minRadius = 48f * density
    private val hsv = FloatArray(3)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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
    // Darkens the lower-right of the orb so it reads as a sphere, matching the home screen.
    private val shadeShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(90, 0, 0, 0)),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP
    )
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = shadeShader
    }
    private val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("serif", Typeface.NORMAL)
        setShadowLayer(4f * density, 0f, 1f * density, Color.argb(90, 0, 0, 0))
    }

    // Depth
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(242, 10, 36, 49) }
    private val depthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 26f * density * fontScale
    }
    private val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEAL
        textSize = 12f * density * fontScale
    }
    private val gaugePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Tally line: white text with a dark shadow reads over both dark and light reels.
    private val tallyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density * fontScale
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(3f * density, 0f, 1f * density, Color.argb(220, 0, 0, 0))
    }
    private val tallyChip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 0, 0, 0) }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 450L
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            val t = it.animatedValue as Float
            for (i in 0 until N) cur[i] = from[i] + (to[i] - from[i]) * t
            invalidate()
        }
    }

    /** Re-reads the chosen template. Called each time the overlay is shown over Instagram. */
    fun refreshTemplate() {
        template = TemplateStore.get(context)
        invalidate()
    }

    /** Rebuilds the tally string (outside onDraw) and redraws. */
    fun refreshTally() {
        tallyText = CategoryTally.line()
        invalidate()
    }

    fun render(s: PressureEngine.Snapshot) {
        tallyText = CategoryTally.line()
        val threshold = PressureEngine.threshold
        val total = Checkpoint.total(threshold)
        val c = s.count.roundToInt()
        framesLeft = max(0, total - c)
        countText = c.toString()
        depthText = "$c m"
        leftText = "$framesLeft to limit"

        val p = s.pressure.coerceIn(0f, 1f)
        val aware = p == 0f && (s.state == PressureEngine.State.AWARE || s.count > 0.6f * threshold)
        // Calm blue -> amber at the warning -> red at the limit. Never passes through green.
        val hue = when {
            p > 0f -> HUE_AMBER * (1f - p)
            aware -> HUE_AMBER
            else -> HUE_CALM
        }
        val used = (s.count / total).coerceIn(0f, 1f)

        for (i in 0 until N) from[i] = cur[i]
        to[P] = p
        to[HUE] = hue
        to[USED] = used
        to[SPIN] = s.count * 30f // a reset spins the reel back: a rewind
        to[BARS] = ((used - BARS_FROM) / (1f - BARS_FROM)).coerceIn(0f, 1f)
        animator.cancel()
        animator.start()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        when (template) {
            Template.BUBBLE -> drawBubble(canvas, w, h)
            Template.REEL -> drawReel(canvas, w, h)
            Template.DEPTH -> drawDepth(canvas, w, h)
        }
    }

    // Right-aligned line ending at the right inset, with a soft dark chip behind it.
    // Fits within 80% of the width; shrinks the text if the line is too long.
    private fun drawTally(canvas: Canvas, w: Float, baseline: Float) {
        if (tallyText.isEmpty()) return
        val base = 12f * density * fontScale
        tallyPaint.textSize = base
        val maxW = w * 0.8f
        val tw = tallyPaint.measureText(tallyText)
        if (tw > maxW) tallyPaint.textSize = base * maxW / tw
        val right = w - inset
        val tw2 = tallyPaint.measureText(tallyText)
        val pad = 6f * density
        val fm = tallyPaint.fontMetrics
        canvas.drawRoundRect(
            right - tw2 - pad, baseline + fm.ascent - pad / 2f,
            right + pad, baseline + fm.descent + pad / 2f,
            8f * density, 8f * density, tallyChip
        )
        tallyPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(tallyText, right, baseline, tallyPaint)
    }

    private fun drawBubble(canvas: Canvas, w: Float, h: Float) {
        val p = cur[P]
        val maxRadius = hypot(w, h) / 2f
        val r = minRadius + (maxRadius - minRadius) * p

        // Parked top-end corner at p==0; centred as soon as pressure builds (eased in).
        val parkX = w - inset - minRadius
        val parkY = statusBarH + inset + minRadius
        val c = min(1f, p * 8f)
        val cx = parkX + (w / 2f - parkX) * c
        val cy = parkY + (h / 2f - parkY) * c

        hsv[0] = cur[HUE]
        hsv[1] = 0.75f
        hsv[2] = 0.95f
        val alpha = (0.35f + 0.5f * p).coerceIn(0f, 1f)
        // Soft glow ring first, then the orb itself.
        fillPaint.color = Color.HSVToColor((alpha * 0.28f * 255f).roundToInt(), hsv)
        canvas.drawCircle(cx, cy, r * 1.22f, fillPaint)
        fillPaint.color = Color.HSVToColor((alpha * 255f).roundToInt(), hsv)
        canvas.drawCircle(cx, cy, r, fillPaint)

        glossMatrix.setScale(r * 1.5f, r * 1.5f)
        glossMatrix.postTranslate(cx - r * 0.3f, cy - r * 0.35f)
        shadeShader.setLocalMatrix(glossMatrix)
        canvas.drawCircle(cx, cy, r, shadePaint)

        glossMatrix.setScale(r, r)
        glossMatrix.postTranslate(cx - r * 0.25f, cy - r * 0.25f)
        glossShader.setLocalMatrix(glossMatrix)
        canvas.drawCircle(cx, cy, r, glossPaint)

        bubbleText.textSize = (22f + 22f * p) * density * fontScale
        val baseline = cy - (bubbleText.ascent() + bubbleText.descent()) / 2f
        canvas.drawText(countText, cx, baseline, bubbleText)

        // Under the parked orb; at high pressure the orb fills the screen, so sit above the nav bar.
        drawTally(canvas, w, if (p < 0.05f) parkY + minRadius * 1.22f + inset else h - 3f * inset)
    }

    private fun drawReel(canvas: Canvas, w: Float, h: Float) {
        if (cur[BARS] > 0f) {
            val barH = h * MAX_BAR * cur[BARS]
            canvas.drawRect(0f, 0f, w, barH, barPaint)
            canvas.drawRect(0f, h - barH, w, h, barPaint)
            val edge = 3f * density
            canvas.drawRect(0f, barH - edge, w, barH, edgePaint)
            canvas.drawRect(0f, h - barH, w, h - barH + edge, edgePaint)
            drawSprockets(canvas, w, h, barH)
        }
        canvas.drawCircle(w - inset - reelR, statusBarH + inset + reelR, reelR * 1.14f, reelBack)
        painter.draw(
            canvas,
            cx = w - inset - reelR,
            cy = statusBarH + inset + reelR,
            r = reelR,
            used = cur[USED],
            spinDeg = cur[SPIN],
            framesLeft = framesLeft
        )
        // Below the corner reel; clear of the top letterbox bar's sprockets only when bar is thin,
        // so drop below the bar if it is taller.
        val reelBottom = statusBarH + inset + reelR * 2f * 1.07f
        drawTally(canvas, w, max(reelBottom + 16f * density, h * MAX_BAR * cur[BARS] + 24f * density))
    }

    // Sprocket holes along both bars. They travel with the spin, so each reel you scroll
    // advances the film by one frame, like the strip under the corner reel.
    private fun drawSprockets(canvas: Canvas, w: Float, h: Float, barH: Float) {
        val hw = 10f * density
        val hh = 14f * density
        if (barH < hh + 12f * density) return
        val pitch = 30f * density
        val shift = (cur[SPIN] / 30f * pitch) % pitch
        val topY = (barH - hh) / 2f
        val botY = h - barH + (barH - hh) / 2f
        var x = -pitch + shift
        while (x < w) {
            canvas.drawRoundRect(x, topY, x + hw, topY + hh, 2f * density, 2f * density, holePaint)
            canvas.drawRoundRect(x, botY, x + hw, botY + hh, 2f * density, 2f * density, holePaint)
            x += pitch
        }
    }

    // Dive computer: depth in metres (1 reel = 1 m), reels left to the limit, and a depth gauge
    // down the screen edge. Turns red in the last third.
    private fun drawDepth(canvas: Canvas, w: Float, h: Float) {
        val used = cur[USED]
        val danger = used >= 0.7f
        val bw = 112f * density
        val bh = 60f * density
        val x = w - inset - bw
        val y = statusBarH + inset
        canvas.drawRoundRect(x, y, x + bw, y + bh, 16f * density, 16f * density, panelPaint)

        depthPaint.color = if (danger) RED_DEPTH else BONE
        canvas.drawText(depthText, x + 14f * density, y + 36f * density, depthPaint)
        canvas.drawText(leftText, x + 14f * density, y + bh - 10f * density, leftPaint)

        val gx = w - 7f * density
        val gt = y + bh + 12f * density
        val gh = h * 0.35f
        val gw = 5f * density
        val cr = gw / 2f
        gaugePaint.color = Color.argb(70, 237, 235, 230)
        canvas.drawRoundRect(gx, gt, gx + gw, gt + gh, cr, cr, gaugePaint)
        gaugePaint.color = if (danger) RED_DEPTH else TEAL
        canvas.drawRoundRect(gx, gt, gx + gw, gt + max(gw, gh * used), cr, cr, gaugePaint)

        // Below the dive panel; the gauge hugs the screen edge (7dp) so the chip stays left of it.
        drawTally(canvas, w - 8f * density, y + bh + 24f * density)
    }

    // Swallow touches so the overlay never leaks a half-handled gesture; the window flag already
    // decides whether touches reach us at all.
    override fun onTouchEvent(event: MotionEvent): Boolean = true

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        // Slots in cur/from/to; N is the slot count.
        const val N = 5
        const val P = 0    // pressure 0..1 (bubble growth, warning intensity)
        const val HUE = 1  // bubble/gauge hue in degrees
        const val USED = 2 // fraction of the reel budget used, 0..1
        const val SPIN = 3 // reel rotation in degrees (30 per reel counted)
        const val BARS = 4 // letterbox closure 0..1, starts once USED passes BARS_FROM

        const val HUE_CALM = 210f
        const val HUE_AMBER = 40f
        const val BARS_FROM = 0.55f
        const val MAX_BAR = 0.11f // fraction of screen height per letterbox bar at full closure

        val BONE = Color.rgb(237, 235, 230)
        val RED = Color.rgb(217, 58, 43)
        val RED_DEPTH = Color.rgb(255, 107, 87)
        val TEAL = Color.rgb(94, 196, 182)
    }
}
