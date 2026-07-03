CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE permissions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code varchar(120) NOT NULL UNIQUE,
  description varchar(500),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE roles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code varchar(120) NOT NULL UNIQUE,
  name varchar(200) NOT NULL,
  system_role boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE role_permissions (
  role_id uuid NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission_id uuid NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  username varchar(120) NOT NULL UNIQUE,
  password_hash varchar(255) NOT NULL,
  full_name varchar(255),
  pin varchar(64),
  phone varchar(64),
  email varchar(255),
  status varchar(32) NOT NULL DEFAULT 'ACTIVE',
  token_version integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (status IN ('ACTIVE', 'BLOCKED', 'DISABLED'))
);

CREATE TABLE user_roles (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id uuid NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash varchar(128) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  replaced_by_hash varchar(128),
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by_ip varchar(80)
);

CREATE TABLE login_audit_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  username varchar(120) NOT NULL,
  user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  success boolean NOT NULL,
  reason varchar(255),
  ip varchar(80),
  user_agent varchar(500),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE regions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code varchar(80) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE departments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  region_id uuid NOT NULL REFERENCES regions(id),
  code varchar(80) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  address varchar(500),
  timezone varchar(80) NOT NULL DEFAULT 'Asia/Bishkek',
  active boolean NOT NULL DEFAULT true,
  closed boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE user_department_scopes (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, department_id)
);

CREATE TABLE department_working_hours (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  day_of_week smallint NOT NULL,
  opens_at time NOT NULL,
  closes_at time NOT NULL,
  break_starts_at time,
  break_ends_at time,
  active boolean NOT NULL DEFAULT true,
  CHECK (day_of_week BETWEEN 1 AND 7)
);

CREATE TABLE department_holidays (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  holiday_date date NOT NULL,
  reason varchar(255),
  UNIQUE (department_id, holiday_date)
);

CREATE TABLE office_rooms (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  code varchar(80) NOT NULL,
  name varchar(255) NOT NULL,
  floor varchar(80),
  active boolean NOT NULL DEFAULT true,
  UNIQUE (department_id, code)
);

CREATE TABLE halls (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  office_room_id uuid REFERENCES office_rooms(id) ON DELETE SET NULL,
  code varchar(80) NOT NULL,
  name varchar(255) NOT NULL,
  active boolean NOT NULL DEFAULT true,
  UNIQUE (department_id, code)
);

CREATE TABLE service_windows (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  hall_id uuid REFERENCES halls(id) ON DELETE SET NULL,
  code varchar(80) NOT NULL,
  display_name varchar(255) NOT NULL,
  active boolean NOT NULL DEFAULT true,
  open boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (department_id, code)
);

CREATE TABLE service_categories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code varchar(40) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  ticket_prefix varchar(20) NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE services (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  category_id uuid NOT NULL REFERENCES service_categories(id),
  code varchar(80) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  description text,
  default_duration_minutes integer NOT NULL DEFAULT 15,
  daily_limit integer,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE department_services (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  service_id uuid NOT NULL REFERENCES services(id) ON DELETE CASCADE,
  active boolean NOT NULL DEFAULT true,
  online_booking_enabled boolean NOT NULL DEFAULT false,
  terminal_enabled boolean NOT NULL DEFAULT true,
  qr_enabled boolean NOT NULL DEFAULT true,
  daily_limit integer,
  UNIQUE (department_id, service_id)
);

CREATE TABLE employee_service_assignments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  service_id uuid NOT NULL REFERENCES services(id) ON DELETE CASCADE,
  active boolean NOT NULL DEFAULT true,
  UNIQUE (user_id, department_id, service_id)
);

CREATE TABLE employee_window_assignments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  service_window_id uuid NOT NULL REFERENCES service_windows(id) ON DELETE CASCADE,
  active boolean NOT NULL DEFAULT true,
  assigned_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, service_window_id)
);

