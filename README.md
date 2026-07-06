# Electronic Queue

Production-oriented monorepo for the Electronic Queue backend platform.

## Services

- `apps/backend-spring`: Spring Boot source of truth for auth, RBAC, directories, queue state, bookings, audit, and domain events.
- `apps/middleware-nest`: NestJS middleware for external clients. It validates, authenticates, normalizes, forwards idempotency headers, masks logs, and proxies to Spring.
- `packages/api-contracts`: Shared API, error, and idempotency contract notes.
- `infra`: Docker Compose, nginx, and service infrastructure.

## Requirements

- Java 21 LTS for the Spring backend. The backend includes `apps/backend-spring/mvnw`, so a global Maven install is optional.
- Node.js 22.x and npm 10.x for the NestJS middleware.
- Docker Engine 24+ with Docker Compose v2 for PostgreSQL, Redis, RabbitMQ, nginx, and tool containers.

## Environment

Copy the example env files before local startup:

```bash
cp .env.example .env
cp apps/backend-spring/.env.example apps/backend-spring/.env
cp apps/middleware-nest/.env.example apps/middleware-nest/.env
```

Core variables:

- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`: PostgreSQL database settings.
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`: datasource settings for Spring and the NestJS middleware auth module.
- `REDIS_HOST`, `REDIS_PORT`: Redis connection settings.
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`: RabbitMQ connection settings.
- `BACKEND_JWT_SECRET`: JWT signing secret. Use a strong secret outside local development and keep the exact same value in Spring and NestJS.
- `BACKEND_INTEGRATION_KEY`: shared key that NestJS forwards to Spring as `X-Backend-Integration-Key`.
- `EXTERNAL_API_KEYS`: comma-separated external client API keys accepted by NestJS.
- `BACKEND_BASE_URL`: Spring base URL used by NestJS.

## Local Infrastructure

Validate Compose:

```bash
docker compose config
```

Start only infrastructure:

```bash
docker compose up -d postgres redis rabbitmq
```

Apply Flyway migrations with the tool container:

```bash
docker compose --profile tools run --rm flyway
```

Start the full stack:

```bash
make dev
```

Run live Docker smoke against PostgreSQL, RabbitMQ, Spring, and NestJS:

```bash
START_STACK=true scripts/live-smoke.sh
```

Useful root commands:

```bash
make build
make test
make migrate
make logs
make down
```

Open:

- Backend health: `http://localhost:8080/api/v1/health`
- Backend Swagger: `http://localhost:8080/swagger-ui.html`
- Backend OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Middleware health: `http://localhost:3000/health`
- Middleware Swagger: `http://localhost:3000/docs`
- Middleware OpenAPI JSON: `http://localhost:3000/docs-json`
- Nginx gateway: `http://localhost:8088`
- RabbitMQ management: `http://localhost:15672`

## Spring Backend

Run backend checks:

```bash
cd apps/backend-spring
JAVA_HOME=/path/to/jdk-21 ./mvnw clean test
```

Run the backend against local infrastructure:

```bash
cd apps/backend-spring
JAVA_HOME=/path/to/jdk-21 ./mvnw spring-boot:run
```

The backend runs Flyway automatically on startup and expects PostgreSQL, Redis, and RabbitMQ to be reachable through the env variables above.

## NestJS Middleware

Run middleware checks:

```bash
cd apps/middleware-nest
npm install
npm test
npm run build
npm audit --omit=dev
```

Run middleware locally:

```bash
cd apps/middleware-nest
npm run start:dev
```

## Bootstrap Admin

Flyway seeds an initial admin:

- username: `admin`
- password: `ZAQ!@#$%tgb*`

Change this password immediately after first login.

## Auth Smoke

Login:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"ZAQ!@#$%tgb*"}'
```

Read current user:

```bash
curl -s http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Refresh and logout:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"

curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
```

Refresh tokens are stored as hashes in `refresh_tokens.token_hash`; raw refresh tokens must never be persisted or logged.

## Ticket Smoke

Create or seed a region, department, service category, service, department-service link, hall, service window, operator assignment, terminal, and TV display first.

Create a ticket:

```bash
curl -X POST http://localhost:8080/api/v1/tickets \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "departmentId": "00000000-0000-0000-0000-000000000001",
    "serviceId": "00000000-0000-0000-0000-000000000002",
    "source": "ADMIN_CREATED",
    "citizenFullName": "Masked in logs"
  }'
```

Call next waiting ticket:

```bash
curl -X POST http://localhost:8080/api/v1/tickets/call-next \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "departmentId": "00000000-0000-0000-0000-000000000001",
    "windowId": "00000000-0000-0000-0000-000000000003",
    "serviceIds": ["00000000-0000-0000-0000-000000000002"]
  }'
```

Lifecycle transitions:

```bash
curl -X POST http://localhost:8080/api/v1/tickets/$TICKET_ID/start -H "Authorization: Bearer $ACCESS_TOKEN"
curl -X POST http://localhost:8080/api/v1/tickets/$TICKET_ID/pause -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" -d '{"comment":"Break"}'
curl -X POST http://localhost:8080/api/v1/tickets/$TICKET_ID/resume -H "Authorization: Bearer $ACCESS_TOKEN"
curl -X POST http://localhost:8080/api/v1/tickets/$TICKET_ID/complete -H "Authorization: Bearer $ACCESS_TOKEN"
curl -X POST http://localhost:8080/api/v1/tickets/$TICKET_ID/cancel -H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json" -d '{"comment":"Client request"}'
curl -X POST http://localhost:8080/api/v1/tickets/$TICKET_ID/no-show -H "Authorization: Bearer $ACCESS_TOKEN"
```

Device and TV examples:

```bash
curl -X POST http://localhost:8080/api/v1/terminal/$TERMINAL_ID/tickets \
  -H "X-Device-Token: $TERMINAL_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"departmentId":"'$DEPARTMENT_ID'","serviceId":"'$SERVICE_ID'"}'

curl -N http://localhost:8080/api/v1/tv/$DEPARTMENT_ID/stream \
  -H "X-Device-Token: $TV_DEVICE_TOKEN"
```

## Booking Smoke

Create department working hours, service booking settings, and booking slots first.

Generate slots:

```bash
curl -X POST http://localhost:8080/api/v1/booking/slots/generate \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "departmentId": "00000000-0000-0000-0000-000000000001",
    "serviceId": "00000000-0000-0000-0000-000000000002",
    "fromDate": "2026-07-02",
    "toDate": "2026-07-31",
    "intervalMinutes": 15,
    "capacity": 1,
    "overwrite": false
  }'
```

Available dates and slots:

```bash
curl "http://localhost:8080/api/v1/booking/available-dates?departmentId=$DEPARTMENT_ID&serviceId=$SERVICE_ID&fromDate=2026-07-02&source=WEBSITE_CABINET" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl "http://localhost:8080/api/v1/booking/slots?departmentId=$DEPARTMENT_ID&serviceId=$SERVICE_ID&date=2026-07-02&source=WEBSITE_CABINET" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Create, cancel, and check in:

```bash
curl -X POST http://localhost:8080/api/v1/booking \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Idempotency-Key: cabinet-booking-123" \
  -H "Content-Type: application/json" \
  -d '{
    "departmentId": "00000000-0000-0000-0000-000000000001",
    "serviceId": "00000000-0000-0000-0000-000000000002",
    "slotId": "00000000-0000-0000-0000-000000000003",
    "source": "WEBSITE_CABINET",
    "externalId": "cabinet-booking-123",
    "citizenFullName": "Masked in logs",
    "citizenPhone": "+996700000000"
  }'

curl -X POST http://localhost:8080/api/v1/booking/$BOOKING_ID/cancel \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"comment":"Client request"}'

curl -X POST http://localhost:8080/api/v1/booking/$BOOKING_ID/check-in \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

External booking create requires `Idempotency-Key`, `X-External-Request-Id`, or a create-body external id. External cancel routes should send an explicit `Idempotency-Key` or `X-External-Request-Id`. Same client and same key/body returns the stored successful response. Same key with a different body returns `IDEMPOTENCY_KEY_CONFLICT`.

## External Middleware Smoke

Website Cabinet:

```bash
curl "http://localhost:3000/external/cabinet/booking/available-dates?departmentId=$DEPARTMENT_ID&serviceId=$SERVICE_ID&fromDate=2026-07-02" \
  -H "X-API-Key: dev-cabinet-key" \
  -H "X-Request-Id: smoke-cabinet-1"

