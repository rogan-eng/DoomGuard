# DoomGuard

Native Android (Kotlin + Compose scaffold, package `com.poc.doomguard`) that counts Instagram reels via an AccessibilityService, grows a floating bubble, and forces a 10s breathing break. Built under a hard 2-hour limit for a mental-health vibe-coding competition.

**Resuming work: read `STATUS.md`, then run the `orchestrate` skill (`/orchestrate`).**

Files: `DOOMGUARD_2HR_SCOPE.md` (what/why/code sketches) · `PLAN_CHUNKS.md` (chunks, contract, gates, cut order) · `STATUS.md` (live state).

Rules that always apply:
- The frozen contract in `PLAN_CHUNKS.md` is not changed without the user's say-so.
- Only the main session / `device-verifier` runs Gradle and adb (adb path in the `device-loop` skill).
- Code freeze at 1:50 from clock start. No new features after 1:45.
- Don't claim VERIFIED without device evidence.
