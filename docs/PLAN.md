# I-ITSM — Project Plan & Requirements Checklist

**Deadline:** 22 Nov 2026 (report PDF + demo video). Hard deadline.
**Architecture:** Option C, full stack: React (web, PWA later) + Spring Boot REST API + PostgreSQL.
**Source spec:** [`requirements.md`](../requirements.md)

---

## Tech stack

| Layer | Choice |
|---|---|
| Frontend | React + Vite (TypeScript), later a PWA |
| UI library | Mantine (custom theme for a distinctive look) + `mantine-react-table` (built on TanStack Table) |
| Backend | Spring Boot (latest stable), Java 21+ |
| Security | Spring Security + JWT + BCrypt |
| Persistence | PostgreSQL + Spring Data JPA (Repository pattern) |
| Background jobs | Spring `@Scheduled` (SLA monitoring, escalation) |
| Real time | WebSocket (STOMP) for the live dashboard |
| Testing | JUnit 5 + Mockito + Spring Boot Test |
| DevOps | Docker Compose, GitHub Actions CI |

---

## Timeline

| Days | Dates | Work |
|---|---|---|
| 1–2 | 8–9 Nov | Domain model, class diagram, scaffolding, DB schema, auth & roles |
| 3–6 | 10–13 Nov | Incident CRUD, lifecycle (State), priority, assignment strategies, SLA deadlines |
| 7–9 | 14–16 Nov | Notifications, audit trail, search/filters, reports, role-specific UIs |
| 10–11 | 17–18 Nov | Intelligent features, escalation, real-time dashboard |
| 12 | 19 Nov | Tests (at least 15 documented), bug fixing |
| 13 | 20 Nov | Report |
| 14 | 21 Nov | Record and upload the video (buffer day) |
| Stretch | | PWA, then Flutter/React Native if there is real time left |

---

## MVP checklist (60 marks: do these first)

Status as of 8 Nov 2026. Everything ticked is implemented **and** covered by automated tests
(`backend/src/test`, 220+ test cases).

### Users & auth
- [x] Register / login (JWT)
- [x] Roles: Employee, Technician, Administrator (role-based authorization)
- [x] Password hashing (BCrypt)
- [x] Different UI per role, with navigation links between screens

### Incident management
- [x] Create / view / update / assign / resolve / close
- [x] Fields: ID, reporter, category, description, affected service, impact, urgency, priority,
      created time, status, assigned technician, SLA, investigation notes, resolution, closure info
- [x] Categories configurable by the admin (new ones need no code change): Admin → Catalogue

### Lifecycle (State pattern)
- [x] NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED
      (plus ON_HOLD while waiting for the reporter, CANCELLED, and MARK_DUPLICATE until resolution)
- [x] Invalid transitions rejected (for example NEW → CLOSED) with a custom exception

### Priority (Strategy pattern)
- [x] Automatic priority from impact, urgency, business criticality, affected users, service
- [x] P1–P4 mapping (two calculators: weighted score, ITIL matrix; switchable at runtime)
- [ ] Algorithm and rationale documented in the report

### Technician assignment (Strategy pattern)
- [x] At least 2 strategies (smart composite, skill-based, load-based, experience-based, round-robin)
- [x] Admin can switch the strategy at runtime
- [x] Workload limit enforced (custom exception when exceeded)

### SLA
- [x] SLA policies per priority (and service), configurable by the admin: Admin → SLA policies
- [x] Response and resolution deadlines calculated
- [x] Response and resolution times tracked
- [x] Warning before a deadline; violation detected and recorded
- [x] Background monitor (`@Scheduled`)

### Notifications (Observer + Factory/Adapter)
- [x] At least 2 channels (in-app + email; SMS simulated)
- [x] Events: created, assigned, priority changed, SLA approaching, SLA violated, resolved, closed
- [x] Adding a new channel = adding one class

### Audit
- [x] Full history per incident: timestamp, user, action, incident ID, old state, new state, description

