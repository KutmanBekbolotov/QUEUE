# ТЗ: интеграция фронтенда с Electronic Queue

Дата актуализации: 2026-07-03.

Документ описывает, как фронтенд должен интегрироваться с текущим backend-проектом `new-queue-backend`: Spring Boot API, NestJS middleware, nginx gateway, JWT/RBAC, очереди, бронирования, терминалы, ТВ-экраны, отчеты и внешние интеграции.

## 1. Цель

Фронтенд должен предоставить рабочие интерфейсы для:

1. Администрирования справочников, пользователей, ролей, окон, услуг, слотов бронирования и отчетов.
2. Рабочего места оператора очереди.
3. Табло/ТВ-экрана для отображения вызванных талонов.
4. Терминала самообслуживания для выдачи талонов.
5. Кабинета/внешних систем бронирования, если они подключаются через серверный BFF или свою backend-часть.

Spring Boot является источником истины для доменной логики, авторизации, транзакций, аудита и отчетов. NestJS является middleware-границей для внешних систем и не должен использоваться как основной API для админки или операторского фронта.

## 2. Архитектура интеграции

### 2.1. Сервисы

- `apps/backend-spring` - основной API: `/api/v1/*`.
- `apps/middleware-nest` - внешнее API для Website Cabinet, Tunduk, CRM/Zenoss: `/external/*`.
- `infra/nginx/default.conf` - локальный gateway:
  - `/api/v1/*` проксируется в Spring.
  - `/external/*` и `/health` проксируются в NestJS.

### 2.2. Базовые URL

Локальная разработка:

- Spring напрямую: `http://localhost:8080`
- NestJS напрямую: `http://localhost:3000`
- nginx gateway: `http://localhost:8088`

Для браузерного фронта предпочтительно использовать gateway:

- staff/admin/operator frontend: `http://localhost:8088/api/v1`
- external server-to-server clients: `http://localhost:8088/external`

Swagger/OpenAPI:

- Spring Swagger UI: `http://localhost:8080/swagger-ui.html`
- Spring OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Nest Swagger UI: `http://localhost:3000/docs`
- Nest OpenAPI JSON: `http://localhost:3000/docs-json`

## 3. Типы фронтендов и API-границы

### 3.1. Админка и рабочее место сотрудника

Должны обращаться напрямую в Spring API `/api/v1/*` через JWT:

- `Authorization: Bearer <accessToken>`
- `Content-Type: application/json`
- `X-Request-Id` желательно генерировать на клиенте для трассировки.

Нельзя использовать `X-Backend-Integration-Key` или `/external/*` из браузерной админки.

### 3.2. Терминал самообслуживания

Терминальный UI обращается в Spring:

- `GET /api/v1/terminal/{terminalId}/config`
- `POST /api/v1/terminal/{terminalId}/tickets`

Аутентификация устройства:

- `X-Device-Token: <deviceToken>`
- Допустим также `Authorization: Bearer <deviceToken>`, но предпочтителен `X-Device-Token`.

Токен устройства нельзя хранить в обычном пользовательском localStorage. Для киоск-устройств он должен поставляться через защищенную конфигурацию устройства или оболочку приложения.

Первичную конфигурацию выполняет администратор через `POST /api/v1/devices/terminals` или `POST /api/v1/devices/tv-displays`. Ответ содержит `device.id` и одноразово показываемый `deviceToken`. Устройство сохраняет оба значения и не вызывает `/api/v1/auth/login`.

### 3.3. ТВ-экран

TV UI обращается в Spring:

- `GET /api/v1/tv/displays/{tvDisplayId}/snapshot`
- `GET /api/v1/tv/displays/{tvDisplayId}/stream`

Аутентификация такая же, как у терминала:

- `X-Device-Token: <deviceToken>`

`/stream` использует SSE. Клиент должен уметь переподключаться при обрыве.

### 3.4. Website Cabinet, Tunduk, CRM, Zenoss

Внешние клиенты должны ходить в NestJS `/external/*`, но это server-to-server API.

Важно: `X-API-Key` нельзя вшивать в публичный браузерный фронт. Если нужен публичный кабинет гражданина, он должен иметь свой backend/BFF, который хранит API key на сервере и проксирует запросы к `/external/cabinet`.

## 4. Общие правила HTTP-интеграции

### 4.1. Форматы

- Тело запросов и ответов: JSON.
- UUID: строка в формате UUID.
- `Instant`: ISO-8601 UTC, например `2026-07-03T09:00:00Z`.
- `LocalDate`: `YYYY-MM-DD`.
- `LocalTime`: `HH:mm:ss`.
- Enum-значения передаются в верхнем регистре, как в backend-коде.

### 4.2. Заголовки

Для всех browser-to-Spring запросов:

- `Authorization: Bearer <accessToken>` для защищенных endpoints.
- `Content-Type: application/json` для запросов с телом.
- `X-Request-Id: <uuid>` желательно на каждый запрос.
- `X-Correlation-Id: <uuid>` опционально, если фронт объединяет несколько запросов в один пользовательский сценарий.

Для идемпотентных мутаций, особенно бронирований и внешних операций:

- `Idempotency-Key: <stable-key>`
- или `X-External-Request-Id: <external-request-id>` для внешних систем.

Для устройств:

- `X-Device-Token: <deviceToken>`

Для external server-to-server клиентов NestJS:

- `X-API-Key: <external-api-key>` или `Authorization: Bearer <external-api-key>`
- `X-Integration-Client: <clientCode>` для CRM-вариантов, например `CRM_MAIN`.
- `Idempotency-Key` обязателен для mutating external routes, если нет `X-External-Request-Id` или `externalId` в теле.

### 4.3. CORS

Spring разрешает origins из `CORS_ALLOWED_ORIGINS`. По умолчанию:

- `http://localhost:3000`
- `http://localhost:8088`

Если frontend dev server работает на другом порту, backend env должен быть обновлен.

Разрешенные CORS headers в Spring:

- `Authorization`
- `Content-Type`
- `X-Request-Id`
- `X-Correlation-Id`
- `Idempotency-Key`
- `X-External-Request-Id`

Exposed response headers:

- `X-Request-Id`
- `X-Correlation-Id`

### 4.4. Ошибки

Все сервисы должны возвращать единый формат:

```json
{
  "timestamp": "2026-07-03T09:00:00Z",
  "requestId": "uuid-or-client-supplied-id",
  "code": "ERROR_CODE",
  "message": "Human readable message",
  "details": {}
}
```

Фронт обязан:

- Показывать пользователю понятное сообщение на основе `message`.
- Логировать `requestId` для поддержки.
- Обрабатывать `401` как необходимость перелогина или refresh.
- Обрабатывать `403` как отсутствие прав и скрывать недоступное действие.
- Обрабатывать `409` как бизнес-конфликт, например неверный переход статуса, занятый слот, конфликт идемпотентности.
- Обрабатывать `429` как rate limit.
- Не показывать сырые stack traces, даже если backend вернул неожиданный текст.

## 5. Авторизация и сессия

### 5.1. Login

`POST /api/v1/auth/login`

Request:

```json
{
  "username": "admin",
  "password": "ZAQ!@#$%tgb*"
}
```

Response:

```json
{
  "accessToken": "jwt",
  "refreshToken": "opaque-refresh-token",
  "tokenType": "Bearer",
  "expiresAt": "2026-07-03T09:15:00Z",
  "roles": ["SUPER_ADMIN"],
  "permissions": ["USER_READ"]
}
```

Требования к фронту:

- Access token хранить в памяти приложения, если возможно.
- Refresh token не логировать и не отправлять в сторонние сервисы.
- Если выбран localStorage/sessionStorage, это должно быть осознанное решение с учетом XSS-рисков.
- За 30-60 секунд до `expiresAt` выполнять refresh.
- При неуспешном refresh очищать сессию и отправлять пользователя на login.

### 5.2. Refresh

`POST /api/v1/auth/refresh`

Request:

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

Backend выполняет rotation refresh token. Фронт обязан заменить оба токена из ответа.

### 5.3. Logout

`POST /api/v1/auth/logout`

Request:

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

Успешный ответ: `204 No Content`. Фронт очищает локальную сессию в любом случае после ответа или сетевой ошибки.

### 5.4. Current user

`GET /api/v1/auth/me`

Response:

```json
{
  "id": "uuid",
  "username": "admin",
  "fullName": "Bootstrap Admin",
  "email": null,
  "phone": null,
  "departmentId": "uuid",
  "windowId": "uuid",
  "serviceIds": ["service-uuid"],
  "serviceCodes": ["VS"],
  "status": "ACTIVE",
  "roles": ["SUPER_ADMIN"],
  "permissions": ["USER_READ", "ROLE_READ"]
}
```

Фронт должен строить меню, роуты и доступность кнопок по `permissions`, а не по названиям ролей. Роли можно использовать только для отображения.

## 6. RBAC и доступность UI

### 6.1. Базовые роли

Системные роли:

- `SUPER_ADMIN`
- `ADMIN`
- `DEPARTMENT_MANAGER`
- `OPERATOR`
- `AUDITOR`
- `INTEGRATION_SERVICE`
- `TERMINAL_DEVICE`
- `TV_DEVICE`

`TERMINAL_DEVICE` и `TV_DEVICE` — зарезервированные legacy-коды. Их нельзя назначать записям пользователей или использовать для login; устройства создаются через `/api/v1/devices/*`.

### 6.2. Основные permission-группы

