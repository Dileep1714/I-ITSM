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

### Users & auth
- [ ] Register / login (JWT)
- [ ] Roles: Employee, Technician, Administrator (role-based authorization)
- [ ] Password hashing (BCrypt)
- [ ] Different UI per role, with navigation links between screens

### Incident management
- [ ] Create / view / update / assign / resolve / close
- [ ] Fields: ID, reporter, category, description, affected service, impact, urgency, priority,
      created time, status, assigned technician, SLA, investigation notes, resolution, closure info
- [ ] Categories configurable by the admin (new ones need no code change)

### Lifecycle (State pattern)
- [ ] NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED
- [ ] Invalid transitions rejected (for example NEW → CLOSED) with a custom exception

### Priority (Strategy pattern)
- [ ] Automatic priority from impact, urgency, business criticality, affected users, service
- [ ] P1–P4 mapping
- [ ] Algorithm and rationale documented in the report

### Technician assignment (Strategy pattern)
- [ ] At least 2 strategies (skill-based, load-based, round-robin…)
- [ ] Admin can switch the strategy at runtime
- [ ] Workload limit enforced (custom exception when exceeded)

### SLA
- [ ] SLA policies per priority (and service), configurable by the admin
- [ ] Response and resolution deadlines calculated
- [ ] Response and resolution times tracked
- [ ] Warning before a deadline; violation detected and recorded
- [ ] Background monitor (`@Scheduled`)

### Notifications (Observer + Factory/Adapter)
- [ ] At least 2 channels (in-app + email; SMS simulated)
- [ ] Events: created, assigned, priority changed, SLA approaching, SLA violated, resolved, closed
- [ ] Adding a new channel = adding one class

### Audit
- [ ] Full history per incident: timestamp, user, action, incident ID, old state, new state, description

### Search & reporting
- [x] At least 5 filters: ID, date range, status, priority, category, service, technician, SLA status
- [ ] Reports: incident overview, technician workload, service analysis

### Intelligent features (at least 2)
- [ ] Duplicate / related incident detection (text similarity)
- [ ] Automatic category suggestion from the description
- [ ] SLA risk detection + automatic escalation (Technician → Team Lead → Sys Admin → IT Manager)

### Persistence
- [ ] PostgreSQL: users, services, incidents, technicians, SLA policies, audit records, resolutions
- [ ] Sample/seed data

### Employee features
- [ ] Track status, add info, respond to info requests, view resolution, give feedback

### Technician features
- [ ] View assigned, acknowledge, investigation notes, request info, resolve, view workload

### Admin features
- [ ] Manage users, technicians, services, SLA policies, categories, assignment strategy
- [ ] Monitor incidents, reports, audit logs, SLA violations

---

## Error handling (custom exceptions)
- [ ] Invalid login, unauthorized operation, invalid incident, invalid state transition
- [ ] Technician unavailable, workload exceeded, service not found, missing SLA config
- [ ] Duplicate incident, database failure, network failure, invalid input
- [ ] Global exception handler returning consistent JSON errors

## Design patterns (at least 2 required; we plan 6)
- [ ] State: incident lifecycle
- [ ] Strategy: assignment, priority
- [ ] Observer: notifications, audit
- [ ] Factory: notification channels, users
- [ ] Adapter: email/SMS providers
- [ ] Repository: Spring Data JPA

## Innovation (extra marks)
- [ ] Real-time operational dashboard (WebSocket)
- [ ] Automated incident escalation
- [ ] Incident similarity detection
- [ ] Analytics & visualization (charts)
- [ ] Docker / cloud deployment
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
