# Phase 4 Reports, Analytics, And Export

## Architecture

Spring Boot remains the report source of truth. The `kg.equeue.backend.reports` module owns filter validation, RBAC/scope checks, PostgreSQL aggregation queries, export jobs, file writing, storage metadata, and audit events.

NestJS does not calculate reports. It only exposes Zenoss proxy routes under `/external/zenoss/reports/*`, validates query/body DTOs, forwards request/correlation headers and `X-Backend-Integration-Key`, and streams export downloads through the middleware.

## Endpoints

Spring:

- `GET /api/v1/reports/summary`
- `GET /api/v1/reports/by-region`
- `GET /api/v1/reports/by-department`
- `GET /api/v1/reports/by-employee`
- `GET /api/v1/reports/by-service`
- `GET /api/v1/reports/by-source`
- `GET /api/v1/reports/by-status`
- `GET /api/v1/reports/waiting-time`
- `GET /api/v1/reports/service-time`
- `GET /api/v1/reports/cancellations`
- `GET /api/v1/reports/no-shows`
- `GET /api/v1/reports/bookings`
- `GET /api/v1/reports/window-workload`
- `GET /api/v1/reports/workload/hourly`
- `GET /api/v1/reports/workload/daily`
- `GET /api/v1/reports/tickets`
- `GET /api/v1/reports/bookings/details`
- `GET /api/v1/reports/integrations`
- `POST /api/v1/reports/export`
- `GET /api/v1/reports/export/{id}`
- `GET /api/v1/reports/export/{id}/download`

Zenoss proxy:

- `GET /external/zenoss/reports/summary`
- `GET /external/zenoss/reports/by-department`
- `GET /external/zenoss/reports/by-service`
- `GET /external/zenoss/reports/bookings`
- `POST /external/zenoss/reports/export`
- `GET /external/zenoss/reports/export/:id`
- `GET /external/zenoss/reports/export/:id/download`

## Filters And Scope

`dateFrom` and `dateTo` are required. Optional filters cover region, department, employee, window, service category, service, source, ticket status, booking status, cancellation reason, grouping, pagination, and personal-data inclusion.

Limits:

- Standard reports: maximum 366 days.
- Detailed ticket/booking reports: maximum 93 days unless the caller has global report scope.
- Detail pagination defaults to `page=0&size=50`; maximum size is 500.

Scope:

- `SUPER_ADMIN`, `ADMIN`, and backend integration callers can see all departments.
- Scoped users are restricted to `user_department_scopes` plus active window assignments.
- If a scoped caller omits `departmentId`, the SQL is forced to the allowed department list.
- `includePersonalData=true` requires `REPORT_VIEW_PERSONAL_DATA`.
- Exporting personal data also requires `REPORT_EXPORT_PERSONAL_DATA`.

## SQL Aggregation Approach

`ReportQueryRepository` uses `NamedParameterJdbcTemplate` and PostgreSQL aggregations instead of loading tickets/bookings into Java. It uses filtered counts, grouped projections, `EXTRACT(EPOCH ...)` duration calculations, and `percentile_cont` for median/P90 where PostgreSQL can compute it directly.

V6 adds report indexes for common filters:

- tickets by department/date, status/date, source/date, service/date, served user/date
- bookings by department/date and status/date
- ticket events by ticket/date
- audit logs by creation time
- integration requests by client/date
- report exports by requester/date and status/date

## Export Pipeline

`POST /api/v1/reports/export` validates `REPORT_EXPORT`, validates filters/scope, creates a `report_exports` row, writes audit event `REPORT_EXPORT_REQUESTED`, generates the file, stores metadata, and writes completion/failure audit events.

Formats:

- CSV: UTF-8 with BOM, semicolon delimiter by default.
- XLSX: Apache POI streaming workbook.
- PDF: PDFBox simple table layout. Oversized PDF exports fail with `REPORT_TOO_LARGE_FOR_PDF`.

Config defaults:

- `app.reports.export.local-dir=./data/exports`
- `app.reports.export.csv-delimiter=;`
- `app.reports.export.csv-max-rows=500000`
- `app.reports.export.xlsx-max-rows=100000`
- `app.reports.export.pdf-max-rows=5000`
- `app.reports.export.expire-scan-ms=3600000`

Completed exports expire after seven days. `ReportExportScheduler` marks expired metadata; file cleanup can be added behind the storage abstraction.

## Storage

`ReportFileStorageService` currently stores files locally under `./data/exports` and streams downloads through Spring. It records `file_bucket=local` and a relative `file_key`.

MinIO can be added behind the same service by writing files to object storage and either streaming through Spring or returning short-lived signed URLs. Direct storage secrets must not be exposed.

## Audit

Audit actions:

- `REPORT_VIEWED`
- `REPORT_EXPORT_REQUESTED`
- `REPORT_EXPORT_COMPLETED`
- `REPORT_EXPORT_FAILED`
- `REPORT_EXPORT_DOWNLOADED`

Audit payloads include report type, format, export id, row count when known, and filter fields with no PII.

## Known Limits

- Aggregation integration tests are skeletons until Testcontainers or a shared PostgreSQL fixture is introduced.
- PDF output is intentionally simple and capped.
- Export processing currently runs synchronously after creating the export row; the job service boundary is ready for async queueing if large exports need background execution.
- Window idle/utilization fields are returned as nullable when reliable shift schedules are not available.
- Integration average response time is nullable because response duration is not stored yet.

## Future Improvements

- Materialized views for high-traffic dashboard summaries.
- Scheduled report generation.
- Dashboard cache keyed by scope/filter.
- MinIO signed downloads and retention cleanup.
- BI integration with read replicas or warehouse export.
- Testcontainers-backed aggregation fixtures for every report type.
