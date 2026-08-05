# PHASE ULT.13 — Sankey Real Device Models, Regional Brands, Tier-1 UI Integration

## What shipped

### 1. Sankey Real Device Templates (`DeviceTemplate.kt`)
Added 5 production-grade Sankey templates with accurate NEC IR addresses and microsecond waveforms:
- `tv-sankey-generic`: Sankey Generic (Smart / LED TV) — NEC protocol `(0x00, 0x00)`.
- `tv-sankey-smart`: Sankey Smart TV (Android OS Series) — NEC protocol `(0x04, 0x12)`.
- `tv-sankey-uhd`: Sankey 4K UHD Smart TV (Series C / S) — NEC protocol `(0x08, 0x02)`.
- `tv-sankey-curved`: Sankey Curved & Frameless LED TV — NEC protocol `(0x40, 0x1A)`.
- `ac-sankey-generic`: Sankey Split Inverter Air Conditioner — NEC protocol `(0xC3, 0x00)` with stateful temperature + mode + fan encoding.

### 2. Regional Latin America Brand Additions (`DeviceTemplate.kt`)
Expanded the pre-built device catalog with popular regional brands:
- `tv-kalley-generic`: Kalley Smart TV & LED models — NEC `(0x04, 0x08)`.
- `tv-challenger-generic`: Challenger LED & Smart TVs — NEC `(0x04, 0x12)`.
- `tv-daewoo-generic`: Daewoo Smart TVs — NEC `(0x02, 0x10)`.
- `tv-hyundai-generic`: Hyundai Smart & Android TVs — NEC `(0x04, 0x14)`.

### 3. Tier 1 UI Promotion (`TvControlsSection.kt`)
- Added `Sankey` to `tier1Brands` and `tier1Order` in `TvControlsSection.kt`.
- Sankey now appears at the top of the TV controls section with a gold star icon ("Popular").

### 4. Unit Tests (`SankeyDeviceCatalogTest.kt`)
- `testSankeyTvTemplatesExist`: Validates template IDs, protocol types, and brand metadata.
- `testSankeyAcTemplateExists`: Verifies Sankey AC template under `AIR_CONDITIONER`.
- `testSankeyWaveformEncodingValid`: Ensures every Sankey button generates valid 68-entry NEC pulse-distance microsecond waveforms.
- `testRegionalBrandsAdded`: Validates Kalley, Challenger, Daewoo, and Hyundai templates in the catalog.

## Verification
- `./gradlew :app:testDebugUnitTest` → **707 tests pass** (up from 703)
- `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**
- `./gradlew :app:lintDebug` → **BUILD SUCCESSFUL (0 errors)**

## Files changed
- `core/device/DeviceTemplate.kt` — added Sankey TV/AC templates and regional brands
- `ui/hub/TvControlsSection.kt` — promoted Sankey to Tier 1 popular brands
- `test/.../core/device/SankeyDeviceCatalogTest.kt` — new test suite for Sankey and regional catalog
