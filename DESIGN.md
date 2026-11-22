---
name: I-ITSM
description: IT service management where every incident is a numbered service docket stamped through its lifecycle.
colors:
  carbon-ink: "#262a7a"
  carbon-deep: "#171a4c"
  carbon-press: "#1f2263"
  carbon-mid: "#5257b8"
  carbon-dimmed: "#4d4f74"
  carbon-placeholder: "#5f6185"
  carbon-rail-text: "#e6e7f6"
  carbon-rail-muted: "#a9acdc"
  carbon-rule: "rgba(38, 42, 122, 0.5)"
  carbon-rule-faint: "rgba(38, 42, 122, 0.22)"
  stamp-red: "#c8102e"
  stamp-red-deep: "#a60d26"
  sheet-white: "#fefefc"
  body-ink: "#1d1d22"
  accent-employee: "#0d6463"
  accent-technician: "#6a2c5b"
  accent-administrator: "#85561a"
  ground-employee: "#eef2f1"
  ground-technician: "#f2eff1"
  ground-administrator: "#f3f0ea"
  on-accent: "#ffffff"
  live-green: "#157a3e"
  live-green-tint: "#e3f3e8"
  connecting-amber: "#8a5a00"
  connecting-amber-tint: "#fbf1dc"
  dark-ground-employee: "#16191b"
  dark-ground-technician: "#1a171a"
  dark-ground-administrator: "#1b1916"
  dark-sheet: "#1d1e26"
  dark-rail: "#15173f"
  dark-body-ink: "#ecebf4"
  dark-carbon-ink: "#b3b6f0"
  dark-dimmed: "#a8a9c4"
  dark-stamp-red: "#ff6b7d"
  dark-accent-employee: "#5fc2bf"
  dark-accent-technician: "#d995c6"
  dark-accent-administrator: "#d6a65e"
  dark-on-accent: "#121316"
  dark-carbon-rule: "rgba(179, 182, 240, 0.42)"
  dark-carbon-rule-faint: "rgba(179, 182, 240, 0.18)"
  dark-live-green: "#4fd38a"
  dark-connecting-amber: "#f0b955"
typography:
  display:
    fontFamily: "Archivo, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "clamp(1.75rem, 3vw, 2.5rem)"
    fontWeight: 800
    lineHeight: 1.05
    letterSpacing: "-0.025em"
  headline:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "2rem"
    fontWeight: 800
    lineHeight: 1.15
  headline-sm:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 800
    lineHeight: 1.25
  title:
    fontFamily: "Archivo, system-ui, sans-serif"
    fontSize: "1.1875rem"
    fontWeight: 800
    lineHeight: 1.3
    letterSpacing: "-0.01em"
  body:
    fontFamily: "Archivo, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.5
  body-sm:
    fontFamily: "Archivo, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: "Archivo Narrow, Archivo, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "0.1em"
  label-sm:
    fontFamily: "Archivo Narrow, Archivo, system-ui, sans-serif"
    fontSize: "0.6875rem"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "0.12em"
  figure:
    fontFamily: "Courier Prime, Courier New, ui-monospace, monospace"
    fontSize: "0.8125rem"
    fontWeight: 700
    lineHeight: 1
  figure-docket:
    fontFamily: "Courier Prime, Courier New, ui-monospace, monospace"
    fontSize: "1.375rem"
    fontWeight: 700
    lineHeight: 1.2
  figure-count:
    fontFamily: "Courier Prime, Courier New, ui-monospace, monospace"
    fontSize: "2.125rem"
    fontWeight: 700
    lineHeight: 1.1
  figure-banner:
    fontFamily: "Courier Prime, Courier New, monospace"
    fontSize: "min(18px, calc(100cqw / (var(--cols) * 0.6)))"
    fontWeight: 400
    lineHeight: 1.12
    letterSpacing: "0"
rounded:
  none: "0"
  dot: "50%"
spacing:
  xs: "10px"
  sm: "12px"
  md: "16px"
  lg: "20px"
  xl: "32px"
  rail-inset: "18px"
