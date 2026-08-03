# Aston Martin ASO Service

Web application for an **Authorized Service Organization (ASO)**. Customers register, add Aston Martin vehicles, and request service. Workshop staff claim, schedule, complete, reject, or cancel bookings. Management manages workers and views booking history. Notification emails (with invoice PDF on completion) are sent at each step.

---

## Stack

| Layer | Technology |
|-------|------------|
| API | Spring Boot 4, Java 21, Spring Security (JWT), JPA, Flyway |
| Database | PostgreSQL 16 |
| Frontend | React (Vite), Axios, React Router |
| Email rendering | Clojure HTTP service (`clojure/email-renderer`) |
| Local SMTP | Mailhog |
| CI | GitHub Actions (Maven tests + frontend lint/build) |

---

## Features

- **Clients** — register (email verification required), login, forgot/reset password, change password, configure vehicles from catalog, request service, cancel own bookings
- **Workshop staff** — claim available bookings, schedule appointments, complete work (invoice), reject or cancel
- **Management** (`ADMIN`, `CEO`, `COO`) — create/update/delete workers, view all bookings
- **Auth** — BCrypt passwords, Bearer JWT; actor identity always from the token (not from request body IDs)
- **Emails** — async delivery via SMTP; completion emails include a PDF invoice
- **Schema** — Flyway migrations; Hibernate `validate` only

---

## Quick start

Build the API JAR, then start the full stack:

```bash
./mvnw package -DskipTests
docker compose up --build
```

Five containers should be running:

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| Frontend | http://localhost:5173 |
| Mailhog UI | http://localhost:8025 |
| Email renderer | http://localhost:3000 |
| Postgres | `localhost:5432` (`aso_service_db`) |

```bash
docker compose ps
```

### Seeded account

On an empty database:

| Role | Login | Password |
|------|-------|----------|
| Admin | `admin` | `admin` |

Clients self-register in the UI. Workers are created from the management dashboard.

### Fresh database

```bash
docker compose down
rm -rf pgdata
./mvnw package -DskipTests
docker compose up --build
```

---

## Demo walkthrough

1. Open http://localhost:5173/register — create a client account.
2. Open http://localhost:8025 — open the verification email and click the link (or copy the token URL).
3. Sign in at http://localhost:5173/ with the verified account.
4. Add a vehicle (`/client/add-vehicle`) using the Aston Martin catalog.
5. Request service (`/client/request-service`) with drop-off time and/or availability notes.
6. Sign out → http://localhost:5173/employee-login — log in as `admin` / `admin`.
7. On the management dashboard, create a workshop worker (e.g. mechanic).
8. Sign out → log in as that worker → claim the booking, schedule a time, complete with a final cost.
9. Open http://localhost:8025 — verify emails (including invoice PDF on completion).

Forgot password: `/forgot-password` → check Mailhog → `/reset-password?token=…`.

Any signed-in user can change their password from their dashboard.

---

## Architecture

```
Client / Staff UI (React)
        │  Bearer JWT
        ▼
   Spring Boot API
        │
        ├── PostgreSQL (Flyway schema)
        ├── Email renderer (Clojure) ──► HTML/text bodies
        └── SMTP (Mailhog in dev) ──► emails + optional PDF
```

**Booking lifecycle**

```
SCHEDULED ──claim──► IN_PROGRESS ──complete──► COMPLETED
    │                     │
    │ reject              │ cancel (customer or assigned worker)
    └─────────────────────┴──► CANCELLED
```

Customer create → `SCHEDULED` (service *request*, not a confirmed appointment).  
Worker schedule sets the confirmed date/time and sends an appointment email.

---

## Authentication

| Endpoint | Who | Notes |
|----------|-----|--------|
| `POST /auth/register` | Public | Creates client (unverified), sends welcome + verify emails |
| `POST /auth/login` | Public | Client login (email + password); requires verified email |
| `POST /auth/employee-login` | Public | Worker/admin login (`login` + password) |
| `POST /auth/forgot-password` | Public | Sends reset link if email exists (generic response) |
| `POST /auth/reset-password` | Public | Sets new password with one-time token |
| `POST /auth/verify-email` | Public | Marks client email verified with one-time token |
| `POST /auth/resend-verification` | Public | Resends verify link if still unverified (generic response) |
| `POST /auth/change-password` | Authenticated | Current + new password (min 8 chars) |

Public routes: the auth endpoints above, `GET /hello`, `GET /vehicles/catalog`. Everything else requires `Authorization: Bearer <token>`.

Frontend helpers: `/forgot-password`, `/reset-password?token=…`, `/verify-email?token=…`, `/resend-verification`. Check Mailhog at http://localhost:8025 for links in local demos.

