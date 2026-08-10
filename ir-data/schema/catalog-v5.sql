-- Elysium Nexus IR Data Fabric — Canonical Catalog Schema v5
-- =========================================================
-- V06 PHASE 5: Schema v4 → v5. Adds the six §14 tables missing from v4:
--   device_families, protocol_definitions, protocol_variants,
--   compatibility_assertions, physical_test_evidence, catalog_rejections
-- Everything else is preserved byte-for-byte from catalog-v4.sql.

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

-- V06-P5 §14: device families aggregate models (e.g. "OLED TV 2023").
CREATE TABLE IF NOT EXISTS device_families (
    id TEXT PRIMARY KEY,
    brand_id TEXT NOT NULL,
    device_type_id TEXT NOT NULL,
    family_name TEXT NOT NULL,
    region TEXT,
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
    -- V0.6.1 §5: independent evidence/eligibility axes (code-set level).
    evidence_level TEXT NOT NULL DEFAULT 'SOURCE_IMPORTED',
    eligibility_status TEXT NOT NULL DEFAULT 'RESEARCH_ONLY',
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
    rejection_reason TEXT,
    -- V0.6.1 §0.3: typed FK chain to the authoritative protocol catalogue.
    -- storage-identity (codec_id) stays as provenance only; eligibility uses
    -- these FKs. RAW signals never carry a parametric definition/variant.
    protocol_definition_id TEXT,
    protocol_variant_id TEXT,
    -- V0.6.1 §4: carrier truth lane — never infer into exact observation.
    carrier_evidence TEXT NOT NULL DEFAULT 'UNKNOWN',
    -- V0.6.1 §5: evidence/eligibility are INDEPENDENT axes (never inferred
    -- from verification_status). Importers set the floor; only the evidence
    -- pipeline promotes.
    evidence_level TEXT NOT NULL DEFAULT 'SOURCE_IMPORTED',
    eligibility_status TEXT NOT NULL DEFAULT 'RESEARCH_ONLY',
    FOREIGN KEY(protocol_definition_id) REFERENCES protocol_definitions(id),
    FOREIGN KEY(protocol_variant_id) REFERENCES protocol_variants(id)
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

-- V5-P5 §14: protocol definitions (authoritative codec catalogue).
CREATE TABLE IF NOT EXISTS protocol_definitions (
    id TEXT PRIMARY KEY,
    family_name TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    carrier_hz INTEGER,
    encoding_kind TEXT NOT NULL,
    spec_url TEXT,
    notes TEXT
);

-- V5 §14: protocol variants used by code sets/signals.
CREATE TABLE IF NOT EXISTS protocol_variants (
    id TEXT PRIMARY KEY,
    protocol_id TEXT NOT NULL,
    variant_name TEXT NOT NULL,
    description TEXT,
    UNIQUE(protocol_id, variant_name),
    FOREIGN KEY(protocol_id) REFERENCES protocol_definitions(id)
);

-- V5 §14: declared compatibility assertions (community/LAB evidence).
CREATE TABLE IF NOT EXISTS compatibility_assertions (
    id TEXT PRIMARY KEY,
    brand_id TEXT NOT NULL,
    device_type_id TEXT NOT NULL,
    device_model_id TEXT,
    code_set_id TEXT NOT NULL,
    assertion_level TEXT NOT NULL,
    transport TEXT NOT NULL,
    firmware TEXT,
    result TEXT NOT NULL,
    source TEXT NOT NULL,
    asserted_at_epoch_ms INTEGER NOT NULL,
    FOREIGN KEY(brand_id) REFERENCES brands(id),
    FOREIGN KEY(device_type_id) REFERENCES device_types(id),
    FOREIGN KEY(code_set_id) REFERENCES code_sets(id)
);

-- V5 §14: physical test evidence (HIL/LAB measurements).
CREATE TABLE IF NOT EXISTS physical_test_evidence (
    id TEXT PRIMARY KEY,
    code_set_id TEXT NOT NULL,
    device_model_id TEXT,
    firmware_version TEXT,
    transport TEXT NOT NULL,
    action_key TEXT NOT NULL,
    result TEXT NOT NULL,
    measured_at_epoch_ms INTEGER NOT NULL,
    test_runner TEXT NOT NULL,
    report_path TEXT,
    FOREIGN KEY(code_set_id) REFERENCES code_sets(id)
);

-- V5 §14: catalogue rejections (traceability of rejected sources).
CREATE TABLE IF NOT EXISTS catalog_rejections (
    id TEXT PRIMARY KEY,
    source_file_id TEXT,
    reason TEXT NOT NULL,
    rejection_kind TEXT NOT NULL CHECK(rejection_kind IN ('LICENSE', 'STRUCTURE', 'UNSUPPORTED_PROTOCOL', 'UNSUPPORTED_ENCODING', 'AMBIGUOUS_VARIANT', 'INVALID_CARRIER', 'MISSING_CARRIER', 'MALFORMED_ADDRESS', 'MALFORMED_COMMAND', 'MALFORMED_RAW', 'RAW_DURATION_TOO_LONG', 'DEDUP', 'OTHER')),
    rejection_epoch_ms INTEGER NOT NULL,
    FOREIGN KEY(source_file_id) REFERENCES source_files(id)
);

-- Indices for high-performance querying
CREATE INDEX IF NOT EXISTS idx_code_sets_remote ON code_sets(remote_id);
CREATE INDEX IF NOT EXISTS idx_command_bindings_codeset ON command_bindings(code_set_id);
CREATE INDEX IF NOT EXISTS idx_command_bindings_action ON command_bindings(action_id);
CREATE INDEX IF NOT EXISTS idx_command_bindings_signal ON command_bindings(signal_id);
CREATE INDEX IF NOT EXISTS idx_signals_physical_sha ON signals(physical_sha256);
CREATE INDEX IF NOT EXISTS idx_remotes_brand ON remotes(brand_id);
CREATE INDEX IF NOT EXISTS idx_remotes_device_type ON remotes(device_type_id);
CREATE INDEX IF NOT EXISTS idx_protocol_variants_protocol ON protocol_variants(protocol_id);
CREATE INDEX IF NOT EXISTS idx_compatibility_assertions_codeset ON compatibility_assertions(code_set_id);
CREATE INDEX IF NOT EXISTS idx_physical_test_evidence_codeset ON physical_test_evidence(code_set_id);

-- V0.6.1 §0.3: eligibility indexes.
CREATE INDEX IF NOT EXISTS idx_signals_protocol_definition ON signals(protocol_definition_id);
CREATE INDEX IF NOT EXISTS idx_signals_eligibility ON signals(eligibility_status);
CREATE INDEX IF NOT EXISTS idx_code_sets_eligibility ON code_sets(eligibility_status);
