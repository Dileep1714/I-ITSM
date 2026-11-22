---
version: 1
slug: "frontend-src-components-authlayout-tsx"
primary_target: "frontend/src/components/AuthLayout.tsx"
related_targets: ["frontend/src/pages/LoginPage.tsx","frontend/src/pages/RegisterPage.tsx"]
---

# Sign-in and registration (AuthLayout)

Scope: the signed-out surfaces (LoginPage, RegisterPage) via AuthLayout. Mode: Persuade (first impression) with Operate restraint: the working action is the sign-in form itself.
Audience: employees and IT staff of the organisation; evaluators see it first in the demo. Demo accounts are a development aid only (shown only in development builds), never part of the design.

## Direction contract

THESIS: The sign-in is the front of the IT service desk: the form sits beside the help people need when they arrive. It refuses the split marketing panel and the lone centred card.

OWN-WORLD: Service Docket (DESIGN.md): one large ruled sheet on the faint ground, carbon ink, 1px rules, Courier Prime reference line, Archivo Narrow tracked labels, Archivo 800 display. Stamp red unused (no alarm here).

STORY: A visitor understands this is their organisation's IT service desk, signs in (or registers as a new employee), and knows what to do if they cannot.

FIRST VIEWPORT: A single ruled sheet with a header band (product mark left, typed reference "IT SERVICE DESK · FORM IT-01 · SIGN-IN" right), three columns: left, display heading and what the desk does as a ruled list with typed tags; centre, the form; right, "Need help?" (forgotten password, new employee, urgent problems). Footer band with typed lines. Phones: one column, form first.

FORM: Chosen structure "Help desk front", dealt lead after a re-roll (position 3 of 7). seed: 6479ba175dab65ff.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