- Пользователи: `USER_READ`, `USER_CREATE`, `USER_UPDATE`, `USER_BLOCK`
- Роли: `ROLE_READ`, `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_ASSIGN_PERMISSION`
- Регионы: `REGION_READ`, `REGION_CREATE`, `REGION_UPDATE`
- Подразделения: `DEPARTMENT_READ`, `DEPARTMENT_CREATE`, `DEPARTMENT_UPDATE`, `DEPARTMENT_CLOSE`
- Окна: `WINDOW_READ`, `WINDOW_CREATE`, `WINDOW_UPDATE`, `WINDOW_OPEN`, `WINDOW_CLOSE`, `WINDOW_ASSIGN_EMPLOYEE`
- Услуги: `SERVICE_READ`, `SERVICE_CREATE`, `SERVICE_UPDATE`, `SERVICE_ASSIGN_TO_DEPARTMENT`, `SERVICE_ASSIGN_TO_EMPLOYEE`
- Талоны: `TICKET_READ`, `TICKET_CREATE`, `TICKET_CALL`, `TICKET_START`, `TICKET_PAUSE`, `TICKET_RESUME`, `TICKET_COMPLETE`, `TICKET_CANCEL`, `TICKET_NO_SHOW`, `TICKET_TRANSFER`
- Бронирования: `BOOKING_READ`, `BOOKING_CREATE`, `BOOKING_CANCEL`, `BOOKING_CHECK_IN`, `BOOKING_SLOT_READ`, `BOOKING_SLOT_MANAGE`
- Терминалы: `TERMINAL_READ`, `TERMINAL_CREATE`, `TERMINAL_UPDATE`, `TERMINAL_CONFIGURE`
- ТВ: `TV_READ`, `TV_CREATE`, `TV_UPDATE`, `TV_CONFIGURE`
- Отчеты: `REPORT_READ`, `REPORT_EXPORT`, `REPORT_VIEW_PERSONAL_DATA`, `REPORT_EXPORT_PERSONAL_DATA`
- Аудит: `AUDIT_READ`
- Интеграции: `INTEGRATION_KEY_READ`, `INTEGRATION_KEY_CREATE`, `INTEGRATION_KEY_REVOKE`

### 6.3. UI-правила

Фронт должен:

- Скрывать пункты меню без соответствующих `*_READ`.
- Disable или скрывать кнопки мутаций без нужного permission.
- После `403` обновлять `/auth/me`, если есть риск, что права изменились.
- Не пытаться обходить scope на клиенте. Backend сам ограничивает доступ по department/window scope.

## 7. Основные страницы и сценарии

### 7.1. Login

Функции:

- Ввод username/password.
- Обработка ошибок `UNAUTHENTICATED`, `VALIDATION_ERROR`.
- Перенаправление после login на dashboard или рабочее место согласно permission.

### 7.2. Dashboard

Минимум:

- Сводка по очереди текущего подразделения.
- Быстрые ссылки к талонам, бронированиям, окнам, отчетам.

Источник данных:

- Для отчетной сводки: `/api/v1/reports/summary` при наличии `REPORT_READ`.
- Для живой очереди: `/api/v1/tickets?departmentId=...` и SSE для операторского окна.

### 7.3. Администрирование пользователей и ролей

Endpoints:

- `GET /api/v1/users`
- `GET /api/v1/users/{id}`
- `POST /api/v1/users`
- `PATCH /api/v1/users/{id}/status`
- `POST /api/v1/users/{id}/roles`
- `GET /api/v1/roles`
- `POST /api/v1/roles`
- `PUT /api/v1/roles/{id}/permissions`
- `GET /api/v1/permissions`

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

type UserStatus = 'ACTIVE' | 'BLOCKED' | 'DISABLED';

type AssignUserRolesRequest = {
  roleCodes: string[];
};

type CreateRoleRequest = {
  code: string;
  name: string;
  permissionCodes?: string[];
};

