# Документация интеграции со Spring Backend

Документ описывает интеграцию с `apps/backend-spring` как с source-of-truth API для авторизации, справочников, очереди, бронирования, отчётов, аудита и устройств.

## 1. Быстрый старт

### 1.1. Локальные адреса

| Назначение | URL |
| --- | --- |
| Spring Backend | `http://localhost:8080` |
| Spring API base | `http://localhost:8080/api/v1` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Nginx gateway | `http://localhost:8088` |
| RabbitMQ management | `http://localhost:15672` |

Если в `.env` изменён `BACKEND_PORT`, локальный порт Spring будет равен этому значению.

### 1.2. Запуск

```bash
make dev
```

Или напрямую:

```bash
docker compose up --build
```

Проверка:

```bash
curl http://localhost:8080/api/v1/health
```

Ожидаемый ответ:

```json
{
  "status": "UP",
  "service": "backend-spring",
  "timestamp": "2026-07-07T00:00:00Z"
}
```

### 1.3. Bootstrap admin

Flyway создаёт стартового пользователя:

| Поле | Значение |
| --- | --- |
| username | `admin` |
| password | `ZAQ!@#$%tgb*` |
| role | `SUPER_ADMIN` |

Пароль нужно сменить после первого входа.

## 2. Общие правила API

### 2.1. Формат данных

| Тип | Формат |
| --- | --- |
| UUID | строка UUID, например `7f1d0e22-2c21-4b61-9d87-9972f5b1d5df` |
| Date | ISO date: `YYYY-MM-DD` |
| Time | ISO local time: `HH:mm:ss` или `HH:mm` |
| Timestamp | ISO instant: `2026-07-07T10:30:00Z` |
| Content-Type | `application/json` для JSON request body |
| SSE | `text/event-stream` |

### 2.2. Request ID

Backend принимает и возвращает:

| Header | Назначение |
| --- | --- |
| `X-Request-Id` | ID конкретного запроса. Если не передан, backend создаст UUID |
| `X-Correlation-Id` | ID цепочки запросов. Если не передан, равен `X-Request-Id` |

Эти заголовки возвращаются в response и используются в ошибках.

### 2.3. Ошибки

Единый формат ошибки:

```json
{
  "timestamp": "2026-07-07T10:30:00Z",
  "requestId": "b24f0a9b-9477-4c0f-a772-5a5b6d6f8e3a",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": {
    "departmentId": "must not be null"
  }
}
```

Типовые HTTP-коды:

| HTTP | Когда |
| --- | --- |
| `400` | валидация, неверный enum, неверное состояние запроса |
| `401` | нет или невалидный access token/device token |
| `403` | нет permission или department/window scope |
| `404` | сущность не найдена |
| `409` | конфликт состояния, idempotency conflict, повторная операция |
| `500` | непредвиденная ошибка backend |

## 3. Авторизация

### 3.1. JWT для staff/admin/operator frontend

Большинство `/api/v1/**` endpoint'ов требуют:

```http
Authorization: Bearer <accessToken>
```

Access token выдаётся через `/api/v1/auth/login`, живёт `15m`. Refresh token живёт `14d`.

Login:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"ZAQ!@#$%tgb*"}'
```

Ответ:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresAt": "2026-07-07T10:45:00Z",
  "roles": ["SUPER_ADMIN"],
  "permissions": ["USER_READ", "TICKET_READ"]
}
```

Refresh:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

Logout:

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

Ответ logout: `204 No Content`.

Current user:

```bash
curl -s http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <accessToken>"
```

### 3.2. Internal integration auth для middleware

Spring также поддерживает внутреннюю авторизацию для middleware:

```http
X-Integration-Client: CRM_ZENOSS
X-Backend-Integration-Key: <BACKEND_INTEGRATION_KEY>
```

При валидной паре заголовков backend выдаёт request'у роль `ROLE_INTEGRATION_SERVICE` и permissions:

`REGION_READ`, `DEPARTMENT_READ`, `SERVICE_READ`, `BOOKING_READ`, `BOOKING_CREATE`, `BOOKING_CANCEL`, `BOOKING_CHECK_IN`, `BOOKING_SLOT_READ`, `TICKET_CREATE`, `TICKET_READ`.

Эта схема предназначена для server-to-server вызовов через middleware, а не для браузерного frontend.

### 3.3. Device auth для terminal/TV

Endpoint'ы terminal и TV открыты на уровне Spring Security, но внутри требуют device token:

```http
X-Device-Token: <raw-device-token>
```

Допускается fallback:

```http
Authorization: Bearer <raw-device-token>
```

Backend сравнивает SHA-256 hash токена с `token_hash` устройства.

## 4. Idempotency

Idempotency используется для integration-запросов создания tickets/bookings и внешней отмены bookings.

Заголовки:

| Header | Назначение |
| --- | --- |
| `Idempotency-Key` | главный ключ идемпотентности |
| `X-External-Request-Id` | внешний request id, fallback если нет `Idempotency-Key` |
| `X-Integration-Client` | код клиента, участвует в scope idempotency |

Правила:

- Для integration-запросов нужен `Idempotency-Key` или внешний `externalId`/`X-External-Request-Id`.
- Повтор с тем же ключом и тем же body возвращает сохранённый успешный response.
- Повтор с тем же ключом и другим body возвращает `409 IDEMPOTENCY_KEY_CONFLICT`.
- Пока первый запрос ещё обрабатывается, повтор может вернуть `409 REQUEST_ALREADY_PROCESSING`.
- Для staff/admin операций `source=ADMIN_CREATED` idempotency не принудительная.

## 5. Permissions и роли

Доступ проверяется через `@PreAuthorize`.

Системные роли:

| Role | Назначение |
| --- | --- |
| `SUPER_ADMIN` | все permissions |
| `ADMIN` | администрирование без полного супер-доступа |
| `DEPARTMENT_MANAGER` | управление отделением, окнами, слотами и отчётами в scope |
| `OPERATOR` | обслуживание tickets/bookings |
| `AUDITOR` | чтение аудита и отчётов |
| `INTEGRATION_SERVICE` | server-to-server интеграции |
| `TERMINAL_DEVICE` | терминальное устройство |
| `TV_DEVICE` | TV display |

