-- Flyway owns this schema. Do not run this file manually outside Flyway.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE tenants (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE verification_requests (
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    id uuid NOT NULL,
    subject_id text NOT NULL CHECK (length(btrim(subject_id)) > 0),
    platform text NOT NULL CHECK (platform IN ('INSTAGRAM', 'TIKTOK', 'X', 'FACEBOOK')),
    normalized_handle text NOT NULL CHECK (length(btrim(normalized_handle)) > 0),
    code_digest bytea NOT NULL CHECK (octet_length(code_digest) = 32),
    code_key_version text NOT NULL CHECK (length(btrim(code_key_version)) > 0),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    max_failed_attempts integer NOT NULL CHECK (max_failed_attempts > 0),
    status text NOT NULL CHECK (status IN ('PENDING','VERIFYING','VERIFIED','EXPIRED','CANCELED','LOCKED')),
    failed_attempts integer NOT NULL CHECK (failed_attempts BETWEEN 0 AND max_failed_attempts),
    verified_at timestamptz,
    canceled_at timestamptz,
    version bigint NOT NULL CHECK (version >= 0),
    verification_lease_id uuid,
    lease_expires_at timestamptz,
    PRIMARY KEY (tenant_id, id),
    CHECK ((status = 'LOCKED') = (failed_attempts = max_failed_attempts)),
    CHECK ((status = 'VERIFIED') = (verified_at IS NOT NULL)),
    CHECK ((status = 'CANCELED') = (canceled_at IS NOT NULL)),
    CHECK (verified_at IS NULL OR (verified_at >= created_at AND verified_at < expires_at)),
    CHECK (canceled_at IS NULL OR (canceled_at >= created_at AND canceled_at < expires_at)),
    CHECK (
        (status = 'VERIFYING' AND verification_lease_id IS NOT NULL AND lease_expires_at IS NOT NULL
            AND lease_expires_at > created_at AND lease_expires_at <= expires_at)
        OR (status <> 'VERIFYING' AND verification_lease_id IS NULL AND lease_expires_at IS NULL)
    ),
    EXCLUDE USING gist (
        tenant_id WITH =, subject_id WITH =, platform WITH =, normalized_handle WITH =,
        tstzrange(created_at, expires_at, '[)') WITH &&
    ) WHERE (status IN ('PENDING', 'VERIFYING'))
);

CREATE INDEX requests_subject_active ON verification_requests (tenant_id, subject_id, expires_at)
    WHERE status IN ('PENDING','VERIFYING');
CREATE INDEX requests_handle_active ON verification_requests (tenant_id, platform, normalized_handle, expires_at)
    WHERE status IN ('PENDING','VERIFYING');

CREATE TABLE verified_accounts (
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    id uuid NOT NULL,
    subject_id text NOT NULL CHECK (length(btrim(subject_id)) > 0),
    platform text NOT NULL CHECK (platform IN ('INSTAGRAM', 'TIKTOK', 'X', 'FACEBOOK')),
    canonical_handle text NOT NULL CHECK (length(btrim(canonical_handle)) > 0),
    provider_account_id text CHECK (provider_account_id IS NULL OR length(btrim(provider_account_id)) > 0),
    evidence_provider text NOT NULL CHECK (length(btrim(evidence_provider)) > 0),
    verified_at timestamptz NOT NULL,
    valid_until timestamptz NOT NULL CHECK (valid_until > verified_at),
    revoked_at timestamptz CHECK (revoked_at IS NULL OR revoked_at >= verified_at),
    version bigint NOT NULL CHECK (version >= 0),
    PRIMARY KEY (tenant_id, id),
    -- Ranges expire naturally; do not use a now()-dependent partial index.
    EXCLUDE USING gist (
        tenant_id WITH =, platform WITH =, canonical_handle WITH =,
        tstzrange(verified_at, LEAST(valid_until, COALESCE(revoked_at, valid_until)), '[)') WITH &&
    ),
    EXCLUDE USING gist (
        tenant_id WITH =, platform WITH =, provider_account_id WITH =,
        tstzrange(verified_at, LEAST(valid_until, COALESCE(revoked_at, valid_until)), '[)') WITH &&
    ) WHERE (provider_account_id IS NOT NULL)
);

CREATE INDEX accounts_subject ON verified_accounts (tenant_id, subject_id);
CREATE INDEX accounts_handle ON verified_accounts (tenant_id, platform, canonical_handle);
CREATE INDEX accounts_provider_id ON verified_accounts (tenant_id, platform, provider_account_id)
    WHERE provider_account_id IS NOT NULL;

CREATE TABLE verification_account_links (
    tenant_id uuid NOT NULL,
    request_id uuid NOT NULL,
    account_id uuid NOT NULL,
    PRIMARY KEY (tenant_id, request_id),
    FOREIGN KEY (tenant_id, request_id) REFERENCES verification_requests(tenant_id, id),
    FOREIGN KEY (tenant_id, account_id) REFERENCES verified_accounts(tenant_id, id)
);

CREATE TABLE verification_audit_events (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    resource_id uuid NOT NULL,
    event_type text NOT NULL CHECK (event_type IN (
        'REQUEST_CREATED','ATTEMPT_STARTED','REQUEST_EXPIRED','REQUEST_CANCELED',
        'CODE_MISMATCH','PROVIDER_FAILED','ACCOUNT_CONFLICT','REQUEST_VERIFIED','ACCOUNT_REVOKED'
    )),
    occurred_at timestamptz NOT NULL
);
CREATE INDEX audit_tenant_resource ON verification_audit_events (tenant_id, resource_id, occurred_at);

-- A VERIFIED request must have a compatible link at COMMIT, allowing the service
-- to update the request and then insert its link within the same transaction.
CREATE FUNCTION validate_verification_link() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    target_tenant uuid;
    target_request uuid;
    request_status text;
    request_subject text;
    request_platform text;
    linked_subject text;
    linked_platform text;
    has_link boolean;
BEGIN
    IF TG_TABLE_NAME = 'verification_requests' THEN
        target_tenant := NEW.tenant_id;
        target_request := NEW.id;
    ELSE
        target_tenant := COALESCE(NEW.tenant_id, OLD.tenant_id);
        target_request := COALESCE(NEW.request_id, OLD.request_id);
    END IF;
    SELECT status, subject_id, platform INTO request_status, request_subject, request_platform
        FROM verification_requests WHERE tenant_id = target_tenant AND id = target_request;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;
    SELECT a.subject_id, a.platform INTO linked_subject, linked_platform
        FROM verification_account_links l JOIN verified_accounts a
            ON a.tenant_id = l.tenant_id AND a.id = l.account_id
        WHERE l.tenant_id = target_tenant AND l.request_id = target_request;
    has_link := FOUND;
    IF (request_status = 'VERIFIED') <> has_link
        OR (has_link AND (request_subject <> linked_subject OR request_platform <> linked_platform)) THEN
        RAISE EXCEPTION 'Inconsistent verification account link' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER request_link_consistency
AFTER INSERT OR UPDATE ON verification_requests DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_verification_link();

CREATE CONSTRAINT TRIGGER account_link_consistency
AFTER INSERT OR UPDATE OR DELETE ON verification_account_links DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION validate_verification_link();
