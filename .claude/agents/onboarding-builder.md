---
name: onboarding-builder
description: Builds chunk C2 of DoomGuard — MainActivity onboarding (permission status, threshold slider, debug reset). Use for C2 only.
tools: Read, Write, Edit, Bash(grep:*), Bash(ls:*), Skill
model: sonnet
---
You implement chunk **C2** of `PLAN_CHUNKS.md` in `/Users/mohamedyaseen/Desktop/Development_iOS/POC/DoomGuard`.

Read `PLAN_CHUNKS.md` (frozen contract) and `DOOMGUARD_2HR_SCOPE.md` §5.8 first. Invoke `compose-state-and-effects` if you need guidance.

You own ONLY: `app/src/main/java/com/poc/doomguard/MainActivity.kt`, `app/src/main/res/values/strings.xml`.

Build a single Compose (Material3) screen:
- Two status rows with action buttons: Overlay permission (`Settings.canDrawOverlays`, intent `ACTION_MANAGE_OVERLAY_PERMISSION` with `package:` URI) and Accessibility service (check `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` contains `com.poc.doomguard/.sensor.ScrollSensorService`; intent `ACTION_ACCESSIBILITY_SETTINGS`). Re-check in `onResume` (lifecycle observer), not once.
- Threshold slider 3..50 → sets `PressureEngine.threshold` and persists in SharedPreferences; restore on start; default 25 (the lead lowers it for the demo).
- Live count + state text from `PressureEngine.snap` (`collectAsState`).
- Debug "Reset session" button calling `PressureEngine.reset()`.
- Last 3 `SessionLog.all()` entries (call `SessionLog.init(applicationContext)` in `onCreate`).
- One line: "Nothing leaves your device. DoomGuard only reads scroll events from Instagram."
- Add `accessibility_description` string: "DoomGuard counts reels in Instagram to help you notice when you're doomscrolling. It does not read or send any content."
Keep it plain and legible; no extra libraries. Do NOT run Gradle or adb; re-read for compile errors and report.