type AssignRolePermissionsRequest = {
  permissionCodes: string[];
};
```

UI-требования:

- При создании пользователя password должен быть 8-120 символов.
- `roleCodes` и `permissionCodes` передаются как set/array строк.
- Для пользователя с ролью `OPERATOR` поле `departmentId` обязательно. Передавайте `windowId` и выбранные услуги в том же `POST /api/v1/users` или `PATCH /api/v1/users/{id}`. Элементы `serviceIds` могут быть UUID или кодами (`VS`, `TS`); aliases `services` и `serviceCodes` также поддерживаются.
- Login, refresh и `GET /api/v1/auth/me` возвращают `departmentId`, `windowId`, `serviceIds`, `serviceCodes` и совместимый alias `services`; не подменять их пустыми значениями на фронте.
- При редактировании пропущенное поле назначения означает «не менять», `windowId: ""` очищает окно, `serviceIds: []` очищает услуги.
- Системные роли отображать как неизменяемые по смыслу, даже если backend разрешит часть операций.
- Публичные формы могут читать `GET /api/v1/regions`, `GET /api/v1/departments`, `GET /api/v1/service-categories` и `GET /api/v1/departments/{departmentId}/services` без Bearer-токена; backend отдаёт только активные публичные записи.

### 7.4. Справочники

Endpoints:

- Регионы:
  - `GET /api/v1/regions`
  - `POST /api/v1/regions`
  - `GET /api/v1/regions/{id}`
  - `PUT /api/v1/regions/{id}`
  - `PATCH /api/v1/regions/{id}/status`
- Подразделения:
  - `GET /api/v1/departments`
  - `POST /api/v1/departments`
  - `GET /api/v1/departments/{id}`
  - `PUT /api/v1/departments/{id}`
  - `PATCH /api/v1/departments/{id}/status`
- Кабинеты:
  - `GET /api/v1/departments/{departmentId}/rooms`
  - `POST /api/v1/departments/{departmentId}/rooms`
  - `PUT /api/v1/rooms/{id}`
- Залы:
  - `GET /api/v1/departments/{departmentId}/halls`
  - `POST /api/v1/departments/{departmentId}/halls`
  - `PUT /api/v1/halls/{id}`
- Окна:
  - `GET /api/v1/departments/{departmentId}/windows`
  - `POST /api/v1/departments/{departmentId}/windows`
  - `GET /api/v1/windows/{id}`
  - `PUT /api/v1/windows/{id}`
  - `PATCH /api/v1/windows/{id}/status`
  - `POST /api/v1/windows/{id}/assign-employee`
  - `POST /api/v1/windows/{id}/open`
  - `POST /api/v1/windows/{id}/close`
- Категории услуг:
  - `GET /api/v1/service-categories`
  - `POST /api/v1/service-categories`
  - `GET /api/v1/service-categories/{id}`
  - `PUT /api/v1/service-categories/{id}`
- Услуги:
  - `GET /api/v1/services`
  - `POST /api/v1/services`
  - `GET /api/v1/services/{id}`
  - `PUT /api/v1/services/{id}`
  - `PATCH /api/v1/services/{id}/status`
- Услуги подразделения:
  - `GET /api/v1/departments/{departmentId}/services`
  - `POST /api/v1/departments/{departmentId}/services/{serviceId}`
  - `DELETE /api/v1/departments/{departmentId}/services/{serviceId}`
- Услуги сотрудника:
  - `POST /api/v1/employees/{employeeId}/services/{serviceId}`
  - `DELETE /api/v1/employees/{employeeId}/services/{serviceId}?departmentId=...`

Ключевые DTO:

```ts
type RegionRequest = {
  code: string;
  name: string;
};

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

type WindowStatus = 'OPEN' | 'CLOSED' | 'INACTIVE';

