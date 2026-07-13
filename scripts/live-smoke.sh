#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080}"
NEST_BASE="${NEST_BASE:-http://localhost:3000}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-ZAQ!@#$%tgb*}"
COMPOSE="${COMPOSE:-docker compose}"
START_STACK="${START_STACK:-false}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-120}"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

pass() {
  printf 'PASS %s\n' "$1"
}

fail() {
  printf 'FAIL %s\n' "$1" >&2
  exit 1
}

json_post() {
  local path="$1"
  local body="$2"
  shift 2
  curl -fsS -X POST "$API_BASE$path" \
    -H 'Content-Type: application/json' \
    "$@" \
    -d "$body"
}

nest_json_post() {
  local path="$1"
  local body="$2"
  shift 2
  curl -fsS -X POST "$NEST_BASE$path" \
    -H 'Content-Type: application/json' \
    "$@" \
    -d "$body"
}

auth_get() {
  local path="$1"
  curl -fsS "$API_BASE$path" -H "Authorization: Bearer $ACCESS_TOKEN"
}

nest_auth_get() {
  local path="$1"
  local token="$2"
  curl -fsS "$NEST_BASE$path" -H "Authorization: Bearer $token"
}

auth_post() {
  local path="$1"
  local body="${2:-{}}"
  curl -fsS -X POST "$API_BASE$path" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

http_code() {
  local output="$1"
  shift
  curl -sS -o "$output" -w '%{http_code}' "$@"
}

expect_code() {
  local expected="$1"
  local label="$2"
  shift 2
  local body="$TMP_DIR/response.json"
  local code
  code="$(http_code "$body" "$@")"
  if [[ "$code" != "$expected" ]]; then
    printf 'Expected HTTP %s for %s, got %s\n' "$expected" "$label" "$code" >&2
    cat "$body" >&2 || true
    exit 1
  fi
  pass "$label"
}

psql_scalar() {
  $COMPOSE exec -T postgres psql -U equeue -d equeue -tA -c "$1" | awk 'NF { print; exit }'
}

hash_value() {
  python3 - "$1" <<'PY'
import base64
import hashlib
import sys

print(base64.urlsafe_b64encode(hashlib.sha256(sys.argv[1].encode()).digest()).decode().rstrip("="))
PY
}

require_json_field() {
  local json="$1"
  shift
  local label="${!#}"
  set -- "${@:1:$(($# - 1))}"
  jq -e "$@" >/dev/null <<<"$json" || fail "$label"
  pass "$label"
}

wait_json_field() {
  local url="$1"
  local filter="$2"
  local label="$3"
  local deadline=$((SECONDS + WAIT_TIMEOUT_SECONDS))
  local body

  while (( SECONDS < deadline )); do
    if body="$(curl -fsS "$url" 2>/dev/null)" && jq -e "$filter" >/dev/null <<<"$body"; then
      pass "$label"
      return
    fi
    sleep 2
  done

  fail "$label did not become ready within ${WAIT_TIMEOUT_SECONDS}s"
}

TODAY="$(date +%F)"
YESTERDAY="$(date -d "$TODAY -1 day" +%F)"
RUN_ID="$(date +%s)"

if [[ "$START_STACK" == "true" || "$START_STACK" == "1" ]]; then
  $COMPOSE up -d --build postgres redis rabbitmq backend-spring middleware-nest
fi

wait_json_field "$API_BASE/api/v1/health" '.status == "UP"' "Spring health is UP"
wait_json_field "$NEST_BASE/health" '.status == "UP"' "Nest health is UP"

NEST_LOGIN_JSON="$(nest_json_post /auth/login "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")"
NEST_ACCESS_TOKEN="$(jq -r '.accessToken' <<<"$NEST_LOGIN_JSON")"
NEST_REFRESH_TOKEN="$(jq -r '.refreshToken' <<<"$NEST_LOGIN_JSON")"
[[ "$NEST_ACCESS_TOKEN" != "null" && -n "$NEST_ACCESS_TOKEN" ]] || fail "Nest admin login access token missing"
[[ "$NEST_REFRESH_TOKEN" != "null" && -n "$NEST_REFRESH_TOKEN" ]] || fail "Nest admin login refresh token missing"
pass "Nest admin login returns access and refresh tokens"

NEST_ME_JSON="$(nest_auth_get /auth/me "$NEST_ACCESS_TOKEN")"
require_json_field "$NEST_ME_JSON" '.username == "admin" and (.roles | index("SUPER_ADMIN")) and (has("passwordHash") | not)' "Nest auth me works from middleware DB auth"

NEST_TOKEN_SPRING_ME="$(curl -fsS "$API_BASE/api/v1/auth/me" -H "Authorization: Bearer $NEST_ACCESS_TOKEN")"
require_json_field "$NEST_TOKEN_SPRING_ME" '.username == "admin" and (.roles | index("SUPER_ADMIN"))' "Nest JWT is accepted by Spring with shared BACKEND_JWT_SECRET"

LOGIN_JSON="$(json_post /api/v1/auth/login "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")"
ACCESS_TOKEN="$(jq -r '.accessToken' <<<"$LOGIN_JSON")"
REFRESH_TOKEN="$(jq -r '.refreshToken' <<<"$LOGIN_JSON")"
[[ "$ACCESS_TOKEN" != "null" && -n "$ACCESS_TOKEN" ]] || fail "admin login access token missing"
[[ "$REFRESH_TOKEN" != "null" && -n "$REFRESH_TOKEN" ]] || fail "admin login refresh token missing"
pass "admin login returns access and refresh tokens"

ME_JSON="$(auth_get /api/v1/auth/me)"
require_json_field "$ME_JSON" '.username == "admin" and (.roles | index("SUPER_ADMIN")) and (has("passwordHash") | not)' "auth me works without password hash"

SPRING_TOKEN_NEST_ME="$(nest_auth_get /auth/me "$ACCESS_TOKEN")"
require_json_field "$SPRING_TOKEN_NEST_ME" '.username == "admin" and (.roles | index("SUPER_ADMIN"))' "Spring JWT is accepted by Nest with shared BACKEND_JWT_SECRET"

REFRESH_HASH="$(hash_value "$REFRESH_TOKEN")"
RAW_REFRESH_COUNT="$(psql_scalar "SELECT count(*) FROM refresh_tokens WHERE token_hash = '$REFRESH_TOKEN';")"
HASH_REFRESH_COUNT="$(psql_scalar "SELECT count(*) FROM refresh_tokens WHERE token_hash = '$REFRESH_HASH';")"
[[ "$RAW_REFRESH_COUNT" == "0" && "$HASH_REFRESH_COUNT" != "0" ]] || fail "refresh token raw value storage check failed"
pass "refresh token raw value is not stored"

ROTATED_JSON="$(json_post /api/v1/auth/refresh "{\"refreshToken\":\"$REFRESH_TOKEN\"}")"
ROTATED_REFRESH_TOKEN="$(jq -r '.refreshToken' <<<"$ROTATED_JSON")"
[[ "$ROTATED_REFRESH_TOKEN" != "$REFRESH_TOKEN" ]] || fail "refresh rotation did not issue a new token"
pass "refresh token rotation returns a new token"

expect_code 401 "old refresh token rejected after rotation" \
  -X POST "$API_BASE/api/v1/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"

expect_code 204 "logout revokes refresh token" \
  -X POST "$API_BASE/api/v1/auth/logout" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$ROTATED_REFRESH_TOKEN\"}"

expect_code 401 "logged out refresh token rejected" \
  -X POST "$API_BASE/api/v1/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$ROTATED_REFRESH_TOKEN\"}"

LOGIN_JSON="$(json_post /api/v1/auth/login "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")"
ACCESS_TOKEN="$(jq -r '.accessToken' <<<"$LOGIN_JSON")"

REGION_ID="$(auth_get /api/v1/regions | jq -r '.[] | select(.code == "BISHKEK") | .id' | head -n1)"
SERVICE_ID="$(auth_get /api/v1/services | jq -r '.[] | select(.code == "TS_PRIMARY_REGISTRATION") | .id' | head -n1)"
[[ -n "$REGION_ID" && "$REGION_ID" != "null" ]] || fail "seed region BISHKEK missing"
[[ -n "$SERVICE_ID" && "$SERVICE_ID" != "null" ]] || fail "seed service TS_PRIMARY_REGISTRATION missing"
pass "seeded directories are readable"

DEPARTMENT_JSON="$(auth_post /api/v1/departments "{\"regionId\":\"$REGION_ID\",\"code\":\"SMOKE_$RUN_ID\",\"name\":\"Smoke Department $RUN_ID\",\"address\":\"Smoke address\",\"timezone\":\"Asia/Bishkek\"}")"
DEPARTMENT_ID="$(jq -r '.id' <<<"$DEPARTMENT_JSON")"
ROOM_JSON="$(auth_post "/api/v1/departments/$DEPARTMENT_ID/rooms" '{"code":"ROOM1","name":"Smoke Room","floor":"1"}')"
ROOM_ID="$(jq -r '.id' <<<"$ROOM_JSON")"
HALL_JSON="$(auth_post "/api/v1/departments/$DEPARTMENT_ID/halls" "{\"officeRoomId\":\"$ROOM_ID\",\"code\":\"HALL1\",\"name\":\"Smoke Hall\"}")"
HALL_ID="$(jq -r '.id' <<<"$HALL_JSON")"
WINDOW_JSON="$(auth_post "/api/v1/departments/$DEPARTMENT_ID/windows" "{\"hallId\":\"$HALL_ID\",\"code\":\"WIN1\",\"displayName\":\"Window 1\"}")"
WINDOW_ID="$(jq -r '.id' <<<"$WINDOW_JSON")"
auth_post "/api/v1/windows/$WINDOW_ID/open" '{}' >/dev/null
auth_post "/api/v1/departments/$DEPARTMENT_ID/services/$SERVICE_ID" '{"onlineBookingEnabled":true,"terminalEnabled":true,"qrEnabled":true,"dailyLimit":100}' >/dev/null
pass "minimum queue/booking directories created"

BLOCKED_USER="blocked_$RUN_ID"
BLOCKED_JSON="$(auth_post /api/v1/users "{\"username\":\"$BLOCKED_USER\",\"password\":\"Blocked123!\",\"fullName\":\"Blocked User\",\"roleCodes\":[\"OPERATOR\"]}")"
BLOCKED_ID="$(jq -r '.id' <<<"$BLOCKED_JSON")"
require_json_field "$BLOCKED_JSON" '(has("passwordHash") | not)' "user create response excludes password hash"
curl -fsS -X PATCH "$API_BASE/api/v1/users/$BLOCKED_ID/status" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"status":"BLOCKED"}' >/dev/null
expect_code 403 "blocked user cannot login" \
  -X POST "$API_BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$BLOCKED_USER\",\"password\":\"Blocked123!\"}"

