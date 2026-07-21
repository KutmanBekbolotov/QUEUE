CREATE TABLE IF NOT EXISTS operator_shifts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  operator_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  department_id uuid NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
  window_id uuid REFERENCES service_windows(id) ON DELETE SET NULL,
  status varchar(32) NOT NULL DEFAULT 'OPEN',
  opened_at timestamptz NOT NULL DEFAULT now(),
  closed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (status IN ('OPEN', 'CLOSED')),
  CHECK (closed_at IS NULL OR closed_at >= opened_at)
);

CREATE INDEX IF NOT EXISTS idx_operator_shifts_operator_opened
  ON operator_shifts(operator_id, opened_at DESC);

CREATE INDEX IF NOT EXISTS idx_operator_shifts_department_status
  ON operator_shifts(department_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS ux_operator_shifts_open_operator
  ON operator_shifts(operator_id)
  WHERE status = 'OPEN';

CREATE UNIQUE INDEX IF NOT EXISTS ux_operator_shifts_open_window
  ON operator_shifts(window_id)
  WHERE window_id IS NOT NULL
    AND status = 'OPEN';
