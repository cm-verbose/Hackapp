# Skills inventory and gap audit (Hackapp)

Owner: skill-curator. Date: 2026-11-21. Status: audit only. Nothing has been installed, created or configured.

## 1. Summary (ranked recommendations)

| Rank | Candidate | Kind | Recommendation |
|---|---|---|---|
| 1 | `refresh-places-db` | New project skill | **Create** (after team-lead approval) |
| 2 | `room-schema-change` | New project skill | **Create**, or merge into rank 1 as a second section |
| 3 | `add-compose-component` | New project skill | **Create** (smaller benefit, but it is the most frequent task) |
| 4 | `kotlin-lsp` plugin (official marketplace) | Third-party plugin | **Defer** (workflow-auditor: not verified, no security blocker, low benefit at this size) |
| 5 | `run` (built-in) + a short project note on launching the emulator | Built-in + project skill | **Consider**: a small `run-android` project skill so `/run` knows `.\gradlew.bat installDebug` and the AVD |
| 6 | `pyright-lsp` plugin | Third-party plugin | **Defer**: the ETL is stable and rarely edited |
| - | anthropics/skills repo | Third-party | **Reject for now**: nothing for Android, Kotlin, Compose, Gradle or accessibility |
| - | `strings-parity` check | New project skill | **Reject**: parity is fine today (see section 3.4); a CLAUDE.md line is enough |

## 2. Inventory

### 2.1 Project skills (`.claude/skills/`)
None. `.claude/` holds only `agents/` (feature-engineer, skill-curator, ux-designer, visual-designer, workflow-auditor) and `settings.local.json`.

### 2.2 User skills (`~/.claude/skills/synced/`)
Two synced buckets (two accounts or orgs) with the same claude.ai skills; manifest sources are `anthropic` or `anthropic-example`. All are surfaced in this session as `anthropic-skills:*`.

| Skill | Purpose | Project relevance |
|---|---|---|
| skill-creator | Author, test and tune skills | **High**: use it to build ranks 1-3 |
| doc-coauthoring | Structured doc writing (second bucket only) | Low (design docs) |
| docs, docx, pdf, pptx, xlsx, google-workspace | Office and Google file handling | Low (pitch deck at the end of the hackathon, maybe) |
| deep-research | Multi-source research reports | Low-medium (event API research) |
| built-in-browser, chrome-browser, computer-use | Browser and desktop control | Medium: computer-use could drive the emulator window for a visual check |
| import-memory, morning | Personal assistant | None |

### 2.3 Built-in / session skills (Claude Code)
`code-review`, `simplify`, `security-review`, `run`, `init`, `fewer-permission-prompts`, `update-config`, `loop`, `schedule`, `claude-api`, `workflow-authoring`, `plugin-authoring`, `keybindings-help`, `dataviz`, `artifact-*`, `claude-in-chrome`.
Relevant here: `code-review` and `security-review` (before merges; note `app/google-services.json` is committed), `simplify`, `run` (no project launch recipe yet, see rank 5), `fewer-permission-prompts` (agents hit many Gradle/git prompts).

### 2.4 Plugins
No plugin is enabled (`~/.claude/settings.json` has no `enabledPlugins`). Two marketplaces are known: `claude-plugins-official` and `claude-code-plugins` (anthropics/claude-code). Relevant entries in the official marketplace, all authored by Anthropic:

| Plugin | Contents | Relevance |
|---|---|---|
| kotlin-lsp | LSP config only (`kotlin-lsp --stdio`, `.kt`/`.kts`) | Medium-high: definitions, references, diagnostics without a full Gradle build |
| pyright-lsp | LSP config only | Low (ETL) |
| pr-review-toolkit | 6 review agents + `/review-pr` | Overlaps the built-in `code-review` |
| feature-dev | Explore/architect/review agents | Overlaps our own team agents |
| claude-md-management | CLAUDE.md audit skill + `/revise-claude-md` | Low-medium: CLAUDE.md is long; team-lead owns it |
| frontend-design | Web UI skill | None (web, not Compose) |
| security-guidance | Hooks (Python) that review edits and commits; calls an LLM | Not now: heavy, adds hooks on every edit |
| hookify | Rule-based hooks | Not now |
| external: firebase | `.mcp.json` (Firebase MCP) | Not now; adds an MCP server and credentials |

## 3. Gaps against real project needs

Procedures documented in CLAUDE.md, judged on whether a skill saves effort over reading CLAUDE.md.

### 3.1 Refresh places.db (rank 1)
Steps today (CLAUDE.md sections 4, 5, 5.2, 8): run the pipeline with the Google cache but **zero** calls (unset `GOOGLE_MAPS_API_KEY`, pass `--google-key ""`, never `--no-google`), check `out/report.json`, copy `out/places.db` into `app/src/main/assets/databases/`, bump `DB_VERSION` in `etl/mtl_food_etl/pipeline.py` **and** `PlacesDatabase.VERSION` (both 4 today), run `python tests/test_etl.py`, then `.\gradlew.bat connectedDebugAndroidTest` with an emulator.
Why a skill helps: the instructions are spread over four sections, and two mistakes are costly (an accidental paid Google run; `--no-google` silently dropping all Google data). A skill can carry a checklist and a guard ("refuse to run without `--google-key \"\"` or `--google-limit`").
Benefit: high per use, though rare (a few times per project). Maintenance: low.

