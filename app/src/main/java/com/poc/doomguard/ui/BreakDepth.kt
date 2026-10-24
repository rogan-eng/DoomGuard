package com.poc.doomguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import com.poc.doomguard.data.Template
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Water = Color(0xFF0A2431)
private val Foam = Color(0xFFEDEBE6)
private val Teal = Color(0xFF5EC4B6)

// Depth template: a dive computer. Your reels are metres of depth; the break is the safety stop
// where you ascend to the surface. The depth reading counts down to 0 as the seconds run.
@Composable
fun BreakDepth(s: BreakState, onDone: () -> Unit) {
    val depth = if (s.finished) 0 else (s.reelsRun * s.left).roundToInt()

    Box(Modifier.fillMaxSize()) {
        DgBackdrop(Template.DEPTH)
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column {
                Text(
                    text = if (s.finished) "Surface." else "Safety stop",
                    style = DgThemes.Depth.displayStyle(40.sp),
                    color = Teal
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = depth.toString(),
                        style = DgThemes.Depth.displayStyle(150.sp).copy(lineHeight = 150.sp),
                        color = Foam,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text = "m",
                        style = DgThemes.Depth.displayStyle(40.sp),
                        color = Foam,
                        modifier = Modifier.alignByBaseline().padding(start = 8.dp)
                    )
                }
                Text(
                    text = if (s.finished) "You are back at the surface." else "Ascending. ${s.remaining} s to go.",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = Foam
                )
                Text(
                    text = "“${s.quote}”",
                    style = TextStyle(fontSize = 24.sp, lineHeight = 32.sp),
                    color = Foam,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }

            Column {
                Text(
                    text = "You went ${s.reelsRun} reels deep.",
                    style = TextStyle(fontSize = 18.sp),
                    color = Foam,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                s.summary?.let { sum ->
                    Text(
                        text = sum.line,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                        color = Foam
                    )
                    Text(
                        text = "${sum.totalLabel} · ${sum.sourceLabel}",
                        style = TextStyle(fontSize = 13.sp),
                        color = Teal,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )
                }
                // Ascent bar: fills as you rise toward the surface.
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    drawRect(Foam.copy(alpha = 0.2f), Offset.Zero, size)
                    drawRect(Teal, Offset.Zero, Size(size.width * (1f - s.left), size.height))
                }
                if (s.finished) {
                    Box(
                        Modifier
                            .padding(top = 24.dp)
                            .heightIn(min = 56.dp)
                            .clip(DgThemes.Depth.button)
                            .background(Teal)
                            .clickable(onClick = onDone)
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Back to Instagram",
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            color = Water
                        )
                    }
                }
            }
        }
    }
}
