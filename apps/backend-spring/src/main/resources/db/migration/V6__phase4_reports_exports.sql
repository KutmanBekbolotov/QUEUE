ALTER TABLE report_exports
  ADD COLUMN IF NOT EXISTS export_format varchar(20) NOT NULL DEFAULT 'CSV',
  ADD COLUMN IF NOT EXISTS department_id uuid REFERENCES departments(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS filters_json jsonb NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN IF NOT EXISTS file_bucket varchar(160),
  ADD COLUMN IF NOT EXISTS file_key varchar(1000),
  ADD COLUMN IF NOT EXISTS file_name varchar(255),
  ADD COLUMN IF NOT EXISTS file_size_bytes bigint,
  ADD COLUMN IF NOT EXISTS error_message text,
  ADD COLUMN IF NOT EXISTS started_at timestamptz,
  ADD COLUMN IF NOT EXISTS expires_at timestamptz;

UPDATE report_exports
SET filters_json = COALESCE(filters_json, parameters, '{}'::jsonb),
    file_key = COALESCE(file_key, file_url),
    file_name = COALESCE(file_name, split_part(file_url, '/', greatest(1, array_length(string_to_array(file_url, '/'), 1)))),
    status = CASE status
      WHEN 'REQUESTED' THEN 'PENDING'
      ELSE status
    END
WHERE filters_json = '{}'::jsonb OR status = 'REQUESTED' OR file_key IS NULL;

ALTER TABLE report_exports
  ALTER COLUMN status SET DEFAULT 'PENDING';

INSERT INTO permissions (code, description) VALUES
  ('REPORT_VIEW_PERSONAL_DATA', 'View personal data in reports'),
  ('REPORT_EXPORT_PERSONAL_DATA', 'Export personal data in reports')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('REPORT_VIEW_PERSONAL_DATA', 'REPORT_EXPORT_PERSONAL_DATA')
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_report_exports_requested_by_created
  ON report_exports(requested_by_user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_report_exports_status_created
  ON report_exports(status, created_at);

CREATE INDEX IF NOT EXISTS idx_report_exports_status_expires
  ON report_exports(status, expires_at);

CREATE INDEX IF NOT EXISTS idx_tickets_department_created
  ON tickets(department_id, created_at);

CREATE INDEX IF NOT EXISTS idx_tickets_department_status_created
  ON tickets(department_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_tickets_department_source_created
  ON tickets(department_id, source, created_at);

CREATE INDEX IF NOT EXISTS idx_tickets_department_service_created
  ON tickets(department_id, service_id, created_at);

CREATE INDEX IF NOT EXISTS idx_tickets_served_by_created
  ON tickets(current_operator_id, created_at);

CREATE INDEX IF NOT EXISTS idx_bookings_department_booking_date
  ON bookings(department_id, booking_date);

CREATE INDEX IF NOT EXISTS idx_bookings_department_status_booking_date
  ON bookings(department_id, status, booking_date);

CREATE INDEX IF NOT EXISTS idx_ticket_events_ticket_created_reports
  ON ticket_events(ticket_id, created_at);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created
  ON audit_logs(created_at);

CREATE INDEX IF NOT EXISTS idx_integration_requests_client_created
  ON integration_requests(client_code, created_at);
