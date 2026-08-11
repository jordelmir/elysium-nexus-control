# PHASE V0.6.2 PR 2 — RUNTIME TRUTH

**Date**: 2026-08-10
**Branch**: `fix/v0.6.2-truth-convergence`
**Parent**: PR 1 (CI Emergency) — `6bc1c3e`
**Scope**: Phases 2, 4, 6 (Runtime Truth, Variant Resolution, Provenance Repair)

## Summary

Runtime protocol dispatch now resolves from catalog V5 foreign keys
(`protocol_definitions.family_name`, `protocol_variants.variant_name`)
instead of legacy strings (`codec_id`, `protocol_name_original`, `protocol_variant`).
Legacy fields are preserved as source provenance only.

## Changes

### Phase 2 — ONE RUNTIME PROTOCOL AUTHORITY

**`IrCatalog.kt`**:
- Added `RuntimeProtocolBinding` data class with V5 FK fields (`definitionId`,
  `familyName`, `variantId`, `variantName`, `carrierHz`, `evidenceLevel`,
  `eligibilityStatus`) plus legacy provenance fields (`legacyCodecId`,
  `legacyProtocolName`, `legacyVariant`).
- Added `resolveProtocolFromFamily()` top-level function: maps
  `pd.family_name` → `IrProtocol` enum (NEC, NecExtended, Samsung,
  SonySirc, Rc5, Rc6, Kaseikyo).

**`IrCatalogRepository.kt`**:
- All 3 signal-loading queries (`getCommandsForCodeSetInternal`,
  `getSignal`, `getCommandsForCodeSet`) now `LEFT JOIN protocol_definitions`
  and `LEFT JOIN protocol_variants` via V5 FKs.
- Protocol resolution priority: `pd.family_name` → `legacyCodecId` → null
  (skip signal with warning).
- Variant resolution priority: `pv.variant_name` → `legacyProtocolName` →
  `legacyVariant` → null.
- `getSignal` single-signal lookup now handles `VariantUnsupported` (logs
  warning instead of silently dropping).

### Phase 4 — VariantUnsupported + Explicit SIRC

**`ProtocolCodecRegistry.kt`**:
- Added `VariantUnsupported` to `CodecResolution` sealed interface with
  fields: `codec`, `requestedVariant`, `availableVariants`.
- Fixed `resolve()`: when `variantHint` is provided but no variant matches,
  returns `VariantUnsupported` instead of silently passing `null` to
  `Resolved`.

**`IrProtocol.kt`**:
- SIRC encoder now explicitly dispatches `SIRC_12` (5-bit), `SIRC_15`
  (8-bit), `SIRC_20` (13-bit) instead of `else -> 5`.
- Added `Log.w()` for null/unknown variantId with explicit default.

### Phase 6 — Provenance Repair (P0-10 + P0-11)

**`IrCatalogRepository.kt`**:
- Fixed P0-10: `sr.version` → `sr.commit_sha` in `getSignalMetadata`
  and `getSignalProvenance` fallback query. V5 `source_revisions` has
  `commit_sha`, not `version`.
- Fixed P0-11: `getSignalProvenance` now queries catalog `signal_sources`
  table first (via immutable catalog DB), then falls back to user Room DB
  `signal_sources` (for session-verified extras), then derives from
  `command_bindings → source_revisions`.

## Files Modified

| File | Lines Changed | Phase |
|------|--------------|-------|
| `IrCatalog.kt` | +50 (RuntimeProtocolBinding + resolveProtocolFromFamily) | 2 |
| `IrCatalogRepository.kt` | ~150 (JOIN queries, sr.version fix, provenance repair) | 2, 6 |
| `ProtocolCodecRegistry.kt` | +18 (VariantUnsupported + resolve fix) | 4 |
| `IrProtocol.kt` | +12 (explicit SIRC dispatch + Log) | 4 |

## Verification

- `sr.version` references: 0 remaining in production code (only in comments)
- `ProtocolCodecRegistry.getCodec()` usages: 3 fallbacks in IrCatalogRepository (V5 FK → legacy → null), 1 in test file
- All `CodecResolution` sealed type branches handled in callers
- Legacy fields (`codec_id`, `protocol_name_original`, `protocol_variant`)
  retained in SELECT queries as provenance only — not used for runtime dispatch

## Breaking Changes

None. Legacy fields are preserved for backward compatibility. Signals
without V5 FKs (`protocol_definition_id IS NULL`) fall back to legacy
`codec_id` resolution with a log warning.
