# UI and UX Specification

Authoritative for: all visual decisions, UI text, screens, interactions, component states, and accessibility. It implements requirements in [PRODUCT_SPEC.md](PRODUCT_SPEC.md) and map rules in [MAPS_AND_ANALYTICS.md](MAPS_AND_ANALYTICS.md).

## 1. Design position

Atlas is an instrument, not a dashboard. It is map-first, restrained, and typographically led: a clear answer, a route, then the evidence behind it. Screens use one continuous surface with section spacing and hairline dividers, not floating cards. Corners are 0 dp except for compact controls (8 dp) and circular map/selection controls. There are no gradients, hero illustrations, decorative icons, stock photos, gauge charts, or colour-coded mode palettes.

### 1.1 Tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `surface` | `#F7F7F5` | `#111111` | app background |
| `surfaceRaised` | `#FFFFFF` | `#191919` | sheet, input, selected row |
| `ink` | `#151515` | `#F2F2F0` | primary text/routes |
| `muted` | `#686866` | `#AAA9A5` | secondary text/lines |
| `rule` | `#D1D1CD` | `#383836` | dividers |
| `quiet` | `#E8E8E4` | `#272725` | selected fill/disabled |
| `danger` | `#A22D2D` | `#FF9B91` | destructive only |

IBM Plex Sans is the UI face; IBM Plex Mono is for times, distances, codes, and tabular numerals. Type: display 32/36 medium, heading 22/28 medium, title 17/22 medium, body 15/21 regular, label 12/16 medium, numeric 14/18 mono. Base grid is 4 dp: page padding 20 dp (24 dp ≥600 dp), section gap 32 dp, group gap 16 dp, row minimum 52 dp, touch target 48 dp. Icons are 24 dp outlined, 1.75 dp stroke, labelled when not universally obvious.

### 1.2 Shared structure and states

Phone: edge-to-edge with 20 dp safe side inset; primary navigation is a bottom bar with Today, History, Insights, Places, Settings. The active destination uses ink text plus a 2 dp top rule, never a pill. Large screens: permanent 240 dp left rail, content max width 960 dp, map/detail side-by-side where useful. Bottom sheets are only for map selection, quick edit, or destructive confirmation; they have square top corners and a drag handle.

Every data view has loading skeleton lines (not fake metrics), empty state that names the next useful action, error state with reason + Retry, and offline state that preserves local data. Health status is a text chip: `Recording`, `Resting`, `Degraded`, `Interrupted`, `Blocked`, `Paused`, or `Off`; it always opens Diagnostics. No state relies on red alone.

## 2. Components

| Component | Construction and interaction |
|---|---|
| `AtlasTopBar` | back/close at left, contextual title, up to two labelled actions; bottom hairline; respects status bar inset |
| `Metric` | mono value above 12 sp label; one primary metric may use display type; long values wrap rather than shrink |
| `SectionHeader` | heading with optional quiet text button aligned baseline; no container |
| `TimelineRow` | time column 56 dp mono; 1 dp vertical rule with point; title, metadata, chevron; tap opens detail |
| `ModeMark` | line pattern swatch + label; never colour alone; size 32×16 dp |
| `AtlasButton` | filled ink primary, outline secondary, text tertiary; 48 dp high; destructive uses text/danger outline, never filled red by default |
| `FilterBar` | horizontally scrollable text controls with underline selected state; includes clear-all and result count |
| `AtlasMap` | full bleed within its region, with scale/north, attribution, and 48 dp labelled controls; gestures pan/zoom/rotate, double tap zoom; camera never steals focus |
| `Chart` | direct labels, rule grid, focus via touch/keyboard, table alternative action; chart descriptions expose summary and extrema |
| `Confirmation` | names irreversible result, count/range, export/backup alternative if relevant, requires deliberate final action |

## 3. Screen specification

