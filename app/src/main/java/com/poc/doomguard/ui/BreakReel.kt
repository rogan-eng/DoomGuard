package com.poc.doomguard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Reel template: a flat red field with the seconds as one huge numeral; the strip of frames you
// just ran clears as the countdown goes. At zero the field turns to ink: "THE END".
@Composable
fun BreakReel(s: BreakState, onDone: () -> Unit) {
    val field by animateColorAsState(if (s.finished) Film.Ink else Film.Red, tween(400), label = "field")
    val text = if (s.finished) Film.Bone else Film.Ink

    Box(Modifier.fillMaxSize().background(field)) {
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
                    text = if (s.finished) "THE END" else s.remaining.toString(),
                    style = TextStyle(
                        fontFamily = Film.Display,
                        fontSize = if (s.finished) 120.sp else 300.sp,
                        lineHeight = if (s.finished) 110.sp else 270.sp
                    ),
                    color = text
                )
                Text(
                    text = "“${s.quote}”",
                    style = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
                    color = text,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }

            Column {
                Text(
                    text = if (s.finished) "That was ${s.reelsRun} reels. A fresh reel is loaded." else "${s.reelsRun} reels this run",
                    style = TextStyle(fontSize = 18.sp),
                    color = text,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                FrameStrip(
                    total = s.total,
                    used = s.total * s.left,
                    usedColor = Film.Ink,
                    emptyColor = Film.Bone.copy(alpha = if (s.finished) 0.2f else 0.35f)
                )
                if (s.finished) {
                    Box(
                        Modifier
                            .padding(top = 24.dp)
                            .heightIn(min = 56.dp)
                            .background(Film.Bone)
                            .clickable(onClick = onDone)
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Load a new reel",
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            color = Film.Ink
                        )
                    }
                }
            }
        }
    }
}
