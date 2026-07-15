INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('REGION_READ', 'DEPARTMENT_READ')
WHERE r.code = 'OPERATOR'
ON CONFLICT DO NOTHING;
