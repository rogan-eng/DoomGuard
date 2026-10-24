# DoomGuard — Live Status (single source of truth)

> The orchestrator reads this first and updates it after every step. Keep it short and factual.
> Update rule: edit the table + append one line to the Log. Never delete Log lines.

**Clock:** STARTED 12:11 IST (2026-10-24). Freeze at 14:01 IST (start+1:50) · No new work after 13:56 · Demo threshold: 5 (set before rehearsal)

## Blockers (resolve before clock)
- [x] Phone shows as `device` in `adb devices` (V2502, API 36, id 10BFC80MGP00A9P)
- [ ] Instagram installed, burner account logged in, auto-update off
- [ ] Competition rules allow native Android (unconfirmed)
- [ ] Solo or team? (unconfirmed → assume solo)

## Chunks
| ID | Chunk | Owner | Status | Notes |
|----|-------|-------|--------|-------|
| C0 | Spike (events, reel rule, bg-launch) | Lead+user | VERIFIED | G0 + G0b passed on device. |
| C1 | PressureEngine + tests | engine-builder | DONE | Compiles; 13/13 unit tests pass (host JVM, not device; engine is pure Kotlin so this is the real evidence). Gradle-verified, not device-verified. Added coroutines + junit deps to app/build.gradle.kts. Extras: `internal var clock`, @Synchronized, reset() gives durationMs=0 if no reels. 13 tests, never run. |
| C2 | Onboarding / MainActivity | onboarding-builder | DONE | unbuilt. Assumes SessionLog.all() is oldest-first (takeLast(3).reversed()) — check vs C4. Threshold prefs `doomguard_prefs`/`threshold` only restored when MainActivity starts → C5 must restore it in the service too. |
| C3 | BubbleOverlay / BubbleView | overlay-builder | DONE | unbuilt. Deviation (visual only): position eases to centre over p 0→0.125; gloss highlight instead of true colour radial gradient. |
| C4 | BreatheActivity + SessionLog | breathe-builder | DONE | unbuilt. SessionLog.all() is oldest-first (appends) → matches C2 assumption. BreatheActivity NOT in manifest yet (C0/C5 lead). Possible build issue: LocalLifecycleOwner import. |
| C5 | Wire ScrollSensorService + manifest + a11y config | Lead | RUNNING | Partially wired: reel counting + bubble + threshold restore + checkpoint launch. NOT wired: 1s tick loop (no decay), BLOCK→BreatheActivity (unreachable by design, see decisions), +1 entry-count fix. |
| C9 | NEW (user request): checkpoint at threshold+10 → 10s blocking screen + random positive quote → reset count (no mood card) | Lead | VERIFIED | Device-verified 12:55 (see Log). | `ui/QuoteBreakActivity.kt`, `data/Quotes.kt` (40 offline quotes, no repeat of last), manifest entry, service `launchCheckpoint()` (3s throttle). Engine contract untouched. |
| C6 | Integration build + device acceptance | device-verifier+Lead | TODO | |
| C7 | Backup recording | user | TODO | |
| C8 | Rehearsal | user | TODO | |

Status values: TODO · RUNNING · DONE (code written, unbuilt) · VERIFIED (built + passed on device) · BLOCKED · CUT

## Gates
| Gate | Result | Evidence |
|------|--------|----------|
| G0 reel rule (10 reels → 10±1) | PASS 12:26 | 10 `adb input swipe` in Reels → REEL #46–#55 = exactly 10, indices 1..10 consecutive. Explore-grid scrolling counted 0 (negative test). Entering Reels tab adds +1 spurious count (index 0). |
| G0b background startActivity works | PASS 12:27 | broadcast while IG foreground+bubble visible → mCurrentFocus became com.poc.doomguard/.ui.BreatheActivity; no BAL block in logcat. Not checked: IG audio actually stopping. |
| M1 counts + bubble grows | PASS (spike svc, provisional) 12:25 | Screenshot: bubble over IG showing 44, full-screen red (count 44 > threshold 25+span 15). Engine tick/decay not wired yet; re-verify after C5. |
| M2 touch-block + BLOCK launches breathing | — | |
| M3 breathe → reset → log | — | |

