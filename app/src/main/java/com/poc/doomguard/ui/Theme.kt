package com.poc.doomguard.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.poc.doomguard.data.Template
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Everything a template changes: field colours, ink, accent, type, shape language and the drawn
 * backdrop. The whole app (home, picker, buttons, slider, break screen) reads from this, so
 * switching template changes the room, not just the hero.
 */
data class DgTheme(
    val bg: Color,
    val bgDeep: Color,
    val surface: Color,
    val ink: Color,
    val inkSoft: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val display: FontFamily,
    val displayWeight: FontWeight,
    val body: FontFamily,
    val heading: FontFamily,
    val headingWeight: FontWeight,
    val button: Shape,
    val card: Shape
) {
    fun displayStyle(size: TextUnit) =
        TextStyle(fontFamily = display, fontWeight = displayWeight, fontSize = size)

    fun headingStyle(size: TextUnit) =
        TextStyle(fontFamily = heading, fontWeight = headingWeight, fontSize = size)

    fun bodyStyle(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = body, fontWeight = weight, fontSize = size)
}

object DgThemes {
    private val Moon = Color(0xFFF2C879)
    private val Cream = Color(0xFFF3EFE6)

    // Bubble: a quiet dusk. Soft rounded shapes, a serif voice, one warm moon-coloured accent.
    val Bubble = DgTheme(
        bg = Color(0xFF0F1E3A),
        bgDeep = Color(0xFF2A4A78),
        surface = Color.White.copy(alpha = 0.10f),
        ink = Cream,
        inkSoft = Cream.copy(alpha = 0.78f),
        accent = Moon,
        onAccent = Color(0xFF0F1E3A),
        danger = Color(0xFFFF8F78),
        display = FontFamily.Serif,
        displayWeight = FontWeight.Medium,
        body = FontFamily.SansSerif,
        heading = FontFamily.Serif,
        headingWeight = FontWeight.Medium,
        button = RoundedCornerShape(percent = 50),
        card = RoundedCornerShape(26.dp)
    )

    // Reel: the original film look. Flat, square, ink on bone, signal red only for data.
    val Reel = DgTheme(
        bg = Film.Bone,
        bgDeep = Film.Bone,
        surface = Film.BoneDeep,
        ink = Film.Ink,
        inkSoft = Film.Ink,
        accent = Film.Ink,
        onAccent = Film.Bone,
        danger = Film.Red,
        display = Film.Display,
        displayWeight = FontWeight.Normal,
        body = FontFamily.SansSerif,
        heading = FontFamily.SansSerif,
        headingWeight = FontWeight.Bold,
        button = RectangleShape,
        card = RectangleShape
    )

    // Depth: a dive computer under water. Monospaced readouts, teal accent, rounded instrument panels.
    val Depth = DgTheme(
        bg = Color(0xFF0F3A50),
        bgDeep = Color(0xFF05141C),
        surface = Color.White.copy(alpha = 0.08f),
        ink = Film.Foam,
        inkSoft = Film.Foam.copy(alpha = 0.8f),
        accent = Film.Teal,
        onAccent = Color(0xFF05141C),
        danger = Color(0xFFFF6B57),
        display = FontFamily.Monospace,
        displayWeight = FontWeight.Bold,
        body = FontFamily.SansSerif,
        heading = FontFamily.SansSerif,
        headingWeight = FontWeight.SemiBold,
        button = RoundedCornerShape(14.dp),
        card = RoundedCornerShape(18.dp)
    )

    fun of(t: Template) = when (t) {
        Template.BUBBLE -> Bubble
        Template.REEL -> Reel
        Template.DEPTH -> Depth
    }
}

val LocalDg = staticCompositionLocalOf { DgThemes.Reel }

/** The target theme with its colours eased, so a template switch fades instead of snapping. */
@Composable
fun animatedTheme(target: DgTheme): DgTheme {
    val spec = tween<Color>(durationMillis = 500)
    val bg by animateColorAsState(target.bg, spec, label = "bg")
    val bgDeep by animateColorAsState(target.bgDeep, spec, label = "bgDeep")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val ink by animateColorAsState(target.ink, spec, label = "ink")
    val inkSoft by animateColorAsState(target.inkSoft, spec, label = "inkSoft")
    val accent by animateColorAsState(target.accent, spec, label = "accent")
    val onAccent by animateColorAsState(target.onAccent, spec, label = "onAccent")
    val danger by animateColorAsState(target.danger, spec, label = "danger")
    return target.copy(
        bg = bg, bgDeep = bgDeep, surface = surface, ink = ink, inkSoft = inkSoft,
        accent = accent, onAccent = onAccent, danger = danger
    )
}

/** The full-screen scenery behind a template. Drawn in code, so it costs no assets. */
@Composable
fun DgBackdrop(template: Template, modifier: Modifier = Modifier.fillMaxSize()) {
    Crossfade(template, animationSpec = tween(500), modifier = modifier, label = "backdrop") { t ->
        when (t) {
            Template.BUBBLE -> BubbleBackdrop()
            Template.REEL -> ReelBackdrop()
            Template.DEPTH -> DepthBackdrop()
        }
    }
}