type WindowRequest = {
  hallId?: string;
  code: string;
  displayName: string;
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
```

Примечание: `WindowStatus` приходит из backend enum `WindowStatus`; фронт должен подтянуть фактические значения из OpenAPI. В текущем backend значения: `OPEN`, `CLOSED`, `INACTIVE`.

### 7.5. Оператор очереди

Основной сценарий:

1. Оператор выбирает/получает свое окно.
2. Открывает смену: `POST /api/v1/operators/{operatorId}/shifts/open`.
3. Открывает окно: `POST /api/v1/windows/{id}/open`.
4. Получает dashboard: `GET /api/v1/operators/{operatorId}/dashboard`.
5. Нажимает "вызвать следующего": `POST /api/v1/tickets/call-next`.
6. Начинает обслуживание: `POST /api/v1/tickets/{id}/start`.
7. Может поставить на паузу, возобновить, завершить, отменить, отметить неявку или перевести.
8. Повторяет вызов текущего талона: `POST /api/v1/tickets/{id}/recall`.
9. Закрывает окно: `POST /api/v1/windows/{id}/close`.
10. Закрывает смену: `POST /api/v1/operators/{operatorId}/shifts/current/close`.

Endpoints:

- `GET /api/v1/tickets?departmentId=...`
- `GET /api/v1/tickets/{id}`
- `DELETE /api/v1/tickets/{id}`
- `POST /api/v1/tickets/{id}/call`
- `POST /api/v1/tickets/{id}/recall`
- `POST /api/v1/tickets/call-next`
- `POST /api/v1/tickets/{id}/start`
- `POST /api/v1/tickets/{id}/pause`
- `POST /api/v1/tickets/{id}/resume`
- `POST /api/v1/tickets/{id}/complete`
- `POST /api/v1/tickets/{id}/cancel`
- `POST /api/v1/tickets/{id}/no-show`
- `POST /api/v1/tickets/{id}/transfer`
- `GET /api/v1/operator/{windowId}/stream`
- `POST /api/v1/operators/{operatorId}/shifts/open`
- `POST /api/v1/operators/{operatorId}/shifts/current/close`
- `GET /api/v1/operators/{operatorId}/dashboard`

DTO:

```ts
type TicketSource =
  | 'TERMINAL'
  | 'QR_SELF_SERVICE'
  | 'WEBSITE_CABINET'
  | 'TUNDUK'
  | 'CRM'
  | 'CRM_ZENOSS'
  | 'ADMIN_CREATED';

type TicketStatus =
  | 'CREATED'
  | 'WAITING'
  | 'CALLED'
  | 'IN_SERVICE'
  | 'PAUSED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'
  | 'EXPIRED'
  | 'TRANSFERRED';

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
  windowId: string | null;
  serviceWindowId: string | null;
  windowNumber: string | null;
  serviceId: string;
  serviceName: LocalizedName | null;
  servedByUserId: string | null;
  operatorId: string | null;
  status: TicketStatus;
};
```

Разрешенные переходы статусов:

- `WAITING -> CALLED | CANCELLED | TRANSFERRED`
- `CALLED -> IN_SERVICE | CANCELLED | NO_SHOW | TRANSFERRED`
- `IN_SERVICE -> PAUSED | COMPLETED | CANCELLED`
- `PAUSED -> IN_SERVICE | CANCELLED`

Фронт должен блокировать кнопки, которые не соответствуют текущему статусу, но окончательную проверку оставлять backend.
Повторный `POST /api/v1/tickets/call-next`, когда у оператора или окна уже есть активный талон, возвращает этот активный `TicketResponse` без ошибки; фронт должен показать его как текущий талон и не считать это неуспешным вызовом.
Удаление из истории: `DELETE /api/v1/tickets/{id}` требует `TICKET_DELETE`, физически удаляет талон из БД и возвращает `204`. После успешного ответа фронт должен убрать талон из списка; backend также публикует `ticket.deleted`.

SSE:

- Подключение: `new EventSource('/api/v1/operator/{windowId}/stream')`.
- Stream привязан к окну для прав доступа, но backend также подписывает подключение на события всего подразделения. Фронт должен слушать `ticket.created`, `ticket.called`, `ticket.started`, `ticket.completed`, `ticket.cancelled`, `ticket.no_show`, `ticket.transferred`, `ticket.deleted` и по ним обновлять список ожидания/активный талон без перезагрузки страницы.
- Так как native `EventSource` не позволяет отправлять `Authorization` header, для защищенного SSE нужны один из вариантов:
  - использовать fetch-based SSE polyfill с headers;
  - проксировать SSE через BFF;
  - доработать backend под cookie/session или query-token, если это будет принято безопасностью.
- При реконнекте нужно заново запросить `GET /api/v1/tickets?departmentId=...`, чтобы синхронизировать состояние.

### 7.6. Создание талона администратором

Endpoint:

- `POST /api/v1/tickets`

Требуется `TICKET_CREATE`. Для фронта администратора `source` должен быть `ADMIN_CREATED`.

### 7.7. Терминальный сценарий

Endpoints:

- `GET /api/v1/terminal/{terminalId}/config`
- `POST /api/v1/terminal/{terminalId}/tickets`

DTO:

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
  type: 'VS';
};

type TerminalConfigCategory = {
  id: string;
  code: string;
  type: 'VS';
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

UI-требования:

- После загрузки конфигурации показать только услуги из `services`; `serviceIds` остаётся для обратной совместимости.
- Не фильтровать услуги ТС по `type`: это legacy-тип терминального элемента и он равен `'VS'`. Для ТС использовать `service.categoryCode === 'TS'`, `category.code === 'TS'` или `service.code.startsWith('TS_')`.
- После создания талона показать `ticketNumber` крупно и подготовить printable view.
- При `DEVICE_TOKEN_REQUIRED` или `401` показывать технический экран "устройство не авторизовано".

### 7.8. QR-сценарий

Endpoints:

- `GET /api/v1/qr/departments/{departmentId}/config`
- `GET /api/v1/qr/tickets/{ticketId}`
- `POST /api/v1/qr/tickets`

DTO:

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

UI-требования:

- QR-ссылка должна открыть публичный маршрут фронта с `departmentId`.
- На старте загрузить config и показывать только `services` из ответа; backend уже отфильтровал услуги по `qrEnabled`.
- Для группировки услуг ТС использовать `categoryCode === 'TS'`, не `type`.
- Не показывать и не отправлять ПИН в QR-сценарии; backend не хранит `citizenPin` для QR-талонов.
- После успешного `POST /api/v1/qr/tickets` сохранить текущий талон в localStorage/sessionStorage минимум как `{ ticketId, departmentId }`; можно дополнительно сохранить `ticketNumber`, `status`, `createdAt`.
- При открытии QR-страницы сначала проверить сохраненный `ticketId`. Если он есть, вызвать `GET /api/v1/qr/tickets/{ticketId}`. Если backend вернул QR-талон в незавершенном статусе `CREATED`, `WAITING`, `CALLED`, `IN_SERVICE` или `PAUSED`, показывать страницу текущего талона вместо формы создания.
- Пока текущий талон не финальный, не показывать кнопку/форму получения нового талона на этом телефоне/браузере. Статус отслеживать polling-запросом `GET /api/v1/qr/tickets/{ticketId}` каждые 5-10 секунд или при возврате вкладки в фокус.
- Если статус стал `COMPLETED`, `CANCELLED`, `NO_SHOW`, `EXPIRED` или `TRANSFERRED`, очистить сохраненный `ticketId` и разрешить взять новый талон.
- Если `GET /api/v1/qr/tickets/{ticketId}` вернул `404 QR_TICKET_NOT_FOUND`, очистить сохраненный `ticketId` и показать форму.
- После выбора услуги отправить `POST /api/v1/qr/tickets`; в ответе показать `ticketNumber` и `status`.
- Не отправлять `Authorization`, `X-Device-Token`, `X-API-Key` или integration headers из публичной QR-страницы.

### 7.9. ТВ-табло

Endpoints:

- `GET /api/v1/tv/displays/{tvDisplayId}/snapshot`
- `GET /api/v1/tv/displays/{tvDisplayId}/stream`

Response snapshot:

```ts
type TvSnapshotResponse = {
  departmentId: string;
  tickets: TicketResponse[];
  generatedAt: string;
};
```

UI-требования:

- На старте загрузить snapshot.
- Затем подключить SSE stream.
- При обрыве stream переподключаться с backoff 1s, 2s, 5s, 10s, 30s.
- Показывать только актуальные вызванные/обслуживаемые/приостановленные талоны. Backend snapshot ориентирован на `CALLED`, `IN_SERVICE` и `PAUSED`.
- В `tickets[]` backend заполняет `windowId`, `serviceWindowId`, `operatorId`, `windowNumber` и `serviceName`, чтобы TV мог показать номер окна и название услуги без дополнительных справочников.
- Для звука можно слушать `ticket_called` и `ticket_recalled`; backend также продолжает отправлять canonical имена `ticket.called` и `ticket.recalled`.

### 7.10. Онлайн-бронирование и слоты

Endpoints Spring для авторизованного staff/admin frontend:

- `GET /api/v1/booking/available-dates?departmentId=...&serviceId=...&fromDate=...&toDate=...&source=...`
- `GET /api/v1/booking/slots?departmentId=...&serviceId=...&date=...&source=...`
- `POST /api/v1/booking`
- `GET /api/v1/booking/{id}`
- `GET /api/v1/booking/by-token/{qrToken}`
- `GET /api/v1/booking/external/{source}/{externalId}`
- `POST /api/v1/booking/{id}/cancel`
- `POST /api/v1/booking/external/{source}/{externalId}/cancel`
- `POST /api/v1/booking/{id}/check-in`
- `POST /api/v1/booking/{id}/expire`
- `POST /api/v1/booking/slots/generate`
- `POST /api/v1/booking/slots/{id}/disable`
- `POST /api/v1/booking/slots/{id}/enable`

DTO:

```ts
type BookingSource =
  | 'WEBSITE_CABINET'
  | 'TUNDUK'
  | 'CRM'
  | 'CRM_ZENOSS'
  | 'ADMIN_CREATED';

