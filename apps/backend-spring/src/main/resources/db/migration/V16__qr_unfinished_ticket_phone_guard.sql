UPDATE tickets
SET citizen_phone = NULLIF(regexp_replace(citizen_phone, '[^0-9]', '', 'g'), '')
WHERE source = 'QR_SELF_SERVICE'
  AND citizen_phone IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tickets_unfinished_qr_phone_lookup
  ON tickets(department_id, citizen_phone, status, created_at)
  WHERE source = 'QR_SELF_SERVICE'
    AND citizen_phone IS NOT NULL
    AND status IN ('CREATED', 'WAITING', 'CALLED', 'IN_SERVICE', 'PAUSED');
