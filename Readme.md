# MediBook — Backend

A microservices-based backend for a healthcare appointment and management platform. Built with Spring Boot 3, Spring Cloud, and deployed via Docker.

---

## Architecture

The system is composed of 10 independent Spring Boot services coordinated through a Eureka service registry and exposed to clients through a single API gateway.

```
Client (Angular)
     |
     v
API Gateway  :8080      ← single entry point, JWT validation, routing
     |
     +-- Eureka  :8761  ← service registry
     |
     +-- Auth Service         :8081
     +-- Provider Service     :8082
     +-- Schedule Service     :8083
     +-- Appointment Service  :8084
     +-- Payment Service      :8085
     +-- Review Service       :8086
     +-- Notification Service :8087
     +-- Record Service       :8088
```

All services register with Eureka on startup. The gateway routes requests via Eureka load-balancer (`lb://service-name`) and validates JWT tokens before forwarding.

---

## Services

### api-gateway
Routes all inbound traffic to downstream services. Validates JWT on protected routes, passes OPTIONS preflight requests through without authentication, and aggregates Swagger docs from all services at `/swagger-ui.html`.

### eureka-service
Netflix Eureka server. Maintains the service registry. No database, no external dependencies.

### auth-service
Handles registration, email verification, login, logout, token refresh, and profile management. Supports Google OAuth2 login. Issues and validates JWT tokens. Uses Redis to store active token blacklists and verification codes. Sends verification emails via SMTP.

### provider-service
Manages healthcare provider profiles, registration, approval workflow, specialization search, location-based filtering, and availability status. Results are cached in Redis.

### schedule-service
Manages provider time slots — creation (single, bulk, recurring), availability queries, and slot state transitions (available → booked → released). Uses Quartz with an in-memory job store for slot expiry handling.

### appointment-service
Books appointments by coordinating with schedule-service (slot reservation), payment-service (order creation), and notification-service (confirmation emails). Supports cancellation, rescheduling, and status updates (complete, no-show).

### payment-service
Integrates with Razorpay for order creation and payment verification. Handles refunds on cancellation. Generates payment invoices. Tracks per-patient payment history.

### review-service
Allows patients to leave reviews after completed appointments. Computes provider rating summaries. Supports admin moderation — flag, unflag, and verify reviews. Results are cached in Redis.

### notification-service
Stores in-app notifications and sends transactional emails (appointment confirmation, cancellation, payment receipt, follow-up reminders) via SMTP. Exposes event endpoints consumed by other services.

### record-service
Manages medical records created by providers after appointments. Supports follow-up scheduling and attachment management. Patients can view their own record history.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.4 |
| Service Discovery | Spring Cloud Netflix Eureka 2023.0.1 |
| API Gateway | Spring Cloud Gateway |
| Auth | Spring Security, JWT (JJWT), Google OAuth2 |
| Database | MySQL 8 (one schema per service) |
| Cache | Redis |
| Scheduler | Quartz (in-memory) |
| Payments | Razorpay |
| Email | JavaMail (Gmail SMTP) |
| API Docs | SpringDoc OpenAPI 3 |
| Build | Maven |
| Containerisation | Docker (multi-stage builds) |

---

## API Routes (via Gateway)

All requests go through `https://<gateway-host>`. The gateway strips or preserves prefixes as configured.

