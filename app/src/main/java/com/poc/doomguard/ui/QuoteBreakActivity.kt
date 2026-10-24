package com.poc.doomguard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.poc.doomguard.data.Quotes
import com.poc.doomguard.engine.PressureEngine
import kotlinx.coroutines.delay

// Checkpoint screen: launched by the service once the count reaches threshold + 10.
// Blocks the screen for 10s with a positive quote, then resets the reel count. No mood card.
class QuoteBreakActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Own task (taskAffinity="" in the manifest): removing it returns to Instagram, not MainActivity.
        setContent { QuoteBreakScreen(onDone = { finishAndRemoveTask() }) }
    }
}

private const val TOTAL_SECONDS = 10
private val BgColor = Color(0xFF0B1220)
private val AccentColor = Color(0xFF5EC4B6)

@Composable
private fun QuoteBreakScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val quote = rememberSaveable { Quotes.next(ctx) }

    // Restart token: bumped on every ON_START so the countdown LaunchedEffect restarts.
    var runToken by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(TOTAL_SECONDS) }
    var finished by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (!finished) {
                        remaining = TOTAL_SECONDS
                        runToken = -1 // cancels the running effect so it can't finish in the background
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (!finished) {
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
        if (runToken <= 0 || finished) return@LaunchedEffect
        remaining = TOTAL_SECONDS
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (!finished) {
            finished = true
            PressureEngine.reset()
        }
    }

    BackHandler(enabled = true) { }

    Box(
        modifier = Modifier.fillMaxSize().background(BgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                val progress = remaining / TOTAL_SECONDS.toFloat()
                Canvas(modifier = Modifier.size(190.dp)) {
                    val stroke = 8.dp.toPx()
                    drawCircle(color = Color.White.copy(alpha = 0.12f), style = Stroke(width = stroke))
                    drawArc(
                        color = if (finished) AccentColor else Color.White,
                        startAngle = -90f,
                        sweepAngle = if (finished) 360f else 360f * progress,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = if (finished) "✓" else remaining.toString(),
                    color = Color.White,
                    fontSize = 44.sp
                )
            }
            Text(
                text = "“$quote”",
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 40.dp)
            )
            if (finished) {
                Text(
                    text = "Your reel count is reset.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor.copy(alpha = 0.35f)),
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    Text("Continue", fontSize = 18.sp, color = Color.White)
                }
            }
        }
    }
}
