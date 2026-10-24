# DoomGuard — 2-Hour Android Build Scope

> Replaces the Chrome-extension plan. Source idea: `../Instagram doomscrolling control bubble - Claude.md`.
> Built against the existing scaffold in this folder (`com.poc.doomguard`, Kotlin 2.1.21, AGP 8.13, Compose BOM 2025.09, minSdk 26 / targetSdk 36, a "DoomGuard" hello-world `MainActivity`).

---

## 1. What it is (with a real example)

DoomGuard is a native Android app that watches the **real Instagram app**. It counts the reels you scroll, shows a floating bubble over Instagram, and grows the bubble (blue → red) once you pass your threshold. The bubble ends up covering the screen. At full size, DoomGuard pulls you out of Instagram into a 10-second breathing exercise, then shows a one-tap reflection ("What were you looking for?").

**What a judge sees on a real phone:**

| Reel # (demo threshold = 5) | What happens |
|---|---|
| 1–3 | Small blue bubble in a screen corner with the count. |
| 4–5 | Bubble turns amber (AWARE). |
| 6–9 | Bubble grows and turns red. It becomes touchable, so swipes that start on it are eaten (RESIST). |
| 10 | Bubble covers the screen → DoomGuard opens its breathing screen. Instagram goes to the background, so audio and video stop. |
| After 10s of breathing | Counter resets to 0. Reflection card: "11 reels · 3 min. What were you looking for?" + mood tap. |

**Pitch delta:** every existing tool (one sec, ScreenZen, Opal, Screen Time) guards the door. DoomGuard counts **reels, not minutes**, and applies friction **inside** the session. Don't claim to be the first anti-doomscrolling app. Claim "none of them sit in the room with you."

**Honesty guardrail:** the evidence is short-term and mostly self-report. Say "behavioral friction", never "treatment".

---

## 2. Scope decision

### The one hard technical truth

Android has no API to ask "what reel is the user on?" in another app. The only practical sensor is an **AccessibilityService** reading Instagram's UI events. Everything depends on whether those events are usable, so the first 15 minutes are a **spike**, not a build.

### IN (must ship)

1. Permission onboarding screen (overlay + accessibility), with live status
2. `AccessibilityService` filtered to `com.instagram.android`, counting reel advances
3. Pure-Kotlin pressure engine: `CALM → AWARE → RESIST → BLOCK`, threshold + span
4. Floating bubble overlay (custom `View`, not Compose) that grows and shifts blue → red
5. Bubble becomes touchable when `RESIST` and eats swipes (physical friction)
6. `BLOCK` → launches `BreatheActivity` (Compose), pulling Instagram to background
7. 10s breathing exercise → reset → reflection card with mood tap
8. Asymmetric decay: slow when idle inside Instagram, 5× faster when Instagram is not foreground
9. Session log in `SharedPreferences`
10. Backup screen recording

### OUT (say "v2" in the pitch)

- 1-in-N gesture swallowing with `dispatchGesture` (the "progressive scroll resistance" from the old plan; see stretch)
- Adaptive threshold, stats dashboards, streaks, accounts, backend, any network call
- Other apps (TikTok, YouTube Shorts). Design the engine to be app-agnostic, ship Instagram only
- Play Store compliance (see risks)
- Room / DataStore / Hilt / navigation libraries. Zero new dependencies

### Stretch (only if M3 is done before 1:30)

- Progressive resistance: in `RESIST`, a transparent touch-eating overlay lets 1 of N swipes through by re-injecting it via `dispatchGesture` (needs `canPerformGestures="true"`). Risky. Don't attempt before M3 is solid.

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────────┐
│ SENSOR  ScrollSensorService : AccessibilityService      │
│  • filter packageName == com.instagram.android          │
│  • TYPE_VIEW_SCROLLED → debounced → "reel advanced"     │
│  • TYPE_WINDOW_STATE_CHANGED → "IG foreground?"         │
└──────────────┬──────────────────────────────────────────┘
               │ onReel() / onForeground(Boolean)
