---
name: device-loop
description: Use when building, installing, or debugging DoomGuard on the connected Android phone — the build → install → re-enable accessibility service → logcat loop, plus the common reasons counting silently stops.
---
# DoomGuard device loop

Paths (this machine):
- `ADB=/Users/mohamedyaseen/Desktop/Development_iOS/AndroidStudioTools/platform-tools/adb`
- Project: `/Users/mohamedyaseen/Desktop/Development_iOS/POC/DoomGuard` (JDK 17: `/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home`)

## Loop
1. `$ADB devices` → must list the phone as `device` (not `unauthorized`/empty).
2. `./gradlew :app:installDebug` (from the project dir).
3. **Re-enable the accessibility service** — reinstall silently disables it:
   `$ADB shell settings get secure enabled_accessibility_services` must contain `com.poc.doomguard/com.poc.doomguard.sensor.ScrollSensorService`.
   If not, toggle in Settings → Accessibility → DoomGuard (or, dev only: `$ADB shell settings put secure enabled_accessibility_services com.poc.doomguard/com.poc.doomguard.sensor.ScrollSensorService && $ADB shell settings put secure accessibility_enabled 1`).
4. Overlay permission (dev only): `$ADB shell appops set com.poc.doomguard SYSTEM_ALERT_WINDOW allow`
5. Logs: `$ADB logcat -c && $ADB logcat -s DG`
6. Launch: `$ADB shell am start -n com.poc.doomguard/.MainActivity`
7. Record: `$ADB shell screenrecord --time-limit 180 /sdcard/dg.mp4` then `$ADB pull /sdcard/dg.mp4 ~/Desktop/`

## "Counting stopped" checklist (in order)
1. Service enabled? (step 3) 2. Instagram foreground package is `com.instagram.android`? 3. Battery optimisation killed it? 4. Debounce too long / signal rule changed with an IG update? 5. Overlay perm revoked?

## Greyed-out accessibility toggle (Android 13+)
App info → ⋮ → Allow restricted settings.
