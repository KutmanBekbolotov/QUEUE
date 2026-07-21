UPDATE tickets
SET citizen_pin = NULL
WHERE source = 'QR_SELF_SERVICE'
  AND citizen_pin IS NOT NULL;
