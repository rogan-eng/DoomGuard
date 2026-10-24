package com.poc.doomguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.poc.doomguard.data.AiConsent
import com.poc.doomguard.data.SessionLog
import com.poc.doomguard.data.Template
import com.poc.doomguard.data.TemplateStore
import com.poc.doomguard.engine.Checkpoint
import com.poc.doomguard.engine.PressureEngine
import com.poc.doomguard.overlay.ReelPainter
import com.poc.doomguard.ui.DgBackdrop
import com.poc.doomguard.ui.DgThemes
import com.poc.doomguard.ui.Film
import com.poc.doomguard.ui.FrameStrip
import com.poc.doomguard.ui.LocalDg
import com.poc.doomguard.ui.animatedTheme
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var overlayGranted by mutableStateOf(false)
    private var accessibilityEnabled by mutableStateOf(false)
    private var recentLog by mutableStateOf<List<SessionLog.Entry>>(emptyList())
    private var template by mutableStateOf(Template.REEL)
    private var aiConsent by mutableStateOf(false)

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionLog.init(applicationContext)
        PressureEngine.threshold = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD).coerceIn(3, 50)
        refreshStatus()

        setContent {
            val theme = animatedTheme(DgThemes.of(template))
            // Dark scenes want light status icons; the bone Reel screen wants dark ones.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                    template == Template.REEL
            }
            CompositionLocalProvider(LocalDg provides theme) {
                MaterialTheme(colorScheme = FilmColors) {
                    Surface(Modifier.fillMaxSize(), color = theme.bg) {
                        Box(Modifier.fillMaxSize()) {
                            DgBackdrop(template)
                            MainScreen()
                        }
                    }
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
        template = TemplateStore.get(this)
        aiConsent = AiConsent.get(this)
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
        val t = LocalDg.current
        val snap by PressureEngine.snap.collectAsState()
        var threshold by remember { mutableIntStateOf(PressureEngine.threshold) }
        val total = Checkpoint.total(threshold)
        val ready = overlayGranted && accessibilityEnabled

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DoomGuard", style = t.displayStyle(30.sp), color = t.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(t.button)
                            .background(if (ready) t.accent else t.danger)
                    )
                    Text(
                        if (ready) "Watching Instagram" else "Not watching",
                        style = t.bodyStyle(15.sp, FontWeight.SemiBold),
                        color = if (ready) t.ink else t.danger,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // The counter follows the chosen look, like the overlay and the break screen do.
            Spacer(Modifier.height(20.dp))
            when (template) {
                Template.BUBBLE -> BubbleHero(snap.count, snap.pressure, snap.state, threshold)
                Template.REEL -> ReelHero(snap.count, total)
                Template.DEPTH -> DepthHero(snap.count, total)
            }

            Spacer(Modifier.height(32.dp))
            Text("Break after $total reels", style = t.headingStyle(22.sp), color = t.ink)
            Text(
                "Warning builds from $threshold",
                style = t.bodyStyle(16.sp),
                color = t.inkSoft,
                modifier = Modifier.padding(top = 2.dp)
            )
            Slider(
                value = threshold.toFloat(),
                onValueChange = {
                    threshold = it.roundToInt()
                    PressureEngine.threshold = threshold
                },
                onValueChangeFinished = {
                    prefs.edit().putInt(KEY_THRESHOLD, threshold).apply()
                },
                valueRange = 3f..50f,
                colors = SliderDefaults.colors(
                    thumbColor = t.accent,
                    activeTrackColor = t.accent,
                    inactiveTrackColor = t.ink.copy(alpha = 0.2f)
                )
            )

            Spacer(Modifier.height(24.dp))
            Text("Choose your look", style = t.headingStyle(22.sp), color = t.ink)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Template.entries.forEach { tpl ->
                    TemplateTile(tpl, selected = tpl == template, modifier = Modifier.weight(1f)) {
                        template = tpl
                        TemplateStore.set(this@MainActivity, tpl)
                    }
                }
            }
            Text(
                template.blurb,
                style = t.bodyStyle(15.sp),
                color = t.inkSoft,
                modifier = Modifier.padding(top = 10.dp)
            )

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Smarter reel categories", style = t.headingStyle(22.sp), color = t.ink)
                    Text(
                        if (aiConsent) "On: at each break, the creators, songs and captions of that session's reels are sent to Google (Gemini) once to sort them."
                        else "Off: reels are sorted by a keyword list on your phone. Turn on to send that session's creators, songs and captions to Google (Gemini) once, at each break, for better results.",
                        style = t.bodyStyle(15.sp),
                        color = t.inkSoft,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Switch(
                    checked = aiConsent,
                    onCheckedChange = {
                        aiConsent = it
                        AiConsent.set(this@MainActivity, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = t.bg,
                        checkedTrackColor = t.accent,
                        uncheckedThumbColor = t.ink,
                        uncheckedTrackColor = t.ink.copy(alpha = 0.2f)
                    )
                )
            }

            Spacer(Modifier.height(24.dp))
            if (!ready) {
                Text("Two things to switch on", style = t.headingStyle(22.sp), color = t.ink)
                Spacer(Modifier.height(8.dp))
                SetupRow(
                    label = "Show the reel over Instagram",
                    done = overlayGranted,
                    action = "Allow",
                    onClick = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }
                )
                SetupRow(
                    label = "Count reels as you swipe",
                    done = accessibilityEnabled,
                    action = "Open settings",
                    onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
                Spacer(Modifier.height(20.dp))
            }

            ThemedButton("Rewind the reel", filled = false) {
                PressureEngine.reset()
                refreshStatus()
            }

            if (recentLog.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text("Recent runs", style = t.headingStyle(22.sp), color = t.ink)
                val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                recentLog.forEach { e ->
                    Text(
                        "${fmt.format(Date(e.ts))}: ${e.reels} reels in ${e.durationMs / 1000}s",
                        style = t.bodyStyle(16.sp),
                        color = t.ink,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Text(
                if (aiConsent) "Only reel text (creator, song, caption) leaves your phone, once at each break, and only because you switched on smarter categories above."
                else "Nothing leaves your phone. DoomGuard only reads scroll events from Instagram.",
                style = t.bodyStyle(14.sp),
                color = t.inkSoft,
                modifier = Modifier.padding(top = 32.dp)
            )
            // Room above the drawn hills / waves so the last line never sits on them.
            Spacer(Modifier.height(64.dp))
        }
    }

    @Composable
    private fun SetupRow(label: String, done: Boolean, action: String, onClick: () -> Unit) {
        val t = LocalDg.current
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = t.bodyStyle(16.sp), color = t.ink, modifier = Modifier.weight(1f))
            if (done) {
                Text("On", style = t.bodyStyle(16.sp, FontWeight.Bold), color = t.accent)
            } else {
                ThemedButton(action, filled = true, onClick = onClick)
            }
        }
    }

    @Composable
    private fun ReelHero(count: Float, total: Int) {
        val t = LocalDg.current
        val left = max(0, total - count.roundToInt())
        Column {
            Text(
                left.toString(),
                style = t.displayStyle(210.sp).copy(lineHeight = 190.sp),
                color = t.ink
            )
            Text(
                if (left == 1) "reel until your next break" else "reels until your next break",
                style = t.bodyStyle(20.sp),
                color = t.ink
            )
            Spacer(Modifier.height(20.dp))
            FrameStrip(
                total = total,
                used = count,
                usedColor = t.danger,
                emptyColor = t.surface
            )
        }
    }

    // The orb grows and warms from blue through amber to red, as the overlay's bubble does,
    // and breathes slowly while it waits.
    @Composable
    private fun BubbleHero(count: Float, pressure: Float, state: PressureEngine.State, threshold: Int) {
        val t = LocalDg.current
        val aware = pressure == 0f && (state == PressureEngine.State.AWARE || count > 0.6f * threshold)
        val hue = when {
            pressure > 0f -> 40f * (1f - pressure)
            aware -> 40f
            else -> 210f
        }
        val mood = when {
            pressure > 0f -> "Time to pause"
            aware -> "Getting warm"
            else -> "Calm"
        }
        val target = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.95f)))
        val color by animateColorAsState(target, tween(450), label = "orbColor")
        val diameter by animateFloatAsState(150f + 100f * pressure, tween(450), label = "orbSize")
        val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathe"
        )
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(330.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = diameter.dp.toPx() / 2f * breathe
                    val c = center
                    drawCircle(color.copy(alpha = 0.10f), r * 1.5f, c)
                    drawCircle(color.copy(alpha = 0.16f), r * 1.22f, c)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(lerp(color, Color.White, 0.4f), color, lerp(color, Color.Black, 0.3f)),
                            center = Offset(c.x - r * 0.3f, c.y - r * 0.35f),
                            radius = r * 1.5f
                        ),
                        r, c
                    )
                    drawCircle(Color.White.copy(alpha = 0.3f), r * 0.16f, Offset(c.x - r * 0.4f, c.y - r * 0.44f))
                }
                Text(count.roundToInt().toString(), style = t.displayStyle(88.sp), color = Color.White)
            }
            Text("reels this run", style = t.headingStyle(22.sp), color = t.ink)
            Text(mood, style = t.bodyStyle(16.sp), color = t.inkSoft, modifier = Modifier.padding(top = 2.dp))
        }
    }

    // A dive-computer panel: depth in metres (1 reel = 1 m), reels left to the limit, depth gauge.
    @Composable
    private fun DepthHero(count: Float, total: Int) {
        val t = LocalDg.current
        val used = (count / total).coerceIn(0f, 1f)
        val danger = used >= 0.7f
        Column(
            Modifier
                .fillMaxWidth()
                .clip(t.card)
                .background(t.surface)
                .padding(20.dp)
        ) {
            Text("Depth", style = t.bodyStyle(16.sp, FontWeight.SemiBold), color = t.accent)
            Row {
                Text(
                    count.roundToInt().toString(),
                    style = t.displayStyle(132.sp).copy(lineHeight = 140.sp),
                    color = if (danger) t.danger else t.ink,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    "m",
                    style = t.displayStyle(36.sp),
                    color = t.ink,
                    modifier = Modifier.alignByBaseline().padding(start = 8.dp)
                )
            }
            Text(
                "${max(0, total - count.roundToInt())} reels to the limit",
                style = t.bodyStyle(18.sp),
                color = t.ink
            )
            Spacer(Modifier.height(16.dp))
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                val corner = CornerRadius(size.height / 2f)
                drawRoundRect(t.ink.copy(alpha = 0.18f), Offset.Zero, size, corner)
                if (used > 0f) {
                    drawRoundRect(
                        if (danger) t.danger else t.accent,
                        Offset.Zero,
                        Size(max(size.height, size.width * used), size.height),
                        corner
                    )
                }
            }
        }
    }

    // Each tile is a small preview of its own look: its field, its type, its shape.
    @Composable
    private fun TemplateTile(tpl: Template, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val t = LocalDg.current
        val tt = DgThemes.of(tpl)
        Column(
            modifier
                .clip(tt.card)
                .background(Brush.verticalGradient(listOf(tt.bg, tt.bgDeep)))
                .then(if (selected) Modifier.border(3.dp, t.accent, tt.card) else Modifier)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.height(64.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (tpl) {
                    Template.BUBBLE -> Canvas(Modifier.size(52.dp)) {
                        val r = size.minDimension / 2f
                        val orb = Color(0xFF7FA8E8)
                        drawCircle(orb.copy(alpha = 0.18f), r)
                        drawCircle(
                            Brush.radialGradient(
                                listOf(lerp(orb, Color.White, 0.4f), orb, lerp(orb, Color.Black, 0.3f)),
                                center = Offset(center.x - r * 0.2f, center.y - r * 0.25f),
                                radius = r
                            ),
                            r * 0.72f
                        )
                    }
                    Template.REEL -> {
                        val d = LocalDensity.current
                        val painter = remember { ReelPainter(d.density) }
                        Canvas(Modifier.size(56.dp)) {
                            drawIntoCanvas {
                                painter.draw(
                                    it.nativeCanvas, size.width / 2f, size.height / 2f, 22.dp.toPx(),
                                    0.4f, 0f, 14, withStrip = false
                                )
                            }
                        }
                    }
                    Template.DEPTH -> Text("12 m", style = tt.displayStyle(24.sp), color = tt.accent)
                }
            }
            Text(tpl.label, style = tt.bodyStyle(15.sp, FontWeight.SemiBold), color = tt.ink)
        }
    }

    // One primary style per look: the theme's own button shape and accent. Unfilled = a tone step.
    @Composable
    private fun ThemedButton(text: String, filled: Boolean, onClick: () -> Unit) {
        val t = LocalDg.current
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(t.button)
                .background(if (filled) t.accent else t.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                style = t.bodyStyle(16.sp, FontWeight.SemiBold),
                color = if (filled) t.onAccent else t.ink
            )
        }
    }

    private companion object {
        val FilmColors = lightColorScheme(
            primary = Film.Ink,
            onPrimary = Film.Bone,
            background = Film.Bone,
            onBackground = Film.Ink,
            surface = Film.Bone,
            onSurface = Film.Ink
        )
        const val PREFS = "doomguard_prefs"
        const val KEY_THRESHOLD = "threshold"
        const val DEFAULT_THRESHOLD = 25
    }
}
