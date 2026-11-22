---
version: 1
slug: "frontend-src"
primary_target: "frontend/src"
related_targets: []
---

# Web app shell (all routes under frontend/src)

Scope: the whole web app's visual world, delivered through theme.ts, global CSS, the app shell, badges, the incident table, the incident detail and the auth screens. Mode: Operate.
Audience and job: employees report, technicians work a priority queue, admins watch SLA and service health. Course evaluators watch the same screens in a recorded demo.
Constraints: a deadline-day, low-risk restyle. No behaviour, routing, API or copy changes. Mantine stays. WCAG AA. Readable on a screen recording.

## Direction contract

THESIS: Every incident is a numbered service docket, and every lifecycle step leaves a dated rubber stamp on it. This refuses the stock SaaS admin look (default indigo, rounded cards, pill badges, shadowed panels).

OWN-WORLD: Carbon-copy job card. Carbon-blue #262a7a nav rail and ink. Role is marked by one muted accent each: deep teal #0d6463 for employees, plum #6a2c5b for technicians, bronze #85561a for administrators (dark mode #5fc2bf / #d995c6 / #d6a65e). The accent fills the header role tag and a 3px top rule, and marks the user's role on the profile; the page ground carries only a faint hint of it (#eef2f1 / #f2eff1 / #f3f0ea). This replaced the original pink and canary grounds at the user's request ("these seem fun. Select more professionally appropriate colors for the roles"). Stamp red #c8102e is reserved for P1 and breaches. Rules and field boxes are 1px carbon. Radius 0, no shadows, no gradients. Archivo for the UI, Archivo Narrow tracked caps for field labels, Courier Prime for docket numbers and times.

STORY: Users see at once which role they are signed in as, which dockets are urgent (P1 stamps are biggest and red, with a double border), and where each incident is in its lifecycle. They act through ordinary Mantine buttons, tables and forms.

FIRST VIEWPORT: A 232px carbon rail on the left with the I-ITSM mark and the nav; the active item is a tab in the ground colour. The top bar carries a 3px accent rule and a filled accent tag reading "<Role> · name", with the Live connection chip: green border, tint and tracked caps with a dot that emits a 2s ring (no motion under reduced motion); amber dashed and blinking while connecting; grey struck through when offline. Requested by the user: "the live also doesnt look very obvious it just looks like simple text, make it like its blinking or something and change colors to make it more obvious". The page ground is a near-neutral tint. Tables are ruled in carbon on the ground, and the selected or focused row lifts to white. On incident detail, the docket header shows the number in large Courier, with ruled field boxes and the stamp strip NEW→CLOSED: passed steps are carbon stamps with times, the current step is a red stamp, and upcoming steps are dashed outlines. Primary actions are flat carbon buttons.

FORM: The assigned direction, position 4 of 7 on the grounded list. seed: a459706c328afff8. Raises: one-device-pixel ruling; inks are carbon, stamp red and the one role accent, plus green scoped to the Live connection chip only; nothing else decorates; a P1–P4 magnitude ramp in stamp size and border weight; SLA state in line form (solid = on track, dashed = paused, struck = breached); flat colour; focus lifts to white.
Signature interaction: the lifecycle stamp strip. Each step change lands as a stamp: a quick 120ms press (scale 1.06→1) with no motion under reduced-motion settings.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

PROFILE NAME: on the profile, the person's name is drawn as an ASCII-art banner (figlet "Big" font, Courier Prime, carbon, scaled to the card width, real name kept as visually hidden text; non-ASCII names fall back to plain Archivo 800). Requested by the user: "use ascii text for the name of the employee/person".