`TERMINAL_DEVICE` и `TV_DEVICE` сохранены как legacy-коды RBAC, но не назначаются login-пользователям. Терминалы и ТВ проходят provisioning через `/api/v1/devices/*` и используют device token.

Scope:

- `SUPER_ADMIN`, `ADMIN`, `ROLE_INTEGRATION_SERVICE` видят все отделения.
- Остальные пользователи ограничены `user_department_scopes` или назначением на окно.
- Операторы могут использовать только назначенное окно, если не имеют admin/manager прав.

## 6. Auth API

Base: `/api/v1/auth`

| Method | Path | Auth | Body | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/login` | public | `LoginRequest` | `AuthResponse` |
| `POST` | `/refresh` | public | `RefreshRequest` | `AuthResponse` |
| `POST` | `/logout` | public | `LogoutRequest` | `204` |
| `GET` | `/me` | JWT | - | `MeResponse` |

DTO:

```ts
type LoginRequest = {
  username: string;
  password: string;
};

type RefreshRequest = {
  refreshToken: string;
};

type LogoutRequest = {
  refreshToken: string;
};

type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresAt: string;
  roles: string[];
  permissions: string[];
  id: string;
  username: string;
  fullName: string | null;
  email: string | null;
  phone: string | null;
  departmentId: string | null;
  windowId: string | null;
  serviceIds: string[];
  serviceCodes: string[];
  status: UserStatus;
};

type MeResponse = {
  id: string;
  username: string;
  fullName: string | null;
  email: string | null;
  phone: string | null;
  departmentId: string | null;
  windowId: string | null;
  serviceIds: string[];
  serviceCodes: string[];
  status: UserStatus;
  roles: string[];
  permissions: string[];
};
```

## 7. Users, roles, permissions

### 7.1. Users

Base: `/api/v1/users`

| Method | Path | Permission | Body | Response |
| --- | --- | --- | --- | --- |
| `GET` | `(base)` | `USER_READ` | - | `UserResponse[]` |
| `GET` | `/{id}` | `USER_READ` | - | `UserResponse` |
| `POST` | `(base)` | `USER_CREATE` | `CreateUserRequest` | `UserResponse` |
| `PUT/PATCH` | `/{id}` | `USER_UPDATE` | `UpdateUserRequest` | `UserResponse` |
| `DELETE` | `/{id}` | `USER_UPDATE` или `USER_BLOCK` | - | `204` |
| `PATCH` | `/{id}/status` | `USER_BLOCK` или `USER_UPDATE` | `UpdateUserStatusRequest` | `UserResponse` |
| `POST` | `/{id}/roles` | `USER_UPDATE` | `AssignUserRolesRequest` | `UserResponse` |

Operators:

| Method | Path | Permission | Response |
| --- | --- | --- | --- |
| `GET` | `/api/v1/operators` | `USER_READ` | `UserResponse[]` только операторы |
| `POST` | `/api/v1/operators/{operatorId}/shifts/open` | `WINDOW_OPEN` | `ShiftResponse` |
| `POST` | `/api/v1/operators/{operatorId}/shifts/current/close` | `WINDOW_CLOSE` | `ShiftResponse` |
| `GET` | `/api/v1/operators/{operatorId}/dashboard` | `TICKET_READ` или `USER_READ` | `OperatorDashboardResponse` |

DTO:

```ts
type CreateUserRequest = {
  username: string;
  password: string;
  fullName?: string;
  email?: string;
  phone?: string;
  departmentId?: string;
  roleCodes?: string[];
  windowId?: string;
  serviceIds?: string[];
};

type UpdateUserRequest = Partial<CreateUserRequest>;

type UpdateUserStatusRequest = {
  status: UserStatus;
};

type AssignUserRolesRequest = {
  roleCodes: string[];
};

type UserResponse = {
  id: string;
  username: string;
  fullName: string | null;
  email: string | null;
  phone: string | null;
  departmentId: string | null;
  windowId: string | null;
  serviceIds: string[];
  serviceCodes: string[];
  status: UserStatus;
  tokenVersion: number;
  roles: string[];
  createdAt: string;
  updatedAt: string;
};

type OpenShiftRequest = {
  departmentId?: string;
  windowId?: string;
};

type ShiftResponse = {
  id: string;
  operatorId: string;
  departmentId: string;
  windowId: string | null;
  status: 'OPEN' | 'CLOSED';
  openedAt: string;
  closedAt: string | null;
};

type OperatorDashboardResponse = {
  operatorId: string;
  shift: ShiftResponse | null;
  window: WindowResponse | null;
  activeTicket: TicketResponse | null;
  generatedAt: string;
};
```

Для пользователя с ролью `OPERATOR` поле `departmentId` обязательно. Создание пользователя или назначение роли без отделения возвращает `400 OPERATOR_DEPARTMENT_REQUIRED`. `windowId` и `serviceIds` сохраняются вместе с пользователем; элементы `serviceIds` могут быть UUID или кодами услуг (`VS`, `TS`). Для совместимости backend также принимает это поле под именами `services` и `serviceCodes`, а в ответах дублирует `serviceCodes` как `services`. При `PATCH` пропущенные назначения не меняются, пустой `windowId` очищает окно, а пустой `serviceIds` очищает услуги. Отдельные endpoints назначения окна и услуг продолжают работать.

Смена оператора хранится отдельно от состояния окна. `/shifts/open` открывает смену для оператора и по умолчанию берёт назначенное окно; `/windows/{id}/open` по-прежнему управляет физическим статусом окна. Dashboard атомарно возвращает текущую смену, окно и активный талон.

### 7.2. Roles and permissions

| Method | Path | Permission | Body | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/roles` | `ROLE_READ` | - | `RoleResponse[]` |
| `POST` | `/api/v1/roles` | `ROLE_CREATE` | `CreateRoleRequest` | `RoleResponse` |
| `PUT` | `/api/v1/roles/{id}/permissions` | `ROLE_ASSIGN_PERMISSION` | `AssignRolePermissionsRequest` | `RoleResponse` |
| `GET` | `/api/v1/permissions` | `ROLE_READ` | - | `PermissionResponse[]` |

