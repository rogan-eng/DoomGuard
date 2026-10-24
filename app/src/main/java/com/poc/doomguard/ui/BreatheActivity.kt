package com.poc.doomguard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.poc.doomguard.data.SessionLog
import com.poc.doomguard.engine.PressureEngine
import kotlinx.coroutines.delay

class BreatheActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionLog.init(this)
        setContent { BreatheScreen(onDone = { finish() }) }
    }
}

private const val TOTAL_SECONDS = 10
private val BgColor = Color(0xFF0B1220)
private val CircleColor = Color(0xFF5EC4B6)

@Composable
private fun BreatheScreen(onDone: () -> Unit) {
    // Restart token: bumped on every ON_START so the countdown LaunchedEffect restarts.
    var runToken by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(TOTAL_SECONDS) }
    var result by remember { mutableStateOf<PressureEngine.ResultOfSession?>(null) }
    var resetDone by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (result == null) {
                        remaining = TOTAL_SECONDS
                        runToken = -1 // cancels the running effect
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (result == null) {
                        remaining = TOTAL_SECONDS
                        runToken = runToken.coerceAtLeast(0) + 1
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(runToken) {
        if (runToken <= 0 || result != null) return@LaunchedEffect
        remaining = TOTAL_SECONDS
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (!resetDone) {
            resetDone = true
            result = PressureEngine.reset()
        }
    }

    BackHandler(enabled = true) { }

    Box(
        modifier = Modifier.fillMaxSize().background(BgColor),
        contentAlignment = Alignment.Center
    ) {
        val r = result
        if (r == null) {
            BreathingContent(remaining)
        } else {
            ReflectionCard(r, onDone)
        }
    }
}

@Composable
private fun BreathingContent(remaining: Int) {
    val transition = rememberInfiniteTransition(label = "breathe")
    val scale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Text toggles every 4s, in sync with the circle (starts on "in").
    var breatheIn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            breatheIn = !breatheIn
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale)
                    .background(CircleColor.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape)
            )
            val progress = remaining / TOTAL_SECONDS.toFloat()
            Canvas(modifier = Modifier.size(300.dp)) {
                val stroke = 8.dp.toPx()
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Text(
                text = remaining.toString(),
                color = Color.White,
                fontSize = 40.sp
            )
        }
        Text(
            text = if (breatheIn) "Breathe in" else "Breathe out",
            color = Color.White,
            fontSize = 28.sp,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

@Composable
private fun ReflectionCard(r: PressureEngine.ResultOfSession, onDone: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
    val minutes = maxOf(1L, r.durationMs / 60000L)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = "${r.reels} reels · $minutes min. What were you actually looking for?",
            color = Color.White,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 32.dp)
        ) {
            listOf("😐", "😶", "😞").forEachIndexed { mood, emoji ->
                Button(
                    onClick = {
                        if (!saved) {
                            saved = true
                            SessionLog.add(r.reels, r.durationMs, mood)
                            onDone()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CircleColor.copy(alpha = 0.3f))
                ) {
                    Text(emoji, fontSize = 32.sp)
                }
            }
        }
    }
}
