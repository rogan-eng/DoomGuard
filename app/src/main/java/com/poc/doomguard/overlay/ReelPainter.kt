package com.poc.doomguard.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.ColorUtils

/**
 * Draws the film reel: a spool whose film disc shrinks as reels are watched, with a strip of
 * film feeding out below it. Flat shapes only. Shared by the overlay View and the Compose hero,
 * so it allocates nothing while drawing.
 */
class ReelPainter(private val density: Float) {

    private fun paint(argb: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb }

    private val body = paint(0xFF111111.toInt())
    private val hole = paint(0xFF111111.toInt())
    private val hub = paint(0xFF111111.toInt())
    private val film = paint(AMBER)
    private val strip = paint(0xFF111111.toInt())
    private val sprocket = paint(0xFFEDEBE6.toInt())
    private val text = paint(0xFFEDEBE6.toInt()).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    /**
     * @param used 0..1, how much of the film has run (0 = full spool, 1 = empty)
     * @param spinDeg total rotation; 30 degrees = one reel
     */
    fun draw(
        c: Canvas, cx: Float, cy: Float, r: Float,
        used: Float, spinDeg: Float, framesLeft: Int, withStrip: Boolean = true
    ) {
        if (withStrip) drawStrip(c, cx, cy, r, used, spinDeg)

        c.drawCircle(cx, cy, r, body)

        val hubR = r * 0.34f
        val filmR = hubR + (r * 0.9f - hubR) * (1f - used)
        film.color = ColorUtils.blendARGB(AMBER, CORAL, ((used - 0.5f) / 0.5f).coerceIn(0f, 1f))
        c.drawCircle(cx, cy, filmR, film)

        val holeR = r * 0.15f
        for (i in 0 until 6) {
            val a = Math.toRadians((spinDeg + i * 60f).toDouble())
            c.drawCircle(
                cx + (Math.cos(a) * r * 0.63f).toFloat(),
                cy + (Math.sin(a) * r * 0.63f).toFloat(),
                holeR, hole
            )
        }

        c.drawCircle(cx, cy, hubR, hub)
        text.textSize = r * 0.5f // fits the hub at any reel size
        val baseline = cy - (text.ascent() + text.descent()) / 2f
        c.drawText(framesLeft.toString(), cx, baseline, text)
    }

    // Film emerges from behind the reel and gets longer as reels are watched. Sprockets scroll
    // with the spin, so each swipe visibly advances the film by one frame.
    private fun drawStrip(c: Canvas, cx: Float, cy: Float, r: Float, used: Float, spinDeg: Float) {
        val w = r * 0.55f
        val left = cx - w / 2f
        val right = cx + w / 2f
        val top = cy
        val bottom = cy + r + used * r * 2.6f
        c.drawRect(left, top, right, bottom, strip)

        val pitch = r * 0.28f
        val sq = pitch * 0.45f
        val offset = (spinDeg / 30f * pitch) % pitch
        var y = top + r + offset
        while (y + sq <= bottom) {
            c.drawRect(left + w * 0.1f, y, left + w * 0.1f + sq, y + sq, sprocket)
            c.drawRect(right - w * 0.1f - sq, y, right - w * 0.1f, y + sq, sprocket)
            y += pitch
        }
    }

    private companion object {
        val AMBER = Color.parseColor("#EDEBE6")
        val CORAL = Color.parseColor("#D93A2B")
    }
}