### 3.2 Room entity / schema change (rank 2)
Steps: edit `local/Entities.kt` -> build to export `app/schemas/<db>/<version>.json` -> copy the CREATE statements into `etl/sql/schema.sql` -> bump both versions -> re-run the ETL (3.1) -> connected test. Room throws on first open if the prepackaged file differs by one column.
Benefit: medium-high; the failure is only visible at runtime on a device. Could live as a section of the rank 1 skill (they share the version bump and test steps). Recommendation: one skill `places-db` with two entry paths, unless team-lead prefers two.

### 3.3 Add a Compose component (rank 3)
Conventions (CLAUDE.md 6.2): theme tokens only, `stringResource` with FR in `values/` and EN in `values-en/` (no `values-fr*`), defaults as parameters, `@Preview` in `HackappTheme` with `PreviewDefaults`, never `/*` inside KDoc. 35 component files exist and more are coming from the screen work.
Benefit: medium per use but frequent; mostly prevents review churn (hard-coded colours, missing EN string, block comment build break). Risk: it repeats CLAUDE.md; keep the skill to a checklist plus one template file and point to CLAUDE.md for detail.

### 3.4 Strings parity (rejected)
Checked: `values/strings.xml` has 196 strings, `values-en/` has 186. The 10 missing keys are all `translatable="false"` (age ranges, language names, `leg_mode_uber`, ...), so parity is correct. Not worth a skill; `lint` (`MissingTranslation`) already covers it.

