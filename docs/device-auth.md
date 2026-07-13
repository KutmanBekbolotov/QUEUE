# Авторизация терминалов и ТВ

Терминал и ТВ — это устройства, а не пользователи. Они не вызывают `POST /api/v1/auth/login` и не получают JWT/refresh token.

Роли `TERMINAL_DEVICE` и `TV_DEVICE` нельзя назначать записям `users`: для новых устройств используется provisioning API ниже.

## 1. Первичная активация

Администратор с JWT создаёт устройство. Для терминала требуется permission `TERMINAL_CREATE`:

```http
POST /api/v1/devices/terminals
Authorization: Bearer <admin-access-token>
Content-Type: application/json

{
  "departmentId": "<department-id>",
  "code": "TERM-01",
  "name": "Главный терминал"
}
```

Для ТВ требуется permission `TV_CREATE`:

```http
POST /api/v1/devices/tv-displays
Authorization: Bearer <admin-access-token>
Content-Type: application/json

{
  "departmentId": "<department-id>",
  "hallId": "<hall-id>",
  "code": "TV-01",
  "name": "Экран главного зала"
}
```

Оба endpoint возвращают:

```json
{
  "device": {
    "id": "<device-id>",
    "type": "TERMINAL",
    "departmentId": "<department-id>",
    "hallId": null,
    "code": "TERM-01",
    "name": "Главный терминал",
    "active": true,
    "lastSeenAt": null
  },
  "deviceToken": "<raw-device-token>"
}
```

`deviceToken` показывается только при создании или ротации. Backend сохраняет только SHA-256 hash. Клиент устройства должен сохранить `device.id` и `deviceToken` в защищённом хранилище ОС/оболочки устройства, не в обычном browser `localStorage`.

## 2. Работа терминала

```http
GET /api/v1/terminal/<terminal-id>/config
X-Device-Token: <raw-device-token>
```

```http
POST /api/v1/terminal/<terminal-id>/tickets
X-Device-Token: <raw-device-token>
Content-Type: application/json

{
  "departmentId": "<department-id>",
  "serviceId": "<service-id>"
}
```

## 3. Работа ТВ

Новый URL содержит ID конкретного экрана, поэтому в одном подразделении может работать несколько ТВ с разными токенами:

```http
GET /api/v1/tv/displays/<tv-display-id>/snapshot
X-Device-Token: <raw-device-token>
```

```http
GET /api/v1/tv/displays/<tv-display-id>/stream
X-Device-Token: <raw-device-token>
Accept: text/event-stream
```

Старые URL `/api/v1/tv/<department-id>/snapshot` и `/stream` временно поддерживаются, но помечены устаревшими.

## 4. Управление и ротация

```http
PATCH /api/v1/devices/terminals/<terminal-id>
Authorization: Bearer <admin-access-token>
Content-Type: application/json

{"active": false}
```

```http
POST /api/v1/devices/terminals/<terminal-id>/rotate-token
Authorization: Bearer <admin-access-token>
```

Для ТВ используются аналогичные endpoint:

- `PATCH /api/v1/devices/tv-displays/<tv-display-id>`
- `POST /api/v1/devices/tv-displays/<tv-display-id>/rotate-token`

После ротации старый токен сразу становится недействительным. Новый токен также возвращается только один раз.

## 5. Диагностика 401

- `BAD_CREDENTIALS`: устройство ошибочно отправлено в `/auth/login` или неверны данные обычного пользователя.
- `DEVICE_TOKEN_REQUIRED`: отсутствует `X-Device-Token`.
- `INVALID_DEVICE_TOKEN`: токен не принадлежит указанному устройству либо уже был ротирован.
- `404 TERMINAL_NOT_FOUND` / `TV_DISPLAY_NOT_FOUND`: устройство не существует или отключено.
