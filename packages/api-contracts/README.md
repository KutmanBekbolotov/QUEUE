# API Contracts

## Error Model

All services return:

```json
{
  "timestamp": "2026-07-01T00:00:00Z",
  "requestId": "uuid-or-client-supplied-id",
  "code": "ERROR_CODE",
  "message": "Human readable message",
  "details": {}
}
```

## Request Headers

- `X-Request-Id`: request-scoped id. Generated when absent.
- `X-Correlation-Id`: end-to-end correlation id. Defaults to request id.
- `Idempotency-Key`: required for mutating external requests.
- `X-External-Request-Id`: external system request id where available.
- `X-Integration-Client`: integration client code, for example `ZENOSS`, `TUNDUK`, `WEBSITE_CABINET`.

## Boundary Rules

External clients call NestJS `/external/*` APIs. NestJS validates and forwards normalized payloads to Spring `/api/v1/*` APIs. Queue and booking decisions are made only in Spring.