AUDITOR_USER="auditor_$RUN_ID"
auth_post /api/v1/users "{\"username\":\"$AUDITOR_USER\",\"password\":\"Auditor123!\",\"fullName\":\"Audit User\",\"roleCodes\":[\"AUDITOR\"]}" >/dev/null
AUDITOR_LOGIN="$(json_post /api/v1/auth/login "{\"username\":\"$AUDITOR_USER\",\"password\":\"Auditor123!\"}")"
AUDITOR_ACCESS="$(jq -r '.accessToken' <<<"$AUDITOR_LOGIN")"
expect_code 200 "AUDITOR can read departments" \
  "$API_BASE/api/v1/departments" \
  -H "Authorization: Bearer $AUDITOR_ACCESS"
expect_code 403 "AUDITOR cannot mutate regions" \
  -X POST "$API_BASE/api/v1/regions" \
  -H "Authorization: Bearer $AUDITOR_ACCESS" \
  -H 'Content-Type: application/json' \
  -d "{\"code\":\"AUDIT_DENY_$RUN_ID\",\"name\":\"Audit Deny\"}"

CREATE_TICKET_BODY="{\"departmentId\":\"$DEPARTMENT_ID\",\"serviceId\":\"$SERVICE_ID\",\"source\":\"ADMIN_CREATED\",\"citizenFullName\":\"Smoke Citizen\"}"
TICKET_JSON="$(auth_post /api/v1/tickets "$CREATE_TICKET_BODY")"
TICKET_ID="$(jq -r '.id' <<<"$TICKET_JSON")"
TICKET_NUMBER="$(jq -r '.ticketNumber' <<<"$TICKET_JSON")"
[[ "$TICKET_NUMBER" == "ТС-001" ]] || fail "expected first ticket number ТС-001, got $TICKET_NUMBER"
pass "ticket number generated as ТС-001"

