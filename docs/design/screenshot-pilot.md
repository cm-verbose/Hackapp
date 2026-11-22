# Screenshot-testing pilot: subjects and matrix

Owner: visual-designer (subjects, expected states). Tool choice and setup: skill-curator. Accessibility review of the expected states: ux-designer. Status: proposal, 2026-11-22.

IDs `A3-n` refer to `docs/design/initial-audit.md`; `D1`-`D5` are the decisions in that file.

Accessibility expectations per subject (contrast figures, no-ellipsis rules, fs 2.0 content-space budget, semantics checks a screenshot cannot catch, optional 360 dp width for S2/S3): `docs/ux/screenshot-states.md` (ux-designer). Both files apply.
S3 also checks **D6** (red text at least 4.5:1 on its actual background): the outline label stays `onSurface`, and any red text on canvas is `onPrimaryContainer` #930018. S2: if a selected-state label or icon is added in red, it is `onPrimaryContainer` on `primaryContainer`.

## Principles
- Render JVM-side from composables. No emulator, no Firebase, no Room: every subject takes plain parameters.
- **Baselines are recorded per subject, when the task that fixes it is accepted.** Before that, a run is only a "current state" record and must not become a golden (S1 is expected to look wrong).

## Timing (team-lead, 2026-11-22)
| Step | When | Baseline |
|---|---|---|
| SC3 pilot infrastructure | after T3 | S2 recorded first (8 images) |
| S1 `LegHeader` | when T10 is accepted (M3) | S1 (8 images) |
| S3 step page | when T11 is accepted (M3) | S3 (4 images) |

Until T10/T11, S1 and S3 may be rendered for review but are not committed as baselines.
- Fixed fixture data (below) in both languages; no `LocalTime.now()`.
- One rendering per cell; file name `<subject>_<theme>_<locale>_fs<scale>.png`, e.g. `legheader_dark_fr_fs2.0.png`.

## Fixed device
| Use | Size | Density |
|---|---|---|
| Components (S1, S2) | width 380 dp (= `PreviewDefaults.WIDTH_DP`), height wraps | 420 dpi (xxhdpi-ish, like the `Medium_Phone` AVD) |
| Screen (S3) | 412 x 915 dp (`Medium_Phone` / Pixel 7 class), portrait | 420 dpi |

No system bars in component shots; S3 includes the navigation bar inset (gesture nav, 24 dp) so `BottomActions` padding is visible.

## Matrix
Axes: theme {light, dark} x fontScale {1.0, 2.0} x locale {fr (default), en}. Locale only where text length changes layout (marked). Total **20 images**.

| Subject | light/dark | fs 1.0 / 2.0 | fr / en | Images |
|---|---|---|---|---|
| S1 `LegHeader` + `LegTimes` | both | both | both | 8 |
| S2 `EventOptionCard` (3 states) in a column | both | both | both | 8 |
| S3 `HackappPage` step page (StepProgress + BottomActions) | both | both | fr only | 4 |

## S1: `LegHeader` with `LegTimes` (Underway Leg header)
Why: A3-1 (P0, hero text on light surface), D3 (Underway stays on hero). Fixed in M3 (T10). Exercises display type at 200%.

