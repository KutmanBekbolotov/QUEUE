# Phase 3.5 Stability Report

Date: 2026-07-01

## Scope

Phase 3.5 focused on backend compile, migration readiness, integration wiring, live smoke coverage, and stability before adding reports or frontend work.

Checked areas:

- Root `docker-compose.yml`, `Makefile`, `.env.example`.
- Spring `pom.xml`, `application.yml`, Flyway migrations `V1` through `V5`.
- Spring packages for auth, users, roles, permissions, directories, tickets, bookings, booking slots, integration clients, audit, SSE, RabbitMQ, and common infrastructure.
- NestJS middleware source, tests, external auth, idempotency, backend client forwarding, and safe logging.
- Continuation run used Node `v24.16.0`, npm `11.13.0`, Java `25.0.3`, and Maven Wrapper `3.9.9`.

## Fixed Or Added

- Added Maven wrapper under `apps/backend-spring` so backend checks can run with `./mvnw`.
- Disabled Spring Data Redis repository auto-detection because the backend has Redis connectivity but no Redis repository interfaces; all current repositories are JPA repositories.
- Set explicit INFO logging defaults for Spring and ran live smoke with `DEBUG=false`; this avoids framework debug logging of auth DTOs/tokens when the host shell exports `DEBUG`.
- Tightened NestJS safe-log masking so header-shaped API keys, backend integration keys, and idempotency keys such as `x-api-key`, `X-Backend-Integration-Key`, and `IdempotencyKey` are masked if they reach logger metadata.
- Added a 405 handler for unsupported HTTP methods so method mistakes do not become generic 500s.
- Added a JSON RabbitMQ message converter so ticket and booking domain event record payloads publish successfully.
- Fixed external booking idempotency fallback so a business `externalId` no longer collides between create and cancel when explicit idempotency keys are supplied.
- Stopped NestJS from deriving idempotency keys from route params for mutating cancel routes; explicit `Idempotency-Key` or `X-External-Request-Id` is required there.
- Hardened SSE disconnect handling so event-stream responses are not passed to the generic JSON error writer.
- Added `.tools/phase35-live-smoke.sh`, a reusable live smoke script for auth, RBAC, tickets, terminal/TV, booking, RabbitMQ, and Nest external routes.

## Passed Commands

```bash
docker compose config
```

Result: passed.

```bash
docker compose up -d postgres redis rabbitmq
docker compose --profile tools run --rm flyway
```

Result: passed with Docker access. Flyway validated and applied `V1` through `V5` to a clean PostgreSQL 16.14 database.

```bash
cd apps/backend-spring
./mvnw clean test
```

Result: passed. Spring result after fixes: 31 tests run, 0 failures, 0 errors, 5 skipped.

Skipped Spring tests are still the DB-concurrency or fixture-backed skeletons:

- `CallNextConcurrencyTest`
- `TicketSequenceConcurrencyTest`
- `AvailableDatesRulesTest`
- `SlotGenerationRulesTest`
- `CreateBookingConcurrencyTest`

```bash
cd apps/middleware-nest
npm test
npm run build
npm audit --omit=dev
```

Result: passed. NestJS test result after fixes: 4 suites, 8 tests passed. Build passed. Audit result: 0 production vulnerabilities.

## Runtime Validation

Spring was started with PostgreSQL, Redis, and RabbitMQ reachable:

```bash
cd apps/backend-spring
DEBUG=false ./mvnw spring-boot:run
```

Result: passed. Spring started on port `8080`, Flyway validated schema version `5`, JPA validation completed, and health returned `UP`.

NestJS was started against Spring:

```bash
cd apps/middleware-nest
BACKEND_BASE_URL=http://localhost:8080 \
BACKEND_INTEGRATION_KEY=dev-backend-integration-key-change-me \
EXTERNAL_API_KEYS=dev-zenoss-key,dev-tunduk-key,dev-cabinet-key \
npm run start:dev
```

Result: passed. Middleware started on port `3000` and health returned `UP`.

## Live Smoke Result

```bash
./.tools/phase35-live-smoke.sh
```

Final result: passed.

Coverage from the final run:

- Spring and Nest health.
- Admin login, `/auth/me`, refresh rotation, old refresh rejection, logout, logged-out refresh rejection.
- Refresh token raw value not stored in `refresh_tokens`.
- Seeded directory reads.
- Department, room, hall, window, department-service setup.
- User create response excludes password hash.
- Blocked-user login denied.
- Auditor read allowed and mutation denied.
- Ticket number generation, call-next, duplicate call-next rejection, start, pause, resume, complete, cancel, no-show, invalid transition conflict.
- Ticket events, audit logs, and RabbitMQ ticket event queue publish.
- Terminal device config and department-bound ticket creation.
- TV snapshot and SSE connection.
- Booking slot generation, past-date exclusion, slot availability, create, replay, conflicting idempotency key, full-slot conflict, cancel decrement, check-in ticket creation, expire.
- Booking events, audit logs, and RabbitMQ booking event queue publish.
- Website Cabinet available dates, slots, create, status, cancel through Nest.
- Tunduk create, status, cancel through Nest.
- Zenoss ticket create/status and booking create/status through Nest.
- `X-Request-Id` forwarding from Nest to Spring audit logs.

Final smoke marker:

```text
LIVE_SMOKE_OK department=ff1434b3-2e6b-41c6-b930-de570898cdf5 service=97dad4a8-8b68-443c-b27c-a5c1a692e340 window=4e7ac59d-9b6f-4efd-b6f0-44eefe7cf9ba
```

## Remaining Risks

- The five skipped Spring tests should be replaced with Testcontainers-backed integration tests for PostgreSQL locking and date/slot fixtures.
- Queue and booking concurrency behavior was live-smoked serially; high-contention `FOR UPDATE SKIP LOCKED`, ticket sequence locking, and slot overbooking prevention still need automated concurrent integration tests.
- RabbitMQ publish was verified against one local broker; multi-instance SSE fanout and broker outage behavior still need production-style tests.

## Next Recommended Phase

1. Convert the remaining disabled DB-concurrency skeletons into Testcontainers tests.
2. Add broker-failure and retry/idempotency tests around RabbitMQ publishing.
3. Add production deployment overlays and observability configuration.
4. Proceed to reports/frontend only after those backend hardening tasks are scheduled.