| Service | Gateway Prefix | Example Endpoints |
|---|---|---|
| auth-service | `/api/auth/**` | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh` |
| provider-service | `/api/providers/**` | `GET /api/providers/search`, `GET /api/providers/{id}` |
| schedule-service | `/api/slots/**` | `GET /api/slots/available/{providerId}`, `PUT /api/slots/{slotId}/book` |
| appointment-service | `/api/appointments/**` | `POST /api/appointments/book`, `PUT /api/appointments/{id}/cancel` |
| payment-service | `/api/payments/**` | `POST /api/payments/razorpay/create-order`, `POST /api/payments/razorpay/verify` |
| review-service | `/api/reviews/**` | `GET /api/reviews/provider/{providerId}`, `GET /api/reviews/rating/{providerId}` |
| notification-service | `/api/notifications/**` | `GET /api/notifications/recipient/{id}`, `POST /api/notifications/events/appointment` |
| record-service | `/api/records/**` | `GET /api/records/patient/{patientId}`, `POST /api/records/followup` |

OAuth2 flow routes (`/oauth2/**`, `/login/oauth2/**`) are forwarded directly to auth-service without prefix stripping.

Swagger UI for all services is aggregated at:
```
GET <gateway-host>/swagger-ui.html
```

---

## Prerequisites

- Java 17
- Maven 3.8+
- Docker (for containerised deployment)
- MySQL 8
- Redis
- A Razorpay account (key ID and secret)
- A Gmail account with an App Password enabled

---

## Local Development

### 1. Clone the repository

```bash
git clone https://github.com/your-username/medibook-backend.git
cd medibook-backend
```

### 2. Create MySQL databases

```sql
CREATE DATABASE medibook_auth;
CREATE DATABASE medibook_provider;
CREATE DATABASE medibook_schedule;
CREATE DATABASE medibook_appointment;
CREATE DATABASE medibook_payment;
CREATE DATABASE medibook_review;
CREATE DATABASE medibook_notification;
CREATE DATABASE medibook_record;
```

### 3. Configure local properties

Each service reads `application-local.properties` when the `local` profile is active. Copy and fill in the template below for any service you want to run:

```properties
# Database
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/<db_name>?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=your_password

# JWT — must be the same value across all services
app.jwt.secret=your_256_bit_secret_key_here

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=

# Eureka
eureka.client.service-url.defaultZone=http://localhost:8761/eureka
```

> Do not commit `application-local.properties` files. They are already in `.gitignore`.

### 4. Start services in order

Services must be started in dependency order. Eureka must be up before any other service attempts to register.

```bash
# 1. Service registry
cd eureka-service && mvn spring-boot:run -Dspring-boot.run.profiles=local

# 2. All other services (in separate terminals)
cd auth-service && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd provider-service && mvn spring-boot:run -Dspring-boot.run.profiles=local
# ... repeat for each service

# 3. Gateway (last)
cd api-gateway && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Eureka dashboard is available at `http://localhost:8761` once running.

---

## Docker

Each service has a `Dockerfile` at its root using a two-stage build — Maven compiles the JAR in stage 1, and a minimal JRE Alpine image runs it in stage 2.

### Build a single service

```bash
cd auth-service
docker build -t medibook-auth:latest .
```

### Run a single service

```bash
docker run -p 8081:8081 \
  -e DB_URL="jdbc:mysql://host:3306/medibook_auth?useSSL=true&serverTimezone=UTC" \
  -e DB_USERNAME="your_user" \
  -e DB_PASSWORD="your_password" \
  -e JWT_SECRET="your_secret" \
  -e REDIS_HOST="your_redis_host" \
  -e REDIS_PORT="6379" \
  -e REDIS_PASSWORD="your_redis_password" \
  -e EUREKA_URL="http://eureka-host:8761/eureka" \
  medibook-auth:latest
```

---

## Environment Variables

The table below lists every environment variable consumed across all services. Variables shared by multiple services (JWT_SECRET, DB credentials, Redis, Eureka) must be consistent.

### Shared by all services

| Variable | Description |
|---|---|
| `JWT_SECRET` | HS256 signing key — must be identical across all 10 services |
| `EUREKA_URL` | Full Eureka registration URL, e.g. `http://eureka:8761/eureka` |

### auth-service

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL for `medibook_auth` |
| `DB_USERNAME` | MySQL username |
| `DB_PASSWORD` | MySQL password |
| `JWT_EXPIRATION_MS` | Token lifetime in milliseconds (default: `86400000`) |
| `REDIS_HOST` | Redis hostname |
| `REDIS_PORT` | Redis port |
| `REDIS_PASSWORD` | Redis password |
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret |
| `GOOGLE_REDIRECT_URI` | OAuth2 redirect URI registered in Google Cloud Console |
| `APP_FRONTEND_OAUTH2_CALLBACK_URL` | Frontend URL to redirect after OAuth2 login |
| `MAIL_HOST` | SMTP host (default: `smtp.gmail.com`) |
| `MAIL_PORT` | SMTP port (default: `587`) |
| `MAIL_USERNAME` | SMTP login address |
| `MAIL_PASSWORD` | SMTP App Password |
| `MAIL_FROM` | From address on outgoing emails |

### provider-service, schedule-service, review-service

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL for the service's own schema |
| `DB_USERNAME` | MySQL username |
| `DB_PASSWORD` | MySQL password |
| `REDIS_HOST` | Redis hostname |
| `REDIS_PORT` | Redis port |
| `REDIS_PASSWORD` | Redis password |

### appointment-service

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL for `medibook_appointment` |
| `DB_USERNAME` | MySQL username |
| `DB_PASSWORD` | MySQL password |
| `SCHEDULE_SERVICE_URL` | Base URL of schedule-service, no trailing slash, no `/api` suffix |
| `PAYMENT_SERVICE_URL` | Base URL of payment-service, no trailing slash, no `/api` suffix |
| `NOTIFICATION_SERVICE_URL` | Base URL of notification-service, no trailing slash, no `/api` suffix |

### payment-service

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL for `medibook_payment` |
| `DB_USERNAME` | MySQL username |
| `DB_PASSWORD` | MySQL password |
| `RAZORPAY_KEY_ID` | Razorpay API key ID |
| `RAZORPAY_KEY_SECRET` | Razorpay API key secret |

### notification-service

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL for `medibook_notification` |
| `DB_USERNAME` | MySQL username |
| `DB_PASSWORD` | MySQL password |
| `MAIL_HOST` | SMTP host |
| `MAIL_PORT` | SMTP port |
| `MAIL_USERNAME` | SMTP login address |
| `MAIL_PASSWORD` | SMTP App Password |
| `MAIL_FROM` | From address on outgoing emails |

### record-service

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL for `medibook_record` |
| `DB_USERNAME` | MySQL username |
| `DB_PASSWORD` | MySQL password |
| `APPOINTMENT_SERVICE_URL` | Base URL of appointment-service, no `/api` suffix |
| `NOTIFICATION_SERVICE_URL` | Base URL of notification-service, no `/api` suffix |

---

## Known Configuration Notes

**notification-service context-path** — `server.servlet.context-path=/api` must be present in `application.properties` (not only in `application-local.properties`). All other services call this service at `/api/notifications/...`. Without this line, those calls will return 404 in any non-local environment.

**OAuth2 redirect URI** — `spring.security.oauth2.client.registration.google.redirect-uri` is hardcoded in `application.properties`. Before deploying, make it an env var and register the production URI in Google Cloud Console.

**Inter-service URL suffixes** — `appointment-service` and `record-service` default URLs include an `/api` suffix for some dependencies (schedule, payment, notification) that do not have a context-path. Always set the `*_SERVICE_URL` env vars explicitly and omit the `/api` suffix for those services.

**JWT secret** — All services independently verify JWT tokens. If the secret differs between services, every cross-service authenticated request will fail with 401.

---

## Project Structure

```
medibook-backend/
├── eureka-service/
├── api-gateway/
├── auth-service/
├── provider-service/
├── schedule-service/
├── appointment-service/
├── payment-service/
├── review-service/
├── notification-service/
└── record-service/
```

Each service follows standard Maven layout:

```
<service>/
├── Dockerfile
├── pom.xml
└── src/
    └── main/
        ├── java/com/medibook/<service>/
        │   ├── config/
        │   ├── dto/
        │   ├── entity/
        │   ├── repository/
        │   ├── resource/       ← REST controllers
        │   └── serviceimpl/
        └── resources/
            ├── application.properties
            └── application-local.properties  ← git-ignored, local only
```

---

## License

This project is for educational and portfolio purposes.