Fixture: `stopName = "Bistro du coin"`, `stopDetails = "Repas 19:30 · Le Plateau-Mont-Royal"` / `"Meal 19:30 · Le Plateau-Mont-Royal"`, `StopTypeTag(StopType.Meal)`, `LegTimes(depart = leg_now, travel = leg_walk_minutes(8), arrive = "19:23")`. Render the composable alone, on the page background it gets in the app (not the preview's HERO_CARD background); that is the point of the test.

Expected (after fix):
- Sits on its own `heroCard` #181A1B container, corner 32 (D4), padding 20. Same in light and dark (hero colours are mode-independent).
- Stop name `displaySmall` in `onHero` #E77488; details and labels in `onHeroCard` #E9E4DE. Every text >= 4.5:1 on its container.
- `StopTypeTag` "Repas"/"Meal": stop-type Meal colour, text #151515.
- fs 2.0: stop name wraps to 2+ lines, nothing clipped; the three LegTimes columns either fit or wrap (no ellipsis on "Maintenant"/"≈ 8 min à pied"). If they don't fit at 380 dp, that is a finding for T10 (allow the row to wrap into a FlowRow).
- fr vs en: "Prochain arrêt / Maintenant / ≈ 8 min à pied" are the long ones.
Current state (before fix): no container; pink and near-white text on whatever is behind. Expected to fail.

## S2: `EventOptionCard` (planning step 4 list item)
Why: A3-10 (empty grey media block), A3-9/D4 (list item = 16 corner), UX #23 / A3-14 (selected must not be colour-only), D3 (white `card` on canvas, dark card #251E1B per D5). First baseline (after T3).

Fixture, three cards stacked with 8 dp gap on `canvas`:
1. default: "Jazz sur le toit", 18:30, end null, `PriceLabel.Amount("20 $")` (shows "fin non publiée" / "end time not published").
2. selected: "Vernissage, galerie du Mile End", 18:00-21:00, `PriceLabel.Free`.
3. long title: "Festival international de jazz de Montréal : grand concert extérieur gratuit", 21:00-23:00, `PriceLabel.Unknown`.

Expected (after fix):
- Corner 16, container `card` (white light, #251E1B dark) on `canvas`; 1 dp `outlineVariant` border default.
- No trailing grey block (A3-10).
- Selected: `primaryContainer` fill + 2 dp `primary` border **plus** a non-colour cue (check icon). Visible in a greyscale copy of the image.
- Title max 2 lines with ellipsis at fs 1.0; at fs 2.0 the card grows in height (min 80 dp is a minimum, not fixed); subtitle allowed to wrap.
- Avatar letter centred in its 40 dp disc at both scales.
Current state: grey 80 dp block on every card, selected only by colour, white card on warm surface.

## S3: step page (`HackappPage` with `StepProgress` and two BottomActions)
Why: A3-2 (progress track uses dark secondaryContainer, D1), A3-6/A3-8/D2 (full-width labelLarge buttons, max 2 pinned), D3 (canvas page). Fixed in M3 (T11).

Fixture: `HackappPage(title = "Nouvel itinéraire", onBack = {}, step = 4, totalSteps = 5)`, content: one `titleMedium` line "18:00 – 19:00" and two S2 default cards; `bottomActions`: `HackappOutlineButton("Arrondissement suivant")` + `HackappPrimaryButton("Terminer ici")`. FR only (the FR labels are the longer ones: "Arrondissement suivant" vs "Next borough").

Expected (after fix):
- Page background `canvas`; app bar on canvas (or surface, whichever T11 picks, but the same on every page).
- StepProgress: 4/5 filled `primary`, remaining track `surfaceContainerHigh` (light) - the track must be visually lighter than the filled part in both themes.
- Two pinned buttons, both full width (16 dp side margins), equal width, `labelLarge`, min height 56, 8 dp apart. No third pinned action.
- fs 2.0: labels may wrap to 2 lines and the buttons grow; content scrolls behind; nothing clipped; the progress bar keeps its 4 dp height.
- Dark: track still lighter than the filled part (dark primary #FFB4AA vs dark surfaceContainerHigh #2F2826).
Current state: dark burgundy track heavier than the filled part; two buttons of different widths and label sizes.

## What a pass/fail means in the pilot
- A pixel diff over threshold against a recorded baseline = regression; review the image, then accept or fix.
- For the pilot, also do a one-time manual review of the 20 images against the "Expected" bullets above (visual-designer) and the a11y bullets (ux-designer: wrapping, greyscale selected state).
- Out of scope for the pilot: maps (`CityMap` loads assets), animations (render with motion disabled / at end state; `entrance()` must not leave alpha 0 in a still frame), screens that need `FlowViewModel`.
