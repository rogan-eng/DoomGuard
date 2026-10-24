---
name: orchestrate
description: Use at the start of any DoomGuard session, or when asked "what's next / status / continue / orchestrate". Resumes the 2-hour build from STATUS.md, dispatches builder agents, enforces gates and the time budget.
---
# DoomGuard orchestrator (runs in the MAIN session)

Subagents cannot spawn subagents, so you — the main session — are the orchestrator. Agents in `.claude/agents/` are your workers.

## On start (always, in this order)
1. Read `STATUS.md`, then `PLAN_CHUNKS.md`. Skim `DOOMGUARD_2HR_SCOPE.md` only if you need detail.
2. Reconcile STATUS with reality (files can be ahead of the table): `ls` the files each chunk owns, check `adb devices`. Fix STATUS if wrong.
3. Ask the user ONE question only if a Blocker is unresolved or the clock start time is blank. Otherwise act.
4. Report in ≤8 lines: elapsed time vs plan, what's DONE/BLOCKED, the next action. Then do it.

## Dispatch rules
- Launch all ready no-device chunks **in one message** (C1–C4 are independent). Prompt each: "Do chunk Cn from PLAN_CHUNKS.md" — the agent definition holds the detail.
- Run the spike (C0) yourself with the user, in parallel with the agents. It is the critical path.
- Never let two agents own the same file. Never give builder agents Gradle/adb.
- When agents return: mark chunk DONE (not VERIFIED), note contract deviations, then compile with the `device-loop` skill. Only a device pass makes a chunk VERIFIED.
- Compile errors: fix them yourself if ≤ 3 small ones; otherwise re-dispatch the owning agent with the error text.

## Time discipline (compare to STATUS clock start)
| Elapsed | Must be true |
|---|---|
| 0:15 | G0 + G0b decided |
| 0:50 | M1 on the phone |
| 1:10 | M2 |
| 1:25 | M3 |
| 1:35 | backup video recorded (hard gate) |
| 1:45 | no new work starts |
| 1:50 | freeze: bug fixes only, then rehearse |
If a milestone is >10 min late, take the next cut from PLAN_CHUNKS "Cut order", record it in STATUS, and say so plainly.

## Honesty rules
- Don't mark anything VERIFIED without device evidence (logcat line, dumpsys, or user confirmation).
- Don't claim a signal works from reading code; Instagram behaviour is only known from the spike.
- If a gate fails, say it failed, name the fallback, continue.

## After every step
Update `STATUS.md` (table row + one Log line with elapsed time). This file is how a fresh chat resumes — treat it as mandatory.

## End-of-session handoff
Before the user closes a chat: make STATUS.md current, list the next 3 actions at the top of the Log, and tell the user to open a new chat and type `/orchestrate`.
