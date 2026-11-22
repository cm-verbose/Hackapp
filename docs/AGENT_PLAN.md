# Multi-agent plan: finishing Planning

**Status: approved 2026-11-22. The design phase is running. No team writes app code until you approve its maquettes.**

This plan covers the rest of section 5 of `docs/IMPLEMENTATION_PLAN.md`: Meal choice, Event and Meal selection, the supporting sheets, Itinerary review and Stop edit warnings. Underway is out of scope because we grill it first. Passport stays with `cmvb`.

## Teams

| Team | Branch | Scope | Depends on |
|---|---|---|---|
| **Meals** | `team/meals` | Meal choice, with per-Meal time and budget | Nothing |
| **Sheets** | `team/sheets` | Meal picker, Planned Departure, Event details with ticket price | Nothing |
| **Selection** | `team/selection` | Event and Meal selection, drop-in Events, running total | Meals, Sheets |
| **Review** | `team/review` | Itinerary review and Stop edit warnings | Selection |

Teams merge in that order. A dependent team starts building once the team it depends on is merged, so it never builds against unfinished code.

## Team composition

A **team** is the six roles below, and at most **5 teams** run at once at every step of the plan. Use that capacity whenever work can safely run in parallel. Each role starts only when its phase begins, so a team never runs all five agents at once. The main session coordinates the teams, merges finished branches, runs the final checks and reports to you. It is not part of any team.

| # | Agent | Model | Writes code? | Skills | Why this model |
|---|---|---|---|---|---|
| 1 | **Feature Engineer** | Opus 5.5 (`claude-opus-5-5`) | Yes: Compose pages and components | `figma:figma-design-to-code`, `figma:figma-implement-motion`, `run` | Handles the largest and most judgment-heavy UI work |
| 2 | **Domain Engineer** | Sonnet 5.5 (`claude-sonnet-5-5`) | Yes: ViewModels, Planning rules, tests | `domain-modeling`, `simplify` | The planning engine already exists and is tested, so this is mostly well-defined wiring |
| 3 | **Maquette Designer** | Sonnet 5.5 (`claude-sonnet-5-5`) | No: Figma only | `figma:figma-use`, `figma:figma-generate-design` | Composes frames from the existing design system |
| 4 | **UX Reviewer** | Opus 5.5 (`claude-opus-5-5`) | No: read-only | `run`, `figma:figma-use` (read-only) | Judges real rendered screens against Figma, the spec and your feedback |
| 5 | **Code Reviewer** | Opus 5.5 (`claude-opus-5-5`) | No: read-only | `code-review` (high), `security-review` | Reviews with no stake in the code, and looks for subtle time and state bugs |
| 6 | **Copy Editor** | Sonnet 5.5 (`claude-sonnet-5-5`) | Strings only | `plan-writing`, `quebec-french-ui`, `ux-writing` | Keeps each new screen concise in French and English from the start |

## Responsibilities

**1. Feature Engineer**
- Owns `*Page.kt` / `*Pages.kt`, `ui/components/`, navigation and `strings.xml` (French and English).
- Builds each page from its maquette as a stateless composable with previews.

**2. Domain Engineer**
- Owns `itinerary/`, `*ViewModel.kt` and their unit tests.
- Publishes each page's `UiState` and callbacks before the Feature Engineer starts that page.
- Implements the drop-in Event rule, per-Meal budgets, the running total, Stop edit warnings and draft saving.

**3. Maquette Designer**
- Adds the missing frames first: Meal picker, Planned Departure sheet and Event details sheet with ticket price.
- Updates existing frames when a decision changes them, including empty, loading and error states.

**4. UX Reviewer**
- Renders each page on the emulator in French and English and compares it with its Figma frame.
- Checks the flow against `screen-list.md`, the Glossary and your standing rules: Material icons, no selection checkmarks, deselectable chips, forward slides, and system Back to Home.
- Reports findings with screenshots and does not edit code.

**5. Code Reviewer**
- Reviews each milestone diff for correctness, especially after-midnight times, Meal Block overlaps, draft restoration and stale state.
- Runs `security-review` on shared ticket price handling.
- Reports ranked findings and does not edit code.

## Workflow

Each team runs through the same five steps. A team's finished feature is one milestone.

1. **Design:** the Maquette Designer updates Figma. **You approve the maquettes.**
2. **Contract:** the Domain Engineer publishes `UiState`, callbacks and any model changes on the team branch, while the Copy Editor adds the strings.
3. **Build:** the Domain and Feature Engineers commit atomically to the team branch, from a dedicated worktree.
4. **Review:** the Code and UX Reviewers check the branch. Owners fix confirmed findings, with at most two rounds before anything left is brought to you.
5. **Ship:** the team reports back. The main session merges its branch into `feat/ui-foundation` locally, runs all tests and installs on your phone. **Once you approve the milestone, the main session pushes `feat/ui-foundation`.**

## Writing pass (done 2026-11-22)

This runs across the whole app before the feature teams build. Five writing teams each cover one area, on a local branch and worktree. Each string key belongs to one team, listed in `docs/copy/key-ownership.md`.

| Team | Branch | Area |
|---|---|---|
| **Access** | `team/writing-access` | Auth, onboarding, Settings |
| **Home** | `team/writing-home` | Home and Itineraries |
| **Planning** | `team/writing-planning` | All Planning steps |
| **Underway** | `team/writing-underway` | Underway and Passport wording |
| **Shared** | `team/writing-shared` | Actions, states, navigation and reused text |

Finished team branches merge into `team/writing`. After a final consistency check across teams, the main session merges it into `feat/ui-foundation`.

Each team has the five roles below:

| Order | Agent | Model | Skills | Output |
|---|---|---|---|---|
| 1 | **Copy Lead** | Opus 5.5 | `plan-writing`, `ux-writing` | Screen-by-screen plan: delete, show through UI, merge or shorten |
| 2 | **French Editor** | Fable 5.1 | `plan-writing`, `quebec-french-ui` | Corrected Québec French |
| 2 | **English Editor** | Sonnet 5.5 | `plan-writing`, `ux-writing` | Corrected Canadian English |
| 3 | **String Steward** | Sonnet 5.5 | `plan-writing`, `localization-review` | Updated `strings.xml` and verified keys |
| 4 | **UX Copy Reviewer** | Opus 5.5 | `plan-writing`, `run` | Rendered check in both languages |

You approve the copy plan before the String Steward applies it. Agent files are in `.claude/agents/`.

## Guardrails

- Teams commit only to their own `team/*` branch, which stays local. Only the main session merges into `feat/ui-foundation` and pushes it. **Never push, merge or open a PR into `main`.**
- Agents own separate files. A change outside an agent's area, or to another team's feature, goes through the main session.
- **One Gradle build and one emulator at a time**, because the host has run out of memory before. The main session schedules them.
- Never run connected tests on your phone, because they uninstall the app and clear its data.
- Add no live Google calls, never commit API keys and never delete the Google cache.
- Do not commit `.idea`, `.kotlin` or `local.properties`.
- Commits are atomic, one feature each, with no Claude co-author trailer.

## Approved decisions

1. Work runs as teams of these six roles, up to 5 teams at every step, each on its own branch.
2. You approve every team's Figma maquettes before it builds.
3. The main session pushes `feat/ui-foundation` after you approve a milestone.
