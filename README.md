# I-ITSM

Intelligent IT Service Management & Incident Resolution System: an object-oriented platform for reporting,
classifying, prioritizing, assigning, tracking and resolving IT incidents, with SLA monitoring, notifications,
auditing and intelligent automation.

**Course:** CS F213 / MAC F212 Object-Oriented Programming, Semester I 2026–27

| Document | Contents |
|---|---|
| [`requirements.md`](requirements.md) | Project specification |
| [`docs/PLAN.md`](docs/PLAN.md) | Tech stack, timeline and requirements checklist |
| [`docs/DOMAIN_MODEL.md`](docs/DOMAIN_MODEL.md) | Class diagrams, design patterns and design decisions |

## Tech stack

Java 21+ · Spring Boot 4 · PostgreSQL 18 · Flyway · React 19 + TypeScript + Mantine (frontend) · Docker

## Getting started

### 1. Install

| Tool | Version | Download |
|---|---|---|
| Git | any recent | https://git-scm.com |
| Java (JDK) | **21 or newer** | https://adoptium.net |
| Docker Desktop | any recent | https://www.docker.com/products/docker-desktop |
| Node.js (frontend only) | LTS | https://nodejs.org |

Maven is **not** needed: the project includes the Maven Wrapper (`mvnw` / `mvnw.cmd`).

After installing, **open a new terminal** so the new commands are found.

### 2. Clone

```bash
git clone https://github.com/Dileep1714/I-ITSM.git
cd I-ITSM
```

### 3. Start the database

Make sure Docker Desktop is running ("Engine running" in its window), then from the repository root:

```bash
docker compose up -d      # starts PostgreSQL in the background
docker compose ps         # wait until STATUS shows "(healthy)"
```

The database schema is created automatically by Flyway the first time the backend starts.

### 4. Build and test the backend

```bash
cd backend
./mvnw test               # Windows PowerShell: .\mvnw.cmd test
```

The first run downloads Maven and all libraries (a few minutes). You should see `BUILD SUCCESS`.
The tests include integration tests that use a separate `iitsm_test` database (created automatically with the
container), so step 3 must be done first.

### 5. Run the backend

```bash
./mvnw spring-boot:run    # Windows PowerShell: .\mvnw.cmd spring-boot:run
```

Check it is up: http://localhost:8080/actuator/health should show `{"status":"UP"}`.

### 6. Run the frontend

In a second terminal, with the backend running:

```bash
cd frontend
npm install               # first time only
npm run dev
```

Open http://localhost:5173 and log in with a demo account below (the login page lists them and fills them in
for you). The dev server forwards `/api` calls to the backend on port 8080.

> Installed Node with **fnm**? Each new PowerShell needs `fnm env --use-on-cd | Out-String | Invoke-Expression`
> before `node`/`npm` work. Add that line to your PowerShell profile (`notepad $PROFILE`) to make it permanent.

On first start with an empty database, demo data is loaded automatically: 8 IT services with dependencies,
9 categories, the spec's SLA table, and these accounts. **All use the password `Password123`** (local demo only):

| Role | Accounts |
|---|---|
| Administrator | `admin@iitsm.example.com` |
| Technicians | `ravi@` (L1, Network/Hardware) · `priya@` (L1, Application/Software) · `arjun@` (L1, junior) · `kavya@` (L2 Team Lead, Database/Cloud) · `farhan@` (L3 System Admin, Security) · `sunita@` (L4 IT Manager) |
| Employees | `asha@` · `vikram@` · `neha@` |

(All `@iitsm.example.com`.) Set `SEED_DEMO_DATA=false` to start with an empty system.

## API overview

All endpoints are under `/api` and, except login and registration, need the header
`Authorization: Bearer <token>` from the login response. Errors always have the same JSON shape:
`{code, message, status, path, timestamp, fieldErrors}`.

| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/auth/register` | anyone | Create an employee account; returns a token |
| `POST /api/auth/login` | anyone | Log in; returns a token |
| `GET /api/auth/me` | logged in | Current user |
| `GET /api/catalog/services`, `/categories` | logged in | Reference data for forms |
| `POST /api/priority/preview` | logged in | Priority an incident would get, with explanation |
| `POST /api/incidents` | employee | Report an incident (auto-prioritized and auto-assigned if a category is given) |
| `GET /api/incidents` | logged in | My incidents (reported / assigned / all for admins), paged |
| `GET /api/incidents/{id}` | reporter, staff | Full detail, including `allowedOperations` for the current user |
| `POST /api/incidents/{id}/classify` | staff | Set category; calculates priority and SLA |
| `GET /api/incidents/{id}/recommendations` | team lead+, admin | Ranked technicians with reasons |
| `POST /api/incidents/{id}/assign` | team lead+, admin | Assign or reassign |
| `POST …/acknowledge`, `/start`, `/request-info`, `/resolve` | assigned technician | Work the incident |
| `POST …/provide-info`, `/accept`, `/reject` | reporter | Answer questions; verify the fix |
| `POST …/close`, `/cancel` | staff (reporter may cancel) | Finish the incident |
| `POST …/notes` | reporter, staff | Add a comment (internal notes: staff only) |
| `GET /api/incidents/{id}/history` | reporter, staff | Audit trail (reporters see only entries meant for them) |
| `GET /api/notifications` (`?unreadOnly=true`) | logged in | My in-app notifications, newest first |
| `GET /api/notifications/unread-count` | logged in | Number for the bell icon |
| `POST /api/notifications/{id}/read`, `/read-all` | logged in | Mark as read |
| `GET /api/admin/audit` | admin | Global audit log |
| `GET /api/admin/sla-violations` | admin | Every missed SLA deadline |
| `GET /api/admin/outbox` | admin | Recent emails and SMS (simulated unless SMTP is configured) |
| `POST /api/admin/sla-monitor/run` | admin | Run the SLA monitor now (it also runs every minute) |
| `GET /api/admin/strategies` | admin | Available priority and assignment strategies |
| `PUT /api/admin/strategies/assignment`, `/priority` | admin | Switch strategy at runtime |

## Everyday commands

| Task | Command (from the repository root unless noted) |
|---|---|
| Start / stop the database | `docker compose up -d` / `docker compose down` |
| **Reset** the database (deletes all data) | `docker compose down -v` then `docker compose up -d` |
| Run all tests | `cd backend` then `./mvnw test` |
| Run the frontend | `cd frontend` then `npm run dev` |
| Type-check, lint and build the frontend | `cd frontend` then `npm run lint` and `npm run build` |
| Get teammates' latest work | `git pull` |
| Share your work | `git add <files>`, `git commit -m "Describe the change"`, `git push` |

## Troubleshooting

| Problem | Fix |
|---|---|
| `docker: command not found` / "not recognized" | Close and reopen the terminal (and your IDE) after installing Docker Desktop |
| `Connection refused` on port 5432 when running tests | The database isn't running: `docker compose up -d` |
| Port 5432 already in use | Another PostgreSQL is installed locally. Stop it, or change the port mapping in `docker-compose.yml` |
| `Schema-validation: missing column…` on startup | Your database has an old schema: reset it (see above) |
| Tests fail with `database "iitsm_test" does not exist` | Your database volume is older than the test-database script: reset it (see above), or run `docker exec iitsm-postgres psql -U iitsm -c "CREATE DATABASE iitsm_test OWNER iitsm;"` |
| `Migration checksum mismatch` | A migration file that was already applied was edited. Never edit applied migrations; add a new `V<n>__….sql` file instead. Reset your local database to recover |

## Project structure

```
I-ITSM/
├── docker-compose.yml           PostgreSQL for local development
├── docs/                        Plan and design documentation
├── frontend/                    React + Vite + Mantine web app
│   └── src/
│       ├── api/                 Typed API client and DTO types
│       ├── auth/                Login state and role checks
│       ├── components/          Layout, badges, tables, notification bell
│       └── pages/               Screens per role (incident detail, admin pages…)
└── backend/                     Spring Boot application
    └── src/
        ├── main/java/com/iitsm/backend/
        │   ├── common/          Exceptions, validation helpers, strategy registry
        │   └── domain/          Domain model: users, catalog, incident lifecycle, SLA, priority, assignment
        ├── main/resources/
        │   ├── application.properties
        │   └── db/migration/    Flyway SQL migrations (the database schema)
        └── test/java/…          Unit and integration tests
```