### 3.5 Running the app / emulator (rank 5)
`adb` is not on `PATH` (it exists at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`). The built-in `run` skill looks for a project launch skill first. A short `run-android` skill (start the `Medium_Phone` AVD, `.\gradlew.bat installDebug`, launch `com.example.hackapp/.MainActivity`, screenshot with `adb exec-out screencap -p`) would let agents verify UI changes on a device, which CLAUDE.md lists as a known gap (flow never driven end to end). Login needs real Google credentials, so the skill should say how to reach screens without signing in, or stop there.

### 3.6 Not gaps
Gradle troubleshooting: covered by CLAUDE.md notes (`.\gradlew.bat`, compose BOM in androidTest); no repeated pattern yet. Accessibility review: no Android-specific skill exists anywhere official; the ux-designer agent can do it from M3 guidance.

## 4. Third-party candidates (detailed format)

### kotlin-lsp
- **Skill**: kotlin-lsp (plugin, LSP server config)
- **Source**: `anthropics/claude-plugins-official`, author Anthropic; wraps JetBrains `Kotlin/kotlin-lsp`
- **Purpose**: go-to-definition, find references, hover, diagnostics for `.kt`/`.kts`
- **Relevant agents**: feature-engineer, workflow-auditor, any reviewer
- **Compatibility**: the README only shows `brew install JetBrains/utils/kotlin-lsp`; on Windows the binary must be installed manually from the JetBrains releases and put on `PATH`. JetBrains kotlin-lsp is pre-release and its Android/AGP 9 project import is unverified; startup timeout is 120 s
- **Security concerns**: plugin has no scripts; risk is the external binary (JetBrains, signed releases). Enabling a plugin changes user config
- **Installation requirements**: user approval; `/plugin install kotlin-lsp@claude-plugins-official`; kotlin-lsp binary + JDK 17+
- **Expected benefits**: faster, cheaper navigation in a codebase of about 100 Kotlin files; diagnostics without a 1-2 min Gradle compile
- **Verification**: open `domain/Planner.kt`, request references to `ItineraryPlanner`, and confirm an unresolved symbol is reported after a deliberate typo
- **Recommendation**: **Defer** (updated after review). workflow-auditor (2026-11-21): NOT VERIFIED, no security blocker. The plugin files (plugin.json, Apache-2.0 LICENSE, README) are low risk. The real risk is the binary: it runs with user privileges and imports the Gradle project (runs build scripts) at startup. Concerns: the binary must come only from JetBrains' GitHub releases with a checked checksum; AGP 9.5 / Kotlin 2.4 import is unverified; it may compete with our builds for the Gradle daemon and locks (only one agent runs Gradle at a time); it needs user approval. Acceptance test if ever approved: go-to-definition and diagnostics on `domain/Planner.kt` are correct while `.\gradlew.bat assembleDebug` still passes. Low benefit over Grep plus a Gradle compile at this project size

### pyright-lsp
- Source: same marketplace, Anthropic. Needs `pip install pyright` (a working Python; note `python` on PATH is the WindowsApps alias). Benefit low because ETL edits are rare. **Defer.**

### anthropics/skills (github.com/anthropics/skills)
19 skills (docx, pdf, pptx, xlsx, skill-creator, mcp-builder, webapp-testing, frontend-design, theme-factory, canvas-design, ...). None targets Android, Kotlin, Compose, Gradle, mobile testing or accessibility; `webapp-testing` is Playwright for web apps. The useful ones (skill-creator, doc skills) are already synced. **Reject for now.**

## 5. Next steps (need team-lead approval)
1. Approve ranks 1-3 (and 5 if wanted); I build them with `skill-creator`, keep each to a checklist that links to CLAUDE.md, and verify discovery plus one representative dry run (no Google calls, no data change).
2. kotlin-lsp: deferred after workflow-auditor's review; revisit only if the codebase grows a lot or Gradle compiles become a bottleneck.
3. Suggest to team-lead: a CLAUDE.md line noting that `adb` is not on `PATH`.

## 5a. Created project skills (team-lead approved, 2026-11-21)
| Skill | Path | Status |
|---|---|---|
| places-db | `.claude/skills/places-db/SKILL.md` | workflow-auditor: **APPROVED** after the fix (first review: CHANGES REQUESTED). **Blocker:** PS 5.1 drops `""` arguments, so the command now uses `--google-key=`. Also fixed: the key is cleared in the same command line as the pipeline run, every command sets its own folder (agent shells reset between calls), and the step numbers were updated. Steps not executed |
| add-compose-component | `.claude/skills/add-compose-component/SKILL.md` | workflow-auditor: **APPROVED**. Nit applied: points to the plan's Gradle rule |
| run-android | `.claude/skills/run-android/SKILL.md` | Approved by team-lead (device checks for "Likely" findings). workflow-auditor: **APPROVED with small changes**, all applied: PS-safe locale reset, reinstall after connected tests, COARSE location grant and revoke, and what `pm clear` keeps and wipes. Facts checked read-only on the running emulator: adb path from `local.properties`, AVD `Medium_Phone` is a Play Store image (no `adb root`), TalkBack installed, `cmd locale set-app-locales` available. `UnderwayReceiver` is not exported, so shell broadcasts cannot trigger it on this image (documented as a limit) |

**Discovery verified (2026-11-21, team-lead):** places-db, add-compose-component and run-android appear in the session skill list without a restart. All three are accepted.

Frontmatter was checked by hand: valid `name` and `description`, no `: ` or ` #` sequences in plain scalars. PyYAML is not installed, so no parser check was run. Claude Code lists new project skills only after a session restart, so discovery still has to be confirmed in a fresh session. kotlin-lsp needs user approval; run-android is deferred until a device test task exists.

## 6. Teammate input
Asked feature-engineer, ux-designer and visual-designer for missing capabilities (one message each). All three replied.

- **feature-engineer**:
  - (a) There is no quick way to check the alarm and notification flow on an emulator: `adb shell dumpsys alarm`, `cmd notification`, `pm grant ... android.permission.POST_NOTIFICATIONS`, and firing `UnderwayReceiver` actions with `am broadcast`.
  - (b) Adding a string in FR and EN in one go.

  Assessment:
  - (a) Same device need as ux-designer. **Fold it into one `run-android` skill** with a section on checking the underway notifications, instead of two skills. adb is at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`, not on `PATH`. Three agents now want this, so it is the strongest remaining candidate.
  - (b) Already covered by step 6 of `add-compose-component`; no separate skill.

- **visual-designer**:
  - (a) A contrast checker that reads `ui/theme/Color.kt` and reports WCAG ratios for every on/container pair and for the stop-type colours against surfaces. These were worked out by hand.
  - (b) Rendering `@Preview` to PNG (Paparazzi or Roborazzi) to see light and dark without an emulator.
- **ux-designer**:
  - (a) Run the app on an emulator, take adb screenshots along a route, and toggle font scale or TalkBack. Without that, back-stack, notification and TalkBack findings stay "Likely".
  - (b) Preview screenshots at 200% font scale in FR and EN.

Assessment:
- **Preview screenshot rendering** (both designers' second choice, and visual-designer's first). This is a **Gradle dependency and test setup** (Roborazzi or Paparazzi, or AGP's Compose Preview Screenshot Testing), not a skill. It changes `build.gradle.kts` and the version catalog, so it is a feature-engineer / workflow-auditor task that needs team-lead approval. A thin skill could later document how to record and inspect the images. **Recommend** proposing it as a Phase 2 task: it serves visual-designer, ux-designer and regression checks.
- **run-android** (ux-designer's first choice) raises the priority of rank 5. It was deferred by team-lead until a device test task exists, and ux-designer's audit is such a need. Login still needs real Google credentials; the skill would have to document a debug route past it. **Recommend** reconsidering it.
- **Contrast checker**: it needs a small script (parse hex tokens, compute WCAG ratios), and the current rules forbid bundling scripts. It could run once as an ad-hoc check by visual-designer instead. **Low priority** as a skill; reconsider only if the theme changes often.
