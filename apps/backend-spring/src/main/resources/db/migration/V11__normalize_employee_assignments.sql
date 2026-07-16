UPDATE employee_service_assignments esa
SET active = false
WHERE esa.active
  AND NOT EXISTS (
    SELECT 1
    FROM user_department_scopes uds
    WHERE uds.user_id = esa.user_id
      AND uds.department_id = esa.department_id
  );

UPDATE employee_window_assignments ewa
SET active = false
WHERE ewa.active
  AND NOT EXISTS (
    SELECT 1
    FROM service_windows sw
    JOIN user_department_scopes uds
      ON uds.user_id = ewa.user_id
     AND uds.department_id = sw.department_id
    WHERE sw.id = ewa.service_window_id
      AND sw.active
  );

WITH ranked AS (
  SELECT id,
         row_number() OVER (
           PARTITION BY service_window_id
           ORDER BY assigned_at DESC, id DESC
         ) AS row_number
  FROM employee_window_assignments
  WHERE active
)
UPDATE employee_window_assignments ewa
SET active = false
FROM ranked
WHERE ewa.id = ranked.id
  AND ranked.row_number > 1;

WITH ranked AS (
  SELECT id,
         row_number() OVER (
           PARTITION BY user_id
           ORDER BY assigned_at DESC, id DESC
         ) AS row_number
  FROM employee_window_assignments
  WHERE active
)
UPDATE employee_window_assignments ewa
SET active = false
FROM ranked
WHERE ewa.id = ranked.id
  AND ranked.row_number > 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_employee_window_assignments_active_user
  ON employee_window_assignments(user_id)
  WHERE active;

CREATE UNIQUE INDEX IF NOT EXISTS ux_employee_window_assignments_active_window
  ON employee_window_assignments(service_window_id)
  WHERE active;
