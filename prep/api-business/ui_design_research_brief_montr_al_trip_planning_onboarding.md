# Design Research & Benchmarking Brief
**UI Architecture: Civic Identity, Multi-Modal Trip Planning & Card-Swipe Onboarding**

> Comparative UI/UX breakdown covering the City of Montréal (*Hochelaga*) design language, real-time navigation interfaces (Chrono, Maps, Waze), and progressive relationship-app onboarding flows (Tinder, Hinge, Bumble).

---

## Executive Summary & Research Scope

This brief consolidates visual systems, layout patterns, and interaction mechanics across three distinct product domains to guide our upcoming UI/UX alignment:

1. **Civic & Municipal Trust (City of Montréal / *Hochelaga*):** High-accessibility color tokens, bilingual typography, rosette branding, and task-first card layouts.
2. **Spatial & Multi-Modal Navigation (Chrono, Google/Apple Maps, Waze):** Bottom-sheet state architecture, transit line color coding, and glanceable floating route cards.
3. **High-Conversion Onboarding & Card Decks (Tinder, Hinge, Bumble):** Progressive single-question disclosure, tactile swipe physics, and modular prompt cards.

---

## Section 01: City of Montréal Design System (*Hochelaga* / `montreal.ca`)
*Balancing WCAG AAA municipal accessibility with iconic Québec civic branding and bilingual clarity.*

Internally codenamed **Hochelaga** and developed by the City of Montréal's UX/IT division, the municipal design system unifies web portals (`montreal.ca`) and citizen-facing mobile utilities (*311 Montréal*, *Info-Neige*). Its core philosophy eliminates decorative clutter in favor of high-contrast scannability, strict bilingual parity (French/English), and instant access to civic services.

### 1.1 Color Palette & Semantic Tokens

| Token / Role | Hex Code | Primary UI Application & Accessibility Notes |
| :--- | :--- | :--- |
| **Primary Civic Red** | `#E4002B` | High-priority primary CTAs, active navigation tabs, focus rings, and the municipal rosette accent. Used sparingly to maintain visual hierarchy. |
| **Deep Slate / Charcoal** | `#1E242B` | Primary body typography, dark header/footer anchors, and high-emphasis icons. Chosen over pure `#000000` to reduce eye strain while keeping WCAG AAA contrast. |
| **Secondary Slate** | `#333C45` | Subheadings, secondary labels, and form field descriptions. |
| **Surface Neutrals** | `#F4F6F8` / `#FFFFFF` | Cool light-gray page canvas (`#F4F6F8`) paired with pure white elevated cards (`#FFFFFF`, `1px solid #DCE1E7` border) to separate dense municipal rules. |
| **Civic Alert & Status** | `#F5A623` / `#00856A` | Warm Amber (`#F5A623`) for construction/snow-removal detours; Teal/Emerald (`#00856A`) for open facility badges and confirmation states. |

### 1.2 Typography, Logos & Iconography
* **Typography System:** Clean humanist/geometric sans-serif pairing (**Plus Jakarta Sans** / **Inter** / **Helvetica Neue** fallbacks). Headers use tight negative tracking (`-0.02em`) for crisp French accents (`é, è, ê, ç`), paired with generous `1.5x` line-height on body copy for long-form civic regulations.
* **The Municipal Rosette Emblem:** The historic four-petal botanical rosette (representing French, English, Scottish, and Irish founding heritage) paired with a bold, clean **Montréal** wordmark. Functions effectively at small mobile header scales (`28px–36px`).
* **UNESCO City of Design Signature:** Developed by Montréal design studio FEED (updated 2025), employing an urban-grid stencil aesthetic used across cultural, architectural, and innovation-focused sub-initiatives.
* **Custom Monoline Iconography:** Uniform `2px`-stroke geometric vector icons (created by Fabien Laborie and the internal UX team) covering public transit, snow removal, permits, cycling paths, and parks—ensuring immediate comprehension across language barriers.