CALLED_JSON="$(auth_post /api/v1/tickets/call-next "{\"departmentId\":\"$DEPARTMENT_ID\",\"windowId\":\"$WINDOW_ID\",\"serviceIds\":[\"$SERVICE_ID\"]}")"
[[ "$(jq -r '.id' <<<"$CALLED_JSON")" == "$TICKET_ID" && "$(jq -r '.status' <<<"$CALLED_JSON")" == "CALLED" ]] || fail "call-next did not call the waiting ticket"
pass "call-next returns next waiting ticket"

expect_code 404 "call-next cannot call same ticket twice" \
  -X POST "$API_BASE/api/v1/tickets/call-next" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"departmentId\":\"$DEPARTMENT_ID\",\"windowId\":\"$WINDOW_ID\",\"serviceIds\":[\"$SERVICE_ID\"]}"

START_JSON="$(auth_post "/api/v1/tickets/$TICKET_ID/start" '{}')"
[[ "$(jq -r '.status' <<<"$START_JSON")" == "IN_SERVICE" ]] || fail "ticket start failed"
PAUSE_JSON="$(auth_post "/api/v1/tickets/$TICKET_ID/pause" '{"comment":"Smoke pause"}')"
[[ "$(jq -r '.status' <<<"$PAUSE_JSON")" == "PAUSED" ]] || fail "ticket pause failed"
RESUME_JSON="$(auth_post "/api/v1/tickets/$TICKET_ID/resume" '{}')"
[[ "$(jq -r '.status' <<<"$RESUME_JSON")" == "IN_SERVICE" ]] || fail "ticket resume failed"
COMPLETE_JSON="$(auth_post "/api/v1/tickets/$TICKET_ID/complete" '{}')"
[[ "$(jq -r '.status' <<<"$COMPLETE_JSON")" == "COMPLETED" ]] || fail "ticket complete failed"
pass "ticket lifecycle CALLED -> IN_SERVICE -> PAUSED -> IN_SERVICE -> COMPLETED works"

