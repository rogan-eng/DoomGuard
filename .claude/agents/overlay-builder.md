---
name: overlay-builder
description: Builds chunk C3 of DoomGuard — the floating bubble overlay (custom View + WindowManager). Use for C3 only.
tools: Read, Write, Edit, Bash(grep:*), Bash(ls:*)
model: sonnet
---
You implement chunk **C3** of `PLAN_CHUNKS.md` in `/Users/mohamedyaseen/Desktop/Development_iOS/POC/DoomGuard`.

Read `PLAN_CHUNKS.md` (frozen contract) and `DOOMGUARD_2HR_SCOPE.md` §5.5 first.

You own ONLY: `app/src/main/java/com/poc/doomguard/overlay/BubbleOverlay.kt` and `overlay/BubbleView.kt`.

Requirements:
- **Plain `View`, NOT Compose** (no ComposeView in a service window).
- `BubbleOverlay(ctx)`: `show()/hide()` idempotent, add/remove one full-screen `TYPE_APPLICATION_OVERLAY` window with `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`, plus `FLAG_NOT_TOUCHABLE` unless `setTouchable(true)` (use `updateViewLayout`). `render(snapshot)` forwards to the view. Guard every WindowManager call with try/catch (window may already be gone).
- `BubbleView`: draws a radial-gradient circle. p==0 → 48dp radius parked top-end corner (inset 24dp, below status bar); p>0 → centred, radius lerps 48dp → hypot(w,h)/2 so p=1 covers the whole screen. Hue 210°→0° with p (use `Color.HSVToColor`), alpha 0.35→0.85. Draw the integer count in white, centred on the circle (text size scales modestly). Animate to each new target with a 450ms `ValueAnimator` (cancel the previous; no allocations in `onDraw`).
- `onTouchEvent` returns `true` (consumes) — only reachable when the window is touchable.
- AWARE state (p==0 but count>0.6·threshold): tint amber instead of blue. Take the state from the Snapshot.
Do NOT run Gradle or adb; re-read for compile errors and report.
