package com.poc.doomguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.poc.doomguard.R
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** The app's whole look: ink, bone, one signal red. Red is only ever used on data. */
object Film {
    val Ink = Color(0xFF111111)
    val Bone = Color(0xFFEDEBE6)
    val BoneDeep = Color(0xFFDAD7CF)
    val Red = Color(0xFFD93A2B)

    // Depth template
    val Water = Color(0xFF0A2431)
    val Foam = Color(0xFFEDEBE6)
    val Teal = Color(0xFF5EC4B6)

    val Display = FontFamily(Font(R.font.anton))
}

/**
 * One cell per reel until the break: the strip of film this app is about. [used] is how many
 * frames have run (fractional, so a count can animate smoothly). Square cells, flat colour.
 */
@Composable
fun FrameStrip(
    total: Int,
    used: Float,
    usedColor: Color,
    emptyColor: Color,
    modifier: Modifier = Modifier
) {
    val cellW = 14.dp
    val cellH = 22.dp
    val gap = 4.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = max(1, floor((maxWidth + gap) / (cellW + gap)).toInt())
        val rows = ceil(total / cols.toFloat()).toInt()
        Canvas(Modifier.fillMaxWidth().height((cellH + gap) * rows - gap)) {
            val w = cellW.toPx()
            val h = cellH.toPx()
            val g = gap.toPx()
            for (i in 0 until total) {
                val x = (i % cols) * (w + g)
                val y = (i / cols) * (h + g)
                drawRect(emptyColor, Offset(x, y), Size(w, h))
                val fill = (used - i).coerceIn(0f, 1f)
                if (fill > 0f) drawRect(usedColor.copy(alpha = fill), Offset(x, y), Size(w, h))
            }
        }
    }
}