expect_code 409 "invalid ticket transition returns conflict" \
  -X POST "$API_BASE/api/v1/tickets/$TICKET_ID/start" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{}'

CANCEL_TICKET="$(auth_post /api/v1/tickets "$CREATE_TICKET_BODY")"
CANCEL_TICKET_ID="$(jq -r '.id' <<<"$CANCEL_TICKET")"
CANCELLED_JSON="$(auth_post "/api/v1/tickets/$CANCEL_TICKET_ID/cancel" '{"comment":"Client request"}')"
[[ "$(jq -r '.status' <<<"$CANCELLED_JSON")" == "CANCELLED" ]] || fail "ticket cancel failed"
pass "ticket cancel accepts valid comment"

NOSHOW_TICKET="$(auth_post /api/v1/tickets "$CREATE_TICKET_BODY")"
NOSHOW_TICKET_ID="$(jq -r '.id' <<<"$NOSHOW_TICKET")"
auth_post "/api/v1/tickets/$NOSHOW_TICKET_ID/call" "{\"windowId\":\"$WINDOW_ID\"}" >/dev/null
NOSHOW_JSON="$(auth_post "/api/v1/tickets/$NOSHOW_TICKET_ID/no-show" '{}')"
[[ "$(jq -r '.status' <<<"$NOSHOW_JSON")" == "NO_SHOW" ]] || fail "ticket no-show failed"
pass "ticket no-show works from CALLED"

TV_TICKET="$(auth_post /api/v1/tickets "$CREATE_TICKET_BODY")"
TV_TICKET_ID="$(jq -r '.id' <<<"$TV_TICKET")"
auth_post "/api/v1/tickets/$TV_TICKET_ID/call" "{\"windowId\":\"$WINDOW_ID\"}" >/dev/null

TICKET_EVENT_COUNT="$(psql_scalar "SELECT count(*) FROM ticket_events WHERE ticket_id = '$TICKET_ID';")"
TICKET_AUDIT_COUNT="$(psql_scalar "SELECT count(*) FROM audit_logs WHERE entity_type = 'TICKET';")"
[[ "$TICKET_EVENT_COUNT" -ge 5 && "$TICKET_AUDIT_COUNT" -ge 1 ]] || fail "ticket events/audit rows missing"
pass "ticket_events and audit_logs are created"