```ts
type CreateRoleRequest = {
  code: string;
  name: string;
  permissionCodes?: string[];
};

type AssignRolePermissionsRequest = {
  permissionCodes: string[];
};

type RoleResponse = {
  id: string;
  code: string;
  name: string;
  systemRole: boolean;
  permissions: string[];
  createdAt: string;
  updatedAt: string;
};

type PermissionResponse = {
  id: string;
  code: string;
  description: string;
  createdAt: string;
};
```

## 8. Directories API

Base controller path: `/api/v1`.

### 8.1. Regions

| Method | Path | Permission | Body | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/regions` | public read или `REGION_READ` | - | `RegionResponse[]` |
| `POST` | `/regions` | `REGION_CREATE` | `RegionRequest` | `RegionResponse` |
| `GET` | `/regions/{id}` | `REGION_READ` | - | `RegionResponse` |
| `PUT/PATCH` | `/regions/{id}` | `REGION_UPDATE` | `RegionRequest` | `RegionResponse` |
| `PATCH` | `/regions/{id}/status` | `REGION_UPDATE` | `ActiveStatusRequest` | `RegionResponse` |

Публичный `GET /regions` без Bearer-токена возвращает только активные регионы.

```ts
type RegionRequest = {
  code: string;
  name: string;
};

type RegionResponse = {
  id: string;
  code: string;
  name: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};
```

### 8.2. Departments

| Method | Path | Permission | Body | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/departments` | public read или `DEPARTMENT_READ` | - | `DepartmentResponse[]` |
| `POST` | `/departments` | `DEPARTMENT_CREATE` | `DepartmentRequest` | `DepartmentResponse` |
| `GET` | `/departments/{id}` | `DEPARTMENT_READ` | - | `DepartmentResponse` |
| `PUT/PATCH` | `/departments/{id}` | `DEPARTMENT_UPDATE` | `DepartmentRequest` | `DepartmentResponse` |
| `PATCH` | `/departments/{id}/status` | `DEPARTMENT_UPDATE` или `DEPARTMENT_CLOSE` | `DepartmentStatusRequest` | `DepartmentResponse` |
| `DELETE` | `/departments/{id}` | `DEPARTMENT_UPDATE` или `DEPARTMENT_CLOSE` | - | `204` |

Публичный `GET /departments` без Bearer-токена возвращает только активные и не закрытые подразделения.

```ts
type DepartmentRequest = {
  regionId: string;
  code: string;
  name: string;
  address?: string;
  timezone?: string;
};

type DepartmentStatusRequest = {
  active: boolean;
  closed?: boolean;
};

type DepartmentResponse = {
  id: string;
  regionId: string;
  code: string;
  name: string;
  address: string | null;
  timezone: string | null;
  active: boolean;
  closed: boolean;
  createdAt: string;
  updatedAt: string;
};
```

### 8.3. Rooms, halls, windows

| Method | Path | Permission | Body | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/departments/{departmentId}/rooms` | `DEPARTMENT_READ` | - | `OfficeRoomResponse[]` |
| `POST` | `/departments/{departmentId}/rooms` | `DEPARTMENT_UPDATE` | `OfficeRoomRequest` | `OfficeRoomResponse` |
| `PUT/PATCH` | `/rooms/{id}` | `DEPARTMENT_UPDATE` | `OfficeRoomRequest` | `OfficeRoomResponse` |
| `GET` | `/departments/{departmentId}/halls` | `DEPARTMENT_READ` | - | `HallResponse[]` |
| `POST` | `/departments/{departmentId}/halls` | `DEPARTMENT_UPDATE` | `HallRequest` | `HallResponse` |
| `PUT/PATCH` | `/halls/{id}` | `DEPARTMENT_UPDATE` | `HallRequest` | `HallResponse` |
| `DELETE` | `/halls/{id}` | `DEPARTMENT_UPDATE` | - | `204` |
| `GET` | `/departments/{departmentId}/windows` | `WINDOW_READ` | - | `WindowResponse[]` |
| `GET` | `/service-windows` | `WINDOW_READ` | - | `WindowResponse[]` |
| `POST` | `/departments/{departmentId}/windows` | `WINDOW_CREATE` | `WindowRequest` | `WindowResponse` |
| `GET` | `/windows/{id}` | `WINDOW_READ` | - | `WindowResponse` |
| `PUT/PATCH` | `/windows/{id}` | `WINDOW_UPDATE` | `WindowRequest` | `WindowResponse` |
| `PATCH` | `/windows/{id}/status` | `WINDOW_UPDATE` | `WindowStatusRequest` | `WindowResponse` |
| `DELETE` | `/windows/{id}` | `WINDOW_UPDATE` | - | `204` |
| `POST` | `/windows/{id}/assign-employee` | `WINDOW_ASSIGN_EMPLOYEE` | `AssignEmployeeToWindowRequest` | `WindowResponse` |
| `POST` | `/windows/{id}/open` | `WINDOW_OPEN` | - | `WindowResponse` |
| `POST` | `/windows/{id}/close` | `WINDOW_CLOSE` | - | `WindowResponse` |

```ts
type OfficeRoomRequest = {
  code: string;
  name: string;
  floor?: string;
};

type HallRequest = {
  officeRoomId?: string;
  code: string;
  name: string;
};

type WindowRequest = {
  hallId?: string;
  code: string;
  displayName: string;
};

type WindowStatusRequest = {
  status: WindowStatus;
};

type AssignEmployeeToWindowRequest = {
  employeeId: string;
};

