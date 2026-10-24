package com.poc.doomguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poc.doomguard.data.SessionLog
import com.poc.doomguard.engine.PressureEngine
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    private var overlayGranted by mutableStateOf(false)
    private var accessibilityEnabled by mutableStateOf(false)
    private var recentLog by mutableStateOf<List<SessionLog.Entry>>(emptyList())

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionLog.init(applicationContext)
        PressureEngine.threshold = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD).coerceIn(3, 50)
        refreshStatus()

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    // Re-check permissions every time we come back from system settings.
    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        overlayGranted = Settings.canDrawOverlays(this)
        accessibilityEnabled = isAccessibilityServiceEnabled()
        recentLog = SessionLog.all().takeLast(3).reversed()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val short = "com.poc.doomguard/.sensor.ScrollSensorService"
        val full = "com.poc.doomguard/com.poc.doomguard.sensor.ScrollSensorService"
        return enabled.split(':').any {
            it.equals(short, ignoreCase = true) || it.equals(full, ignoreCase = true)
        }
    }

    @Composable
    private fun MainScreen() {
        val snap by PressureEngine.snap.collectAsState()
        var threshold by remember { mutableIntStateOf(PressureEngine.threshold) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("DoomGuard", style = MaterialTheme.typography.headlineMedium)

            StatusRow(
                label = "Overlay permission",
                ok = overlayGranted,
                buttonText = "Grant",
                onClick = {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            )
            StatusRow(
                label = "Accessibility service",
                ok = accessibilityEnabled,
                buttonText = "Open settings",
                onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            )

            Column {
                Text("Threshold: $threshold reels", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = threshold.toFloat(),
                    onValueChange = {
                        threshold = it.toInt()
                        PressureEngine.threshold = threshold
                    },
                    onValueChangeFinished = {
                        prefs.edit().putInt(KEY_THRESHOLD, threshold).apply()
                    },
                    valueRange = 3f..50f,
                    steps = 46
                )
            }

            Column {
                Text("Live", style = MaterialTheme.typography.titleMedium)
                Text("Count: ${snap.count.toInt()}")
                Text("State: ${snap.state}")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    PressureEngine.reset()
                    refreshStatus()
                }) { Text("Reset session (debug)") }
            }

            Column {
                Text("Recent sessions", style = MaterialTheme.typography.titleMedium)
                if (recentLog.isEmpty()) {
                    Text("None yet")
                } else {
                    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    recentLog.forEach { e ->
                        val mood = when (e.mood) { 0 -> "neutral"; 1 -> "flat"; else -> "low" }
                        Text("${fmt.format(Date(e.ts))}: ${e.reels} reels, ${e.durationMs / 1000}s, $mood")
                    }
                }
            }

            Text(
                "Nothing leaves your device. DoomGuard only reads scroll events from Instagram.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    @Composable
    private fun StatusRow(label: String, ok: Boolean, buttonText: String, onClick: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(if (ok) "Granted" else "Not granted")
            }
            if (ok) {
                OutlinedButton(onClick = onClick) { Text(buttonText) }
            } else {
                Button(onClick = onClick) { Text(buttonText) }
            }
        }
    }

    private companion object {
        const val PREFS = "doomguard_prefs"
        const val KEY_THRESHOLD = "threshold"
        const val DEFAULT_THRESHOLD = 25
    }
}
