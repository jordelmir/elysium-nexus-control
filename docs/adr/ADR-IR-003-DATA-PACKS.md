# ADR-IR-003: Signed Immutable IR Data Packs & Dynamic Database Ingestion

* Status: Accepted
* Deciders: Antigravity Engineering
* Date: 2026-08-05

## Context

The previous prototype contained hardcoded JSON files in `assets/` and Kotlin catalogs in code alongside database files, leading to multiple out-of-sync sources of truth. 

## Decision

1. **Single Authoritative Database**: A SQLite database (`ir_catalog.sqlite`) generated deterministically by `tools/ir-ingestion` serves as the sole source of truth for physical IR signals, brands, models, and code sets.
2. **Immutable Data Packs**: Data sets are modularized into immutable packs (e.g. `core-tv`, `hvac-global`, `community-unverified`).
3. **Integrity & Verification**: Data packs include content SHA-256 hashes, schema versions, and Ed25519 signatures.
4. **Atomic Updates**: Downloads or local updates are validated in temp storage and atomically swapped into place with rollback support.

## Consequences

- Kotlin `DeviceTemplate` retains layout and UI styling responsibility only; physical signals live strictly in `ir_catalog.sqlite`.
- System can dynamically load or update database packs without code modifications.