TERMINAL_DEVICE="$(auth_post /api/v1/devices/terminals "{\"departmentId\":\"$DEPARTMENT_ID\",\"code\":\"TERM_$RUN_ID\",\"name\":\"Smoke Terminal\"}")"
TERMINAL_ID="$(jq -r '.device.id' <<<"$TERMINAL_DEVICE")"
TERMINAL_TOKEN="$(jq -r '.deviceToken' <<<"$TERMINAL_DEVICE")"
TV_DEVICE="$(auth_post /api/v1/devices/tv-displays "{\"departmentId\":\"$DEPARTMENT_ID\",\"hallId\":\"$HALL_ID\",\"code\":\"TV_$RUN_ID\",\"name\":\"Smoke TV\"}")"
TV_DISPLAY_ID="$(jq -r '.device.id' <<<"$TV_DEVICE")"
TV_TOKEN="$(jq -r '.deviceToken' <<<"$TV_DEVICE")"
[[ "$TERMINAL_TOKEN" != "null" && "$TV_TOKEN" != "null" ]] || fail "device provisioning token missing"
pass "terminal and TV provisioning returns one-time device tokens"

TERMINAL_CONFIG="$(curl -fsS "$API_BASE/api/v1/terminal/$TERMINAL_ID/config" -H "X-Device-Token: $TERMINAL_TOKEN")"
require_json_field "$TERMINAL_CONFIG" --arg service "$SERVICE_ID" '(.serviceIds | index($service))' "terminal config is department-bound"
TERMINAL_TICKET="$(curl -fsS -X POST "$API_BASE/api/v1/terminal/$TERMINAL_ID/tickets" \
  -H "X-Device-Token: $TERMINAL_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"departmentId\":\"$DEPARTMENT_ID\",\"serviceId\":\"$SERVICE_ID\",\"citizenPin\":\"123\"}")"
[[ "$(jq -r '.source' <<<"$TERMINAL_TICKET")" == "TERMINAL" ]] || fail "terminal ticket source mismatch"
pass "terminal device can create ticket for configured department"

expect_code 403 "terminal cannot create ticket for another department" \
  -X POST "$API_BASE/api/v1/terminal/$TERMINAL_ID/tickets" \
  -H "X-Device-Token: $TERMINAL_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"departmentId\":\"00000000-0000-0000-0000-000000000001\",\"serviceId\":\"$SERVICE_ID\"}"

TV_SNAPSHOT="$(curl -fsS "$API_BASE/api/v1/tv/displays/$TV_DISPLAY_ID/snapshot" -H "X-Device-Token: $TV_TOKEN")"
require_json_field "$TV_SNAPSHOT" --arg ticket "$TV_TICKET_ID" '(.tickets | map(.id) | index($ticket))' "TV snapshot returns called tickets"
TV_SSE_OUT="$TMP_DIR/tv-sse.txt"
if timeout 3 curl -fsS -N "$API_BASE/api/v1/tv/displays/$TV_DISPLAY_ID/stream" \
  -H "X-Device-Token: $TV_TOKEN" >"$TV_SSE_OUT"; then
  :
else
  sse_status="$?"
  [[ "$sse_status" == "124" ]] || fail "TV SSE endpoint failed with exit $sse_status"
fi
grep -q 'event:connected' "$TV_SSE_OUT" || fail "TV SSE did not send connected event"
pass "TV SSE endpoint connects and streams"

GENERATE_JSON="$(auth_post /api/v1/booking/slots/generate "{\"departmentId\":\"$DEPARTMENT_ID\",\"serviceId\":\"$SERVICE_ID\",\"fromDate\":\"$TODAY\",\"toDate\":\"$TODAY\",\"intervalMinutes\":15,\"capacity\":1,\"overwrite\":false}")"
require_json_field "$GENERATE_JSON" '(.created + .skipped) > 0' "booking slots generate"

AVAILABLE_DATES="$(curl -fsS "$API_BASE/api/v1/booking/available-dates?departmentId=$DEPARTMENT_ID&serviceId=$SERVICE_ID&fromDate=$YESTERDAY&source=WEBSITE_CABINET" -H "Authorization: Bearer $ACCESS_TOKEN")"
require_json_field "$AVAILABLE_DATES" --arg today "$TODAY" '(.dates | length) > 0 and all(.dates[]; . >= $today)' "available-dates excludes past dates"

SLOTS_JSON="$(curl -fsS "$API_BASE/api/v1/booking/slots?departmentId=$DEPARTMENT_ID&serviceId=$SERVICE_ID&date=$TODAY&source=WEBSITE_CABINET" -H "Authorization: Bearer $ACCESS_TOKEN")"
SLOT_IDS=($(jq -r '.[].id' <<<"$SLOTS_JSON" | head -n 8))
[[ "${#SLOT_IDS[@]}" -ge 5 ]] || fail "not enough available booking slots for smoke"
pass "available-slots returns active slots with capacity"

