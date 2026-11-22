---
name: add-compose-component
description: Checklist for adding or changing a reusable Jetpack Compose component in Hackapp (ui/components/), usually from a Figma frame. Use when creating a new composable card, row, chip, sheet or map widget, or when adding user-facing strings to one. Covers theme tokens, the team's design decisions (corners, colours, buttons, spacing), French-default/English strings, @Preview with HackappTheme, and the KDoc "/*" build trap.
---

# add-compose-component

Background: CLAUDE.md section 6 (6.1 theme, 6.2 component conventions, 6.3 strings). Figma file `ETS-HackApp` (fileKey `VXMqds2RqjgaZ0JXHNNM6o`), Components page `53:1079`, strings page `55:2` (FR frame `55:3`, EN frame `55:7`).
Reference example to copy the structure from (KDoc, parameters, preview): `app\src\main\java\com\example\hackapp\ui\components\InfoCallout.kt`. Its corner (`Medium`) and background predate the design decisions in step 4a; follow step 4a, not the example, for those.

## Before writing
1. Search `ui\components\` (and `ui\components\map\`) for an existing component; extend it rather than duplicating it. Check `HackappSmallButton.kt`, `HackappButtons.kt`, `HackappIcons.kt`, `StopTypeTag.kt`, `ItineraryStrip.kt`, `StopRow.kt`.
2. Note the Figma node id. Put it in the KDoc, e.g. `/** Figma "InfoCallout" (53:1327): ... */`.

## Writing the component
3. File: `ui\components\<Name>.kt`, package `com.example.hackapp.ui.components`. One public `@Composable fun <Name>(..., modifier: Modifier = Modifier)`.
4. **Tokens only, no literals for design values:**
   - Colours: `MaterialTheme.colorScheme.*`, `HackappTheme.appColors.*`, or `HackappTheme.stopTypeColors[type]`. Text on stop-type colours is the fixed `#151515` defined in the theme. Never `Color(0x...)` in a component.
   - Type: `MaterialTheme.typography.*`.
   - Corners: `RoundedCornerShape(HackappCorners.<Size>)`, where the sizes are ExtraSmall 4, Small 8, Medium 16, LargeIncreased 20, Large 24, ExtraLarge 32, ExtraExtraLarge 48.
   - Spacing in `dp` is fine (no spacing tokens exist).
   - It must work in dark theme too: check the preview with `darkTheme = true` if the component uses surfaces or hero colours.
4a. **Design decisions.** Source: the "Decisions" section of `docs/design/initial-audit.md`, approved by team-lead; read it for the reasons. They apply to **new and changed components now**. Existing components are brought in line by tasks T10-T12, so do not refactor unrelated files.
   - Corners by role (D4): hero/feature card `ExtraLarge` 32 with padding 20; content card or callout `Large` 24 with padding 16; list item or option card `Medium` 16 (content 16 horizontal, 12 vertical).
   - Never rely on M3 defaults that use `secondaryContainer`, which is dark burgundy #5D002C (D1). Examples: `FilledTonalButton`, the `NavigationBar` indicator, a `SegmentedButton` active container, `Badge`, chip selected containers. Pass explicit colours, e.g. `primaryContainer` / `onPrimaryContainer`.
   - Content cards are `AppColors.card` on a `AppColors.canvas` page (D3, D5). Use `onHero` / `onHeroCard` text only inside a component that draws its own `hero` / `heroCard` background, never relying on the caller's page.
   - Buttons (D2): reuse `HackappButtons` / `HackappSmallButton`. Labels use `labelLarge`. Size with `heightIn(min = ...)` only (56 page, 40 small, touch target at least 48). No fixed `height` and no `maxLines = 1`.
   - Red text (D6, final): red text must reach at least 4.5:1 on the background it actually sits on.
     - `primary` is for fills, borders, icons and progress. Use it for text only on a white `card` (4.85:1).
     - Red text anywhere else (canvas, sheets, dialogs, chips) and all text on `primaryContainer` use `onPrimaryContainer`.
     - On `errorContainer`, use `onErrorContainer`.
     - Do not use `SesameRedAccessible` / #D60028.
     - Always pass `contentColor` explicitly to `TextButton` and dialog buttons, because the M3 default is `primary`.
   - Spacing grid: 4 / 8 / 12 / 16 / 24 dp (no 10). 20 is used only for hero/feature card padding (D4).
5. Icons: through the `HackappIcon` enum / `HackappIconView` in `HackappIcons.kt`. Add an enum entry rather than using Material icons directly.
6. **Strings:** user-facing text comes from `stringResource(R.string.x)`, passed as a parameter default so callers can override it (`label: String = stringResource(R.string.x)`).
   - Add every key to **both** `app\src\main\res\values\strings.xml` (French, the default, copied from Figma frame `55:3`) and `app\src\main\res\values-en\strings.xml` (English, from frame `55:7`).
   - Mark non-translatable keys `translatable="false"` in `values\` only.
   - Never create `values-fr*` folders (CLAUDE.md 6.3).
   - If you wrote the English yourself, or a key is not on the Figma strings page, say so in your report. CLAUDE.md open item 8 tracks that.
7. **Comment trap:** Kotlin block comments nest. Never write `/*` inside a comment, e.g. "Icons/*" or a glob path in KDoc. The file then fails with "Unclosed comment". Write `Icons/...` instead.

## Preview
8. Add at least one private preview, wrapped in `HackappTheme`, using `PreviewDefaults`:
   `@Preview(name = "<Name>", showBackground = true, backgroundColor = PreviewDefaults.CANVAS, widthDp = PreviewDefaults.WIDTH_DP)`
   Preview on the background the screen actually uses (`CANVAS` for pages, D3). A component that does not draw its own hero background must also have a `CANVAS` preview, because a `HERO_CARD` preview background hid defect A3-1. Use `PreviewDefaults.HERO_CARD` only for components shown on a hero card. Add extra previews for important states (selected, disabled, long text in French).

## Verify
9. Compile: `.\gradlew.bat :app:compileDebugKotlin`. Run Gradle from one agent at a time. During audits only workflow-auditor runs Gradle (`docs/implementation-plan.md`).
10. Grep your new file for hard-coded design values and expect no hits: a `Color(0x` literal, a `.sp` font size outside the theme, or a hard-coded string in `Text("...")`.
11. Report which strings you added, whether the English was checked against Figma, and the Figma node id.
12. Do not commit unless asked. If the component is part of a Figma group listed in CLAUDE.md 6.2, suggest the CLAUDE.md update to team-lead instead of editing it.
