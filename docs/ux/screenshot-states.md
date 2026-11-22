# Screenshot pilot: accessibility expectations (UX input)

Author: ux-designer, 2026-11-22. Companion to `docs/design/screenshot-pilot.md` (visual-designer owns subjects, fixtures and visual expectations). This file adds the **a11y bullets** for S1-S3 only, plus the checks a screenshot cannot make. visual-designer's spec links here instead of copying.

Contrast ratios below were computed with the WCAG 2.x formula from the hex values in the pilot spec and `Color.kt`.

## S1: `LegHeader` + `LegTimes`
Screenshot checks:
- Text contrast on `heroCard` #181A1B. Stop name `onHero` #E77488 = **6.0:1**; details and labels `onHeroCard` #E9E4DE = **13.8:1**. Both pass AA. Re-check if any token changes in T10.
- fs 2.0: no text clipped and no ellipsis on any LegTimes **value**. Labels ("Départ" / "Trajet" / "Arrivée") may wrap but must not be cut. If the three columns cannot fit at 380 dp, the expected result is a wrap (FlowRow or stacked), never truncation.
- `StopTypeTag` text #151515 on the Meal colour must be >= 4.5:1. visual-designer should confirm the Meal hex in each palette (Analogue at least).
- The type is not shown by colour alone: the tag shows the word ("Repas"/"Meal") and its icon.

Needs a semantics test, not a screenshot:
- Each LegTimes column is read as one item ("Départ, maintenant"), not as label and value separately (`mergeDescendants`).
- Reading order: stop name, then tag, then details, then the times.

## S2: `EventOptionCard` (default / selected / long title)
Screenshot checks:
- **Selected is visible in a greyscale copy** (check icon plus the 2 dp border). The fill change alone does not count.
- Card height >= 48 dp at both scales (the min is 80 dp, so this passes by construction; keep it in the expectations so a refactor can't drop it).
- Subtitle `onSurfaceVariant` on `card`: >= 4.5:1 in both themes. Dark is #D7C2BA on #251E1B = 9.6:1 (passes). Light: confirm with the final `card` token.
- fs 2.0: the long title may ellipsize after 2 lines, but the time and price in the subtitle must stay fully visible; they are what users compare. If the subtitle is cut, that is a finding.
- "fin non publiée" / "end time not published" is fully visible in both locales.

Needs a semantics test:
- The card exposes `selected = true` / `false` (for example `Modifier.semantics { selected = ... }` or `selectable(...)`), so TalkBack says "Selected". Today `HorizontalInfoCard` exposes no state (A2 #23).
- The check icon is decorative (`contentDescription = null`), so it is not announced twice.
- Role is Button; the whole card is one focus stop with title, time and price merged.
- `assertHeightIsAtLeast(48.dp)` on the clickable node.

## S3: step page (StepProgress + 2 BottomActions), FR
Screenshot checks:
- Both pinned buttons are >= 48 dp high (spec says 56) and full width; labels wrap rather than ellipsize at fs 2.0.
- **At fs 2.0 the scrolling content keeps at least about half the screen** (915 dp height: app bar, progress and pinned actions together <= about 450 dp). If they take more, the page is unusable at large font (A2 #24) and that is a finding for T11.
- **Contrast warning for D3 (canvas pages):** `primary` #E4002B on `canvas` #F0F0F1 = **4.25:1**, which **fails AA (4.5:1) for normal-size text**. It affects any primary-coloured text on canvas: the outline button label "Arrondissement suivant", `StopTypeHeader`, text buttons, and "Retirer". On white it passes (about 4.8:1). Either keep those texts on white or a card, or use a darker red for text on canvas. Please check this in the S3 images.
- The StepProgress track stays distinguishable from the filled part (also in greyscale). The step number is not conveyed by the bar alone; see below.
- The back arrow's touch target is >= 48 dp (`IconButton` gives this).

Needs a semantics test:
- StepProgress is read as "Étape 4 de 5" / "Step 4 of 5" (it uses `onboarding_step`; the string is generic, so it works for Planning too).
- Focus order: back, title, progress, content, then the pinned actions last.
- `assertHeightIsAtLeast(48.dp)` on both buttons; the visible label is the accessible name (no `contentDescription` override).

## Optional: 360 dp width
For S2 and S3, only if skill-curator says it is cheap. 360 dp at fs 2.0 is where the FR labels and LegTimes break first. Not required for the pilot minimum (FR + fs 2.0, EN + fs 1.0), which the 20-image matrix already covers.

## Outside the pilot (kept for later, not S1-S3)
The per-screen states for StopSelection (loading, empty, meal prompt, last borough), DistrictPick, Review, AtStop, EndOfDay and the T3 dialog need `FlowViewModel` or assets, so they are out of scope for the JVM pilot. Cover them with an emulator pass (`run-android` skill) or after the pilot. The TalkBack, map, notification-language and live-change checks also need a device; see `docs/ux/initial-audit.md` #10, #11, #15 and #23.
