INSERT INTO users (username, password_hash, full_name, status)
VALUES (
  'admin',
  '$2a$10$18zFYunaR5aSTQ5V5I1c9OjIZN0y/C5T1kk0CJ1bH5HAdxhNgkSCi',
  'Bootstrap Admin',
  'ACTIVE'
)
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    full_name = EXCLUDED.full_name,
    status = 'ACTIVE',
    token_version = users.token_version + 1,
    updated_at = now();

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'SUPER_ADMIN'
WHERE u.username = 'admin'
ON CONFLICT DO NOTHING;