type BookingStatus =
  | 'CREATED'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'NO_SHOW';

type BookingSlotStatus =
  | 'ACTIVE'
  | 'DISABLED'
  | 'FULL'
  | 'EXPIRED';

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
```

Идемпотентность:

- Для внешних бронирований всегда отправлять `Idempotency-Key`.
- Для staff/admin создания через Spring также желательно отправлять `Idempotency-Key`, если пользователь может повторно нажать кнопку или сеть нестабильна.
- При `IDEMPOTENCY_KEY_CONFLICT` показать ошибку "ключ уже использован для другого запроса" и не повторять автоматически.
- При `REQUEST_ALREADY_PROCESSING` можно показать "запрос уже обрабатывается" и дать пользователю обновить статус.

Check-in:

- `POST /api/v1/booking/{id}/check-in` создает связанный талон.
- Возможные конфликты:
  - `BOOKING_CHECK_IN_TOO_EARLY`
  - `BOOKING_CHECK_IN_TOO_LATE`
  - `BOOKING_INVALID_STATUS`

### 7.11. Отчеты и экспорт

Endpoints:

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

Фильтры:

```ts
type ReportFilter = {
  dateFrom: string;
  dateTo: string;
  regionId?: string;
  departmentId?: string;
  employeeId?: string;
  windowId?: string;
  serviceCategoryId?: string;
  serviceId?: string;
  source?: TicketSource | BookingSource;
  ticketStatus?: TicketStatus;
  bookingStatus?: BookingStatus;
  cancellationReasonId?: string;
  groupBy?: string;
  includePersonalData?: boolean;
  page?: number;
  size?: number;
};