### Search & reporting
- [x] At least 5 filters: ID, date range, status, priority, category, service, technician, SLA status
- [x] Reports: incident overview, technician workload, service analysis (Reports page, with charts)

### Intelligent features (at least 2) — all three done
- [x] Duplicate / related incident detection (TF-IDF cosine similarity + service-graph bonus; one-click
      "mark as duplicate"; past fixes suggested; a reporter's own repeat is refused with DUPLICATE_INCIDENT)
- [x] Automatic category suggestion from the description (admin keywords + k-nearest past incidents; applied
      automatically when confident, suggested live on the report form)
- [x] SLA risk detection + automatic escalation (Technician → Team Lead → Sys Admin → IT Manager)
- [x] Bonus: root-cause correlation (open incidents on services sharing a dependency) on the dashboard

### Persistence
- [x] PostgreSQL: users, services, incidents, technicians, SLA policies, audit records, resolutions
- [x] Sample/seed data

### Employee features
- [x] Track status, add info, respond to info requests, view resolution, give feedback

### Technician features
- [x] View assigned, acknowledge, investigation notes, request info, resolve, view workload (My profile,
      Reports), set own availability

### Admin features
- [x] Manage users, technicians, services, SLA policies, categories, assignment strategy
- [x] Monitor incidents, reports, audit logs, SLA violations

---

## Error handling (custom exceptions)
- [x] Invalid login, unauthorized operation, invalid incident, invalid state transition
- [x] Technician unavailable, workload exceeded, service not found, missing SLA config
- [x] Duplicate incident, database failure, network failure, invalid input
- [x] Global exception handler returning consistent JSON errors

## Design patterns (at least 2 required; we use 7)
- [x] State: incident lifecycle
- [x] Strategy: assignment, priority
- [x] Observer: notifications, audit, SLA violations, real-time push
- [x] Factory: notification channels, users (admin user creation)
- [x] Adapter: email/SMS providers
- [x] Repository: Spring Data JPA
- [x] Chain of Responsibility: escalation levels

## Innovation (extra marks)
- [x] Real-time operational dashboard (WebSocket/STOMP, "Live" indicator)
- [x] Automated incident escalation
- [x] Incident similarity detection and root-cause correlation
- [x] Analytics & visualization (charts on the Reports page)
- [ ] Docker / cloud deployment (today: Docker Compose for PostgreSQL only; app containers not yet)
- [x] CI: GitHub Actions runs backend tests on PostgreSQL and lints/builds the frontend
- [ ] Stretch: PWA + push notifications

---

## Deliverables (report PDF, one per group)
- [ ] 1. Problem analysis: problem, stakeholders, challenges, proposed solution, assumptions
- [ ] 2. Requirements: functional, non-functional, user roles, business rules
- [ ] 3. Architecture diagram
- [ ] 4. UML: use case diagram + class diagram (attributes, methods, interfaces, inheritance, associations, composition)
- [ ] 5. Technology stack + answers to the 9 technology-selection questions
- [ ] 6. Source code repo link (submitted via the spreadsheet)
- [ ] 7. Database design: ER diagram, schema, relationships, sample data
- [ ] 8. Testing: at least 15 documented test cases (normal, boundary, invalid input, exceptions, authorization, business rules)
- [ ] 9. Security considerations documented

## Video (15–20 min)
- [ ] Every member introduces themselves and explains their module(s)
- [ ] Workflow 1: employee reports → auto priority → technician assigned → notification
- [ ] Workflow 2: technician investigates → SLA monitored → resolved → user notified
- [ ] Workflow 3: multiple incidents → duplicates detected → correlated/escalated
- [ ] File name: `<GroupNo>_OOP_S1_2025-2026_Project`

## Team & responsibilities
| Member | Area |
|---|---|
| | UI / UX (React) |
| | Backend / APIs |
| | Domain model / OOP |
| | Database / persistence |
| | Testing / DevOps / intelligent features |