BOOKING_BODY_1="{\"departmentId\":\"$DEPARTMENT_ID\",\"serviceId\":\"$SERVICE_ID\",\"slotId\":\"${SLOT_IDS[0]}\",\"source\":\"WEBSITE_CABINET\",\"externalId\":\"cabinet-$RUN_ID-1\",\"citizenFullName\":\"Booking Citizen\",\"citizenPhone\":\"+996700000000\"}"
BOOKING_JSON="$(json_post /api/v1/booking "$BOOKING_BODY_1" -H "Authorization: Bearer $ACCESS_TOKEN" -H "Idempotency-Key: booking-$RUN_ID-1")"
BOOKING_ID="$(jq -r '.id' <<<"$BOOKING_JSON")"
[[ "$(jq -r '.status' <<<"$BOOKING_JSON")" == "CONFIRMED" ]] || fail "booking create did not confirm"
pass "create booking locks slot and confirms booking"

REPLAY_JSON="$(json_post /api/v1/booking "$BOOKING_BODY_1" -H "Authorization: Bearer $ACCESS_TOKEN" -H "Idempotency-Key: booking-$RUN_ID-1")"
[[ "$(jq -r '.id' <<<"$REPLAY_JSON")" == "$BOOKING_ID" ]] || fail "idempotency replay did not return original booking"
pass "duplicate idempotency key with same body replays response"