type ReportType =
  | 'SUMMARY'
  | 'BY_REGION'
  | 'BY_DEPARTMENT'
  | 'BY_EMPLOYEE'
  | 'BY_SERVICE'
  | 'BY_SOURCE'
  | 'BY_STATUS'
  | 'WAITING_TIME'
  | 'SERVICE_TIME'
  | 'CANCELLATIONS'
  | 'NO_SHOWS'
  | 'BOOKINGS'
  | 'WINDOW_WORKLOAD'
  | 'WORKLOAD_HOURLY'
  | 'WORKLOAD_DAILY'
  | 'TICKETS_DETAIL'
  | 'BOOKINGS_DETAIL'
  | 'INTEGRATIONS';

type ReportExportFormat = 'CSV' | 'XLSX' | 'PDF';
type ReportExportStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'EXPIRED';
```

Ограничения:

- `dateFrom` и `dateTo` обязательны.
- Стандартные отчеты: максимум 366 дней.
- Детальные tickets/bookings для scoped users: максимум 93 дня.
- `page` по умолчанию 0.
- `size` по умолчанию 50, максимум 500.
- `includePersonalData=true` требует `REPORT_VIEW_PERSONAL_DATA`.
- Экспорт персональных данных требует `REPORT_EXPORT_PERSONAL_DATA`.

Экспорт:

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
  fileName?: string;
  fileSizeBytes?: number;
  createdAt: string;
  completedAt?: string;
  downloadUrl?: string;
  errorMessage?: string;
};
```

UI-требования:

- После `POST /reports/export` показать статус.
- Если статус не `COMPLETED`, опрашивать `GET /reports/export/{id}`.
- Для скачивания использовать `GET /reports/export/{id}/download` с `Authorization` header.
- Для скачивания через browser `window.open` с Authorization не подходит. Нужно использовать `fetch`, получить `Blob`, затем создать object URL.

### 7.12. Аудит

Endpoint:

- `GET /api/v1/audit-logs?limit=50`

Response:

```ts
type AuditLogResponse = {
  id: string;
  actorType: string;
  actorId?: string;
  action: string;
  entityType: string;
  entityId?: string;
  ip?: string;
  source?: string;
  requestId?: string;
  createdAt: string;
};
```

Требуется `AUDIT_READ`.

## 8. External middleware API

Этот раздел нужен только для серверных клиентов и BFF. Публичный browser frontend не должен напрямую использовать эти endpoints с API key.

### 8.1. Website Cabinet

Base: `/external/cabinet`

- `GET /regions`
- `GET /departments`
- `GET /services`
- `GET /booking/available-dates`
- `GET /booking/slots`
- `POST /booking`
- `POST /booking/{id}/cancel`
- `GET /booking/{id}/status`

Create booking body:

```ts
type CreateExternalBookingDto = {
  departmentId: string;
  serviceId: string;
  slotId: string;
  externalBookingId: string;
  citizenFullName?: string;
  citizenPin?: string;
  citizenPhone?: string;
  vehicleNumber?: string;
};
```

### 8.2. Tunduk

Base: `/external/tunduk`

- `POST /bookings`
- `POST /bookings/{externalId}/cancel`
- `GET /bookings/{externalId}/status`
- `GET /booking/slots`
- `GET /directories/departments`
- `GET /directories/services`

### 8.3. CRM

Base: `/external/crm`

- `POST /tickets`
- `GET /tickets/{id}`
- `GET /tickets/{id}/status`
- `POST /bookings`
- `POST /bookings/{id}/cancel`
- `GET /bookings/{id}/status`
- `GET /directories/departments`
- `GET /directories/services`
- `GET /booking/slots`

CRM может передавать `departmentId/serviceId` или `departmentCode/serviceCode`, но в текущем Spring-коде нет контроллера `/api/v1/integration-mappings/resolve`, который нужен middleware для резолва внешних кодов. До backend-доработки CRM/BFF должен передавать UUID.

### 8.4. Zenoss

Base: `/external/zenoss`

Текущий код содержит deprecated alias к CRM:

- `POST /tickets`
- `GET /tickets/{id}`
- `GET /tickets/{id}/status`
- `POST /bookings`
- `POST /bookings/{id}/cancel`
- `GET /bookings/{id}/status`
- `GET /booking/slots`
- `GET /directories/departments`
- `GET /directories/services`