## Decisions made (append only)
- Native Android (Kotlin, AccessibilityService), not Chrome extension.
- Bubble = plain View; BLOCK launches BreatheActivity (stops IG audio).
- Only lead/device-verifier run Gradle & adb; builders never build.

## Reel rule chosen in spike
Signal A (index change). Event: `TYPE_VIEW_SCROLLED`, `pkg == com.instagram.android`, `className == androidx.viewpager.widget.ViewPager`, `fromIndex >= 0`. Count when `fromIndex != lastIndex` (first index after IG returns to foreground = baseline, not counted). Known wrinkles: +1 on entering Reels tab (index jumps to 0); backward swipe also counts; index 20→0 wrap seen once and counted (feed vs reels ViewPager not separated).

## Decisions made in C0 (append)
- a11y config has NO `packageNames` filter (scope §5.2 had one). With a filter the service never sees the window-change to another app, so the bubble can't hide / igForeground can't clear. Service filters to `com.instagram.android` in code instead. Pitch privacy line should say "ignores everything except Instagram", not "only receives Instagram events".
- Spike service ignores `com.android.systemui` window changes so shade/keyboard don't flip igForeground.

- Phone is a vivo (V2502): `Log.d` from the app is dropped, only Log.w/e show in logcat. All diagnostic logging (spike and later) must use `Log.w`. Cost ~3 min to find.
- `adb shell settings put secure enabled_accessibility_services ""` is invalid; to disable use `settings delete secure enabled_accessibility_services`.
- After every `installDebug` the a11y service ends up bound but DEAD (no events, no onServiceConnected). Fix = full toggle: `settings put secure accessibility_enabled 0; settings delete secure enabled_accessibility_services; sleep 2; settings put secure enabled_accessibility_services <svc>; settings put secure accessibility_enabled 1`. Verify a `service connected` line in `logcat -s DG` BEFORE asking the user to test. Two user test runs were wasted on this.
- Reel rule (G0 candidate, signal A): IG Reels = `androidx.viewpager.widget.ViewPager`, `fromIndex` = current reel; 2-4 events per swipe with same index. Count index CHANGES (first index after leaving IG is baseline, not counted). Not yet validated against exactly 10 swipes.
- USER DECISION (12:30): checkpoint fires when count >= threshold + 10; timer 10s, no skip; quote from built-in offline list; count resets via PressureEngine.reset() when timer ends; NO SessionLog entry and NO mood card. Consequence: BLOCK (threshold+span 15) is never reached because the count resets at +10, so BreatheActivity is dormant. User did not ask to remove it.
- BLOCKER (12:44): `settings put secure enabled_accessibility_services` now exits 0 but reads back null immediately (both users 0 and 999, nothing in logcat), after having worked for 10+ min. Service is registered+enabled in PackageManager. Needs manual enable in Settings → Accessibility → DoomGuard (if greyed: App info → ⋮ → Allow restricted settings). Cause unknown.
- RESOLVED/REVISED (12:55): once the user enabled DoomGuard in Settings UI, `installDebug` KEEPS it enabled and it reconnects by itself. `adb settings put/delete ... enabled_accessibility_services` is now unreliable: `delete` works, `put` is silently rejected. So NEVER toggle via adb after the user has enabled it in the UI; after a reinstall just check `logcat -s DG` for `service connected`. (Earlier "dead after install" finding applied only to the adb-enabled instance.)
- Checkpoint verified on device 12:51: fired at count=23.0 (threshold 13 + 10), QuoteBreakActivity got focus, 10s ring + quote shown, ended with ✓ + "Your reel count is reset." + Continue; MainActivity Live panel then showed Count 0 / CALM. Bug found: Continue landed on DoomGuard MainActivity (shared task). Fixed with taskAffinity="" + excludeFromRecents + finishAndRemoveTask (built, NOT yet verified).

