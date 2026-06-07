# Aston Martin ASO Service

Auto service organization (ASO) web API for managing customers, vehicles, and service bookings. Customers request service online; workshop staff claim, schedule, complete, or cancel bookings. Notification emails are sent at each step.

## Stack

- **Spring Boot** (Java 21) — REST API, JPA, SMTP delivery
- **PostgreSQL** — persistence
- **React** (`aso-frontend`) — client, employee, and admin UI
- **Clojure** (`clojure/email-renderer`) — HTTP service that renders email subject and bodies
- **Mailhog** — local SMTP capture (dev)

## Run with Docker

The `app` image copies a pre-built JAR from `target/`. **Always run Maven before rebuilding the API container:**

```bash
./mvnw package -DskipTests
docker compose up --build
```

You should see **5** running containers: `aso-service-app`, `aso-frontend`, `aso-postgres`, `aso-mailhog`, `aso-email-renderer`.

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| Frontend | http://localhost:5173 |
| Mailhog (sent emails) | http://localhost:8025 |
| Email renderer | http://localhost:3000 |

Check containers:

```bash
docker compose ps
```

### Fresh database

```bash
docker compose down
rm -rf pgdata
./mvnw package -DskipTests
docker compose up --build
```

One admin account is seeded on an empty database:

| Role | Login | Password |
|------|-------|----------|
| Admin | `admin` | `admin` |

Customers register via the client UI. Workers are added from the admin dashboard.

## Changes

Summary of recent features and fixes.

### Booking flow

- Customer creates a booking with **estimated drop-off time** and/or **availability notes** (at least one required). This is a request, not a confirmed appointment.
- Worker **claims** the booking (service types + optional estimated cost) → `IN_PROGRESS`.
- Worker **schedules** the agreed date/time via `POST /bookings/{id}/schedule` → appointment confirmed email.
- Worker **completes** with final cost → completion email with invoice PDF attachment.
- Worker can **reject** unclaimed bookings with a reason → `CANCELLED` by workshop.
- Customer or assigned worker can **cancel** open bookings → cancellation email.

### Emails

| Event | When |
|-------|------|
| `created` | Customer submits a service request |
| `technician_assigned` | Worker claims a booking |
| `appointment_scheduled` | Worker sets confirmed date/time |
| `booking_rejected` | Workshop declines an unclaimed booking |
| `booking_cancelled` | Customer or worker cancels |
| `booking_completed` | Work finished (invoice PDF attached) |
| `vehicle_added` / `vehicle_removed` | Customer adds or removes a vehicle |
| `customer_registered` | New customer signup |

### Worker roles

Roles: `ADMIN`, `CEO`, `COO`, `CLIENT_SERVICE_CONSULTANT`, `MECHANIC`, `APPRENTICE_MECHANIC`.

- **Admin, CEO, COO** — manage workers (create, change role, delete) and view booking history (`GET /admins/bookings?requesterId=`).
- **Mechanic / apprentice** — claim and manage assigned bookings on the employee dashboard.
- `ADMIN` role is not assignable to workshop worker records.

### Customer UI

- Dashboard at `/client` with separate pages for **add vehicle** (`/client/add-vehicle`) and **request service** (`/client/request-service`).
- Remove vehicle is blocked while the car has `SCHEDULED` or `IN_PROGRESS` bookings (warning shown in UI).

### Admin

- Admin dashboard: worker management + full booking history.
- Single seeded admin (`admin` / `admin`) on fresh DB.

## Frontend

The UI runs in Docker with the rest of the stack. No local Node.js install is required.

| Page | URL |
|------|-----|
| Client register | http://localhost:5173/register |
| Client dashboard | http://localhost:5173/client |
| Staff login | http://localhost:5173/employee-login |
| Admin dashboard | http://localhost:5173/admin |

Local dev (optional): `cd aso-frontend && npm install && npm run dev`

## Booking API flow

```bash
# 1. Customer creates booking (availability required)
curl -s -X POST http://localhost:8080/bookings \
  -H 'Content-Type: application/json' \
  -d '{"vehicleId":1,"customerDescription":"Strange noise when braking","estimatedDropOffTime":"2026-06-15T10:30:00","availabilityNotes":"Tuesday mornings preferred"}'

# 2. Worker claims booking
curl -s -X POST http://localhost:8080/bookings/1/claim \
  -H 'Content-Type: application/json' \
  -d '{"workerId":1,"estimatedCost":350.00,"serviceTypes":["Brake inspection"]}'

# 3. Worker schedules after phone call
curl -s -X POST http://localhost:8080/bookings/1/schedule \
  -H 'Content-Type: application/json' \
  -d '{"workerId":1,"scheduledDateTime":"2026-06-16T14:00:00"}'

# 4. Worker completes (invoice PDF attached to email)
curl -s -X POST http://localhost:8080/bookings/1/complete \
  -H 'Content-Type: application/json' \
  -d '{"workerId":1,"finalCost":375.50}'

# Reject unclaimed booking (worker)
curl -s -X POST http://localhost:8080/bookings/1/reject \
  -H 'Content-Type: application/json' \
  -d '{"workerId":1,"reason":"Cannot service this model at this site"}'

# Cancel (customer or worker — provide one of customerId / workerId)
curl -s -X POST http://localhost:8080/bookings/1/cancel \
  -H 'Content-Type: application/json' \
  -d '{"customerId":1}'
```

Open http://localhost:8025 to verify emails.

## Email architecture

1. Booking or vehicle events persist data first.
2. `BookingNotificationService` enqueues work by booking id.
3. `BookingNotificationExecutor` runs `@Async`.
4. `BookingNotificationDelivery` reloads the booking in a transaction, builds payload, calls Clojure (`POST /render/booking-email`), sends via SMTP (Mailhog in dev).

Customer vehicle events use `CustomerNotificationService` → `POST /render/customer-email`.

## Database migration note

If upgrading from the old single `serviceType` field:

```bash
docker exec aso-postgres psql -U postgres -d aso_service_db \
  -c "ALTER TABLE service_booking DROP COLUMN IF EXISTS service_type;"
```

## Manual setup test

```bash
# Create a customer
curl -s -X POST http://localhost:8080/customers \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Jane","lastName":"Doe","email":"jane@example.com"}'

# List Aston Martin catalog
curl -s http://localhost:8080/vehicles/catalog | head -c 400

# Create a vehicle (configurationId from catalog; VIN is free text)
curl -s -X POST http://localhost:8080/vehicles/customer/1 \
  -H 'Content-Type: application/json' \
  -d '{"configurationId":"db12-coupe-4.0-tt-v8-8a","productionYear":2024,"vin":"SCFRMFCW7M12345"}'

# Remove vehicle (blocked while open bookings exist)
curl -s -X DELETE http://localhost:8080/vehicles/1/customer/1 -w "%{http_code}\n"
```

## Clojure tests

```bash
cd clojure/email-renderer
clojure -M:test
```