Документация `docs/phase-4-reports.md` и тест `zenoss-reports.controller.spec.ts` упоминают routes `/external/zenoss/reports/*`, но в текущем `ZenossIntegrationController` этих методов нет. Фронт/BFF не должен рассчитывать на эти routes до реализации или восстановления контроллера.

## 9. Состояния и UX обработки

### 9.1. Loading и retries

- GET-запросы можно повторять при сетевой ошибке, `502`, `503`, `504`.
- POST/PUT/PATCH/DELETE повторять автоматически только при наличии `Idempotency-Key`.
- Для форм использовать optimistic UI только после успешного ответа backend.

### 9.2. Empty states

- Справочники: "нет записей" плюс кнопка создания при наличии create permission.
- Очередь: "нет ожидающих талонов".
- Бронирования: "нет доступных дат/слотов".
- Отчеты: "нет данных за выбранный период".

### 9.3. Персональные данные

Фронт должен:

- Маскировать PIN/телефон/ФИО там, где пользователь не имеет права на персональные данные.
- Не логировать request/response bodies с `citizenFullName`, `citizenPin`, `citizenPhone`, `password`, `token`.
- Для отчетов показывать toggle `includePersonalData` только при наличии `REPORT_VIEW_PERSONAL_DATA`.

## 10. Требования к API-клиенту фронта

Нужно реализовать единый HTTP client:

- Подставляет base URL из env.
- Добавляет `Authorization`.
- Генерирует `X-Request-Id`.
- Добавляет `X-Correlation-Id`, если сценарий требует.
- На `401` один раз пытается refresh, затем повторяет исходный запрос.
- Защищает от параллельных refresh storm: один refresh promise на все запросы.
- Возвращает нормализованную ошибку `{ status, code, message, details, requestId }`.
- Поддерживает download blob для отчетов.
- Поддерживает SSE через polyfill или BFF, если нужен Authorization header.

Рекомендуемые env для frontend:

```env
VITE_API_BASE_URL=http://localhost:8088/api/v1
VITE_EXTERNAL_BASE_URL=http://localhost:8088/external
```

`VITE_EXTERNAL_BASE_URL` использовать только для server-side/BFF frontend runtime. Не хранить external API keys в публичных env.

## 11. Минимальные TypeScript-модели

```ts
type UUID = string;
type ISOInstant = string;
type ISODate = string;
type ISOTime = string;

type ErrorResponse = {
  timestamp: ISOInstant;
  requestId: string;
  code: string;
  message: string;
  details: Record<string, unknown>;
};

type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
```

Фронт-команда должна генерировать типы из OpenAPI при каждой backend-итерации или поддерживать ручные типы в одном пакете, чтобы enum-значения не расходились.

## 12. Acceptance criteria

Интеграция считается готовой, если выполнено:

1. Login, refresh, logout, `/auth/me` работают без потери сессии при истечении access token.
2. Меню и actions управляются permissions из `/auth/me`.
3. Все mutating external requests отправляют `Idempotency-Key`.
4. Оператор может открыть окно, вызвать талон, начать, поставить на паузу, возобновить, завершить, отменить, отметить no-show и перевести талон.
5. Терминал может загрузить config и создать талон по `X-Device-Token`.
6. QR-страница может загрузить публичный config и создать талон без JWT/device-token.
7. ТВ-табло загружает snapshot и получает live updates через SSE с автоматическим reconnect.
8. Бронирования: доступные даты, слоты, создание, отмена, check-in и управление слотами работают с правильными статусами.
9. Отчеты отображают фильтры, ограничения периода, пагинацию деталей, экспорт и скачивание blob.
10. Все ошибки показываются через единый обработчик с `requestId`.
11. Публичный browser frontend не содержит `X-API-Key`, `X-Backend-Integration-Key`, refresh token в логах или device token в обычном localStorage.
12. В dev окружении фронт работает через `http://localhost:8088/api/v1` без CORS-ошибок.
13. Swagger/OpenAPI сверка проходит перед релизом: нет фронтовых вызовов к endpoints, отсутствующим в текущем backend-коде.

## 13. Известные backend-риски для фронта

1. В текущем NestJS коде отсутствуют `/external/zenoss/reports/*`, хотя они описаны в `docs/phase-4-reports.md` и ожидаются тестом. Нужно либо реализовать эти routes, либо убрать ожидание из внешнего фронта/BFF.
2. В текущем Spring коде нет public controller для `/api/v1/integration-mappings/resolve`, хотя CRM middleware вызывает его при работе с `departmentCode/serviceCode`. До доработки использовать UUID.
3. Native `EventSource` не поддерживает `Authorization` header. Для operator SSE нужен polyfill, BFF или backend-решение по безопасной авторизации stream.
4. Native `EventSource` не позволяет установить `X-Device-Token`. Для TV SSE нужен EventSource polyfill/fetch-stream либо same-origin device shell, который добавляет заголовок.
