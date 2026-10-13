-- The credential ID is public; only the digest of the random secret is stored.
-- Multiple credentials per tenant allow rotation without immediate downtime.
CREATE TABLE tenant_api_keys (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    name text NOT NULL CHECK (length(btrim(name)) > 0),
    secret_digest bytea NOT NULL CHECK (octet_length(secret_digest) = 32),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz,
    revoked_at timestamptz,
    CONSTRAINT tenant_api_keys_expiry_valid
        CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT tenant_api_keys_revocation_valid
        CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

-- Authentication looks up the public ID using the primary-key index.
-- This index supports listing and managing credentials for a tenant.
CREATE INDEX tenant_api_keys_tenant ON tenant_api_keys (tenant_id);

COMMENT ON COLUMN tenant_api_keys.secret_digest IS
    '32-byte digest of a cryptographically random API-key secret; never plaintext.';
COMMENT ON COLUMN tenant_api_keys.expires_at IS
    'Null means no scheduled expiration; credentials are invalid at or after this time.';
COMMENT ON COLUMN tenant_api_keys.revoked_at IS
    'Non-null means revoked; authentication must reject this credential.';
