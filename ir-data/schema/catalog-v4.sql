-- Elysium Nexus IR Data Fabric — Canonical Catalog Schema v4
-- =========================================================

CREATE TABLE IF NOT EXISTS sources (
    id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    repository_url TEXT NOT NULL,
    license_id TEXT NOT NULL,
    production_approved INTEGER NOT NULL CHECK(production_approved IN (0,1))
);

CREATE TABLE IF NOT EXISTS source_revisions (
    id TEXT PRIMARY KEY,
    source_id TEXT NOT NULL,
    commit_sha TEXT NOT NULL,
    tree_sha TEXT NOT NULL,
    content_sha256 TEXT NOT NULL,
    license_sha256 TEXT NOT NULL,
    FOREIGN KEY(source_id) REFERENCES sources(id)
);

CREATE TABLE IF NOT EXISTS source_files (
    id TEXT PRIMARY KEY,
    source_revision_id TEXT NOT NULL,
    relative_path TEXT NOT NULL,
    blob_sha TEXT,
    content_sha256 TEXT NOT NULL,
    introduced_commit TEXT,
    last_modified_commit TEXT,
    license_status TEXT NOT NULL CHECK(license_status IN ('APPROVED', 'QUARANTINED', 'BLOCKED', 'UNKNOWN')),
    rejection_reason TEXT,
    FOREIGN KEY(source_revision_id) REFERENCES source_revisions(id)
);

CREATE TABLE IF NOT EXISTS brands (
    id TEXT PRIMARY KEY,
    normalized_name TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS device_types (
    id TEXT PRIMARY KEY,
    canonical_name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS device_models (
    id TEXT PRIMARY KEY,
    brand_id TEXT NOT NULL,
    device_type_id TEXT NOT NULL,
    normalized_model TEXT,
    display_model TEXT,
    region TEXT,
    oem_platform_id TEXT,
    FOREIGN KEY(brand_id) REFERENCES brands(id),
    FOREIGN KEY(device_type_id) REFERENCES device_types(id)
);

CREATE TABLE IF NOT EXISTS remotes (
    id TEXT PRIMARY KEY,
    source_file_id TEXT NOT NULL,
    brand_id TEXT NOT NULL,
    device_type_id TEXT NOT NULL,
    normalized_remote_model TEXT,
    display_remote_model TEXT,
    region TEXT,
    FOREIGN KEY(source_file_id) REFERENCES source_files(id),
    FOREIGN KEY(brand_id) REFERENCES brands(id),
    FOREIGN KEY(device_type_id) REFERENCES device_types(id)
);

CREATE TABLE IF NOT EXISTS code_sets (
    id TEXT PRIMARY KEY,
    remote_id TEXT NOT NULL,
    source_revision_id TEXT NOT NULL,
    protocol_family TEXT,
    protocol_variant TEXT,
    region TEXT,
    verification_status TEXT NOT NULL,
    runtime_status TEXT NOT NULL,
    FOREIGN KEY(remote_id) REFERENCES remotes(id),
    FOREIGN KEY(source_revision_id) REFERENCES source_revisions(id)
);

CREATE TABLE IF NOT EXISTS actions (
    id TEXT PRIMARY KEY,
    canonical_key TEXT NOT NULL UNIQUE,
    action_family TEXT NOT NULL,
    payload_json TEXT
);

CREATE TABLE IF NOT EXISTS signals (
    id TEXT PRIMARY KEY,
    encoding_type TEXT NOT NULL CHECK(encoding_type IN ('PARAMETRIC', 'RAW')),
    codec_id TEXT,
    protocol_name_original TEXT,
    protocol_variant TEXT,
    carrier_hz INTEGER NOT NULL,
    address_value INTEGER,
    sub_device_value INTEGER,
    command_value INTEGER,
    repeat_count INTEGER NOT NULL DEFAULT 0,
    toggle_policy TEXT,
    pattern_blob BLOB,
    compression TEXT,
    slice_count INTEGER,
    duration_us INTEGER,
    uncompressed_bytes INTEGER,
    physical_sha256 TEXT NOT NULL UNIQUE,
    canonical_sha256 TEXT NOT NULL,
    runtime_status TEXT NOT NULL,
    validation_status TEXT NOT NULL,
    rejection_reason TEXT
);

CREATE TABLE IF NOT EXISTS command_bindings (
    id TEXT PRIMARY KEY,
    code_set_id TEXT NOT NULL,
    action_id TEXT NOT NULL,
    signal_id TEXT NOT NULL,
    repeat_policy TEXT NOT NULL,
    press_type TEXT NOT NULL,
    source_priority INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(code_set_id) REFERENCES code_sets(id),
    FOREIGN KEY(action_id) REFERENCES actions(id),
    FOREIGN KEY(signal_id) REFERENCES signals(id),
    UNIQUE(code_set_id, action_id, signal_id)
);

CREATE TABLE IF NOT EXISTS signal_sources (
    signal_id TEXT NOT NULL,
    source_file_id TEXT NOT NULL,
    PRIMARY KEY(signal_id, source_file_id),
    FOREIGN KEY(signal_id) REFERENCES signals(id),
    FOREIGN KEY(source_file_id) REFERENCES source_files(id)
);

CREATE TABLE IF NOT EXISTS code_set_models (
    code_set_id TEXT NOT NULL,
    device_model_id TEXT NOT NULL,
    match_type TEXT NOT NULL,
    PRIMARY KEY(code_set_id, device_model_id),
    FOREIGN KEY(code_set_id) REFERENCES code_sets(id),
    FOREIGN KEY(device_model_id) REFERENCES device_models(id)
);

-- Indices for high-performance querying
CREATE INDEX IF NOT EXISTS idx_code_sets_remote ON code_sets(remote_id);
CREATE INDEX IF NOT EXISTS idx_command_bindings_codeset ON command_bindings(code_set_id);
CREATE INDEX IF NOT EXISTS idx_command_bindings_action ON command_bindings(action_id);
CREATE INDEX IF NOT EXISTS idx_command_bindings_signal ON command_bindings(signal_id);
CREATE INDEX IF NOT EXISTS idx_signals_physical_sha ON signals(physical_sha256);
CREATE INDEX IF NOT EXISTS idx_remotes_brand ON remotes(brand_id);
CREATE INDEX IF NOT EXISTS idx_remotes_device_type ON remotes(device_type_id);
