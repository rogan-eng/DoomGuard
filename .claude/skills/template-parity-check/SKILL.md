---
name: template-parity-check
description: Use after adding or editing a DoomGuard template, before handing off to a build — greps that every Template entry is handled at every touchpoint, without running Gradle.
---
# Template parity check

Cheap, no-Gradle check that no template was forgotten. Run from the project dir `/Users/mohamedyaseen/Desktop/Development_iOS/POC/DoomGuard`.

## 1. List the entries
```
grep -nE '^\s+[A-Z_]+\("' app/src/main/java/com/poc/doomguard/data/Template.kt
```
Call the count **N**.

## 2. Count branches per touchpoint
Each command must print **N** (the enum's own entries, per touchpoint):
```
cd app/src/main/java/com/poc/doomguard
grep -c 'Template\.[A-Z]* ->' ui/Theme.kt          # of() + DgBackdrop() = 2*N
grep -c 'Template\.[A-Z]* ->' ui/QuoteBreakActivity.kt   # N
grep -c 'Template\.[A-Z]* ->' overlay/BubbleView.kt      # N
grep -c 'Template\.[A-Z]* ->' MainActivity.kt            # hero + tile preview = 2*N
```
Also: `ls ui/Break*.kt` must show one `Break<Name>.kt` per entry (plus `BreakHost.kt`), and `grep -n 'val [A-Z][a-z]* = DgTheme(' ui/Theme.kt` must show N themes.

## 3. Smells (judge by eye, grep only finds candidates)
```
grep -n 'else ->' ui/QuoteBreakActivity.kt overlay/BubbleView.kt MainActivity.kt ui/Theme.kt
grep -n 'Color(0x' ui/Break*.kt
```
- An `else ->` is only a problem if it belongs to a `when (template)`. `BubbleView.kt` has a legitimate one (`else -> HUE_CALM`, a pressure-state `when`).
- Raw `Color(0x…)` in a break screen should live in `DgTheme`. `BreakDepth.kt` already has three (`Water`, `Foam`, `Teal`) — a known deviation, so only flag **new** ones in the file you changed.

## Report
One line per touchpoint: `ok` or `MISSING <entry>`. A mismatch means fix before building. A clean result only means the wiring is complete — it says nothing about how it looks or behaves; that needs the device.
