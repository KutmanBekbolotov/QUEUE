ALTER TABLE booking_slots
  ADD COLUMN IF NOT EXISTS slot_start time,
  ADD COLUMN IF NOT EXISTS slot_end time,
  ADD COLUMN IF NOT EXISTS booked_count integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS status varchar(32) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

UPDATE booking_slots
SET slot_start = COALESCE(slot_start, starts_at),
    slot_end = COALESCE(slot_end, ends_at),
    booked_count = GREATEST(booked_count, reserved_count),
    status = CASE
      WHEN active = false THEN 'DISABLED'
      WHEN reserved_count >= capacity THEN 'FULL'
      ELSE status
    END
WHERE slot_start IS NULL OR slot_end IS NULL OR booked_count < reserved_count;

ALTER TABLE booking_slots
  ALTER COLUMN slot_start SET NOT NULL,
  ALTER COLUMN slot_end SET NOT NULL,
  ADD CONSTRAINT chk_booking_slots_status CHECK (status IN ('ACTIVE', 'DISABLED', 'FULL', 'EXPIRED')),
  ADD CONSTRAINT chk_booking_slots_booked_count CHECK (booked_count >= 0 AND booked_count <= capacity);

ALTER TABLE bookings
  ADD COLUMN IF NOT EXISTS booking_number varchar(80),
  ADD COLUMN IF NOT EXISTS ticket_id uuid REFERENCES tickets(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS external_id varchar(160),
  ADD COLUMN IF NOT EXISTS external_source varchar(40),
  ADD COLUMN IF NOT EXISTS idempotency_key varchar(160),
  ADD COLUMN IF NOT EXISTS slot_id uuid REFERENCES booking_slots(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS vehicle_number varchar(80),
  ADD COLUMN IF NOT EXISTS booking_date date,
  ADD COLUMN IF NOT EXISTS booking_start time,
  ADD COLUMN IF NOT EXISTS booking_end time,
  ADD COLUMN IF NOT EXISTS qr_token varchar(160),
  ADD COLUMN IF NOT EXISTS cancellation_reason_id uuid REFERENCES cancellation_reasons(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS cancel_comment text,
  ADD COLUMN IF NOT EXISTS expired_at timestamptz,
  ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;
ALTER TABLE bookings
  ADD CONSTRAINT bookings_status_check CHECK (status IN ('CREATED', 'CONFIRMED', 'CHECKED_IN', 'CANCELLED', 'EXPIRED', 'NO_SHOW'));

UPDATE bookings
SET external_id = COALESCE(external_id, external_booking_id),
    external_source = COALESCE(external_source, external_client_code, source),
    slot_id = COALESCE(slot_id, booking_slot_id),
    booking_date = COALESCE(booking_date, booked_date),
    booking_start = COALESCE(booking_start, starts_at),
    booking_end = COALESCE(booking_end, ends_at),
    booking_number = COALESCE(booking_number, 'B-' || upper(replace(id::text, '-', ''))::varchar(34)),
    qr_token = COALESCE(qr_token, encode(gen_random_bytes(32), 'base64'))
WHERE booking_date IS NULL OR booking_start IS NULL OR booking_end IS NULL OR booking_number IS NULL OR qr_token IS NULL;

ALTER TABLE bookings
  ALTER COLUMN booking_number SET NOT NULL,
  ALTER COLUMN booking_date SET NOT NULL,
  ALTER COLUMN booking_start SET NOT NULL,
  ALTER COLUMN booking_end SET NOT NULL,
  ALTER COLUMN qr_token SET NOT NULL;

ALTER TABLE integration_requests
  ADD COLUMN IF NOT EXISTS error_code varchar(120),
  ADD COLUMN IF NOT EXISTS response_status integer;

CREATE TABLE IF NOT EXISTS booking_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id uuid NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  event_type varchar(120) NOT NULL,
  from_status varchar(40),
  to_status varchar(40),
  actor_type varchar(40),
  actor_id uuid,
  department_id uuid REFERENCES departments(id) ON DELETE SET NULL,
  service_id uuid REFERENCES services(id) ON DELETE SET NULL,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_booking_slots_department_service_date_start
  ON booking_slots(department_id, service_id, slot_date, slot_start);

CREATE INDEX IF NOT EXISTS idx_booking_slots_department_service_status
  ON booking_slots(department_id, service_id, status);

CREATE INDEX IF NOT EXISTS idx_bookings_department_service_date
  ON bookings(department_id, service_id, booking_date);

CREATE INDEX IF NOT EXISTS idx_bookings_status_date
  ON bookings(status, booking_date);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bookings_external_source_external_id
  ON bookings(external_source, external_id)
  WHERE external_source IS NOT NULL AND external_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bookings_idempotency_key
  ON bookings(idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_bookings_qr_token
  ON bookings(qr_token);

CREATE UNIQUE INDEX IF NOT EXISTS ux_integration_requests_client_external_request_id
  ON integration_requests(client_code, external_request_id)
  WHERE external_request_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_integration_requests_client_idempotency_key
  ON integration_requests(client_code, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_booking_events_booking_created
  ON booking_events(booking_id, created_at);

CREATE INDEX IF NOT EXISTS idx_booking_events_department_created
  ON booking_events(department_id, created_at);