┌──────────────▼──────────────────────────────────────────┐
│ ENGINE  PressureEngine (pure Kotlin object, no Android) │
│  count → pressure p∈[0,1] → state; tick() decay         │
│  exposes StateFlow<Snapshot>                            │
└───────┬───────────────────────┬─────────────────────────┘
        ▼                       ▼
  BubbleOverlay            BreatheActivity (Compose)
  (WindowManager +         10s breathing → engine.reset()
   custom View)            → reflection → SessionLog
```

The engine has no Android dependencies, so it is unit-testable and portable. That is your architecture-slide story: the sensor is platform-specific, the engine isn't.

### Process model
Accessibility service, overlay and activities all run in one process, so `PressureEngine` as a Kotlin `object` is shared state. No IPC, no bound service.

### Files to add (package `com.poc.doomguard`)

```
engine/PressureEngine.kt        pure Kotlin
sensor/ScrollSensorService.kt   AccessibilityService
overlay/BubbleOverlay.kt        WindowManager + custom View
ui/MainActivity.kt              (exists) onboarding + threshold slider
ui/BreatheActivity.kt           Compose breathing + reflection
data/SessionLog.kt              SharedPreferences wrapper
res/xml/accessibility_service_config.xml
```
Edit `AndroidManifest.xml` and `res/values/strings.xml`.

---

## 4. Timeline (120 minutes)

The scaffold already builds, which saves the usual setup time.

| Time | Task | Gate |
|---|---|---|
| 0:00–0:15 | **SPIKE.** Real device + burner IG account. Register the service, log every event from IG while scrolling 10 reels (class, type, fromIndex/toIndex, scrollDeltaY, timestamps). Decide the "reel advanced" rule. | 🚦 No usable signal in 15 min → pick the burst-count fallback (below) and move on. Do not keep investigating. |
| 0:15–0:35 | Manifest, service config, permission onboarding in `MainActivity`, hardcoded bubble drawn over IG. | Bubble visible over Instagram |
| 0:35–1:00 | Engine, reel counting, bubble size/colour, foreground detection, decay. | ✅ **M1:** counts and grows. Demoable. |
| 1:00–1:20 | Bubble becomes touchable at RESIST; BLOCK launches `BreatheActivity`. | ✅ **M2:** the "wow". Demoable. |
| 1:20–1:35 | 10s breathing → reset → reflection card → `SessionLog`. | ✅ **M3:** the mental-health turn. |
| 1:35–1:45 | **Record the backup demo** (`adb shell screenrecord` or the phone's screen recorder), narrated. | 🚦 Hard gate. |
| 1:45–1:50 | Threshold slider in `MainActivity` (otherwise hardcode 5). | |
| **1:50** | 🔒 **CODE FREEZE** | No edits after this |
| 1:50–2:00 | Rehearse the pitch twice with the live phone. | |

**Rule:** nothing new after 1:45. Late features are what break demos.

---

## 5. Low-level implementation

### 5.1 Manifest additions

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<application ...>
    <activity android:name=".ui.BreatheActivity"
        android:exported="false"
        android:launchMode="singleTask"
        android:theme="@android:style/Theme.Material.Light.NoActionBar" />

    <service
        android:name=".sensor.ScrollSensorService"
        android:exported="true"
        android:label="@string/app_name"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
        <meta-data
            android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config" />
    </service>
</application>
```

Existing `MainActivity` is in `com.poc.doomguard`. If you move it to `ui/`, update `android:name` in the manifest. Easiest: leave it where it is.

Add strings: `accessibility_description` ("DoomGuard counts reels in Instagram to help you notice when you're doomscrolling. It does not read or send any content.").

### 5.2 `res/xml/accessibility_service_config.xml`

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeViewScrolled|typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:packageNames="com.instagram.android"
    android:description="@string/accessibility_description" />
```

`packageNames` limits events to Instagram, which is also a good privacy line for the pitch. Leave `canPerformGestures` off unless you attempt the stretch.

### 5.3 `PressureEngine.kt`

```kotlin
object PressureEngine {
    enum class State { CALM, AWARE, RESIST, BLOCK }
    data class Snapshot(val count: Float, val pressure: Float, val state: State)

