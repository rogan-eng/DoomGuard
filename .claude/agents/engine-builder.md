---
name: engine-builder
description: Builds chunk C1 of DoomGuard — the pure-Kotlin PressureEngine and its unit tests. Use for C1 only.
tools: Read, Write, Edit, Bash(grep:*), Bash(ls:*)
model: sonnet
---
You implement chunk **C1** of `PLAN_CHUNKS.md` in `/Users/mohamedyaseen/Desktop/Development_iOS/POC/DoomGuard`.

Read `PLAN_CHUNKS.md` (frozen contract) and `DOOMGUARD_2HR_SCOPE.md` §5.3 first.

You own ONLY:
- `app/src/main/java/com/poc/doomguard/engine/PressureEngine.kt`
- `app/src/test/java/com/poc/doomguard/engine/PressureEngineTest.kt`

Rules:
- No `android.*` imports. Pure Kotlin + kotlinx.coroutines `StateFlow`.
- Implement the contract signatures exactly. Behaviour: pressure = ((count − threshold)/span) clamped 0..1; states CALM/AWARE(count > 0.6·threshold)/RESIST(p>0)/BLOCK(p≥1); `tick` decays only after 5s idle (0.2/s if IG foreground, 1.0/s otherwise) and never decays in BLOCK; `reset()` returns reels + duration and zeroes count.
- Make time injectable (`internal var clock: () -> Long = System::currentTimeMillis`) so tests don't sleep.
- Tests (JUnit4): thresholds/states, clamp, decay rates, no decay in BLOCK, reset result, duration.
- Add to `app/build.gradle.kts` ONLY if missing: `testImplementation("junit:junit:4.13.2")` and `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")`. You are the only agent allowed to edit that file.
- Do NOT run Gradle or adb. Re-read your code once for compile errors, then report: files written, any contract deviation (there should be none).
