# Aston Martin ASO Service

Auto service organization (ASO) web API for managing customers, vehicles, and service bookings. When bookings are created or their status changes, the app sends notification emails to the customer.

## Stack

- **Spring Boot** (Java 21) — REST API, JPA, SMTP delivery
- **PostgreSQL** — persistence
- **Clojure** (`clojure/email-renderer`) — HTTP service that renders email subject and bodies
- **Mailhog** — local SMTP capture (dev)

## Run with Docker

1. Build the Java app JAR:

   ```bash
   ./mvnw package -DskipTests
   ```

2. Start the full stack:

   ```bash
   docker compose up --build
   ```

3. API: http://localhost:8080  
4. Mailhog UI (view sent emails): http://localhost:8025  
5. Email renderer health: http://localhost:3000 (POST `/render/booking-email`)

## Email flow

1. `POST /bookings` or `PATCH /bookings/{id}/status` persists the booking.
2. `BookingNotificationService` resolves the customer email via `booking → vehicle → customer`.
3. Java calls the Clojure renderer (`POST /render/booking-email`) for subject/text/HTML.
4. Java sends the message via SMTP (Mailhog in dev).

## Manual test

```bash
# Create a customer
curl -s -X POST http://localhost:8080/customers \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Jane","lastName":"Doe","email":"jane@example.com"}'

# Create a vehicle (use customer id from above)
curl -s -X POST http://localhost:8080/vehicles/customer/1 \
  -H 'Content-Type: application/json' \
  -d '{"model":"Vantage V12","vin":"1HGBH41JXMN109186","productionYear":2024}'

# Create a booking
curl -s -X POST http://localhost:8080/bookings \
  -H 'Content-Type: application/json' \
  -d '{"vehicleId":1,"serviceType":"Oil change"}'

# Update status
curl -s -X PATCH http://localhost:8080/bookings/1/status \
  -H 'Content-Type: application/json' \
  -d '{"status":"IN_PROGRESS"}'

# Mark finished (COMPLETED) to trigger invoice PDF attachment
curl -s -X PATCH http://localhost:8080/bookings/1/status \
  -H 'Content-Type: application/json' \
  -d '{"status":"COMPLETED"}'
```

Open http://localhost:8025 to confirm confirmation and status emails.

## Clojure tests

```bash
cd clojure/email-renderer
clojure -M:test
```