CREATE TABLE terminals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  code varchar(80) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  token_hash varchar(128) NOT NULL,
  active boolean NOT NULL DEFAULT true,
  last_seen_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE tv_displays (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  hall_id uuid REFERENCES halls(id) ON DELETE SET NULL,
  code varchar(80) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  token_hash varchar(128) NOT NULL,
  active boolean NOT NULL DEFAULT true,
  last_seen_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE booking_slots (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  service_id uuid NOT NULL REFERENCES services(id) ON DELETE CASCADE,
  slot_date date NOT NULL,
  starts_at time NOT NULL,
  ends_at time NOT NULL,
  capacity integer NOT NULL DEFAULT 1,
  reserved_count integer NOT NULL DEFAULT 0,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (capacity >= 0),
  CHECK (reserved_count >= 0),
  CHECK (reserved_count <= capacity),
  UNIQUE (department_id, service_id, slot_date, starts_at)
);

CREATE TABLE bookings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  department_id uuid NOT NULL REFERENCES departments(id),
  service_id uuid NOT NULL REFERENCES services(id),
  booking_slot_id uuid REFERENCES booking_slots(id) ON DELETE SET NULL,
  source varchar(40) NOT NULL,
  status varchar(40) NOT NULL DEFAULT 'CREATED',
  external_client_code varchar(80),
  external_booking_id varchar(160),
  citizen_full_name varchar(255),
  citizen_pin varchar(64),
  citizen_phone varchar(64),
  booked_date date NOT NULL,
  starts_at time NOT NULL,
  ends_at time NOT NULL,
  checked_in_at timestamptz,
  cancelled_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (status IN ('CREATED', 'CONFIRMED', 'CHECKED_IN', 'CANCELLED', 'EXPIRED')),
  UNIQUE (external_client_code, external_booking_id)
);

CREATE TABLE tickets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  department_id uuid NOT NULL REFERENCES departments(id),
  service_id uuid NOT NULL REFERENCES services(id),
  service_category_id uuid NOT NULL REFERENCES service_categories(id),
  booking_id uuid REFERENCES bookings(id) ON DELETE SET NULL,
  ticket_number varchar(40) NOT NULL,
  work_date date NOT NULL DEFAULT CURRENT_DATE,
  source varchar(40) NOT NULL,
  status varchar(40) NOT NULL,
  priority integer NOT NULL DEFAULT 0,
  citizen_full_name varchar(255),
  citizen_pin varchar(64),
  citizen_phone varchar(64),
  current_window_id uuid REFERENCES service_windows(id) ON DELETE SET NULL,
  current_operator_id uuid REFERENCES users(id) ON DELETE SET NULL,
  called_at timestamptz,
  started_at timestamptz,
  completed_at timestamptz,
  cancelled_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (status IN ('CREATED', 'WAITING', 'CALLED', 'IN_SERVICE', 'PAUSED', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'EXPIRED', 'TRANSFERRED')),
  CHECK (source IN ('TERMINAL', 'QR_SELF_SERVICE', 'WEBSITE_CABINET', 'TUNDUK', 'CRM_ZENOSS', 'ADMIN_CREATED')),
  UNIQUE (department_id, service_category_id, work_date, ticket_number)
);

CREATE TABLE ticket_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id uuid NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
  event_type varchar(80) NOT NULL,
  old_status varchar(40),
  new_status varchar(40),
  actor_type varchar(40),
  actor_id uuid,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ticket_sequences (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  service_category_id uuid NOT NULL REFERENCES service_categories(id) ON DELETE CASCADE,
  work_date date NOT NULL,
  last_number integer NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (department_id, service_category_id, work_date)
);

CREATE TABLE cancellation_reasons (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code varchar(80) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  active boolean NOT NULL DEFAULT true
);

CREATE TABLE pause_reasons (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code varchar(80) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  active boolean NOT NULL DEFAULT true
);

CREATE TABLE audit_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_type varchar(40) NOT NULL,
  actor_id uuid,
  action varchar(120) NOT NULL,
  entity_type varchar(120) NOT NULL,
  entity_id uuid,
  old_value jsonb,
  new_value jsonb,
  ip varchar(80),
  user_agent varchar(500),
  source varchar(80),
  request_id varchar(120),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE integration_clients (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code varchar(80) NOT NULL UNIQUE,
  name varchar(255) NOT NULL,
  auth_type varchar(40) NOT NULL,
  api_key_hash varchar(128),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE integration_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  client_code varchar(80) NOT NULL,
  external_request_id varchar(160),
  idempotency_key varchar(160),
  request_hash varchar(128) NOT NULL,
  response_body jsonb,
  status varchar(40) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (client_code, external_request_id),
  UNIQUE (client_code, idempotency_key)
);

CREATE TABLE report_exports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  requested_by_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  report_type varchar(120) NOT NULL,
  status varchar(40) NOT NULL DEFAULT 'REQUESTED',
  file_url varchar(1000),
  parameters jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);

CREATE TABLE system_settings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  key varchar(160) NOT NULL UNIQUE,
  value jsonb NOT NULL,
  description varchar(500),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_departments_region_id ON departments(region_id);
CREATE INDEX idx_tickets_department_status ON tickets(department_id, status, created_at);
CREATE INDEX idx_ticket_events_ticket_id ON ticket_events(ticket_id);
CREATE INDEX idx_bookings_department_date ON bookings(department_id, booked_date, status);
CREATE INDEX idx_booking_slots_lookup ON booking_slots(department_id, service_id, slot_date);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_integration_requests_lookup ON integration_requests(client_code, external_request_id, idempotency_key);