type WindowResponse = {
  id: string;
  departmentId: string;
  hallId: string | null;
  code: string;
  displayName: string;
  active: boolean;
  open: boolean;
  status: WindowStatus;
  createdAt: string;
  updatedAt: string;
};
```

### 8.4. Service categories, services, assignments

| Method | Path | Permission | Body | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/service-categories` | public read или `SERVICE_READ` | - | `ServiceCategoryResponse[]` |
| `POST` | `/service-categories` | `SERVICE_CREATE` | `ServiceCategoryRequest` | `ServiceCategoryResponse` |
| `GET` | `/service-categories/{id}` | `SERVICE_READ` | - | `ServiceCategoryResponse` |
| `PUT/PATCH` | `/service-categories/{id}` | `SERVICE_UPDATE` | `ServiceCategoryRequest` | `ServiceCategoryResponse` |
| `GET` | `/services` | `SERVICE_READ` | - | `ServiceResponse[]` |
| `POST` | `/services` | `SERVICE_CREATE` | `ServiceRequest` | `ServiceResponse` |
| `GET` | `/services/{id}` | `SERVICE_READ` | - | `ServiceResponse` |
| `PUT/PATCH` | `/services/{id}` | `SERVICE_UPDATE` | `ServiceRequest` | `ServiceResponse` |
| `PATCH` | `/services/{id}/status` | `SERVICE_UPDATE` | `ActiveStatusRequest` | `ServiceResponse` |
| `DELETE` | `/services/{id}` | `SERVICE_UPDATE` | - | `204` |
| `GET` | `/departments/{departmentId}/services` | public read или `SERVICE_READ` | - | `DepartmentServiceResponse[]` |
| `POST` | `/departments/{departmentId}/services/{serviceId}` | `SERVICE_ASSIGN_TO_DEPARTMENT` | `DepartmentServiceRequest?` | `DepartmentServiceResponse` |
| `DELETE` | `/departments/{departmentId}/services/{serviceId}` | `SERVICE_ASSIGN_TO_DEPARTMENT` | - | `204` |
| `POST` | `/employees/{employeeId}/services/{serviceId}` | `SERVICE_ASSIGN_TO_EMPLOYEE` | `AssignEmployeeServiceRequest` | `204` |
| `DELETE` | `/employees/{employeeId}/services/{serviceId}?departmentId={departmentId}` | `SERVICE_ASSIGN_TO_EMPLOYEE` | - | `204` |

Публичные `GET /service-categories` и `GET /departments/{departmentId}/services` без Bearer-токена возвращают только активные категории, активные услуги и активные связи department-service.

```ts
type ServiceCategoryRequest = {
  code: string;
  name: string;
  ticketPrefix: string;
};

type ServiceRequest = {
  categoryId: string;
  code: string;
  name: string;
  description?: string;
  defaultDurationMinutes?: number;
  dailyLimit?: number;
};

type DepartmentServiceRequest = {
  onlineBookingEnabled?: boolean;
  terminalEnabled?: boolean;
  qrEnabled?: boolean;
  dailyLimit?: number;
};

type AssignEmployeeServiceRequest = {
  departmentId: string;
};
```

## 9. Tickets API

Base: `/api/v1/tickets`

### 9.1. Endpoints

| Method | Path | Permission | Body/query | Response |
| --- | --- | --- | --- | --- |
| `POST` | `(base)` | `TICKET_CREATE` | `CreateTicketRequest` | `TicketResponse` |
| `GET` | `(base)?departmentId={id}` | `TICKET_READ` | optional `departmentId` | `TicketResponse[]` |
| `GET` | `/{id}` | `TICKET_READ` | - | `TicketResponse` |
| `POST` | `/{id}/call` | `TICKET_CALL` | `CallTicketRequest` | `TicketResponse` |
| `POST` | `/{id}/recall` | `TICKET_CALL` | - | `TicketResponse` |
| `POST` | `/call-next` | `TICKET_CALL` | `CallNextTicketRequest` | `TicketResponse` |
| `POST` | `/{id}/start` | `TICKET_START` | - | `TicketResponse` |
| `POST` | `/{id}/pause` | `TICKET_PAUSE` | `PauseTicketRequest?` | `TicketResponse` |
| `POST` | `/{id}/resume` | `TICKET_RESUME` | - | `TicketResponse` |
| `POST` | `/{id}/complete` | `TICKET_COMPLETE` | - | `TicketResponse` |
| `POST` | `/{id}/cancel` | `TICKET_CANCEL` | `CancelTicketRequest` | `TicketResponse` |
| `POST` | `/{id}/no-show` | `TICKET_NO_SHOW` | - | `TicketResponse` |
| `POST` | `/{id}/transfer` | `TICKET_TRANSFER` | `TransferTicketRequest` | `TicketResponse` |

Для scoped users `GET /tickets` без `departmentId` вернёт `403 DEPARTMENT_REQUIRED`. Admin/Super Admin могут читать все.

### 9.2. DTO

```ts
type CreateTicketRequest = {
  departmentId: string;
  serviceId: string;
  bookingId?: string;
  citizenFullName?: string;
  citizenPin?: string;
  citizenPhone?: string;
  source: TicketSource;
  comment?: string;
  externalId?: string;
  idempotencyKey?: string;
};

type CallTicketRequest = {
  windowId: string;
};

type CallNextTicketRequest = {
  departmentId: string;
  windowId: string;
  serviceIds: string[];
};

type PauseTicketRequest = {
  pauseReasonId?: string;
  comment?: string;
};

type CancelTicketRequest = {
  cancellationReasonId?: string;
  comment?: string;
};

type TransferTicketRequest = {
  targetDepartmentId: string;
  targetServiceId: string;
  targetWindowId?: string;
  comment?: string;
};

type TicketResponse = {
  id: string;
  ticketNumber: string;
  ticketPrefix: string;
  sequenceNumber: number;
  workDate: string;
  regionId: string;
  departmentId: string;
  officeRoomId: string | null;
  hallId: string | null;
  windowId: string | null;
  categoryId: string;
  serviceId: string;
  citizenFullName: string | null;
  citizenPin: string | null;
  citizenPhone: string | null;
  source: TicketSource;
  status: TicketStatus;
  createdAt: string;
  calledAt: string | null;
  recalledAt: string | null;
  recallCount: number;
  serviceStartedAt: string | null;
  servicePausedAt: string | null;
  serviceCompletedAt: string | null;
  cancelledAt: string | null;
  cancellationReasonId: string | null;
  pauseReasonId: string | null;
  servedByUserId: string | null;
  operatorId: string | null;
  serviceWindowId: string | null;
  windowNumber: string | null;
  serviceName: LocalizedName | null;
  comment: string | null;
  version: number;
};
```

### 9.3. Ticket lifecycle

Allowed transitions:

| From | To |
| --- | --- |
| `WAITING` | `CALLED`, `CANCELLED`, `TRANSFERRED` |
| `CALLED` | `IN_SERVICE`, `CANCELLED`, `NO_SHOW`, `TRANSFERRED` |
| `IN_SERVICE` | `PAUSED`, `COMPLETED`, `CANCELLED` |
| `PAUSED` | `IN_SERVICE`, `CANCELLED` |

Important rules:

- `call` and `call-next` требуют открытое окно (`WindowStatus.OPEN`) в том же department.
- `call-next` выбирает первый `WAITING` ticket по `priority DESC, created_at ASC`.
- Для одного окна и одного оператора одновременно разрешён только один активный ticket в статусе `CALLED`, `IN_SERVICE` или `PAUSED`.
- Если `call-next` вызван повторно, когда у оператора или окна уже есть активный ticket, backend возвращает текущий активный `TicketResponse` без ошибки. Конфликт `409 OPERATOR_HAS_ACTIVE_TICKET` остается защитой для прямого `call` и гонок на уровне переходов.
- `recall` и повторный `call` для уже вызванного ticket не меняют статус, обновляют `calledAt`/`recalledAt`, увеличивают `recallCount` и публикуют `ticket.recalled`.
- `cancel` требует `cancellationReasonId` или `comment`.
- `pauseReasonId` и `cancellationReasonId` проверяются на существование.
- `transfer` переводит текущий ticket в `TRANSFERRED`; новый ticket в target department не создаётся.

### 9.4. Example: call-next flow

```bash
curl -s -X POST http://localhost:8080/api/v1/tickets/call-next \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "departmentId": "<departmentId>",
    "windowId": "<windowId>",
    "serviceIds": ["<serviceId>"]
  }'
```

## 10. Booking API

Base: `/api/v1/booking`

### 10.1. Endpoints

| Method | Path | Permission | Body/query | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/available-dates` | `BOOKING_SLOT_READ` или `BOOKING_READ` | query | `AvailableDatesResponse` |
| `GET` | `/slots` | `BOOKING_SLOT_READ` или `BOOKING_READ` | query | `SlotResponse[]` |
| `POST` | `(base)` | `BOOKING_CREATE` | `CreateBookingRequest` | `BookingResponse` |
| `GET` | `/{id}` | `BOOKING_READ` | - | `BookingResponse` |
| `GET` | `/by-token/{qrToken}` | `BOOKING_READ` | - | `BookingResponse` |
| `GET` | `/external/{source}/{externalId}` | `BOOKING_READ` | - | `BookingResponse` |
| `POST` | `/{id}/cancel` | `BOOKING_CANCEL` | `CancelBookingRequest?` | `BookingResponse` |
| `POST` | `/external/{source}/{externalId}/cancel` | `BOOKING_CANCEL` | `CancelBookingRequest?` | `BookingResponse` |
| `POST` | `/{id}/check-in` | `BOOKING_CHECK_IN` | - | `BookingResponse` |
| `POST` | `/{id}/expire` | `BOOKING_CANCEL` | - | `BookingResponse` |
| `POST` | `/slots/generate` | `BOOKING_SLOT_MANAGE` | `GenerateSlotsRequest` | `GenerateSlotsResponse` |
| `POST` | `/slots/{id}/disable` | `BOOKING_SLOT_MANAGE` | - | `SlotResponse` |
| `POST` | `/slots/{id}/enable` | `BOOKING_SLOT_MANAGE` | - | `SlotResponse` |

Query для available dates:

```text
departmentId=<uuid>&serviceId=<uuid>&fromDate=2026-07-07&toDate=2026-08-07&source=WEBSITE_CABINET
```

`regionId` принимается controller'ом, но текущая service-логика его не использует.

Query для slots:

```text
departmentId=<uuid>&serviceId=<uuid>&date=2026-07-07&source=WEBSITE_CABINET
```

### 10.2. DTO

```ts
type AvailableDatesResponse = {
  departmentId: string;
  serviceId: string;
  dates: string[];
};

type SlotResponse = {
  id: string;
  departmentId: string;
  serviceId: string;
  date: string;
  start: string;
  end: string;
  capacity: number;
  bookedCount: number;
  remaining: number;
  status: BookingSlotStatus;
};

type CreateBookingRequest = {
  departmentId: string;
  serviceId: string;
  slotId: string;
  citizenFullName?: string;
  citizenPin?: string;
  citizenPhone?: string;
  vehicleNumber?: string;
  source: BookingSource;
  externalId?: string;
  idempotencyKey?: string;
};

type CancelBookingRequest = {
  cancellationReasonId?: string;
  comment?: string;
  externalId?: string;
  idempotencyKey?: string;
};

type GenerateSlotsRequest = {
  departmentId: string;
  serviceId: string;
  fromDate: string;
  toDate: string;
  intervalMinutes: number;
  capacity: number;
  overwrite: boolean;
};

type GenerateSlotsResponse = {
  created: number;
  skipped: number;
  disabled: number;
};

type BookingResponse = {
  id: string;
  bookingNumber: string;
  status: BookingStatus;
  departmentId: string;
  serviceId: string;
  slotId: string;
  bookingDate: string;
  bookingStart: string;
  bookingEnd: string;
  source: BookingSource;
  externalId: string | null;
  qrToken: string;
  ticketId: string | null;
  ticketNumber: string | null;
  ticketStatus: TicketStatus | null;
  createdAt: string;
  updatedAt: string;
  cancelledAt: string | null;
  checkedInAt: string | null;
  expiredAt: string | null;
};
```

### 10.3. Booking rules

- `available-dates` и `slots` скрывают прошедшие слоты, праздники, disabled/full слоты.
- Если `fromDate` не передан или в прошлом, используется текущая дата.
- Если `toDate` не передан, используется `from + 30 days`.
- `generateSlots` использует рабочие часы department из `department_working_hours`.
- Если рабочих часов нет, fallback для будней: `09:00-18:00`, break `13:00-14:00`.
- Выходные без рабочих часов пропускаются.
- `overwrite=true` удаляет будущие пустые слоты с таким же стартом перед созданием новых.
- `create` создаёт booking со статусом `CONFIRMED`.
- `check-in` разрешён только для `CONFIRMED` booking в окне времени `slotStart - 30m` до `slotStart + 15m`.
- `check-in` создаёт связанный ticket и переводит booking в `CHECKED_IN`.
- Фоновая job истекает старые `CONFIRMED` bookings после `slotStart + 15m`.

### 10.4. Example: booking flow

```bash
curl -s "http://localhost:8080/api/v1/booking/available-dates?departmentId=<departmentId>&serviceId=<serviceId>&source=WEBSITE_CABINET" \
  -H "Authorization: Bearer <accessToken>"