    @Volatile var threshold = 25
    @Volatile var span = 15
    private var count = 0f
    private var lastReelAt = System.currentTimeMillis()
    private var sessionStart = 0L

    private val _snap = MutableStateFlow(Snapshot(0f, 0f, State.CALM))
    val snap: StateFlow<Snapshot> = _snap

    fun onReel() {
        if (count == 0f) sessionStart = System.currentTimeMillis()
        count += 1; lastReelAt = System.currentTimeMillis(); publish()
    }

    fun tick(igForeground: Boolean) {            // call every 1000ms
        if (_snap.value.state == State.BLOCK) return          // only breathing clears BLOCK
        if (System.currentTimeMillis() - lastReelAt > 5000) {
            count = (count - if (igForeground) 0.2f else 1.0f).coerceAtLeast(0f)  // asymmetric decay
        }
        publish()
    }

    fun reset(): Pair<Int, Long> {
        val result = count.toInt() to (System.currentTimeMillis() - sessionStart)
        count = 0f; publish(); return result
    }

    private fun publish() {
        val p = ((count - threshold) / span).coerceIn(0f, 1f)
        val s = when {
            p >= 1f -> State.BLOCK
            p > 0f -> State.RESIST
            count > threshold * 0.6f -> State.AWARE
            else -> State.CALM
        }
        _snap.value = Snapshot(count, p, s)
    }
}
```

Add `kotlinx-coroutines` only if it is not already transitively available. `StateFlow` is in `kotlinx-coroutines-core`; `activity-compose` brings it in. Verify at build time and add `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")` only if the build fails.

### 5.4 `ScrollSensorService.kt` (the risky part)

```kotlin
class ScrollSensorService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var igForeground = false
    private var lastReelAt = 0L
    private lateinit var bubble: BubbleOverlay

    override fun onServiceConnected() {
        bubble = BubbleOverlay(this)
        handler.post(object : Runnable {
            override fun run() { PressureEngine.tick(igForeground); handler.postDelayed(this, 1000) }
        })
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        when (e.eventType) {
            TYPE_WINDOW_STATE_CHANGED -> {
                igForeground = e.packageName == "com.instagram.android"
                if (igForeground) bubble.show() else bubble.hide()
            }
            TYPE_VIEW_SCROLLED -> {
                Log.d("DG", "scroll cls=${e.className} from=${e.fromIndex} to=${e.toIndex} dy=${e.scrollDeltaY}")
                val now = SystemClock.uptimeMillis()
                if (now - lastReelAt > 600) {            // debounce: one swipe burst = one reel
                    lastReelAt = now
                    PressureEngine.onReel()
                }
            }
        }
    }
    override fun onInterrupt() {}
    override fun onDestroy() { bubble.hide(); handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
```

**Detection decision from the spike (pick one, write it in a comment):**

| Signal | Use when | Risk |
|---|---|---|
| A. `TYPE_VIEW_SCROLLED` with `fromIndex != toIndex` from a pager-like class | Events carry usable indexes | Best if available |
| B. Burst-count: `TYPE_VIEW_SCROLLED` events separated by >600ms gaps | Indexes are missing | Counts feed scrolls too, so also require the Reels tab (C) |
| C. "In Reels?" check via `rootInActiveWindow` text/contentDescription containing "Reels" and selected | Needed to avoid counting the home feed | Walking the node tree is slow. Do it at most once per second |

If you cannot cleanly separate Reels from the home feed, **say so in the pitch**: it counts feed scrolling too. That is still doomscrolling and an honest limitation.

Do not select by resource ID unless the spike shows a stable one. Instagram's IDs are obfuscated and change between versions.

### 5.5 `BubbleOverlay.kt`

Use a **plain custom `View`** drawing a radial-gradient circle. Do not use `ComposeView` in a service window: it needs hand-wired `LifecycleOwner` / `SavedStateRegistryOwner` / `ViewModelStoreOwner` and costs 20+ minutes of debugging. Compose is fine in `BreatheActivity`.

