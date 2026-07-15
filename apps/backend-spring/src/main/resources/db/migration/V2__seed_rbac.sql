INSERT INTO permissions (code, description) VALUES
  ('USER_READ', 'Read users'),
  ('USER_CREATE', 'Create users'),
  ('USER_UPDATE', 'Update users'),
  ('USER_BLOCK', 'Block users'),
  ('ROLE_READ', 'Read roles'),
  ('ROLE_CREATE', 'Create roles'),
  ('ROLE_UPDATE', 'Update roles'),
  ('ROLE_ASSIGN_PERMISSION', 'Assign permissions to roles'),
  ('REGION_READ', 'Read regions'),
  ('REGION_CREATE', 'Create regions'),
  ('REGION_UPDATE', 'Update regions'),
  ('DEPARTMENT_READ', 'Read departments'),
  ('DEPARTMENT_CREATE', 'Create departments'),
  ('DEPARTMENT_UPDATE', 'Update departments'),
  ('DEPARTMENT_CLOSE', 'Close departments'),
  ('WINDOW_READ', 'Read windows'),
  ('WINDOW_CREATE', 'Create windows'),
  ('WINDOW_UPDATE', 'Update windows'),
  ('WINDOW_OPEN', 'Open windows'),
  ('WINDOW_CLOSE', 'Close windows'),
  ('WINDOW_ASSIGN_EMPLOYEE', 'Assign employees to windows'),
  ('SERVICE_READ', 'Read services'),
  ('SERVICE_CREATE', 'Create services'),
  ('SERVICE_UPDATE', 'Update services'),
  ('SERVICE_ASSIGN_TO_DEPARTMENT', 'Assign services to departments'),
  ('SERVICE_ASSIGN_TO_EMPLOYEE', 'Assign services to employees'),
  ('TICKET_READ', 'Read tickets'),
  ('TICKET_CREATE', 'Create tickets'),
  ('TICKET_CALL', 'Call tickets'),
  ('TICKET_START', 'Start tickets'),
  ('TICKET_PAUSE', 'Pause tickets'),
  ('TICKET_RESUME', 'Resume tickets'),
  ('TICKET_COMPLETE', 'Complete tickets'),
  ('TICKET_CANCEL', 'Cancel tickets'),
  ('TICKET_NO_SHOW', 'Mark tickets no-show'),
  ('TICKET_TRANSFER', 'Transfer tickets'),
  ('BOOKING_READ', 'Read bookings'),
  ('BOOKING_CREATE', 'Create bookings'),
  ('BOOKING_CANCEL', 'Cancel bookings'),
  ('BOOKING_CHECK_IN', 'Check in bookings'),
  ('BOOKING_SLOT_READ', 'Read booking slots'),
  ('BOOKING_SLOT_MANAGE', 'Manage booking slots'),
  ('TERMINAL_READ', 'Read terminals'),
  ('TERMINAL_CREATE', 'Create terminals'),
  ('TERMINAL_UPDATE', 'Update terminals'),
  ('TERMINAL_CONFIGURE', 'Configure terminals'),
  ('TV_READ', 'Read TV displays'),
  ('TV_CREATE', 'Create TV displays'),
  ('TV_UPDATE', 'Update TV displays'),
  ('TV_CONFIGURE', 'Configure TV displays'),
  ('REPORT_READ', 'Read reports'),
  ('REPORT_EXPORT', 'Export reports'),
  ('AUDIT_READ', 'Read audit logs'),
  ('INTEGRATION_KEY_READ', 'Read integration keys'),
  ('INTEGRATION_KEY_CREATE', 'Create integration keys'),
  ('INTEGRATION_KEY_REVOKE', 'Revoke integration keys')
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (code, name, system_role) VALUES
  ('SUPER_ADMIN', 'Super administrator', true),
  ('ADMIN', 'Administrator', true),
  ('DEPARTMENT_MANAGER', 'Department manager', true),
  ('OPERATOR', 'Operator', true),
  ('AUDITOR', 'Auditor', true),
  ('INTEGRATION_SERVICE', 'Integration service', true),
  ('TERMINAL_DEVICE', 'Terminal device', true),
  ('TV_DEVICE', 'TV display device', true)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
  'USER_READ', 'USER_CREATE', 'USER_UPDATE', 'USER_BLOCK',
  'ROLE_READ',
  'REGION_READ', 'REGION_CREATE', 'REGION_UPDATE',
  'DEPARTMENT_READ', 'DEPARTMENT_CREATE', 'DEPARTMENT_UPDATE', 'DEPARTMENT_CLOSE',
  'WINDOW_READ', 'WINDOW_CREATE', 'WINDOW_UPDATE', 'WINDOW_OPEN', 'WINDOW_CLOSE', 'WINDOW_ASSIGN_EMPLOYEE',
  'SERVICE_READ', 'SERVICE_CREATE', 'SERVICE_UPDATE', 'SERVICE_ASSIGN_TO_DEPARTMENT', 'SERVICE_ASSIGN_TO_EMPLOYEE',
  'TICKET_READ', 'BOOKING_READ', 'BOOKING_SLOT_READ', 'BOOKING_SLOT_MANAGE',
  'REPORT_READ', 'REPORT_EXPORT', 'AUDIT_READ',
  'TERMINAL_READ', 'TERMINAL_CREATE', 'TERMINAL_UPDATE', 'TERMINAL_CONFIGURE',
  'TV_READ', 'TV_CREATE', 'TV_UPDATE', 'TV_CONFIGURE'
)
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
  'DEPARTMENT_READ', 'DEPARTMENT_UPDATE',
  'WINDOW_READ', 'WINDOW_UPDATE', 'WINDOW_OPEN', 'WINDOW_CLOSE', 'WINDOW_ASSIGN_EMPLOYEE',
  'SERVICE_READ', 'SERVICE_ASSIGN_TO_EMPLOYEE',
  'TICKET_READ', 'BOOKING_READ', 'BOOKING_SLOT_READ', 'BOOKING_SLOT_MANAGE',
  'REPORT_READ'
)
WHERE r.code = 'DEPARTMENT_MANAGER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
  'TICKET_READ', 'TICKET_CALL', 'TICKET_START', 'TICKET_PAUSE', 'TICKET_RESUME',
  'TICKET_COMPLETE', 'TICKET_CANCEL', 'TICKET_NO_SHOW', 'TICKET_TRANSFER',
  'BOOKING_READ', 'BOOKING_CHECK_IN',
  'REGION_READ', 'DEPARTMENT_READ', 'SERVICE_READ',
  'WINDOW_READ', 'WINDOW_OPEN', 'WINDOW_CLOSE'
)
WHERE r.code = 'OPERATOR'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('AUDIT_READ', 'REPORT_READ', 'DEPARTMENT_READ', 'SERVICE_READ', 'TICKET_READ', 'BOOKING_READ')
WHERE r.code = 'AUDITOR'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('TICKET_CREATE', 'TICKET_READ', 'BOOKING_CREATE', 'BOOKING_READ', 'BOOKING_CANCEL', 'DEPARTMENT_READ', 'SERVICE_READ')
WHERE r.code = 'INTEGRATION_SERVICE'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('TICKET_CREATE', 'DEPARTMENT_READ', 'SERVICE_READ')
WHERE r.code = 'TERMINAL_DEVICE'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('TV_READ', 'TICKET_READ', 'DEPARTMENT_READ')
WHERE r.code = 'TV_DEVICE'
ON CONFLICT DO NOTHING;

INSERT INTO users (username, password_hash, full_name, status)
VALUES ('admin', '$2b$10$pMVTvK7Zx.Wn/SmDsBmkAumMuw8aphUplUztGCtCSo1d87CwYGAYm', 'Bootstrap Admin', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'SUPER_ADMIN'
WHERE u.username = 'admin'
ON CONFLICT DO NOTHING;
