# ADR-IR-002: IR Source Licensing Gate & Data Provenance

* Status: Accepted
* Deciders: Antigravity Engineering, Legal & Compliance
* Date: 2026-08-05

## Context

Universal IR databases available on the web originate from multiple repositories (Flipper-IRDB, SmartIR, probonopd/irdb, LIRC remotes, irplus, Global Caché, RemoteCentral). Bundling external data into a production application requires strict licensing compliance and provenance tracking to prevent intellectual property violations and redistribution blocks.

## Decision

1. **License Gate Categories**:
   - `APPROVED` (Allowed for bundling): CC0-1.0, MIT, Apache-2.0, BSD-2-Clause, BSD-3-Clause.
   - `CONDITIONAL` (Requires explicit audit/attribution): Custom open licenses (e.g. `probonopd/irdb`), LGPL-2.1, GPL-2.0, GPL-3.0.
   - `BLOCKED` (Never bundled in production APK): Proprietary databases, Global Caché Control Tower (restricted to Global Caché hardware), unverified commercial dumps.
2. **Provenance Metadata**: Every imported `IrCodeSet` and `IrSignal` must record source URL, commit SHA, file path, source hash, SPDX license ID, and attribution text.
3. **Verification Status Baseline**: External imports without physical lab captures default to `VerificationStatus.UNVERIFIED` or `IMPORTED_UNREVIEWED`.

## Consequences

- Automated build steps enforce license metadata integrity before bundling database packs.
- Third-party attribution notice manifests (`THIRD_PARTY_IR_DATA_NOTICES.md`) are automatically updated.
- Proprietary or restricted datasets are quarantined and never included in standard APK builds.