components:
  button-primary:
    backgroundColor: "{colors.carbon-ink}"
    textColor: "{colors.on-accent}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.none}"
    height: "36px"
    padding: "0 18px"
  button-primary-hover:
    backgroundColor: "{colors.carbon-press}"
  button-outline:
    backgroundColor: "transparent"
    textColor: "{colors.carbon-ink}"
    rounded: "{rounded.none}"
    height: "36px"
    padding: "0 18px"
  sheet:
    backgroundColor: "{colors.sheet-white}"
    rounded: "{rounded.none}"
    padding: "20px"
  input-field:
    backgroundColor: "{colors.sheet-white}"
    textColor: "{colors.body-ink}"
    rounded: "{rounded.none}"
    height: "36px"
  field-label:
    textColor: "{colors.carbon-ink}"
    typography: "{typography.label}"
  nav-rail:
    backgroundColor: "{colors.carbon-ink}"
    textColor: "{colors.carbon-rail-text}"
    width: "232px"
  nav-item:
    textColor: "{colors.carbon-rail-text}"
    typography: "{typography.body-sm}"
    padding: "9px 18px"
    rounded: "{rounded.none}"
  nav-item-active:
    backgroundColor: "{colors.ground-employee}"
    textColor: "{colors.carbon-ink}"
  top-bar:
    backgroundColor: "{colors.ground-employee}"
    height: "56px"
  role-tag:
    backgroundColor: "{colors.accent-employee}"
    textColor: "{colors.on-accent}"
    typography: "{typography.label}"
    padding: "5px 10px"
    rounded: "{rounded.none}"
  live-chip:
    backgroundColor: "{colors.live-green-tint}"
    textColor: "{colors.live-green}"
    typography: "{typography.label}"
    padding: "4px 10px 4px 9px"
    rounded: "{rounded.none}"
  priority-stamp:
    textColor: "{colors.body-ink}"
    typography: "{typography.figure}"
    padding: "3px 6px"
    rounded: "{rounded.none}"
  priority-stamp-p1:
    textColor: "{colors.stamp-red}"
    padding: "2px 6px"
  status-mark:
    textColor: "{colors.carbon-ink}"
    typography: "{typography.label-sm}"
    padding: "2px 7px"
    rounded: "{rounded.none}"
  status-mark-active:
    backgroundColor: "{colors.carbon-ink}"
    textColor: "{colors.sheet-white}"
  lifecycle-stamp:
    textColor: "{colors.carbon-ink}"
    typography: "{typography.label-sm}"
    padding: "6px 4px"
    height: "56px"
    rounded: "{rounded.none}"
  lifecycle-stamp-now:
    textColor: "{colors.stamp-red}"
---

# Design System: I-ITSM

## Overview

**Creative North Star: "The Service Docket"**

Every incident is a numbered docket from a carbon job book, and every lifecycle step leaves a dated rubber stamp on it. The interface is printed in one ink, carbon blue, on a quiet paper ground. White sheets are ruled in one-pixel carbon, corners are square, and nothing casts a shadow. Docket numbers, times and counts are typed in Courier Prime. Field labels are tracked narrow capitals, as on a pre-printed form. The system turns away from the stock SaaS admin look: default indigo, rounded cards, pill badges and shadowed panels.

The palette does the explaining. Carbon carries almost everything. Stamp red appears only when something is genuinely wrong (a P1 docket, a breached or at-risk SLA, the current or voided lifecycle stamp). The signed-in role shows as one muted accent: teal for employees, plum for technicians, bronze for administrators. That accent fills the header role tag, the 3px top rule and the avatar box, and the page ground carries only a faint hint of it. Green belongs to the real-time connection chip and nowhere else.

The density is operational. Tables are ruled straight onto the ground, and the row under the pointer or focus lifts to a white sheet. Urgency is shown in form as well as colour: stamp size, border weight and line style rank priority and SLA state, so meaning never depends on hue alone.