curl -s "http://localhost:8080/api/v1/booking/slots?departmentId=<departmentId>&serviceId=<serviceId>&date=2026-07-07&source=WEBSITE_CABINET" \
  -H "Authorization: Bearer <accessToken>"

curl -s -X POST http://localhost:8080/api/v1/booking \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: booking-123" \
  -d '{
    "departmentId": "<departmentId>",
    "serviceId": "<serviceId>",
    "slotId": "<slotId>",
    "citizenFullName": "Test User",
    "citizenPhone": "+996700000000",
    "source": "WEBSITE_CABINET",
    "externalId": "web-123"
  }'
```

## 11. Terminal and TV devices

### 11.1. Terminal

Base: `/api/v1/terminal/{terminalId}`

| Method | Path | Auth | Body | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/config` | device token | - | `TerminalConfigResponse` |
| `POST` | `/tickets` | device token | `TerminalCreateTicketRequest` | `TicketResponse` |

```ts
type TerminalConfigResponse = {
  terminalId: string;
  departmentId: string;
  code: string;
  name: string;
  serviceIds: string[];
  services: TerminalConfigService[];
  categories: TerminalConfigCategory[];
};

type LocalizedName = {
  ru: string;
  ky: string;
};

type TerminalConfigService = {
  id: string;
  code: string;
  name: LocalizedName;
  categoryId: string;
  categoryCode: string;
  type: "VS";
};

type TerminalConfigCategory = {
  id: string;
  code: string;
  type: "VS";
  name: LocalizedName;
};

type TerminalCreateTicketRequest = {
  departmentId: string;
  serviceId: string;
  citizenFullName?: string;
  citizenPin?: string;
  citizenPhone?: string;
  comment?: string;
};
```

`services` содержит детали услуг из `serviceIds`; `categories` содержит категории этих услуг. Backend возвращает активные услуги подразделения, у которых включён `terminalEnabled`, а также активна сама услуга и её категория. `type: "VS"` — legacy-тип терминального элемента, не код категории; для услуг ТС фильтровать/группировать нужно по `categoryCode === "TS"` или по `categories[].code`. Пока локализация хранится одной строкой, поэтому `name.ru` и `name.ky` заполняются одинаковым значением. Terminal ticket source всегда нормализуется в `TERMINAL`. Terminal не может создать ticket для другого department.

### 11.2. QR self-service

Base: `/api/v1/qr`

| Method | Path | Auth | Body | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/departments/{departmentId}/config` | public | - | `QrConfigResponse` |
| `GET` | `/tickets/{ticketId}` | public | - | `TicketResponse` |
| `POST` | `/tickets` | public | `QrCreateTicketRequest` | `TicketResponse` |

```ts
type QrConfigResponse = {
  departmentId: string;
  departmentCode: string;
  departmentName: string;
  services: QrConfigService[];
  categories: QrConfigCategory[];
};

type QrConfigService = {
  id: string;
  code: string;
  name: LocalizedName;
  categoryId: string;
  categoryCode: string;
};

type QrConfigCategory = {
  id: string;
  code: string;
  name: LocalizedName;
};

type QrCreateTicketRequest = {
  departmentId: string;
  serviceId: string;
  citizenFullName?: string;
  citizenPhone?: string;
  comment?: string;
};
```

QR config возвращает только активные услуги подразделения, у которых включён `qrEnabled`, а также активна сама услуга и категория. `POST /tickets` создаёт талон с `source = QR_SELF_SERVICE`; backend дополнительно проверяет `qrEnabled` перед созданием. ПИН в QR-сценарии не принимается и не хранится: `citizenPin` для QR-талона всегда `null`. Backend не блокирует повторную выдачу по номеру телефона; фронт должен сохранить `ticketId` созданного QR-талона на устройстве пользователя и показывать текущий талон после обновления страницы. `GET /tickets/{ticketId}` публично возвращает только талоны с `source = QR_SELF_SERVICE`; для отсутствующего или не-QR талона возвращается `404 QR_TICKET_NOT_FOUND`.

### 11.3. TV Display

Base: `/api/v1/tv/displays/{tvDisplayId}`

| Method | Path | Auth | Response |
| --- | --- | --- | --- |
| `GET` | `/snapshot` | device token | `TvSnapshotResponse` |
| `GET` | `/stream` | device token | SSE |

```ts
type TvSnapshotResponse = {
  departmentId: string;
  tickets: TicketResponse[];
  generatedAt: string;
};
```

Snapshot возвращает до 20 последних tickets со статусами `CALLED`, `IN_SERVICE` и `PAUSED`. Для TV snapshot backend заполняет `windowId`, `serviceWindowId`, `operatorId`, `windowNumber` и `serviceName`; `serviceName` имеет форму `{ ru, ky }`.

### 11.4. Device registry

| Method | Path | Permission | Response |
| --- | --- | --- | --- |
| `GET` | `/api/v1/devices` | `TERMINAL_READ` или `TV_READ` | `DeviceResponse[]` |
| `POST` | `/api/v1/devices/terminals` | `TERMINAL_CREATE` | `ProvisionedDeviceResponse` |
| `PATCH` | `/api/v1/devices/terminals/{id}` | `TERMINAL_UPDATE` | `DeviceResponse` |
| `POST` | `/api/v1/devices/terminals/{id}/rotate-token` | `TERMINAL_CONFIGURE` | `ProvisionedDeviceResponse` |
| `POST` | `/api/v1/devices/tv-displays` | `TV_CREATE` | `ProvisionedDeviceResponse` |
| `PATCH` | `/api/v1/devices/tv-displays/{id}` | `TV_UPDATE` | `DeviceResponse` |
| `POST` | `/api/v1/devices/tv-displays/{id}/rotate-token` | `TV_CONFIGURE` | `ProvisionedDeviceResponse` |

```ts
type DeviceResponse = {
  id: string;
  type: "TERMINAL" | "TV_DISPLAY";
  departmentId: string;
  hallId: string | null;
  code: string;
  name: string;
  active: boolean;
  lastSeenAt: string | null;
};

