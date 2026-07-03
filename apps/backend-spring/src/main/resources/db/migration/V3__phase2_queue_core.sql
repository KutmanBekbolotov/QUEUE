ALTER TABLE tickets
  ADD COLUMN IF NOT EXISTS ticket_prefix varchar(20),
  ADD COLUMN IF NOT EXISTS sequence_number integer,
  ADD COLUMN IF NOT EXISTS region_id uuid REFERENCES regions(id),
  ADD COLUMN IF NOT EXISTS office_room_id uuid REFERENCES office_rooms(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS hall_id uuid REFERENCES halls(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS booking_time timestamptz,
  ADD COLUMN IF NOT EXISTS service_started_at timestamptz,
  ADD COLUMN IF NOT EXISTS service_paused_at timestamptz,
  ADD COLUMN IF NOT EXISTS service_completed_at timestamptz,
  ADD COLUMN IF NOT EXISTS cancellation_reason_id uuid REFERENCES cancellation_reasons(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS pause_reason_id uuid REFERENCES pause_reasons(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS qr_token varchar(160),
  ADD COLUMN IF NOT EXISTS comment text,
  ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

UPDATE tickets
SET service_started_at = COALESCE(service_started_at, started_at),
    service_completed_at = COALESCE(service_completed_at, completed_at)
WHERE service_started_at IS NULL OR service_completed_at IS NULL;

ALTER TABLE ticket_events
  ADD COLUMN IF NOT EXISTS from_status varchar(40),
  ADD COLUMN IF NOT EXISTS to_status varchar(40),
  ADD COLUMN IF NOT EXISTS department_id uuid REFERENCES departments(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS window_id uuid REFERENCES service_windows(id) ON DELETE SET NULL;

UPDATE ticket_events
SET from_status = COALESCE(from_status, old_status),
    to_status = COALESCE(to_status, new_status)
WHERE from_status IS NULL OR to_status IS NULL;

ALTER TABLE ticket_sequences
  ADD COLUMN IF NOT EXISTS current_value integer NOT NULL DEFAULT 0;

UPDATE ticket_sequences
SET current_value = GREATEST(current_value, last_number)
WHERE current_value < last_number;

ALTER TABLE service_windows
  ADD COLUMN IF NOT EXISTS status varchar(32) NOT NULL DEFAULT 'CLOSED';

UPDATE service_windows
SET status = CASE
  WHEN active = false THEN 'INACTIVE'
  WHEN open = true THEN 'OPEN'
  ELSE 'CLOSED'
END;

CREATE INDEX IF NOT EXISTS idx_tickets_department_service_status_created
  ON tickets(department_id, service_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_tickets_work_date_department_category
  ON tickets(work_date, department_id, service_category_id);

CREATE INDEX IF NOT EXISTS idx_ticket_events_ticket_created
  ON ticket_events(ticket_id, created_at);

CREATE UNIQUE INDEX IF NOT EXISTS ux_ticket_sequences_department_category_work_date
  ON ticket_sequences(department_id, service_category_id, work_date);

CREATE INDEX IF NOT EXISTS idx_service_windows_department_status
  ON service_windows(department_id, status);

CREATE INDEX IF NOT EXISTS idx_employee_window_assignments_window_active
  ON employee_window_assignments(service_window_id, active);

CREATE INDEX IF NOT EXISTS idx_employee_service_assignments_user_service_active
  ON employee_service_assignments(user_id, service_id, active);