### 1.3 Page Layouts & Digital Identity Architecture
* **Task-First Above-the-Fold Hero:** Replaces decorative full-bleed photography with an immediate global search bar and "Quick Access" pill shortcuts (e.g., *Info-Remorquage*, *Collectes*, *Permis*), prioritizing citizen utility over marketing.
* **Card-Grid & Accordion Modularity:** Relies on flat bordered cards (`1px solid #DCE1E7`, `8px` border-radius) and progressive accordions to structure multi-step procedures and bilingual notices without overwhelming mobile viewports.

**Reference Links:**
* [Dribbble: City of Montréal Design System (Fabien Laborie)](https://dribbble.com/shots/9530940-City-of-Montreal-design-system)
* [Hochelaga Design System Case Study (Jean-François Poulin)](https://www.jfpoulin.com/blog/2019/12/15/le-design-system-de-la-ville-de-montreal/)
* [Montréal UNESCO City of Design Guide](https://designmontreal.com/en/show-you-belong)

---

## Section 02: Trip Planning & Navigation UIs (Chrono, Maps, Waze)
*Spatial hierarchy, bottom-sheet state machines, and glanceable multi-modal route comparison.*

Navigation and trip-planning interfaces solve a dual-state UX challenge: high information density during pre-trip route comparison, followed by ultra-low cognitive load during active transit or driving. Analyzing **Chrono** (ARTM Montréal), **Google/Apple Maps**, and **Waze** highlights complementary strengths for local multi-modal routing.

| Platform | Core Layout Pattern | Key UI Components & Micro-Interactions | Visual & Color Strategy |
| :--- | :--- | :--- | :--- |
| **Chrono** (ARTM Montréal) | **Split Map + Persistent Bottom Drawer** with a card-based home dashboard for favorite stops and OPUS card balance. | • **Multi-Modal Filter Chips:** Unified timeline blending STM Metro, REM, EXO commuter trains, BIXI dock counts, and Communauto car-share.<br>• **Live Occupancy Bars:** 3-bar crowding icons embedded inside departure rows.<br>• **NFC Fare Widget:** Tap-to-read OPUS pass card UI. | Official transit line color-coding (STM Orange `#F37021`, Green `#009B48`, Blue `#0083CA`, Yellow `#FFE400`, REM Green `#82C341`) anchored on crisp white cards. |
| **Google & Apple Maps** | **Full-Bleed Interactive Map Canvas** overlaid by a 3-state draggable bottom sheet (*Peek → Half-Screen → Full List*). | • **Segmented Route Cards:** Side-by-side duration/distance pills with inline badges (*Fastest*, *Fewest Transfers*, *Wheelchair Accessible*).<br>• **Vertical Transit Spine:** Connected dot-and-line diagrams displaying transfer stations and exact stop counts. | Desaturated base map tiles (`#F8F9FA` roads, muted sage parks) so active route polylines (`#1A73E8` electric blue) pop with immediate figure-ground separation. |
| **Waze** | **HUD-Style Floating Overlays** engineered to maximize forward map visibility while in motion. | • **"Block-by-Block" Modular Cards:** Rounded floating cards (`16px–20px` radius) showing bold ETA deltas (`+3 min`, *Best route*) and oversized tap targets.<br>• **Crowdsourced Hazard Pins:** Expressive vector pins ("Moods" & road alerts). | Designed by Pentagram around **Boing** rounded sans-serif typography, flat vibrant fills, and a playful street-grid visual language. |

**Reference Links:**
* [Chrono Official UI Overview](https://chronoapp.quebec/en/chrono-mobile-app)
* [Pentagram: Waze Brand & UI System](https://www.pentagram.com/work/waze)
* [Dribbble: Waze Floating Card Explorations](https://dribbble.com/search/waze)

---

## Section 03: Onboarding Flows & Swipe-Card Interfaces (Tinder, Hinge, Bumble)
*Progressive single-step disclosure, tactile card-stack physics, and attribute-level engagement.*

Modern relationship and discovery apps replace traditional multi-field registration forms with conversational, low-friction card flows. Crucially, the onboarding flow doubles as an interactive tutorial—teaching users the core gesture vocabulary before they ever reach the main discovery feed.

### 3.1 Onboarding Pacing & Conversion Mechanics (Bumble & Hinge)
* **One-Question-Per-Screen Pacing:** Breaks preference capture into 10–15 bite-sized steps anchored by a thin top segmented progress bar. Eliminates vertical scrolling and reduces cognitive fatigue on mobile screens.
* **Privacy & Trust Micro-Reassurance:** Bumble embeds small lock/eye icons with reassuring microcopy directly beneath input fields (e.g., *"We only show your age, not your birthday"* or *"You can change this anytime in settings"*), significantly lowering drop-off.
* **Interactive Preference Chips:** Multi-select pill tags (interests, intent, lifestyle badges) with spring-scale tap feedback allow rapid profile calibration without keyboard typing.

### 3.2 Card-Stack Swipe Physics vs. Prompt-Driven Scroll

| Interaction Model | UI Anatomy & Gesture Mechanics | Best Fit for Product Discovery |
| :--- | :--- | :--- |
| **Binary Card Stack** (Tinder / Bumble) | • **Full-Bleed Vertical Card** (`4:5` or `9:16` ratio, `16px–24px` radius) with bottom linear gradient scrim (`rgba(0,0,0,0)` → `rgba(0,0,0,0.75)`) for legible `24px+` white overlay type.<br>• **Drag & Tilt Physics:** Dragging tilts the card (`±12°` rotation) and fades in directional stamp badges (`LIKE` / `PASS`), snapping back if released below `~35%` screen width.<br>• **Bottom Circular Action Dock:** Rewind, Pass, Save/Star, and Like buttons provide accessible tap alternatives. | Ideal for rapid preference calibration during onboarding (e.g., swiping right/left on sample Montréal activities, neighborhoods, or trip styles) and fast binary browsing. |
| **Prompt-Driven Modular Scroll** (Hinge "Anti-Swipe") | • **Vertical Interleaved Feed:** Mixes full-bleed photos with structured text-prompt cards (*"A weekend ritual I love..."*).<br>• **Contextual Element Reactions:** Dedicated heart/comment buttons attached to each individual card module rather than the whole profile. | Ideal when users want to save or react to a specific attribute of a destination or itinerary (e.g., liking a specific café stop or transit option within a broader route). |

**Reference Links:**
* [Hinge UI Design Patterns & Prompts (Appthetics)](https://www.appthetics.com/blog/hinge-dating-app-ui-design-patterns)
* [Bumble Onboarding UX Teardown (UX Collective)](https://uxdesign.cc/3-lessons-from-bumbles-onboarding-f45f817f751f)
* [Tinder vs. Bumble UI/UX Comparison (Medium)](https://medium.com/design-bootcamp/decoding-ui-ux-quick-comparison-of-tinder-and-bumble-9a3cb2b76f28)

---

## Section 04: Synthesis — Combining All Three Paradigms
*Actionable blueprint for blending civic trust, spatial transit clarity, and swipe-based discovery.*

### Recommended Hybrid UI Architecture for Team Discussion
* **Visual Foundation (Montréal Civic Layer):** Adopt Deep Slate (`#1E242B`) typography, clean `#F4F6F8` / `#FFFFFF` card surfaces, and Civic Red (`#E4002B`) primary actions to establish immediate local authenticity and WCAG contrast.
* **Onboarding Flow (Bumble/Tinder Layer):** Use a 5-to-7 screen progressive card deck with a segmented top progress bar and swipeable preference cards so users effortlessly set their vibe, budget, and mobility preferences in under 30 seconds.
* **Core Exploration & Routing (Chrono/Maps Layer):** Transition into a full-bleed desaturated map with a 3-state draggable bottom sheet, STM/BIXI/REM multi-modal filter pills, and Waze-style floating ETA comparison cards.