type ProvisionedDeviceResponse = {
  device: DeviceResponse;
  deviceToken: string; // возвращается только при создании/ротации
};
```

## 12. SSE streams

### 12.1. Operator stream

```http
GET /api/v1/operator/{windowId}/stream
Authorization: Bearer <accessToken>
Accept: text/event-stream
```

Permission: `TICKET_READ`.

Backend проверяет доступ к окну. При подключении приходит событие:

```text
event: connected
data: {"windowId":"<windowId>","departmentId":"<departmentId>"}
```

Далее публикуются ticket domain events для этого окна и для очереди всего подразделения. Поэтому operator stream получает события ожидающей очереди без `windowId`, например `ticket.created`, а также события вызова/обслуживания с `windowId`.

### 12.2. TV stream

```http
GET /api/v1/tv/displays/{tvDisplayId}/stream
X-Device-Token: <raw-device-token>
Accept: text/event-stream
```

Приходит `connected`, затем ticket events по department.

### 12.3. Ticket event payload

```ts
type TicketDomainEvent = {
  eventId: string;
  eventType: string;
  ticketId: string;
  ticketNumber: string;
  departmentId: string;
  windowId: string | null;
  operatorId: string | null;
  serviceId: string;
  status: TicketStatus;
  occurredAt: string;
  timestamp: string;
};
```

Event types:

`ticket.created`, `ticket.called`, `ticket.recalled`, `ticket.started`, `ticket.paused`, `ticket.resumed`, `ticket.completed`, `ticket.cancelled`, `ticket.no_show`, `ticket.transferred`.

SSE отправляет canonical event name с точкой и совместимый alias с подчёркиванием (`ticket.called` и `ticket_called`). Для паузы дополнительно отправляется alias `service_paused`.

## 13. Reports API

Base: `/api/v1/reports`

### 13.1. Common filter

Все GET отчёты принимают query params:

| Param | Required | Описание |
| --- | --- | --- |
| `dateFrom` | yes | начало периода, `YYYY-MM-DD` |
| `dateTo` | yes | конец периода, `YYYY-MM-DD` |
| `regionId` | no | фильтр региона |
| `departmentId` | no | фильтр отделения |
| `employeeId` | no | фильтр сотрудника |
| `windowId` | no | фильтр окна |
| `serviceCategoryId` | no | фильтр категории |
| `serviceId` | no | фильтр услуги |
| `source` | no | ticket или booking source |
| `ticketStatus` | no | статус ticket |
| `bookingStatus` | no | статус booking |
| `cancellationReasonId` | no | причина отмены |
| `groupBy` | no | дополнительная группировка |
| `includePersonalData` | no | `true/false`, требует специальных прав |
| `page` | no | default `0` |
| `size` | no | default `50`, max `500` |

Ограничения:

- `dateFrom <= dateTo`.
- Стандартные отчёты: максимум `366` дней.
- Детальные отчёты для не-admin: максимум `93` дня.
- `includePersonalData=true` требует permission на просмотр персональных данных в report permission service.

### 13.2. Report endpoints

Все endpoints ниже требуют `REPORT_READ`.

| Method | Path | Response |
| --- | --- | --- |
| `GET` | `/summary` | `SummaryResponse` |
| `GET` | `/by-region` | `ByRegionRow[]` |
| `GET` | `/by-department` | `ByDepartmentRow[]` |
| `GET` | `/by-employee` | `ByEmployeeRow[]` |
| `GET` | `/by-service` | `ByServiceRow[]` |
| `GET` | `/by-source` | `BySourceRow[]` |
| `GET` | `/by-status` | `ByStatusRow[]` |
| `GET` | `/waiting-time` | `WaitingTimeResponse` |
| `GET` | `/service-time` | `ServiceTimeResponse` |
| `GET` | `/cancellations` | `CancellationsResponse` |
| `GET` | `/no-shows` | `NoShowsResponse` |
| `GET` | `/bookings` | `BookingsResponse` |
| `GET` | `/window-workload` | `WindowWorkloadRow[]` |
| `GET` | `/workload/hourly` | `WorkloadHourlyRow[]` |
| `GET` | `/workload/daily` | `WorkloadDailyRow[]` |
| `GET` | `/tickets` | `PageResponse<TicketDetailRow>` |
| `GET` | `/bookings/details` | `PageResponse<BookingDetailRow>` |
| `GET` | `/integrations` | `IntegrationReportRow[]` |

Example:

```bash
curl -s "http://localhost:8080/api/v1/reports/summary?dateFrom=2026-07-01&dateTo=2026-07-31" \
  -H "Authorization: Bearer <accessToken>"
```

### 13.3. Report export

| Method | Path | Permission | Body | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/export` | `REPORT_EXPORT` | `ExportRequest` | `ExportResponse` |
| `GET` | `/export/{id}` | `REPORT_READ` | - | `ExportResponse` |
| `GET` | `/export/{id}/download` | `REPORT_READ` | - | file |

```ts
type ExportRequest = {
  reportType: ReportType;
  format: ReportExportFormat;
  filters: ReportFilter;
};

type ExportResponse = {
  id: string;
  reportType: ReportType;
  format: ReportExportFormat;
  status: ReportExportStatus;
  fileName: string | null;
  fileSizeBytes: number | null;
  createdAt: string;
  completedAt: string | null;
  downloadUrl: string | null;
  errorMessage: string | null;
};
```

Export formats: `CSV`, `XLSX`, `PDF`.

Export statuses: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `EXPIRED`.

## 14. Audit API

| Method | Path | Permission | Query | Response |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/audit-logs` | `AUDIT_READ` | `limit`, default `50` | `AuditLogResponse[]` |

```ts
type AuditLogResponse = {
  id: string;
  actorType: string;
  actorId: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  ip: string | null;
  source: string | null;
  requestId: string | null;
  createdAt: string;
};
```

## 15. RabbitMQ events

Spring публикует domain events в RabbitMQ.

| Entity | Exchange | Queue | Routing key |
| --- | --- | --- | --- |
| tickets | `equeue.domain-events` | `equeue.ticket-events` | `ticket.*` |
| bookings | `equeue.domain-events` | `equeue.booking-events` | `booking.*` |

Booking event payload:

```ts
type BookingDomainEvent = {
  eventId: string;
  eventType: string;
  bookingId: string;
  bookingNumber: string;
  ticketId: string | null;
  departmentId: string;
  serviceId: string;
  slotId: string;
  source: BookingSource;
  externalId: string | null;
  status: BookingStatus;
  occurredAt: string;
};
```

Booking event types:

`booking.created`, `booking.cancelled`, `booking.checked_in`, `booking.expired`.

## 16. Enums

```ts
type UserStatus = "ACTIVE" | "BLOCKED" | "DISABLED";

