---
name: add-template
description: Use when adding a new DoomGuard template (a selectable look beside Bubble / Reel / Depth) or restyling one — lists every file that must change and the order to do it in.
---
# Add a DoomGuard template

A template = one `Template` enum entry. It must be handled in **6 places**; miss one and you get a compile error (exhaustive `when`) or a template that renders wrong on one surface. Paths are under `app/src/main/java/com/poc/doomguard/`. Use `NAME` for the enum entry (e.g. `TIDE`) and `Name` for the Kotlin names (`Tide`).

Before starting, pin down with the user: label, one-line blurb, palette (bg, accent, danger), display font (system family, or a `res/font/` file like `anton.ttf`), shape language (rounded vs square), and what the overlay draws as the count rises.

## Touchpoints, in order
1. **`data/Template.kt`** — add `NAME("Label", "blurb")` to the enum. `TemplateStore.get` falls back to `REEL`; leave that alone.
2. **`ui/Theme.kt`**
   - `val Name = DgTheme(...)` inside `DgThemes` (fill every field).
   - `Template.NAME -> Name` in `DgThemes.of()`.
   - `Template.NAME -> NameBackdrop()` in `DgBackdrop()`, and a `private fun NameBackdrop()` drawn in code (no image assets).
3. **`ui/BreakName.kt`** (new) — `@Composable fun BreakName(s: BreakState, onDone: () -> Unit)`. Copy the structure of `BreakBubble.kt`: `DgBackdrop(Template.NAME)`, `DgThemes.Name` styles, show `s.remaining`, `s.left`, `s.quote`, `s.finished`, and a done button that calls `onDone`. Never touch the countdown — `BreakHost` owns it.
4. **`ui/QuoteBreakActivity.kt`** — `Template.NAME -> BreakName(state, done)` in the `when`.
5. **`overlay/BubbleView.kt`** — `Template.NAME -> drawName(canvas, w, h)` in `onDraw`, plus `private fun drawName(...)`. It reads the same pressure/count as the others. Plain `View`, no allocations in `onDraw`.
6. **`MainActivity.kt`** — two `when (Template)` blocks: the hero (`BubbleHero / ReelHero / DepthHero` call site) and the preview inside `TemplateTile`. Add `NameHero(...)` beside the other heroes.

## Then
- Run the `template-parity-check` skill. All counts must match.
- Builders do not run Gradle/adb. Hand off to the main session / `device-verifier` for the build and the device pass (`device-loop` skill).
- Update `STATUS.md` with a Log line. The template is DONE when it compiles, VERIFIED only with device evidence on all three surfaces (home, break, overlay).

## Don't
- Don't add `else ->` to a `when` over `Template`.
- Don't hard-code colours or fonts in screens; put them in `DgTheme`.
- Don't change the order of existing enum entries (`TemplateStore` persists the `name`, but the picker row follows enum order).
