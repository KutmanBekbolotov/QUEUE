INSERT INTO permissions (code, description) VALUES
  ('USER_DELETE', 'Permanently delete users'),
  ('DEPARTMENT_DELETE', 'Permanently delete departments and their data'),
  ('TERMINAL_DELETE', 'Permanently delete terminals'),
  ('TV_DELETE', 'Permanently delete TV displays')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
  'USER_DELETE',
  'DEPARTMENT_DELETE',
  'TERMINAL_DELETE',
  'TV_DELETE'
)
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;

ALTER TABLE bookings
  DROP CONSTRAINT IF EXISTS bookings_department_id_fkey;

ALTER TABLE bookings
  ADD CONSTRAINT bookings_department_id_fkey
  FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE CASCADE;

ALTER TABLE tickets
  DROP CONSTRAINT IF EXISTS tickets_department_id_fkey;

ALTER TABLE tickets
  ADD CONSTRAINT tickets_department_id_fkey
  FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE CASCADE;
