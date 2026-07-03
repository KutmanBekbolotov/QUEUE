CREATE TABLE IF NOT EXISTS external_directory_mappings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  client_code varchar(120) NOT NULL,
  entity_type varchar(40) NOT NULL,
  external_code varchar(160) NOT NULL,
  internal_id uuid NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (entity_type IN ('DEPARTMENT', 'SERVICE', 'REGION'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_external_directory_mappings_client_type_code
  ON external_directory_mappings(client_code, entity_type, external_code);

CREATE INDEX IF NOT EXISTS idx_external_directory_mappings_search
  ON external_directory_mappings(client_code, entity_type, active);