```kotlin
class BubbleOverlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val view = BubbleView(ctx)
    private var shown = false

    private fun params(touchable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT)

    fun show() { if (!shown) { wm.addView(view, params(false)); shown = true } }
    fun hide() { if (shown) { wm.removeView(view); shown = false } }
    fun setTouchable(t: Boolean) { if (shown) wm.updateViewLayout(view, params(t)) }
}
```

`BubbleView.onDraw`: radius = `lerp(48dp, hypot(w, h)/2, p)`, centered on screen when `p > 0` and parked in a corner when `p == 0`. Colour hue goes 210° → 0° with `p`; alpha 0.35 → 0.85. Animate with `ValueAnimator` (~450ms) to the new target on each snapshot. Collect `PressureEngine.snap` from the service with a `CoroutineScope(Dispatchers.Main)`.

`setTouchable(snap.state == RESIST || snap.state == BLOCK)`, and in `BubbleView.onTouchEvent` return `true` (consume).

Honest limit: only swipes that **start on the bubble** are blocked. Swipes starting outside it still scroll. As the bubble grows toward full screen, the loophole closes, which is the original idea. Say that in the pitch.

### 5.6 BLOCK → breathing

In the service, on a snapshot with `state == BLOCK`:

```kotlin
startActivity(Intent(this, BreatheActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
```

Starting an activity from the background is restricted on Android 10+. An app with a visible `SYSTEM_ALERT_WINDOW` window is exempt, and the bubble is such a window. **Verify this on the demo device in the spike** by calling it once manually. If it fails, fall back to `performGlobalAction(GLOBAL_ACTION_HOME)` and show the breathing UI as an overlay.

Why launch an activity instead of covering Instagram: Instagram goes to `onPause`, so the video and **audio** stop. An overlay alone leaves reels playing underneath, which looks broken.

### 5.7 `BreatheActivity.kt` (Compose)

- Circle scaling 1.0 ↔ 1.6 on a 4s `infiniteRepeatable` tween, "Breathe in" / "Breathe out" text swap
- 10s countdown (`LaunchedEffect` + `delay`); pause/restart the countdown if the activity is stopped (`onStop`), so it can't be completed in the background
- On completion: `val (reels, ms) = PressureEngine.reset()`, then show the reflection card
- Reflection card: `"$reels reels · ${ms/60000} min. What were you actually looking for?"` + three mood buttons (😐 😶 😞). Tap → `SessionLog.add(...)`, `finish()`
- Block the back button during breathing: `BackHandler { }`
- Edge case: if the user leaves via Home during `BLOCK`, the engine stays in `BLOCK` and the bubble stays full. That's intended, since only breathing clears it. Make sure the bubble `hide()`s while IG is not foreground.

### 5.8 `MainActivity` (onboarding)

Compose screen with:
- Status row + button: **Overlay permission** → `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` with `Uri.parse("package:$packageName")`; status via `Settings.canDrawOverlays(this)`
- Status row + button: **Accessibility service** → `Settings.ACTION_ACCESSIBILITY_SETTINGS`; status by checking `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` contains your component
- Re-check status in `onResume`
- Threshold slider (3–50) writing `PressureEngine.threshold` and `SharedPreferences`
- A "Reset" debug button calling `PressureEngine.reset()` (essential for rehearsal)
- One line of copy: "Nothing leaves your device. DoomGuard only reads scroll events from Instagram."

---

## 6. Android gotchas that cost people the most time

1. **Reinstalling from Android Studio can silently disable the accessibility service.** If counting stops after a redeploy, toggle the service off and on in Settings → Accessibility. Check this *first* whenever "it doesn't work".
2. **Restricted settings (Android 13+):** if the accessibility toggle is greyed out for a sideloaded app: App info → ⋮ → *Allow restricted settings*.
3. **OEM battery killers** (Xiaomi, Oppo, Samsung "sleeping apps") can kill the service. Exempt DoomGuard from battery optimisation on the demo phone.
4. **Emulators** work only with a Google Play system image and a logged-in Instagram. A real device is strongly preferred. At the time of writing, `adb devices` shows **no connected device**.
5. **Don't demo on a personal account.** Use a burner Instagram account.
6. **Instagram updates** can change the event pattern. Freeze the Instagram version on the demo phone (disable auto-update) after the spike.

