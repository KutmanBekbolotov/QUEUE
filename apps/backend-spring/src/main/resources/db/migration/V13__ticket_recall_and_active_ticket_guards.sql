ALTER TABLE tickets
  ADD COLUMN IF NOT EXISTS recalled_at timestamptz,
  ADD COLUMN IF NOT EXISTS recall_count integer NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_tickets_active_window_lookup
  ON tickets(current_window_id, status)
  WHERE current_window_id IS NOT NULL
    AND status IN ('CALLED', 'IN_SERVICE', 'PAUSED');

CREATE INDEX IF NOT EXISTS idx_tickets_active_operator_lookup
  ON tickets(current_operator_id, status)
  WHERE current_operator_id IS NOT NULL
    AND status IN ('CALLED', 'IN_SERVICE', 'PAUSED');

CREATE UNIQUE INDEX IF NOT EXISTS ux_tickets_one_active_per_window
  ON tickets(current_window_id)
  WHERE current_window_id IS NOT NULL
    AND status IN ('CALLED', 'IN_SERVICE', 'PAUSED');

CREATE UNIQUE INDEX IF NOT EXISTS ux_tickets_one_active_per_operator
  ON tickets(current_operator_id)
  WHERE current_operator_id IS NOT NULL
    AND status IN ('CALLED', 'IN_SERVICE', 'PAUSED');
