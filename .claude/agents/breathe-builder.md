---
name: breathe-builder
description: Builds chunk C4 of DoomGuard — BreatheActivity (10s breathing, reset, reflection card) and SessionLog. Use for C4 only.
tools: Read, Write, Edit, Bash(grep:*), Bash(ls:*), Skill
model: sonnet
---
You implement chunk **C4** of `PLAN_CHUNKS.md` in `/Users/mohamedyaseen/Desktop/Development_iOS/POC/DoomGuard`.

Read `PLAN_CHUNKS.md` (frozen contract) and `DOOMGUARD_2HR_SCOPE.md` §5.7 first. Invoke `compose-animations` / `compose-state-and-effects` if useful.

You own ONLY: `app/src/main/java/com/poc/doomguard/ui/BreatheActivity.kt` and `data/SessionLog.kt`.

BreatheActivity (ComponentActivity + Compose):
- Full-screen dark calm background; circle scaling 1.0↔1.6 on a 4s `infiniteRepeatable` tween; text "Breathe in"/"Breathe out" toggling every 4s; visible countdown 10→0 as a progress ring.
- Countdown runs in `LaunchedEffect`; it must **cancel and restart** when the activity stops (observe lifecycle ON_STOP / ON_START) so it cannot complete in the background.
- `BackHandler { }` blocks back during breathing.
- On completion: `val r = PressureEngine.reset()` once (guard against double-fire), then show the reflection card: "${r.reels} reels · ${minutes} min. What were you actually looking for?" with three mood buttons 😐 😶 😞 → `SessionLog.add(...)`, then `finish()`. Minutes = max(1, durationMs/60000).
- Manifest entry is lead-owned; don't touch it.
SessionLog: SharedPreferences `doomguard_log`, store entries as a JSON string list via `org.json` (no new deps), try/catch every read/write, `init(ctx)` stores applicationContext.
Do NOT run Gradle or adb; re-read for compile errors and report.
