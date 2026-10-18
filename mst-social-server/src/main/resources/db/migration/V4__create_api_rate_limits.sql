-- Four bounded buckets per tenant; existing rows are reused when their minute expires.
-- Authentication happens before these counters are touched.
CREATE TABLE api_rate_limits (
    tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    operation varchar(16) NOT NULL CHECK (operation IN ('CREATE', 'VERIFY', 'READ', 'MUTATE')),
    window_started_at timestamptz NOT NULL,
    request_count integer NOT NULL CHECK (request_count > 0),
    PRIMARY KEY (tenant_id, operation)
);
