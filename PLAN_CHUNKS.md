# DoomGuard — Chunked Build Plan (120 min)

Companion to `DOOMGUARD_2HR_SCOPE.md` (the *what*). This file is the *who/when/in what order*.

## Ground rules
- **Not a git repo** → no worktrees. Parallel agents are safe only because each owns **disjoint files** (table below). Never edit a file you don't own.
- **Only the human/lead runs Gradle and adb.** Builder agents write code and self-review; they do not build (concurrent Gradle runs on one project fight over locks and report each other's half-written files as errors).
- **The contract below is frozen.** Builders code against it exactly. Changing it = lead's decision.
- SDK: `sdk.dir` is in `local.properties` (`.../AndroidStudioTools`). adb: `.../AndroidStudioTools/platform-tools/adb`. JDK 17.
- Nothing new after **1:45**. Freeze at **1:50**.

## Frozen contract (package `com.poc.doomguard`)

```kotlin
// engine/PressureEngine.kt  (pure Kotlin, NO android.* imports)
object PressureEngine {
    enum class State { CALM, AWARE, RESIST, BLOCK }
    data class Snapshot(val count: Float, val pressure: Float, val state: State)
    @Volatile var threshold: Int          // default 25
    @Volatile var span: Int               // default 15
    val snap: StateFlow<Snapshot>
    fun onReel()
    fun tick(igForeground: Boolean)       // called every 1000ms by the service
    fun reset(): ResultOfSession          // BLOCK only clears via reset()
    data class ResultOfSession(val reels: Int, val durationMs: Long)
}

// data/SessionLog.kt
object SessionLog {
    fun init(ctx: Context)                // SharedPreferences "doomguard_log"
    fun add(reels: Int, durationMs: Long, mood: Int)   // mood: 0=😐 1=😶 2=😞
    fun all(): List<Entry>
    data class Entry(val ts: Long, val reels: Int, val durationMs: Long, val mood: Int)
}

// overlay/BubbleOverlay.kt
class BubbleOverlay(ctx: Context) {
    fun show(); fun hide()
    fun setTouchable(t: Boolean)
    fun render(s: PressureEngine.Snapshot)   // animates size/colour toward snapshot
}

// ui/BreatheActivity.kt  — launched with FLAG_ACTIVITY_NEW_TASK; no extras needed
```

## Chunks

| ID | Chunk | Owner | Files (exclusive) | Needs device | Depends on | Est. |
|----|-------|-------|-------------------|--------------|-----------|------|
| **C0** | **Spike**: log IG events, pick reel rule, test background `startActivity` | **Lead + you** | `sensor/ScrollSensorService.kt` (throwaway log version), manifest, a11y config | ✅ | — | 15 |
| C1 | Engine + unit tests | `engine-builder` | `engine/PressureEngine.kt`, `app/src/test/.../PressureEngineTest.kt` | ❌ | contract | 15 |
| C2 | Onboarding screen, threshold slider, debug reset | `onboarding-builder` | `MainActivity.kt`, `res/values/strings.xml` | ❌ | contract | 20 |
| C3 | Bubble overlay (custom View + WindowManager) | `overlay-builder` | `overlay/BubbleOverlay.kt`, `overlay/BubbleView.kt` | ❌ | contract | 25 |
| C4 | Breathe activity + reflection card + SessionLog | `breathe-builder` | `ui/BreatheActivity.kt`, `data/SessionLog.kt` | ❌ | contract | 25 |
| C5 | Wire service: counting rule, foreground detect, tick loop, BLOCK launch | **Lead** | `sensor/ScrollSensorService.kt` | ✅ | C0, C1, C3, C4 | 20 |
| C6 | Integration build, device run, fix-ups, acceptance checklist | `device-verifier` + Lead | any (lead-approved) | ✅ | C5 | 25 |
| C7 | Backup screen recording | You | — | ✅ | C6 | 10 |
| C8 | Pitch rehearsal | You | — | ✅ | C7 | 10 |

Manifest and `accessibility_service_config.xml` are **lead-owned** and written in C0 (all other chunks assume they exist).

## Waves (critical path in bold)

```
0:00 ──► **C0 spike (Lead+you, device)** ───────────────┐
0:00 ──► C1 ┐                                           │
0:00 ──► C2 ├─ parallel agents (no device, no gradle) ──┤
0:00 ──► C3 │                                           ▼
0:00 ──► C4 ┘                          0:25 **C5 wire service (Lead)**
                                       0:45 **C6 first integration build → M1 on phone**
                                       1:05 M2 (touch-block + BLOCK launch)
                                       1:20 M3 (breathe + reset + log)
                                       1:35 **C7 record**  → 1:50 freeze → C8
```

The agents finish in ~25 min while the spike runs. The spike is the only thing on the critical path that can't be parallelized, so start it first and don't wait for agents.

## Milestone gates (device)

| Gate | Pass condition | If it fails |
|------|----------------|-------------|
| **G0 (0:15)** | Log shows a rule that gives count = 10±1 over 10 reels | Use burst-count (600ms gap) and note the limitation in the pitch |
| G0b (0:15) | Manual `startActivity(BreatheActivity)` from the service works while IG is foreground | Fallback: `GLOBAL_ACTION_HOME` + overlay breathing UI |
| **M1 (0:50)** | Bubble over IG, count increments, bubble grows | Cut: colour shift. Keep size. |
| **M2 (1:10)** | Swipes on bubble are eaten; BLOCK opens BreatheActivity | Cut: touch-eating. Keep BLOCK launch. |
| **M3 (1:25)** | 10s breathe → reset → mood card saved | Cut: reflection card. Keep reset. |

## Cut order if running late (cut from the top)
1. Threshold slider (hardcode 5)
2. Colour shift on bubble (single red)
3. Reflection card / SessionLog
4. Asymmetric decay (single rate)
5. Touch-eating bubble (keep BLOCK only)

Never cut: counting, growing bubble, BLOCK → breathing, backup video.

## Agent prompts (paste-ready)

Each agent definition lives in `.claude/agents/`. Launch C1–C4 **in one message** so they run concurrently. Each prompt only needs: *"Do chunk Cn from PLAN_CHUNKS.md."*

## Skills
- `device-loop` (`.claude/skills/device-loop/SKILL.md`): build → install → re-enable service → logcat. Used by Lead and `device-verifier`.
- Existing global Compose skills are available for C2/C4: `compose-expert`, `compose-animations`, `compose-state-and-effects`.

## Before the clock starts (blocking)
- [ ] `adb devices` lists your phone (currently **empty**: enable USB debugging, accept the RSA prompt, try another cable/port)
- [ ] Instagram installed + burner account logged in; auto-update disabled
- [ ] Phone battery optimisation off for DoomGuard (after first install)
