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

Java 21+ · Spring Boot 4 · PostgreSQL 18 · Flyway · React + Mantine (frontend, in progress) · Docker

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
The tests include integration tests that use the database, so step 3 must be done first.

### 5. Run the backend

```bash
./mvnw spring-boot:run    # Windows PowerShell: .\mvnw.cmd spring-boot:run
```

Check it is up: http://localhost:8080/actuator/health should show `{"status":"UP"}`.

## Everyday commands

| Task | Command (from the repository root unless noted) |
|---|---|
| Start / stop the database | `docker compose up -d` / `docker compose down` |
| **Reset** the database (deletes all data) | `docker compose down -v` then `docker compose up -d` |
| Run all tests | `cd backend` then `./mvnw test` |
| Get teammates' latest work | `git pull` |
| Share your work | `git add <files>`, `git commit -m "Describe the change"`, `git push` |

## Troubleshooting

| Problem | Fix |
|---|---|
| `docker: command not found` / "not recognized" | Close and reopen the terminal (and your IDE) after installing Docker Desktop |
| `Connection refused` on port 5432 when running tests | The database isn't running: `docker compose up -d` |
| Port 5432 already in use | Another PostgreSQL is installed locally. Stop it, or change the port mapping in `docker-compose.yml` |
| `Schema-validation: missing column…` on startup | Your database has an old schema: reset it (see above) |
| `Migration checksum mismatch` | A migration file that was already applied was edited. Never edit applied migrations; add a new `V<n>__….sql` file instead. Reset your local database to recover |

## Project structure

```
I-ITSM/
├── docker-compose.yml           PostgreSQL for local development
├── docs/                        Plan and design documentation
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
