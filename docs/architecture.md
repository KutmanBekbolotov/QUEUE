# Architecture

Spring Boot owns domain state, authorization, audit, and transactional business rules. PostgreSQL is the source of truth. Redis supports cache, rate limiting, live-update fanout support, and device/session state. RabbitMQ carries ticket and booking domain events.

NestJS is a middleware boundary for external systems: Zenoss, Tunduk, and Website Cabinet. It authenticates external clients, validates DTOs, normalizes requests, enforces idempotency headers, logs safely, and proxies to Spring.

Both services are stateless and horizontally scalable. Durable session state is stored in PostgreSQL and transient state is stored in Redis.

