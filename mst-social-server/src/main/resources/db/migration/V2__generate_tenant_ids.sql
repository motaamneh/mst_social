-- Preserve existing tenant IDs and references; generate an ID when omitted.
ALTER TABLE tenants ALTER COLUMN id SET DEFAULT gen_random_uuid();
