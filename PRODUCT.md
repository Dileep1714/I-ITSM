# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

The product has two clients that share one backend:
- **Web app** (`frontend/`): React 19 + TypeScript + Mantine, installable PWA. Designed as a web surface.
- **Mobile app** (`mobile/`): Expo / React Native. Uses Material 3 conventions on Android and native iOS navigation (the platform's own tab bars and headers).

User, catalogue and SLA-policy configuration is web-only.

## Users

Three roles in a (fictional) organisation, each with its own UI:
- **Employees** report IT problems, answer technicians' questions, and confirm or reject fixes. They use it occasionally, often while something is broken and they are frustrated.
- **Technicians** (L1–L4: support, team lead, system admin, IT manager) work a queue sorted by priority: acknowledge, investigate, resolve, request info, set availability. Team leads and above assign work.
- **Administrators** watch SLA compliance and service health, manage users, catalogue, SLA policies and strategies, and read the audit log.

There is a second audience that carries equal weight: **course evaluators** (CS F213 / MAC F212 OOP, Sem I 2026–27). They judge the product through a recorded demo video and a PDF report. Design for realistic daily use, and make sure every screen also reads clearly on a screen recording.

## Product Purpose

I-ITSM is an IT Service Management and incident-resolution system. It takes an incident through report, classification, prioritisation, assignment, work, resolution, verification and closure, and adds SLA monitoring, escalation, notifications, auditing and intelligent automation. Success means incidents reach the right technician quickly and SLAs are visibly kept. It also means the object-oriented design (State, Strategy, Chain of Responsibility, Observer) is visible and can be defended in the demo.

## Positioning

The automation explains itself. Priority previews show their reasoning, assignment recommendations rank technicians with reasons, duplicate and similar incidents are found with TF-IDF similarity and shown with their past fixes, root-cause service correlation spans dependent services, and admins can switch priority and assignment strategies while the system runs. Wherever the system makes a decision, the user can see why.

## Operating Context

- Lifecycle: NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED, plus ON_HOLD (waiting on the reporter), CANCELLED and duplicate marking. Invalid transitions are rejected.
- Live updates arrive over STOMP/WebSocket; staff see every incident change and everyone gets their own notifications, including a "Live" indicator.
- Notifications are in-app, plus email and SMS (simulated unless SMTP is configured).
- The SLA monitor runs every minute; it warns before deadlines and records violations.
- Mobile adds swipe actions, action sheets, biometric unlock, system notifications, camera photos and haptics.
- Demo flow: log in with the seeded accounts (password `Password123`) and walk through at least three complete scenarios on video.

## Capabilities and Constraints

- The backend is Spring Boot 4 / Java 21 / PostgreSQL / Flyway with a REST API under `/api`. Every error comes back in one JSON shape.
- Terms used in the product: incident, service, category, impact, urgency, priority (P1–P4), SLA (response/resolution deadlines), escalation, technician level L1–L4, resolution code.
- Assumed constraints (chosen by Claude on the user's instruction, so revisit them if they turn out wrong):
  - Keep Mantine on the web app; change the look through `frontend/src/theme.ts` and CSS, never by swapping the library.
  - Design work stays in the clients; it does not change the backend or the API.
  - Treat WCAG 2.2 AA as required: text contrast, keyboard operation, visible focus, and colour is never the only signal (for example, priority and SLA state).
  - Seeded demo data (names, accounts, services, categories, SLA table) stays as it is, because it appears in the report and video.
- Hard deadline: 22 Nov 2026 (report and demo video). Late changes must be low-risk.

## Brand Commitments

- Name: **I-ITSM**. The logo and icons are in `frontend/public/` (`logo.svg`, `favicon.svg`, PWA icons) and `mobile/assets/`.
- Voice from the existing copy: plain, sentence-case, factual and instructive, with no hype.
- The visual identity is not final: `theme.ts` notes that the brand colour is still Mantine's default indigo, "to be replaced by the project palette".

## Evidence on Hand

- Demo data: 8 IT services with dependencies, 9 categories, the spec's SLA table, 1 admin, 6 technicians (L1–L4) and 3 employees (`backend/.../config/DemoDataSeeder.java`).
- Specification: `requirements.md`; plan: `docs/PLAN.md`; design: `docs/DOMAIN_MODEL.md`.
- Tests: 220+ automated backend tests.
- There are no real customers, testimonials, usage metrics or deployment claims. Never invent any.

## Product Principles

1. **Show the reasoning.** Every automated decision (priority, category, assignee, duplicate, escalation) shows why it was made.
2. **Urgency first.** Priority, SLA deadlines and breaches are the first thing anyone sees.
3. **One role, one job.** Each role's screens are built around that role's task; nobody should have to dig past another role's tools.
4. **Live, not stale.** Changes appear without a reload, and the interface says when it is connected.
5. **Readable on camera.** Hierarchy and state should be clear in a screen recording.

## Accessibility & Inclusion

WCAG 2.2 AA is assumed to be required (see Capabilities and Constraints). Priority and SLA state must never rely on colour alone.