**Key Characteristics:**
- Carbon-blue ink and rail (#262a7a) on a near-neutral role-tinted ground.
- Square corners everywhere (radius 0). Flat colour, no shadows, no gradients.
- One-pixel carbon rules for sheets, tables and field boxes.
- Three type voices: Archivo for the interface, Archivo Narrow tracked caps for labels, Courier Prime for typed figures.
- A mark grammar of solid, double, dashed, dotted and struck lines for priority, status and SLA.
- A lifecycle stamp strip with slight alternating tilts, where the current step is pressed in red.

## Colors

One ink (carbon), one alarm (stamp red), one role accent, and one connection signal, all on paper.

### Primary
- **Carbon Ink** (carbon-ink): the rail, primary buttons, field labels, status marks, table headers, focus outlines, text selection, caret and docket numbers. Mantine's `carbon` tuple runs from #eeeefa to #171a4c. Light mode uses shade 7 (carbon-ink) as primary and dark mode uses shade 5 (carbon-mid).
- **Carbon Deep** (carbon-deep): the modal overlay (45% opacity) and the tooltip fill. **Carbon Press** (carbon-press, tuple shade 8) is the filled-button hover.
- **Carbon Dimmed** (carbon-dimmed): secondary text. It is tinted from carbon, never a neutral grey, so it holds 4.5:1 on every ground.

### Secondary
- **Stamp Red** (stamp-red): P1 priority stamps, AT_RISK and BREACHED SLA marks, the current, held and voided lifecycle stamps, over-capacity workload slots and the SLA-breached stat. Mantine's `stamp` tuple runs from #fdeaec to #640717. Dark mode uses dark-stamp-red.

### Tertiary (role accents)
- **Docket Teal** (accent-employee), **Docket Plum** (accent-technician), **Docket Bronze** (accent-administrator). Exactly one accent is live per session, keyed off `html[data-role]` as `--app-accent`. It fills the header role tag, the 3px top rule of the header, the avatar initials box and the user's own role card on the profile (2px border, 14px swatch, typed level). Text on the accent is on-accent (white in light mode, #121316 in dark mode).
- **Live Green** (live-green on live-green-tint): the Live state of the real-time connection chip only. **Connecting Amber** (connecting-amber on connecting-amber-tint) is that chip's connecting state, with a dashed border. Offline uses dimmed text, is struck through and has no tint.

### Neutral
- **Role Grounds** (ground-employee / ground-technician / ground-administrator): the page canvas, the top bar, the active rail tab and passed rungs on the escalation ladder. Each is a near-neutral paper with a faint hint of its role's accent.
- **Sheet White** (sheet-white): cards, record cards, inputs and the hovered or focused table row.
- **Body Ink** (body-ink): running text and P2–P4 priority stamps.
- **Carbon Rule** (carbon-rule) and **Carbon Rule Faint** (carbon-rule-faint): sheet borders, input borders and table header rules use the full rule. Row separators and internal field dividers use the faint one.
- **Rail Text** (carbon-rail-text) and **Rail Muted** (carbon-rail-muted): nav labels, section headings, icons and the footer on the carbon rail.
- Dark mode swaps every role through the `dark-*` tokens. The ground goes near-black with a faint role hint, sheets become dark-sheet, the rail becomes dark-rail, ink becomes dark-carbon-ink, and the accents lighten to stay at AA.

### Named Rules
**The Four Inks Rule.** Carbon, stamp red, the one active role accent and the Live chip's green are the only hues. The theme remaps Mantine's `green`, `teal` and `orange` to carbon so that defaults cannot add a fifth.

**The Real Alarm Rule.** Stamp red marks only P1, SLA at risk or breached, the current, held or voided lifecycle stamp, and over capacity. A button, link or heading in red is a defect.

**The One Accent Rule.** A screen shows the signed-in role's accent and no other role's accent. The accent identifies who you are. It never encodes priority, status or action.

**The Green Is Connection Rule.** Green means the real-time connection is live. It is never used for success, "met" or "available".

## Typography

**Display Font:** Archivo (with system-ui, -apple-system, Segoe UI, sans-serif)
**Label Font:** Archivo Narrow (with Archivo, sans-serif)
**Figure Font:** Courier Prime (with Courier New, ui-monospace, monospace)

**Character:** A sturdy grotesque at heavy weights for names and headings, a condensed tracked capital for the pre-printed form labels, and a typewriter face for anything a numbering machine or clerk would type. Fonts load from Google Fonts: Archivo 400–800, Archivo Narrow 500–700, Courier Prime 400 and 700.

### Hierarchy
- **Display** (800, clamp(1.75rem, 3vw, 2.5rem), 1.05, -0.025em): the fallback for a profile name the banner font cannot draw. The sign-in panel's statement uses the same voice at 36px with -0.02em.
- **Figure Banner** (Courier Prime 400, at most 18px, 1.12): the profile name drawn as a figlet "Big" ASCII-art banner in carbon. The font size is the container width divided by the widest line × 0.6, so the banner always fits its card.
- **Headline** (800, 2rem / 1.5rem, 1.15 / 1.25): page titles (h1) and docket titles (h2).
- **Title** (800, 1.1875rem, 1.3): section titles and modal titles. h4 is 1.0625rem.
- **Body** (400, 0.9375rem, 1.5): running text. Body-sm (0.875rem) is used for nav items, buttons and notes. Notes cap at 62ch.
- **Label** (Archivo Narrow 600, 0.75rem, 0.08–0.14em, uppercase): input labels, field labels over values, the role tag and the Live chip (700). Label-sm (0.6875rem) is used for table headers, status marks, badges, rail section headings (0.14em) and stamp-strip step names (11.5px, 700).
- **Figure** (Courier Prime 700): priority stamps (13–15px), stamp times (13px), the docket number (22px), dashboard and profile counts (34px), escalation levels (26px), the profile photo initials (44px) and email addresses (Courier 400, 15px).

### Named Rules
**The Typed Figure Rule.** Docket numbers, times, priorities, counts and levels are set in Courier Prime. Table cells use tabular numerals.

**The Form Label Rule.** Tracked narrow capitals label a field, column or state that sits directly next to its value. They are never used as a decorative line above a headline.

## Layout

The shell is a fixed carbon rail beside a working page. The rail is 232px wide and collapses behind a burger below the `sm` breakpoint (48em). The top bar is 56px tall, sits on the ground, and carries a 3px accent top rule and a 1px carbon bottom rule. Content is centred with a 1400px maximum width, padded 12px on phones and 20px from `sm` up. Rail items are inset 18px. The active item is a tab filled with the ground colour, so it reads as cut from the page.

Spacing uses Mantine's default steps (10 / 12 / 16 / 20 / 32px). Sheets pad at 20px. Record-card fields are 12px × 20px cells in an auto-fit grid of at least 150px, separated by faint rules. Tables use vertical spacing `sm` and horizontal spacing `md`.

Responsive behaviour: below `sm`, the role tag hides and the accent avatar becomes the role signal. The header shows the product name, and the Live chip hides below `xs` (36em). Incident tables become a stack of tappable rows with 12px × 8px padding, separated by 1px rules. The record card stacks below 40em, and the escalation ladder goes from four columns to two below 48em. The auth screen is a 5fr / 7fr split (carbon panel at least 380px wide, form at most 400px) that drops to the form alone below `md` (62em). Fields that share a row keep their hints to one ellipsised line so the boxes stay aligned.

## Elevation & Depth

The system is flat. Every Mantine shadow token is `none`. Depth comes from paper, not light: white sheets sit on a tinted ground, a row lifts to white on hover or focus-within, and the active rail item takes the ground colour. Modals are separated by a carbon-deep overlay at 45% opacity, not by a shadow.

### Named Rules
**The Flat Paper Rule.** Nothing casts a shadow and nothing uses a gradient. To separate a surface, change the paper (ground to sheet white) or rule it in 1px carbon.

## Shapes

All corners are square: radius 0 across every Mantine size, including avatars, inputs, badges, modals, tooltips and timeline bullets. The only curve is a circular dot, used by the Live chip and nowhere else. Borders form a grammar of lines:

- **Solid 1px**: ordinary rules, sheets, fields and P3 priority.
- **Heavier solid** (1.5–2.5px): P2 priority (2px), passed lifecycle stamps (1.5px) and the current stamp (2.5px red).
- **Double 3px**: maximum weight. Used for P1 (red), the voided stamp, over-capacity slots and the profile photo frame (carbon).
- **Dashed**: provisional or paused. Used for P4, ON_HOLD, a paused SLA, upcoming stamps, empty workload slots, the held stamp and the connecting chip.
- **Dotted**: not yet started or unset. Used for an unset priority and an SLA that has not started.
- **Struck through**: ended badly. Used for CANCELLED, a BREACHED SLA (2px strike plus a 4px double rule) and the offline chip.

The brand mark is a numbering-machine stamp: a 1.5px square around a 0.75px inner square, holding "IT" in Courier Prime 700.

## Components

### Buttons
Flat, square and carbon. They are ordinary Mantine buttons, not ornaments.
- **Shape:** square (radius 0). Default size `sm` (36px), weight 600.
- **Primary:** filled carbon ink with white text. Dark mode uses carbon shade 5.
- **Hover / Focus:** the fill darkens one shade. Focus shows a 2px carbon outline at a 2px offset.
- **Outline / Light / Subtle / Default:** carbon-tinted Mantine variants for secondary actions.
- **Toggle groups:** availability is four real buttons at size `md` with a minimum width of 140px. The current one is filled and shows a check icon with `aria-pressed`. The others are outlined and show their own icon.

### Chips (marks)
- **Priority stamp:** Courier Prime 700 in a ruled box on a magnitude ramp. P1 is 15px red with a 3px double rule. P2 is 14px with a 2px rule. P3 is 13px with a 1px rule. P4 is dimmed and dashed. Unset is dotted, narrow caps at 11px. A tooltip gives the word (Critical, High, Medium, Low).
- **Status mark:** tracked carbon capitals in a 1px box. IN_PROGRESS and ACKNOWLEDGED are filled carbon with sheet-coloured text. ON_HOLD is dashed. CLOSED and VERIFIED are dimmed. CANCELLED is dimmed and struck.
- **SLA line:** an 18px rule in front of the text. It is a solid carbon rule on track, dotted and dimmed when not started, dashed when paused, red at risk (600 weight), and red, struck through and led by a 4px double rule when breached (700). A met SLA is dimmed.
- **Badges** default to outline, square, label-sm caps.

### Cards / Containers
- **Corner Style:** square (0).
- **Background:** sheet white on the ground. A sheet holding a table goes transparent, so the table is ruled on the ground.
- **Shadow Strategy:** none (see Elevation & Depth).
- **Border:** 1px carbon rule. Internal dividers use the faint rule.
- **Internal Padding:** 20px (`lg`).

### Inputs / Fields
- **Style:** square field boxes on sheet white with a 1px carbon rule. Labels are Archivo Narrow 600, 0.75rem, 0.08em, uppercase, in carbon ink.
- **Focus:** a 2px carbon outline at a 2px offset, with a carbon caret.
- **Read-only fields:** a label above a 500-weight value, with a faint top rule (incident detail) or a faint cell rule (profile record card).

### Navigation
- **Rail:** carbon background. Items are 9px × 18px, body-sm, weight 500, with a muted icon. Hover adds an 8% white wash with white text over 120ms. The active item is a ground-coloured tab in carbon ink, weight 700. Section headings are 11px narrow caps at 0.14em in rail-muted. The footer is separated by a 14% white rule.
- **Top bar:** ground background, 3px accent top rule and 1px carbon bottom rule. It carries the role tag ("Role · name", filled accent, 12px narrow caps at 0.14em), the Live chip and a square accent avatar (30px).
- **Mobile:** a burger opens the rail, which has its own white close burger.

### Live Chip
A connection state, not decoration. Narrow caps, 700 weight, 12px, 0.14em tracking, in a 1px box with an 8px dot. **Live** is green on a green tint. Its dot sends out a 2px ring that scales 1 to 3.2 and fades over 2s on cubic-bezier(0.16, 1, 0.3, 1), repeating. **Connecting** is amber with a dashed box, and its dot blinks over 1.2s. **Offline** is dimmed and struck through, with a hollow dot. Under reduced motion the dots stay still.

### Lifecycle Stamp Strip (signature)
One rubber-stamp box per step from NEW to CLOSED, in a grid of cells at least 112px wide with 10px gaps. Each box is at least 56px tall with the step name in label-sm (700) over its time in Courier Prime 13px 700. Passed steps are 1.5px carbon impressions that alternate tilts of -1.5deg and 1.2deg. Upcoming steps are 1px dashed rule-coloured outlines with no tilt. The current step is a 2.5px red stamp at -2.5deg, held is a 2.5px dashed red stamp, and voided is a 3px double red stamp at -4deg. A new stamp lands with a 140ms press (scale 1.08 to 1, opacity 0.6 to 1) on cubic-bezier(0.16, 1, 0.3, 1). Under reduced motion it does not animate.

### ASCII Name Banner (profile)
The person's name is drawn as a figlet "Big" ASCII-art banner in a `pre` set in Courier Prime 400, carbon ink, line-height 1.12, with no tracking. It sits in an inline-size container. Its font size is min(18px, 100cqw / (cols × 0.6)), where cols is the widest line, so it scales to the card and never overflows. The banner is `aria-hidden`, and the real name stays in the element as visually hidden text. A name with characters the font cannot draw falls back to plain Display (Archivo 800). The banner is used only for the profile name. It is not a heading style for other surfaces.

### Record Card (profile)
A sheet split into a 132 × 152px initials frame (3px double carbon on the ground, Courier 44px) and a body with the ASCII name banner, a Courier email and a grid of ruled field cells. The profile shows only the signed-in user's own role, as a card with a 2px accent border, an accent swatch and a typed level. Counts sit in a three-cell strip set in Courier 34px. The technician's escalation ladder is a row of joined ruled rungs: passed rungs take the ground colour and the current rung is filled carbon. Workload slots are 44 × 56px boxes: dashed when empty, carbon when used, double red when over capacity. Skill pips are 14px squares filled carbon.

## Do's and Don'ts

### Do:
- **Do** keep every corner at radius 0 and every shadow at `none`.
- **Do** rule sheets, tables and fields in 1px carbon-rule, and use carbon-rule-faint for internal dividers.
- **Do** encode priority and SLA in line form (size, weight, double, dashed, dotted, struck) as well as colour.
- **Do** set docket numbers, times, priorities and counts in Courier Prime 700 with tabular numerals.
- **Do** derive the role from `html[data-role]` and use only `--app-accent` for the role tag, the 3px top rule, the avatar and the user's own role card.
- **Do** lift the hovered or focused table row to sheet white rather than tinting it.
- **Do** tint secondary text from carbon (#4d4f74 light, #a8a9c4 dark) so it holds AA on every ground.
- **Do** gate every animation behind `prefers-reduced-motion: no-preference`.
- **Do** keep the real text available to assistive technology whenever a figure is drawn decoratively (the ASCII name banner is `aria-hidden` and has a visually hidden name).

### Don't:
- **Don't** use stamp red for actions, links, headings or decoration. It is for P1, at-risk or breached SLA, and the current, held or voided stamp only.
- **Don't** use green anywhere except the Live connection chip. It never means success, met or available.
- **Don't** show another role's accent, or use a role accent to encode state.
- **Don't** introduce rounded cards, pill badges, shadowed panels, gradients or default Mantine indigo.
- **Don't** let Mantine colour defaults (`red`, `yellow`) render as raw hues. Map them to carbon or stamp.
- **Don't** put tracked narrow capitals above a headline as an eyebrow. Labels sit next to the value they name.