JWT payload drives authorization via `AuthSupport`. Prefer self routes: `/vehicles/me`, `/customers/me/bookings`, `/workers/me/bookings`.

**Roles**

| Role | Access |
|------|--------|
| `CLIENT` | Own vehicles and bookings |
| `MECHANIC`, `APPRENTICE_MECHANIC`, `CLIENT_SERVICE_CONSULTANT` | Available queue + assigned bookings |
| `ADMIN`, `CEO`, `COO` | Worker management + all bookings |

Env overrides: `APP_JWT_SECRET` (min 32 characters), `APP_JWT_EXPIRATION_MS` (default 24h).

---

## Booking API

Identity of the acting user always comes from the JWT.

```bash
# Client token
CLIENT_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"you@example.com","password":"yourpassword"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")

# Create booking (vehicle must belong to the client)
curl -s -X POST http://localhost:8080/bookings \
  -H "Authorization: Bearer $CLIENT_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"vehicleId":1,"customerDescription":"Noise when braking","estimatedDropOffTime":"2026-06-15T10:30:00","availabilityNotes":"Tuesday mornings"}'

# Worker token
WORKER_TOKEN=$(curl -s -X POST http://localhost:8080/auth/employee-login \
  -H 'Content-Type: application/json' \
  -d '{"login":"mechanic1","password":"secret123"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])")

curl -s -X POST http://localhost:8080/bookings/1/claim \
  -H "Authorization: Bearer $WORKER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"estimatedCost":350.00,"serviceTypes":["Brake inspection"]}'

curl -s -X POST http://localhost:8080/bookings/1/schedule \
  -H "Authorization: Bearer $WORKER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"scheduledDateTime":"2026-06-16T14:00:00"}'

curl -s -X POST http://localhost:8080/bookings/1/complete \
  -H "Authorization: Bearer $WORKER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"finalCost":375.50}'
```

| Action | Endpoint |
|--------|----------|
| Create | `POST /bookings` |
| Claim | `POST /bookings/{id}/claim` |
| Schedule | `POST /bookings/{id}/schedule` |
| Complete | `POST /bookings/{id}/complete` |
| Reject (unclaimed) | `POST /bookings/{id}/reject` |
| Cancel | `POST /bookings/{id}/cancel` |

---

## Emails

| Event | When |
|-------|------|
| `customer_registered` | Client signup |
| `vehicle_added` / `vehicle_removed` | Vehicle changes |
| `created` | Service request submitted |
| `technician_assigned` | Worker claims booking |
| `appointment_scheduled` | Confirmed date/time set |
| `booking_rejected` | Workshop declines unclaimed booking |
| `booking_cancelled` | Customer or worker cancels |
| `booking_completed` | Finished — invoice PDF attached |
| `password_changed` | After change-password or successful reset |

Flow: persist → enqueue → `@Async` delivery → Clojure `POST /render/...` → SMTP.

---

## Frontend

Runs in Docker with the stack (no local Node required for the demo).

| Page | URL |
|------|-----|
| Home / client login | http://localhost:5173/ |
| Register | http://localhost:5173/register |
| Client dashboard | http://localhost:5173/client |
| Staff login | http://localhost:5173/employee-login |
| Employee dashboard | http://localhost:5173/employee |
| Management dashboard | http://localhost:5173/admin |

Optional local UI: `cd aso-frontend && npm install && npm run dev`

---

## Database

Migrations: `src/main/resources/db/migration/`. Flyway applies them on startup; Hibernate only validates.

Test database: `aso_service_test` (see `src/test/resources/application-test.properties`).

---

## Tests

```bash
# Create test DB once (Postgres from docker compose is fine)
docker exec aso-postgres psql -U postgres -c "CREATE DATABASE aso_service_test;"

./mvnw test
```

Coverage includes authorization, booking lifecycle, and password change.

```bash
cd clojure/email-renderer && clojure -M:test
```

CI (`.github/workflows/ci.yml`) runs Maven tests/package and frontend lint/build on push and pull requests.

---

## Project layout

```
aso-service/
├── src/main/java/...     Spring Boot API
├── src/main/resources/
│   ├── db/migration/     Flyway SQL
│   └── catalog/          Aston Martin vehicle catalog
├── src/test/java/...     Integration tests
├── aso-frontend/         React UI
├── clojure/email-renderer/
├── .github/workflows/    CI
└── docker-compose.yml
```

---

## Rebuild API after code changes

The `app` image uses the JAR from `target/`. Rebuild before restarting the API container:

```bash
./mvnw package -DskipTests
docker compose up -d --build app
```