| ID | Screen | Hierarchy, actions, and special states |
|---|---|---|
| S01 | Welcome | Wordmark as plain “Atlas”, one sentence: “A private record of where you go.” Two facts: local-only and visible recording notification. `Continue`; links to privacy and licence text. |
| S02 | Permission setup | One permission per page: precise foreground location, background location (Settings handoff), activity recognition, notifications. Explain purpose, system status, `Open Settings`, `Not now`. Denial lands in Blocked, never loops system prompt. |
| S03 | Keep Atlas running | Checklist: battery optimisation, Allow background activity/auto-launch where present, lock in recents. Device-neutral copy, direct App info shortcut, “I have done this” verifies what Android exposes; result says “Needs device test” for OEM-only steps. |
| S04 | Today | top date and health; 42% viewport map; below: large distance, moving time, journey count; chronological timeline of visits/journeys; `+` is absent. Pull refresh only refreshes view. Empty: “No movement recorded yet” + health reason. |
| S05 | Day map | reached from Today/history header; full map with date strip, route legend, recorded-points toggle, layer button, zoom-to-day. Tap route selects segment and opens 40% sheet; tap marker selects stay/transition. |
| S06 | History | month calendar with coverage ticks, previous/next, filter bar (mode/place/has gap); selected day opens S04 in historical mode; below calendar is result list. No heatmap here. |
| S07 | Journey detail | top time range + health/gap indicator; map 40%; metrics row distance/time/average speed; segment timeline, stops, gaps; actions Playback, Edit, Export. Deleted journey shows restore/purge context. |
| S08 | Playback | map, time mono display, scrubber with mode/gap ticks, 48 dp play/pause and speed 1×/2×/4×. Playback stops at gaps with explanatory label; resume jumps only when user continues. |
| S09 | Edit journey | chronology-first list, not map editing. Actions Set mode, move transition, split, merge, mark not a journey, delete; preview has “Changes affect statistics” and Cancel/Apply. |
| S10 | Mode picker | full-height list with pattern samples: Walk, Run, Bicycle, Motorbike / scooter, Car, Auto-rickshaw, Bus, Train, Metro, Other motorised, Flight, Unknown. Search absent; selected interval stated in title. |
| S11 | Insights overview | period switcher day/week/month/year/lifetime; headline distance + coverage; six-period trend; mode distribution; links to Records, Patterns, Heatmap. Low coverage carries “Estimated”. |
| S12 | Metric detail | definition link, chart, accessible table, selected point annotation, compare period switch. Every metric exposes its formula and exclusions. |
| S13 | Records | concise list grouped by journey/day/mode; title, value, date and coverage; ties explicit. Empty explains it needs confirmed history. |
| S14 | Heatmap | full map, filters, time range, five-step density legend, screenshot-safe toggle. Privacy-zone interior is masked in safe mode. |
| S15 | Places | confirmed places first, candidates next, ignored hidden behind text action; each row shows visits and last visit. Add place opens S16. |
| S16 | Place detail/edit | map centred on place; name, radius stepper/text entry, privacy-zone switch, visits; actions merge/split/ignore/delete appropriately. Radius circle is solid boundary, not filled blob. |
| S17 | Settings | grouped plain rows: Tracking, Maps, Data, Privacy, Appearance, About. No toggle wall. |
| S18 | Tracking settings | enabled toggle, pause one hour/until resumed, sampling explanation, notification status. Turning off requires “Stops recording; past data remains.” |
| S19 | Map settings | light/dark/system, ambient cache size, offline regions, tile attribution; host URL is developer-only, not general settings. |
| S20 | Data management | storage breakdown, export, backup, import, retention, delete range/all. Each opens a focused sheet/flow with exact scope. |
| S21 | Privacy and lock | app lock switch, privacy zones link, diagnostic sharing default, network policy readout. Lock change invokes device authentication. |
| S22 | Appearance and accessibility | system/light/dark, text-size preview, reduce-motion switch, high-contrast verification note; follows system font scale always. |
| S23 | Diagnostics | current health, last fix/time/accuracy, service/session, permissions/OEM checklist, today’s 24-hour coverage strip, health-event list, Export diagnostics. Coordinates never shown in share preview. |
| S24 | Gap detail | selected interval, cause, evidence timeline, affected journey, recommended next step. “Unknown” says exactly that; no speculation. |
| S25 | About and licences | version/build/schema/model versions, open-source notices, data format version, privacy statement. |

## 4. Key flows and microcopy

Health messages: `Recording — last fix 12 s ago`; `Resting — waiting for movement`; `Degraded — fixes are less frequent`; `Interrupted — recording stopped 18 min ago`; `Blocked — precise location is required`; `Paused until 14:30`; `Off — tracking is turned off`. Never say “all good” when no evidence exists.

Destructive language is exact: “Delete journey removes it from Atlas history. Raw locations remain until you purge them.” “Purge 12–14 October removes 2,430 recorded locations, routes, corrections and exports for that range. This cannot be undone.” Export asks format, date range, privacy-zone treatment, and destination through SAF; completion names item count and file result.

Back preserves unsaved edit values and asks Discard only after a meaningful change. Undo is offered for soft delete, correction and place changes for 10 seconds; hard purge has no undo. Map long-press has no action in v1 to prevent accidental location creation.

## 5. Accessibility and motion

All text/background combinations meet WCAG 2.2 AA; essential text never sits over dynamic map imagery without a solid surface. TalkBack order follows visual hierarchy; routes announce mode, time, distance, confidence, then action. A map has a textual route/segment alternative. Charts provide a table. Actions have verb-first labels; icon-only controls have content descriptions. Focus targets are 48×48 dp minimum, including map markers.

At 200% font scale, metrics wrap, timeline rows grow, bottom navigation becomes labelled two-line or rail, and no essential value truncates. Reduce motion disables camera animation, playback auto-advance and chart tweening; state changes crossfade at most 100 ms. Respect RTL layout even though launch copy is en-IN.