private class Star(val x: Float, val y: Float, val r: Float, val a: Float)

private val Stars = Random(42).let { rnd ->
    List(48) { Star(rnd.nextFloat(), rnd.nextFloat() * 0.72f, 0.6f + rnd.nextFloat() * 1.5f, 0.25f + rnd.nextFloat() * 0.6f) }
}

@Composable
private fun loopingPhase(millis: Int, label: String) =
    rememberInfiniteTransition(label = label).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart),
        label = label
    )

private fun DrawScope.wave(baseY: Float, amp: Float, cycles: Float, phase: Float, color: Color, fromTop: Boolean) {
    val p = Path()
    val edge = if (fromTop) 0f else size.height
    p.moveTo(0f, edge)
    var x = 0f
    val step = 10.dp.toPx()
    while (x <= size.width + step) {
        p.lineTo(x, baseY + amp * sin((x / size.width * cycles + phase) * 2f * PI.toFloat()))
        x += step
    }
    p.lineTo(size.width, edge)
    p.close()
    drawPath(p, color)
}

@Composable
private fun BubbleBackdrop() {
    val twinkle = loopingPhase(6000, "twinkle")
    val theme = DgThemes.Bubble
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(theme.bg, theme.bgDeep)))
        val t = twinkle.value
        Stars.forEachIndexed { i, s ->
            val a = s.a * (0.65f + 0.35f * sin((t + i * 0.137f) * 2f * PI.toFloat()))
            drawCircle(Color(0xFFF3EFE6).copy(alpha = a), s.r.dp.toPx(), Offset(s.x * size.width, s.y * size.height))
        }
        // Crescent moon: a disc with a bite taken out of it.
        val c = Offset(size.width * 0.84f, size.height * 0.15f)
        val r = 20.dp.toPx()
        val disc = Path().apply { addOval(Rect(c, r)) }
        val bite = Path().apply { addOval(Rect(c + Offset(r * 0.5f, -r * 0.2f), r * 0.85f)) }
        drawPath(Path.combine(PathOperation.Difference, disc, bite), Color(0xFFF2C879))
        // Two layers of hills so the bottom of the screen has depth.
        wave(size.height - 90.dp.toPx(), 14.dp.toPx(), 1.2f, 0.15f, Color(0xFF1F3C66).copy(alpha = 0.85f), fromTop = false)
        wave(size.height - 52.dp.toPx(), 10.dp.toPx(), 1.7f, 0.6f, Color(0xFF15294A), fromTop = false)
    }
}

@Composable
private fun ReelBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Film.Bone)
        // Sprocket holes down both edges, like the margins of a film strip.
        val w = 9.dp.toPx()
        val h = 14.dp.toPx()
        var y = 8.dp.toPx()
        while (y < size.height) {
            drawRoundRect(Film.BoneDeep, Offset(6.dp.toPx(), y), Size(w, h), CornerRadius(2.dp.toPx()))
            drawRoundRect(Film.BoneDeep, Offset(size.width - 6.dp.toPx() - w, y), Size(w, h), CornerRadius(2.dp.toPx()))
            y += 30.dp.toPx()
        }
    }
}

@Composable
private fun DepthBackdrop() {
    val drift = loopingPhase(14000, "drift")
    val rise = loopingPhase(22000, "rise")
    val theme = DgThemes.Depth
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(theme.bg, theme.bgDeep)))
        // The surface, seen from below: two slow bands of light.
        val d = drift.value
        wave(70.dp.toPx(), 7.dp.toPx(), 1.5f, d, Film.Teal.copy(alpha = 0.10f), fromTop = true)
        wave(54.dp.toPx(), 9.dp.toPx(), 1.1f, -d + 0.3f, Film.Teal.copy(alpha = 0.08f), fromTop = true)
        // Depth ruler on the right edge: a longer tick every fifth.
        var i = 0
        var y = 0f
        val tick = 24.dp.toPx()
        while (y < size.height) {
            val len = if (i % 5 == 0) 14.dp.toPx() else 7.dp.toPx()
            drawLine(Film.Foam.copy(alpha = 0.32f), Offset(size.width - len, y), Offset(size.width, y), 1.5.dp.toPx())
            y += tick
            i++
        }
        // Air bubbles drifting up.
        val ring = Stroke(1.2.dp.toPx())
        for (k in 0 until 9) {
            val p = (rise.value + k / 9f) % 1f
            val x = size.width * (0.12f + 0.78f * ((k * 37 % 100) / 100f)) + 6.dp.toPx() * sin((p * 3f + k) * 2f * PI.toFloat())
            drawCircle(Film.Foam.copy(alpha = 0.22f * (1f - p)), (3 + k % 4).dp.toPx(), Offset(x, size.height * (1.05f - 1.1f * p)), style = ring)
        }
    }
}
