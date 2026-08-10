#!/usr/bin/env bash
# V06-P37 — Documentation claims audit (§33: no silent claims).
#
# Scans the repository's markdown for UNPROVEN production claims that
# would violate the no-silent-claims rule:
#   - maturity-state claims above UNIT_VERIFIED (they may only be granted
#     by test reports + the reality matrix, never by prose),
#   - absolutist compatibility phrases ("works with every", "compatible
#     con todas" — compatibility states are exactly the 7 ordered values
#     of §33, not hype).
#
# Authorities (allowed to state device truth): the reality matrix and the
# changelog — every other doc must defer to them.
#
# Read-only. Exit: 0 = no violations, 1 = violations found.
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ALLOW_PATHS=(
  "docs/audits/V06_REALITY_MATRIX.md"
  "docs/audits/V06_REALITY_LEDGER.md"
  "docs/changelogs/"
  "docs/architecture/MASTER_ORDER.md"
  "docs/architecture/MASTER_ORDER_SECTIONS.md"
)
cd "$ROOT" || exit 1

PATTERNS=(
  'PRODUCTION_APPROVED'
  'HIL_VERIFIED'
  'REAL_DEVICE_VERIFIED'
  'DEVICE_MATRIX_VERIFIED'
  'ON_DEVICE_VERIFIED'
  'soporta todas'
  'funciona con todas'
  'works with every'
  'compatible with all'
  'compatible con todas'
  '100% de los'
  'resuelve todo'
  'probado en hardware'
  'hardware-verified'
  'producto terminado'
  'production-ready'
)

violations=0
for pat in "${PATTERNS[@]}"; do
  while IFS=: read -r rawfile line rest; do
    file="${rawfile#./}"          # normalize './' prefix for allowlist matching
    file="$ROOT/$file"
    allowed=0
    for ap in "${ALLOW_PATHS[@]}"; do
      case "$file" in
        "$ROOT/$ap"*) allowed=1 ;;
      esac
    done
    if [ "$allowed" -eq 0 ]; then
      echo "VIOLATION ${pat}: $file:$line: $rest"
      violations=$((violations + 1))
    fi
  done < <(grep -rniE "$pat" --include='*.md' . || true)
done

if [ "$violations" -gt 0 ]; then
  echo "claims audit: FAIL ($violations violation(s))"
  exit 1
fi
echo "claims audit: PASS"
exit 0