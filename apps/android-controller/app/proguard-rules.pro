# Elysium Nexus — ProGuard / R8 rules
#
# Per ADR-0001, we do not enable minification in 0.1. This file is
# referenced from build.gradle.kts so a future `assembleRelease` will
# pick it up without forcing an edit on that iteration. The actual
# keep rules land alongside the canonical engine in 0.2 — for the
# value classes there is nothing to keep, and for AndroidX / Compose
# (added later) we will copy the standard rules.

# Empty for now.