type WindowStatus = "OPEN" | "CLOSED" | "INACTIVE";

type TicketStatus =
  | "CREATED"
  | "WAITING"
  | "CALLED"
  | "IN_SERVICE"
  | "PAUSED"
  | "COMPLETED"
  | "CANCELLED"
  | "NO_SHOW"
  | "EXPIRED"
  | "TRANSFERRED";

type TicketSource =
  | "TERMINAL"
  | "QR_SELF_SERVICE"
  | "WEBSITE_CABINET"
  | "TUNDUK"
  | "CRM"
  | "CRM_ZENOSS"
  | "ADMIN_CREATED";

type BookingSource =
  | "WEBSITE_CABINET"
  | "TUNDUK"
  | "CRM"
  | "CRM_ZENOSS"
  | "ADMIN_CREATED";

type BookingStatus =
  | "CREATED"
  | "CONFIRMED"
  | "CHECKED_IN"
  | "CANCELLED"
  | "EXPIRED"
  | "NO_SHOW";

type BookingSlotStatus = "ACTIVE" | "DISABLED" | "FULL" | "EXPIRED";

type ReportType =
  | "SUMMARY"
  | "BY_REGION"
  | "BY_DEPARTMENT"
  | "BY_EMPLOYEE"
  | "BY_SERVICE"
  | "BY_SOURCE"
  | "BY_STATUS"
  | "WAITING_TIME"
  | "SERVICE_TIME"
  | "CANCELLATIONS"
  | "NO_SHOWS"
  | "BOOKINGS"
  | "WINDOW_WORKLOAD"
  | "WORKLOAD_HOURLY"
  | "WORKLOAD_DAILY"
  | "TICKETS_DETAIL"
  | "BOOKINGS_DETAIL"
  | "INTEGRATIONS";

type ReportExportFormat = "CSV" | "XLSX" | "PDF";

type ReportExportStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED" | "EXPIRED";
```

## 17. Recommended frontend flows

### 17.1. Staff/admin frontend

1. `POST /api/v1/auth/login`.
2. Store access token in memory or secure app state.
3. Use refresh token according to frontend security policy; do not log tokens.
4. On `401`, call `/auth/refresh`; if refresh fails, send user to login.
5. Use `GET /auth/me` to build permission-based navigation.
6. Hide actions by permissions, but still handle backend `403`.

### 17.2. Operator flow

1. Login.
2. Load `/api/v1/auth/me`.
3. Load assigned windows/services via directories endpoints.
4. Open window: `POST /api/v1/windows/{id}/open`.
5. Connect SSE: `GET /api/v1/operator/{windowId}/stream`.
6. Call next: `POST /api/v1/tickets/call-next`.
7. Start service: `POST /api/v1/tickets/{id}/start`.
8. Complete/cancel/pause/resume/no-show according to lifecycle.
9. Close window: `POST /api/v1/windows/{id}/close`.

### 17.3. External booking via middleware

Recommended external clients should call Nest middleware `/external/**`, not Spring directly. Middleware forwards to Spring with:

```http
X-Integration-Client: <client-code>
X-Backend-Integration-Key: <backend-key>
Idempotency-Key: <stable-key>
```

Spring direct flow is:

1. Read available dates.
2. Read slots for date.
3. Create booking with `source`, `externalId`, and `Idempotency-Key`.
4. Store `bookingId`, `bookingNumber`, `qrToken`.
5. Cancel by `/api/v1/booking/external/{source}/{externalId}/cancel` when needed.

### 17.4. Terminal flow

1. Device sends `X-Device-Token`.
2. Load config: `GET /api/v1/terminal/{terminalId}/config`.
3. Show only returned `services`; do not filter by `type`. For ТС use `service.categoryCode === "TS"`.
4. Create ticket: `POST /api/v1/terminal/{terminalId}/tickets`.
5. Print/display `ticketNumber`.

### 17.5. QR self-service flow

1. QR code opens public frontend route with `departmentId`.
2. Frontend first checks locally saved QR `ticketId`. If it exists, load status with `GET /api/v1/qr/tickets/{ticketId}`.
3. If saved ticket is in `CREATED`, `WAITING`, `CALLED`, `IN_SERVICE` or `PAUSED`, show the current ticket screen and poll the same endpoint for status updates.
4. If there is no saved active ticket, frontend loads config: `GET /api/v1/qr/departments/{departmentId}/config`.
5. Frontend shows returned `services`; for ТС grouping/filtering use `service.categoryCode === "TS"`.
6. Citizen selects a service and frontend creates ticket: `POST /api/v1/qr/tickets`.
7. Frontend saves created `ticketId` locally, does not show or submit PIN, and shows `ticketNumber` plus current `status`; source is created as `QR_SELF_SERVICE`.

### 17.6. TV flow

1. Device sends `X-Device-Token`.
2. Load snapshot: `GET /api/v1/tv/displays/{tvDisplayId}/snapshot`.
3. Connect stream: `GET /api/v1/tv/displays/{tvDisplayId}/stream`.
4. On ticket events, refresh local queue state or merge event payload.

## 18. Security notes

- Не логировать `password`, `accessToken`, `refreshToken`, `X-Backend-Integration-Key`, `X-Device-Token`, `Idempotency-Key`.
- Не логировать персональные поля без маскирования: `citizenFullName`, `citizenPin`, `citizenPhone`, `vehicleNumber`.
- Raw refresh tokens и raw device tokens не должны сохраняться в клиентских логах.
- Backend refresh tokens хранит как hash.
- Device token хранится в БД как SHA-256 Base64 URL-safe hash.
- Browser clients должны использовать JWT, а не `X-Backend-Integration-Key`.

## 19. Где смотреть актуальный контракт

Главный источник актуального machine-readable контракта:

```text
http://localhost:8080/v3/api-docs
```

UI:

```text
http://localhost:8080/swagger-ui.html
```

Перед релизом фронт/интеграции должны сверять свои типы и enum'ы с OpenAPI JSON.