---

## 7. Acceptance checklist (run at 1:30 and again at 1:50)

- [ ] App installs; onboarding shows both permissions as granted
- [ ] Bubble appears when Instagram is foreground and disappears when it is not
- [ ] Scroll 10 reels → count is 10 ± 1
- [ ] Bubble visibly grows and turns red past the threshold
- [ ] Swiping on the grown bubble does nothing
- [ ] At BLOCK, `BreatheActivity` opens and Instagram's audio stops
- [ ] 10s of breathing resets the count to 0
- [ ] Leaving Instagram for 10s lowers the count more than idling inside it
- [ ] A session record is written (check via a debug log or the onboarding screen)
- [ ] Backup video recorded and plays on the presentation machine

---

## 8. Risks (brutally honest)

| Risk | Likelihood | Mitigation |
|---|---|---|
| Instagram's accessibility events don't give a clean per-reel signal | Medium | 15-min spike; burst-count fallback; admit the limitation |
| Counts home-feed scrolling as reels | Medium | "In Reels?" check at most 1×/sec; otherwise disclose it |
| Background `startActivity` blocked on the demo device | Medium | Test in the spike; fall back to `GLOBAL_ACTION_HOME` |
| Service disabled after redeploy / killed by OEM | High | Gotchas #1 and #3; re-verify before going on stage |
| No physical device available | Unknown | Resolve **before** starting the clock; emulator with Play Store as plan B |
| Swipes that start outside the bubble still work | Certain | Bubble reaches full screen at BLOCK; stretch goal is `dispatchGesture` |
| Play Store rejects non-accessibility use of `AccessibilityService` | Certain for a store release | Fine for a competition/sideload. Say "v2 would need Usage Access or an approved-category path" |
| Scope creep after 1:45 | High | Code freeze 1:50 |

---

## 9. Pitch (2 minutes)

1. **0:00–0:30:** Screen-mirror the phone. Scroll Instagram Reels in silence. The bubble grows, turns red, then Instagram is pulled away. Then speak.
2. **0:30–1:00:** Problem: short-form consumption is associated with depression, anxiety and stress (2025 meta-analysis, 71 studies, ~98k participants). Association from mostly self-report data, not causation.
3. **1:00–1:20:** Gap: existing tools guard the door, not the session.
4. **1:20–1:40:** Mechanic: counted in reels, continuous, felt rather than read.
5. **1:40–1:55:** The turn: breathe-to-dissolve + reflection. "It doesn't fight you. It gives you a way out you chose."
6. **1:55–2:00:** Engine/sensor split → "other apps are one sensor away." "Nothing leaves the device."

**Objection prep:**
- *"Just another blocker?"* Blockers are binary. This scales with what you actually consumed.
- *"Can't I just ignore it?"* Yes, and you should be able to. It's a Ulysses contract, not a jail. Friction research shows 30–50% reductions, not 100%.
- *"Clinical evidence?"* None for this intervention. The friction literature is short-term and self-report heavy. I'd run an n-of-1 trial before claiming anything.
- *"Is that accessibility API appropriate?"* It's scoped to one package, reads only scroll events, and nothing is stored or sent. A store release would need a policy-compliant path.

---

## 10. Recommendation

Do the 15-minute spike first, and make the go/no-go call on the reel-detection rule. Then build M1 → M2 → M3 strictly in order, record the backup video at 1:35, and freeze at 1:50.

The two things that make this win are the growing touch-blocking bubble (your original idea) and breathe-to-dissolve (the mental-health angle). Skip the gesture-injection stretch unless M3 is finished early.

Before starting the clock, confirm:
1. You have a **physical Android device** with a burner Instagram login and USB debugging enabled
2. The competition rules allow native Android (and don't require something else)
3. Whether you're solo. With a teammate, one person can take `BreatheActivity` and the reflection card while the other does the sensor.