## Cuts taken
_(none)_

## Log
- (setup) Scope, PLAN_CHUNKS.md, 5 agents, device-loop skill created. No code written yet.
- 0:00 (12:11 IST) Clock started by user decision, phone NOT yet in `adb devices` (C0 blocked until it is). Dispatched C1–C4 in parallel.
- 0:02 `.claude/agents/*` not registered in this session (agents were created after session start). Fallback: dispatched C1–C4 as general-purpose agents (sonnet) with the agent definitions inlined. Restart the session before C6 if `device-verifier` is needed.
- 0:05 C1 returned DONE (unbuilt, tests not run). No contract deviation. Waiting on C2, C3, C4.
- 0:06 C2 returned DONE (unbuilt). Open items: SessionLog order check vs C4; C5 restores threshold from prefs. Waiting on C3, C4.
- 0:09 C3, C4 returned DONE (unbuilt). SessionLog order verified oldest-first. All of C1–C4 DONE; nothing compiled or run. Next: C0 spike needs phone (adb empty); manifest + a11y config + throwaway service are lead-owned.
- 0:03 (12:14 IST, wall clock; earlier 0:05–0:09 log stamps were estimates and ran ahead) Phone connected (V2502, API 36, IG installed). Wrote manifest, a11y config, throwaway logging ScrollSensorService. `installDebug` OK first try (2 deprecation warnings only), C1–C4 all compile together. 13/13 engine tests pass. Service bound, SYSTEM_ALERT_WINDOW allowed via adb. Waiting on user to scroll IG reels for G0 and trigger G0b.
- 0:06 (12:17 IST) First G0 scroll attempt produced no log: Log.d suppressed on this ROM. Switched spike to Log.w, reinstalled, service connected. Need user to redo the 10-reel scroll.
- 0:10 (12:23 IST) Self-hide bug found+fixed (bubble's own window event flipped igForeground=false; now ignored). Index-change reel rule implemented in spike service. Service was dead after reinstall; full toggle revived it, REEL lines now logging. Still need a clean exactly-10-swipe run for G0, and G0b.
- 0:16 (12:27 IST) C0 DONE: G0 PASS (10→10), G0b PASS, M1 provisional. Reel rule written above. Next: C5 wire service (tick loop, BLOCK launch w/ touch-eat, threshold restore from prefs, fix +1 entry count, BLOCK re-entry guard), then M2/M3. Unhandled: BreatheActivity now open on phone from test; user must finish it (10s then pick a mood).
- 0:33 (12:44 IST) Built QuoteBreak checkpoint (C9). Could not device-test: a11y service got switched off and adb can no longer re-enable it. Waiting on user to enable it in Settings UI, then run the checkpoint test (threshold saved = 10 → fires at 20).
- 0:44 (12:55 IST) C9 checkpoint verified up to reset. Task-return fix built. I toggled the a11y service off via adb and cannot re-enable it; user must toggle on in Settings. Then verify: Continue returns to Instagram, bubble shows 0, next swipe counts 1.
- 0:46 (12:56 IST) C9 VERIFIED on device, two full cycles. Cycle 2 (threshold 13): CHECKPOINT at count=23.0 → QuoteBreakActivity shown 12:55:21 → Continue tapped 12:55:33 → Instagram returned (window ig=true 12:55:34, bubble visible, own task fix works) → next counted reel #24 restarted the count; bubble showed 6 after 6 user-made reels (12:55:37–42, not sent by lead), 7 after one lead swipe. Evidence: logcat tag DG + screenshots. Not verified: quote differs on consecutive runs (saw 'Take your time back...' once), IG audio stops during block.
- NEXT (C5 leftovers): 1s tick loop/decay, +1 spurious count on entering Reels, decide fate of dormant BreatheActivity/BLOCK flow, bubble re-render after checkpoint relies on IG window event. Then C7 backup recording by 1:35 (13:46 IST), freeze 14:01.