expect_code 409 "same idempotency key with different body conflicts" \
  -X POST "$API_BASE/api/v1/booking" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: booking-$RUN_ID-1" \
  -d "{\"departmentId\":\"$DEPARTMENT_ID\",\"serviceId\":\"$SERVICE_ID\",\"slotId\":\"${SLOT_IDS[1]}\",\"source\":\"WEBSITE_CABINET\",\"externalId\":\"cabinet-$RUN_ID-conflict\"}"

expect_code 409 "full slot returns BOOKING_SLOT_NOT_AVAILABLE" \
  -X POST "$API_BASE/api/v1/booking" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: booking-$RUN_ID-full" \
  -d "{\"departmentId\":\"$DEPARTMENT_ID\",\"serviceId\":\"$SERVICE_ID\",\"slotId\":\"${SLOT_IDS[0]}\",\"source\":\"WEBSITE_CABINET\",\"externalId\":\"cabinet-$RUN_ID-full\"}"

CANCEL_BOOKING_JSON="$(auth_post "/api/v1/booking/$BOOKING_ID/cancel" '{"comment":"Client request"}')"
[[ "$(jq -r '.status' <<<"$CANCEL_BOOKING_JSON")" == "CANCELLED" ]] || fail "booking cancel failed"
SLOT_REMAINING_AFTER_CANCEL="$(curl -fsS "$API_BASE/api/v1/booking/slots?departmentId=$DEPARTMENT_ID&serviceId=$SERVICE_ID&date=$TODAY&source=WEBSITE_CABINET" -H "Authorization: Bearer $ACCESS_TOKEN" | jq -r --arg slot "${SLOT_IDS[0]}" '.[] | select(.id == $slot) | .remaining')"
[[ "$SLOT_REMAINING_AFTER_CANCEL" == "1" ]] || fail "booking cancel did not decrement slot count"
pass "cancel booking decrements booked_count"

BOOKING_BODY_2="{\"departmentId\":\"$DEPARTMENT_ID\",\"serviceId\":\"$SERVICE_ID\",\"slotId\":\"${SLOT_IDS[0]}\",\"source\":\"WEBSITE_CABINET\",\"externalId\":\"cabinet-$RUN_ID-checkin\"}"
CHECKIN_BOOKING="$(json_post /api/v1/booking "$BOOKING_BODY_2" -H "Authorization: Bearer $ACCESS_TOKEN" -H "Idempotency-Key: booking-$RUN_ID-checkin")"
CHECKIN_BOOKING_ID="$(jq -r '.id' <<<"$CHECKIN_BOOKING")"
CHECKIN_JSON="$(auth_post "/api/v1/booking/$CHECKIN_BOOKING_ID/check-in" '{}')"
[[ "$(jq -r '.status' <<<"$CHECKIN_JSON")" == "CHECKED_IN" && "$(jq -r '.ticketId' <<<"$CHECKIN_JSON")" != "null" ]] || fail "booking check-in did not create linked ticket"
pass "booking check-in creates linked ticket"

BOOKING_BODY_3="{\"departmentId\":\"$DEPARTMENT_ID\",\"serviceId\":\"$SERVICE_ID\",\"slotId\":\"${SLOT_IDS[1]}\",\"source\":\"WEBSITE_CABINET\",\"externalId\":\"cabinet-$RUN_ID-expire\"}"
EXPIRE_BOOKING="$(json_post /api/v1/booking "$BOOKING_BODY_3" -H "Authorization: Bearer $ACCESS_TOKEN" -H "Idempotency-Key: booking-$RUN_ID-expire")"
EXPIRE_BOOKING_ID="$(jq -r '.id' <<<"$EXPIRE_BOOKING")"
EXPIRE_JSON="$(auth_post "/api/v1/booking/$EXPIRE_BOOKING_ID/expire" '{}')"
[[ "$(jq -r '.status' <<<"$EXPIRE_JSON")" == "EXPIRED" ]] || fail "booking expire failed"
pass "booking expire endpoint works"

BOOKING_EVENT_COUNT="$(psql_scalar "SELECT count(*) FROM booking_events WHERE booking_id = '$CHECKIN_BOOKING_ID';")"
BOOKING_AUDIT_COUNT="$(psql_scalar "SELECT count(*) FROM audit_logs WHERE entity_type = 'BOOKING';")"
[[ "$BOOKING_EVENT_COUNT" -ge 2 && "$BOOKING_AUDIT_COUNT" -ge 1 ]] || fail "booking events/audit rows missing"
pass "booking_events and audit_logs are created"

TICKET_QUEUE_MESSAGES="$($COMPOSE exec -T rabbitmq rabbitmqctl list_queues name messages --formatter json | jq -r '.[] | select(.name == "equeue.ticket-events") | .messages')"
BOOKING_QUEUE_MESSAGES="$($COMPOSE exec -T rabbitmq rabbitmqctl list_queues name messages --formatter json | jq -r '.[] | select(.name == "equeue.booking-events") | .messages')"
[[ "${TICKET_QUEUE_MESSAGES:-0}" -gt 0 && "${BOOKING_QUEUE_MESSAGES:-0}" -gt 0 ]] || fail "RabbitMQ domain event queues did not receive messages"
pass "RabbitMQ ticket and booking events publish"

CABINET_DATES="$(curl -fsS "$NEST_BASE/external/cabinet/booking/available-dates?departmentId=$DEPARTMENT_ID&serviceId=$SERVICE_ID&fromDate=$TODAY" \
  -H 'X-API-Key: dev-cabinet-key' \
  -H "X-Request-Id: smoke-cabinet-dates-$RUN_ID")"
require_json_field "$CABINET_DATES" '.dates | length > 0' "Nest cabinet available-dates calls Spring"

CABINET_SLOTS="$(curl -fsS "$NEST_BASE/external/cabinet/booking/slots?departmentId=$DEPARTMENT_ID&serviceId=$SERVICE_ID&date=$TODAY" \
  -H 'X-API-Key: dev-cabinet-key' \
  -H "X-Request-Id: smoke-cabinet-slots-$RUN_ID")"
CABINET_SLOT_ID="$(jq -r '.[0].id' <<<"$CABINET_SLOTS")"
[[ "$CABINET_SLOT_ID" != "null" && -n "$CABINET_SLOT_ID" ]] || fail "cabinet slots did not return a slot"
pass "Nest cabinet slots calls Spring"

CABINET_BOOKING="$(curl -fsS -X POST "$NEST_BASE/external/cabinet/booking" \
  -H 'X-API-Key: dev-cabinet-key' \
  -H "X-Request-Id: smoke-cabinet-create-$RUN_ID" \
  -H "Idempotency-Key: cabinet-$RUN_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"departmentId\":\"$DEPARTMENT_ID\",\"serviceId\":\"$SERVICE_ID\",\"slotId\":\"$CABINET_SLOT_ID\",\"externalBookingId\":\"cabinet-ext-$RUN_ID\"}")"
CABINET_BOOKING_ID="$(jq -r '.id' <<<"$CABINET_BOOKING")"
curl -fsS "$NEST_BASE/external/cabinet/booking/$CABINET_BOOKING_ID/status" -H 'X-API-Key: dev-cabinet-key' >/dev/null
curl -fsS -X POST "$NEST_BASE/external/cabinet/booking/$CABINET_BOOKING_ID/cancel" \
  -H 'X-API-Key: dev-cabinet-key' \
  -H "Idempotency-Key: cabinet-cancel-$RUN_ID" >/dev/null
pass "Nest cabinet create/status/cancel calls Spring"

FRESH_SLOTS="$(curl -fsS "$API_BASE/api/v1/booking/slots?departmentId=$DEPARTMENT_ID&serviceId=$SERVICE_ID&date=$TODAY&source=TUNDUK" -H "Authorization: Bearer $ACCESS_TOKEN")"
TUNDUK_SLOT_ID="$(jq -r '.[0].id' <<<"$FRESH_SLOTS")"
TUNDUK_BOOKING="$(curl -fsS -X POST "$NEST_BASE/external/tunduk/bookings" \
  -H 'X-API-Key: dev-tunduk-key' \
  -H "Idempotency-Key: tunduk-$RUN_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"departmentId\":\"$DEPARTMENT_ID\",\"serviceId\":\"$SERVICE_ID\",\"slotId\":\"$TUNDUK_SLOT_ID\",\"externalBookingId\":\"tunduk-ext-$RUN_ID\"}")"
