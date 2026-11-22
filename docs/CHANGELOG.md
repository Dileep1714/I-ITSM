# I-ITSM changelog

Everything built for the project, newest first. Dates are 2026. For the design rationale see
[`DOMAIN_MODEL.md`](DOMAIN_MODEL.md) (object model and patterns), [`../PRODUCT.md`](../PRODUCT.md) (product brief) and
[`../DESIGN.md`](../DESIGN.md) (visual design system).

---

## 22 November: innovation features, redesign, mobile app, deployment

### Service dependency graph and live status page (Innovation: "Service dependency graph")
- **`ServiceHealthAnalyzer`** (`domain/catalog`, pure Java): combines each service's admin status with its open
  incidents (an open P1 means an outage; a P2 or 3+ open incidents means degraded), then **propagates impact** along
  dependencies: a service that depends, directly or transitively, on a failing one is marked *impacted*, with the
  cause named. Also computes each service's **blast radius** (how many services depend on it).
- `GET /api/catalog/health` (any signed-in user; counts only, never incident details).
- **Service map page** (`/services`, all roles): status summary ("Known issues · IT is aware of these"), a layered
  dependency map (infrastructure at the bottom), red animated edges carrying a problem up to its dependents, and a
  details panel (reasons, criticality, blast radius, open incidents) that highlights the selected service's chain.

### AI self-help before reporting
- `POST /api/ai/self-help`: when an employee writes a report, a **known issue** on the chosen service is shown at
  once ("IT already knows VPN is down"), which prevents duplicate reports; with AI on, Gemini suggests 2 to 4
  **safe** steps to try first, informed by how similar incidents were fixed. Past incidents only inform the model:
  employees never see other people's incident numbers, titles or resolutions.
- "Before you report" card on the report form.

### Predictive SLA violation detection (Innovation: "Predictive SLA violation detection")
- `domain/prediction`: **`BreachPredictor`** (Strategy) and **`HistoricalBreachPredictor`**: learns resolution
  times from past incidents, comparing like with like (category + priority, widening to category, priority, all
  when there are fewer than 5 samples), adjusts for the assignee's own speed and the queue ahead of the incident,
  and computes the breach probability **conditionally on the time already spent** ("of the comparable incidents
  still open after this long, how many ended up late"). Thin history blends in a prior from the SLA target and is
  flagged low-confidence.
- `GET /api/incidents/{id}/sla-forecast`, `GET /api/intelligence/sla-forecasts?minRisk=`.
- **Early warning**: the SLA monitor raises `SLA_BREACH_PREDICTED` once per incident when the risk is high,
  notifying the assignee and team leads *before* the deadline (migration `V6__sla_breach_prediction.sql`).
- Web: "SLA forecast" card on the incident (probability, expected finish vs deadline, reasons) and a "Likely to
  breach" list on the staff dashboard.

### AI copilot with Google Gemini (Innovation: AI classification, AI resolution recommendation, chatbot reporting)
- **`AiAssistant`** (Strategy/Adapter) with **`GeminiAssistant`** (Google Gemini API, free tier, structured JSON
  output, model fallback list for busy/over-quota models) and **`DisabledAssistant`** (Null Object, no key).
  The key lives in the git-ignored `backend/ai.local.properties` locally, or `GEMINI_API_KEY` when deployed.
- **Hybrid classification**: the TF-IDF keyword classifier decides when confident; only uncertain reports go to
  Gemini.
- **Resolution recommendation** (`POST /api/incidents/{id}/ai-suggestion`, staff): fix steps grounded in similar
  resolved incidents, citing them; invented incident numbers are removed.
- **Chat-based reporting** (`POST /api/ai/report-chat`): the assistant interviews the employee and prepares a
  draft; the employee reviews and submits it (the AI never creates incidents itself).
- Every model answer is validated (ids must exist, enums must be valid) and labelled as AI-generated in the UI.
- Tests never call the real API (`application-test.properties` disables it).

### "Service Docket" visual redesign (web)
- Replaced the generic admin look with a distinctive design system documented in `DESIGN.md`: carbon-blue ink,
  stamp red reserved for P1 and breaches, one muted accent per role (teal employee, plum technician, bronze
  administrator), Archivo / Archivo Narrow / Courier Prime type, square ruled sheets, no shadows.
- Signature **lifecycle stamp strip** (NEW to CLOSED as dated rubber stamps), priority stamps on a magnitude ramp
  (P1 double-ruled red down to P4 dashed) so priority never relies on colour alone, SLA shown in line form.
- Profile page: record card, ASCII-art name, the user's role, incident record, escalation ladder (L1 to L4),
  workload slots, availability buttons, skill pips. Pulsing **Live** chip for the real-time connection.
- Fixes: report form fields aligned; toast and notification badge restyled; WCAG AA contrast for secondary text.

### Mobile app (React Native / Expo, Android and iOS)
- Same design system translated natively: docket marks, stamp strip, typed figures, profile.
- **Navigation**: iOS uses the system tab bar (Liquid Glass on iOS 26) and **minimizes while scrolling**; Android
  gets a **Material 3 floating navigation bar** with a sliding indicator so both platforms navigate alike.
- Photo uploads: images are resized (max 1600 px) before upload; oversized uploads now get a clear 413 instead of a
  dropped connection.

### Deployment
- `backend/Dockerfile`, `frontend/Dockerfile` (nginx serving the app and proxying `/api` and `/ws`),
  `docker-compose.prod.yml`, `.env.example`: the whole system on any server with Docker.
- **Render** (free): `render.yaml` Blueprint plus `render/Dockerfile`, which bundles the React app into the Spring
  Boot jar so **one service** serves the site, API and WebSocket (free Render services cannot talk privately).
  Tested locally within the free plan's 512 MB.
- `docs/DEPLOYMENT.md`: VM (Azure/Google/Oracle) and Render guides. `mobile/eas.json` for APK builds.

### Tests
- 13 new tests (predictor model, AI copilot safety checks); the suite totals **243 passing tests**.

---

## 9 to 21 November: photo attachments, PWA, mobile app (first version)
- Photo attachments on incidents (`V5__incident_attachments.sql`): upload, list and view; image type checked from
  the file bytes; web and mobile.
- Installable **PWA**: manifest and icons, Workbox service worker, "new version available" prompt, offline banner,
  iOS Add-to-Home-Screen guide.
- First **Expo mobile app** with role-specific tabs (employee, technician, administrator), swipe actions, action
  sheets, biometric unlock, system notifications, camera photos and haptics.

## 8 November: the core system
- Project plan, requirements, Spring Boot skeleton, domain model document.
- **Domain model with unit tests**: users and roles, categories, exceptions; **incident lifecycle (State pattern)**,
  SLA tracking, service catalogue; **priority calculators and assignment strategies (Strategy pattern)**.
- PostgreSQL via Docker Compose, Flyway schema, persistence tests.
- JWT security, REST API, application services, demo data.
- Audit trail, notifications (in-app, email, SMS; Observer pattern), SLA monitoring and **escalation (Chain of
  Responsibility)**.
- React frontend with role-based screens; server-side incident search with filters.
- Intelligent features: automatic categorisation (TF-IDF), duplicate and similar-incident detection, root-cause
  correlation.
- Admin management, reports and real-time updates (STOMP over WebSocket) with their frontend pages.
- GitHub Actions CI; self-assignment ("Assign to me" / "Take it"); priority calculation hidden from employees;
  sign-up restricted to company domains; first UI redesign.

## 7 November
- Repository created.
