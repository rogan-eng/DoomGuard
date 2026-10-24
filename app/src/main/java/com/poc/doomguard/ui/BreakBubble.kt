package com.poc.doomguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poc.doomguard.data.Template

// Bubble template: the dusk scene from the home screen, a countdown ring around the number, a quote.
@Composable
fun BreakBubble(s: BreakState, onDone: () -> Unit) {
    val t = DgThemes.Bubble
    Box(Modifier.fillMaxSize()) {
        DgBackdrop(Template.BUBBLE)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
                Canvas(modifier = Modifier.size(196.dp)) {
                    val stroke = 8.dp.toPx()
                    drawCircle(color = t.ink.copy(alpha = 0.14f), style = Stroke(width = stroke))
                    drawArc(
                        color = t.accent,
                        startAngle = -90f,
                        sweepAngle = if (s.finished) 360f else 360f * s.left,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = if (s.finished) "✓" else s.remaining.toString(),
                    color = t.ink,
                    style = t.displayStyle(64.sp)
                )
            }
            Text(
                text = "“${s.quote}”",
                color = t.ink,
                style = t.headingStyle(26.sp).copy(lineHeight = 36.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 40.dp)
            )
            s.summary?.let { sum ->
                Text(
                    text = sum.line,
                    color = t.ink,
                    style = t.bodyStyle(16.sp, FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Text(
                    text = "${sum.totalLabel} · ${sum.sourceLabel}",
                    color = t.inkSoft,
                    style = t.bodyStyle(13.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (s.finished) {
                Text(
                    text = "Your reel count is reset.",
                    color = t.inkSoft,
                    style = t.bodyStyle(16.sp),
                    modifier = Modifier.padding(top = 24.dp)
                )
                Box(
                    Modifier
                        .padding(top = 24.dp)
                        .heightIn(min = 52.dp)
                        .clip(t.button)
                        .background(t.accent)
                        .clickable(onClick = onDone)
                        .padding(horizontal = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Continue", style = t.bodyStyle(18.sp, FontWeight.SemiBold), color = t.onAccent)
                }
            }
        }
    }
}