curl -X POST http://localhost:3000/external/cabinet/booking \
  -H "X-API-Key: dev-cabinet-key" \
  -H "Idempotency-Key: cabinet-booking-123" \
  -H "Content-Type: application/json" \
  -d '{"departmentId":"'$DEPARTMENT_ID'","serviceId":"'$SERVICE_ID'","slotId":"'$SLOT_ID'","externalBookingId":"cabinet-booking-123"}'
```

Tunduk:

```bash
curl -X POST http://localhost:3000/external/tunduk/bookings \
  -H "X-API-Key: dev-tunduk-key" \
  -H "Idempotency-Key: tunduk-booking-123" \
  -H "Content-Type: application/json" \
  -d '{"departmentId":"'$DEPARTMENT_ID'","serviceId":"'$SERVICE_ID'","slotId":"'$SLOT_ID'","externalBookingId":"tunduk-booking-123"}'
```

Zenoss:

```bash
curl -X POST http://localhost:3000/external/zenoss/tickets \
  -H "X-API-Key: dev-zenoss-key" \
  -H "X-Request-Id: zenoss-ticket-1" \
  -H "Content-Type: application/json" \
  -d '{"departmentId":"'$DEPARTMENT_ID'","serviceId":"'$SERVICE_ID'"}'
```

NestJS forwards `X-Request-Id`, `Idempotency-Key`, `X-External-Request-Id`, `X-Integration-Client`, and `X-Backend-Integration-Key` to Spring.

## Reports And Exports

Phase 4 reports live in Spring under `/api/v1/reports`. Spring is the source of truth for report calculations; NestJS only proxies selected Zenoss report routes.

Common filters:

- `dateFrom` and `dateTo` are required ISO dates.
- Optional filters include `regionId`, `departmentId`, `employeeId`, `windowId`, `serviceCategoryId`, `serviceId`, `source`, `ticketStatus`, `bookingStatus`, `cancellationReasonId`, `groupBy`, `page`, and `size`.
- Standard reports allow up to 366 days. Detailed ticket/booking reports allow up to 93 days for scoped users.
- Personal data is masked by default. `includePersonalData=true` requires `REPORT_VIEW_PERSONAL_DATA`; exporting personal data also requires `REPORT_EXPORT_PERSONAL_DATA`.
- Report read requires `REPORT_READ`; export requires `REPORT_EXPORT`.

Examples:

```bash
curl "http://localhost:8080/api/v1/reports/summary?dateFrom=2026-07-01&dateTo=2026-07-31" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl "http://localhost:8080/api/v1/reports/by-department?dateFrom=2026-07-01&dateTo=2026-07-31&departmentId=$DEPARTMENT_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl "http://localhost:8080/api/v1/reports/by-service?dateFrom=2026-07-01&dateTo=2026-07-31" \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl "http://localhost:8080/api/v1/reports/bookings?dateFrom=2026-07-01&dateTo=2026-07-31" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Export:

```bash
curl -X POST http://localhost:8080/api/v1/reports/export \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "reportType": "BY_DEPARTMENT",
    "format": "CSV",
    "filters": {
      "dateFrom": "2026-07-01",
      "dateTo": "2026-07-31",
      "includePersonalData": false
    }
  }'

curl http://localhost:8080/api/v1/reports/export/$EXPORT_ID \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl -OJ http://localhost:8080/api/v1/reports/export/$EXPORT_ID/download \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Exports are tracked in `report_exports`. Local development stores files under `./data/exports`; MinIO can be added behind `ReportFileStorageService` later without moving report logic out of Spring. CSV includes a UTF-8 BOM and semicolon delimiter by default, XLSX uses Apache POI, and PDF uses a simple table layout with a configured row cap.

Zenoss proxy routes:

```bash
curl "http://localhost:3000/external/zenoss/reports/summary?dateFrom=2026-07-01&dateTo=2026-07-31" \
  -H "X-API-Key: dev-zenoss-key" \
  -H "X-Request-Id: zenoss-report-1"
