INSERT INTO permissions (code, description)
VALUES ('TICKET_DELETE', 'Permanently delete tickets')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'TICKET_DELETE'
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;