curl -fsS "$NEST_BASE/external/tunduk/bookings/tunduk-ext-$RUN_ID/status" -H 'X-API-Key: dev-tunduk-key' >/dev/null
curl -fsS -X POST "$NEST_BASE/external/tunduk/bookings/tunduk-ext-$RUN_ID/cancel" \
  -H 'X-API-Key: dev-tunduk-key' \
  -H "Idempotency-Key: tunduk-cancel-$RUN_ID" >/dev/null
pass "Nest Tunduk create/status/cancel calls Spring"

ZENOSS_TICKET="$(curl -fsS -X POST "$NEST_BASE/external/zenoss/tickets" \
  -H 'X-API-Key: dev-zenoss-key' \
  -H "X-Request-Id: zenoss-ticket-$RUN_ID" \
  -H "Idempotency-Key: zenoss-ticket-$RUN_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"departmentId\":\"$DEPARTMENT_ID\",\"serviceId\":\"$SERVICE_ID\",\"externalTicketId\":\"zenoss-ticket-$RUN_ID\"}")"
ZENOSS_TICKET_ID="$(jq -r '.id' <<<"$ZENOSS_TICKET")"
curl -fsS "$NEST_BASE/external/zenoss/tickets/$ZENOSS_TICKET_ID/status" -H 'X-API-Key: dev-zenoss-key' >/dev/null
pass "Nest Zenoss ticket create/status calls Spring"

ZENOSS_SLOT_ID="$(curl -fsS "$API_BASE/api/v1/booking/slots?departmentId=$DEPARTMENT_ID&serviceId=$SERVICE_ID&date=$TODAY&source=CRM_ZENOSS" -H "Authorization: Bearer $ACCESS_TOKEN" | jq -r '.[0].id')"
ZENOSS_BOOKING="$(curl -fsS -X POST "$NEST_BASE/external/zenoss/bookings" \
  -H 'X-API-Key: dev-zenoss-key' \
  -H "Idempotency-Key: zenoss-booking-$RUN_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"departmentId\":\"$DEPARTMENT_ID\",\"serviceId\":\"$SERVICE_ID\",\"slotId\":\"$ZENOSS_SLOT_ID\",\"externalBookingId\":\"zenoss-booking-$RUN_ID\"}")"
ZENOSS_BOOKING_ID="$(jq -r '.id' <<<"$ZENOSS_BOOKING")"
curl -fsS "$NEST_BASE/external/zenoss/bookings/$ZENOSS_BOOKING_ID/status" -H 'X-API-Key: dev-zenoss-key' >/dev/null
pass "Nest Zenoss booking create/status calls Spring"

REQUEST_ID_FORWARDED="$(psql_scalar "SELECT count(*) FROM audit_logs WHERE request_id IN ('smoke-cabinet-create-$RUN_ID', 'zenoss-ticket-$RUN_ID');")"
[[ "$REQUEST_ID_FORWARDED" -ge 1 ]] || fail "forwarded X-Request-Id was not observed in audit logs"
pass "X-Request-Id forwarded through Nest to Spring"

printf 'LIVE_SMOKE_OK department=%s service=%s window=%s\n' "$DEPARTMENT_ID" "$SERVICE_ID" "$WINDOW_ID"