```

## Phase 3.5 Live Smoke

After PostgreSQL, Redis, RabbitMQ, Spring, and NestJS are running locally, execute the reusable live smoke:

```bash
scripts/live-smoke.sh
```

The script creates isolated smoke data and verifies auth, RBAC, ticket lifecycle, terminal/TV device flows, booking idempotency, RabbitMQ queue publish, Nest external routes, and request-id audit forwarding. It uses `docker compose exec` for PostgreSQL and RabbitMQ checks, so run it from a shell that can access the Docker socket.

## Logging And Secrets

- NestJS `SafeLoggerService` masks password, token, API key, backend integration key, idempotency key, PIN, phone, and full-name fields recursively, including header-style names such as `x-api-key`.
- Spring auth stores refresh-token hashes only.
- Device tokens and backend integration keys must be sent through headers and must not be logged.
- Avoid logging request bodies for auth, booking, ticket, and external integration endpoints unless they are masked first.

## Troubleshooting

- Docker socket denied: add the user to the Docker group or run in a session with access to `/var/run/docker.sock`.
- Spring fails with `Connection to localhost:5432 refused`: start PostgreSQL with `docker compose up -d postgres` or set `DB_HOST` to a reachable PostgreSQL host.
- Flyway fails during startup: run `docker compose --profile tools run --rm flyway` against a clean database and inspect the first failed migration.
- Java mismatch: install Java 21 LTS and set `JAVA_HOME` before running `./mvnw`.
- Middleware cannot call Spring: verify `BACKEND_BASE_URL` and that `BACKEND_INTEGRATION_KEY` matches in both services.
- External request rejected: ensure `X-API-Key` is in `EXTERNAL_API_KEYS` and mutating external calls include an idempotency key or external request id.

## Phase Coverage

Implemented in this scaffold:

- Monorepo structure.
- Docker Compose with Spring, NestJS, PostgreSQL, Redis, RabbitMQ, nginx, optional MinIO profile.
- Spring Boot app with Flyway, JPA, Security, OpenAPI, health, request id filter, global error model.
- Custom JWT auth with refresh-token rotation and hashed DB refresh tokens.
- DB-backed roles and permissions with Flyway seeds.
- Users, roles, permissions, and audit foundation APIs.
- NestJS middleware with validation, external auth guard, idempotency forwarding, safe logging, request normalization, external routes, backend client, and health.
- Phase 2 directories and queue core: regions, departments, rooms, halls, windows, services, assignments, ticket creation, call-next, lifecycle transitions, terminal ticket creation, TV/operator SSE, ticket events, audit, and RabbitMQ ticket domain events.
- Phase 3 online booking: booking slots, available dates/slots, booking create/cancel/check-in/expire, idempotency persistence through `integration_requests`, booking lifecycle events, RabbitMQ booking events, and external middleware wiring for Website Cabinet, Tunduk, and Zenoss.
- Phase 3.5 stability work: Spring Maven wrapper, backend compile/test pass, expanded auth/scope/idempotency/terminal/booking-slot tests, NestJS test/build pass, Flyway clean apply, Spring/Nest live startup, RabbitMQ publish verification, and full auth/RBAC/ticket/booking/external middleware smoke pass via `scripts/live-smoke.sh`.
- Phase 4 reports and exports: scoped Spring report module, PostgreSQL aggregations, detailed paginated ticket/booking reports, export tracking and local storage, CSV/XLSX/PDF writers, audit hooks, personal-data masking, report permissions, and Zenoss report proxy routes.

Next phases should replace remaining disabled DB-concurrency/report skeletons with Testcontainers or integration fixtures, add RabbitMQ-backed SSE fanout across instances, add MinIO-backed signed export URLs if needed, and prepare production deployment overlays.
