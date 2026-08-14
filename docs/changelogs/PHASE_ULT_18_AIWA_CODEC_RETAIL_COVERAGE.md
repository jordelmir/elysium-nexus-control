# PHASE ULT.18 — Aiwa Codec + Cobertura Retail Gollo/Monge (Costa Rica)

## Problema resuelto (reportado por Jor)

1. **Konka no existía en el catálogo**: el control universal debía cubrir las marcas que venden Gollo, Monge y El Verdugo (Samsung, LG, Hisense, Telstar, RCA, AIWA, Konka, JVC, TCL, Xiaomi…). Los remotes reales de Konka en probonopd/irdb usan el protocolo **Aiwa** (device=25, subdevice=1), que era rechazado por el pipeline (`UNSUPPORTED_PROTOCOL`) y no existía en el runtime Kotlin.
2. **El pool del barrido universal ignoraba TVs reales**: los remotes irdb con tipo `Unknown_*` (Konka, Sony, Sanyo, Emerson, CCE…), `Plasma`, `LED_TV` y los universales Flipper no matcheaban el filtro `LIKE 'TV%'` del flujo universal → quedaban fuera del barrido aunque fueran TVs.

## Qué se implementó

### 1. Codec Aiwa completo (Kotlin)
- `IrProtocol.kt`: nuevo enum `Aiwa` (38.123 kHz) + `resolveProtocol("Aiwa…")` + dispatch de encode.
- `IrWaveform.kt`: `encodeAiwa(address, subDevice, command, carrierHz)` según el IRP real de IrpProtocols.xml:
  `{38.123k,550}<1,-1|1,-3>(16,-8,D:8,S:5,~D:8,~S:5,F:8,~F:8,1,-42,(16,-8,1,-165)*)` — header 8800/4400, D:8 LSB + S:5 LSB + inversos + F:8 + ~F:8 + stop 550 (61 entries). Validación de rangos (D ≤ 255, S ≤ 31, F ≤ 255).
- `ProtocolCodecRegistry.kt`: codec `AIWA` con variante `AIWA_42`, status `UNIT_SHAPE_VALIDATED` (transmisible en producción), alias `AIWA_RC501`.

### 2. Aiwa en el pipeline de datos (`tools/ir-data/ingest_v5.py`)
- `PROTOCOL_MAP["aiwa"] = ("Aiwa", 38123)` + `VARIANT_NAME_MAP["aiwa"] = "AIWA_42"` → los remotes Konka de irdb entran con variant_id correcta (`variant:Aiwa:AIWA_42`) y codec_id `Aiwa` (resoluble por el registry).
- Protocol rejects: **32.778 → 30.442** (Aiwa ya no es protocolo desconocido).

### 3. Normalización de device types TV (el pool real del barrido)
- `classify_irdb_device_type()`: los remotes irdb `Unknown_<modelo>` de marcas que venden TV (46 marcas, incl. Konka, Sony, Sanyo, Emerson, CCE) → `Unknown_tv` → `TV`. Las marcas no-TV quedan `Unknown` (fuera del pool — sin claims falsos).
- `DEVICE_TYPE_ALIASES`: `unknown_tv`, `plasma`, `plasma displays`, `led_tv`, `rear projection dlp tv`, `lcd tv` → `TV`; `universal_tv_remotes` → `TV` (antes `Universal_Remote`, que rompía el filtro exacto de la app).

### 4. Tarjetas del hub (`TvControlsSection.kt` + `DeviceTemplate.kt`)
- Tier-1 ampliado: **Konka, Telstar, AIWA, RCA, JVC, Xiaomi / Mi** (antes solo Control Universal TV, Sankey, Kintech, Samsung, LG, Sony, Panasonic, Philips, TCL, Hisense).
- Nuevos templates `tv-aiwa-generic` (protocol Aiwa, device 25) y `tv-telstar-generic` (NEC, marca propia Monge).

## Catálogo v0.5.4 (datos verificados en SQLite)
- Pool del barrido universal TV: **436 → 808 code sets** (query exacta de la app, `getCandidateCountForActions("TV", …)`).
- Konka: 2 remotes reales (KK-Y199 + KK-Y250A, Aiwa 25/1, 49 señales) dentro del pool.
- Cobertura retail (pool TV, code sets): Samsung 115 · Sony 75 · Philips 72 · LG 60 · Panasonic 44 · Toshiba 39 · AIWA 34 · JVC 27 · Sanyo 22 · Hitachi 16 · Hisense 14 · Sharp 13 · RCA 9 · Daewoo 7 · TCL 6 · Westinghouse 4 · CCE 4 · Konka 2 · Philco 1 · Gradiente 1.
- 4.715 code sets · 106.033 señales · 223.571 bindings · DB 250.59 MB · manifest SHA verificado (integrity=ok).
- Locks: 8/8 verificados. Ruff: 12 errores preexistentes (E501/E402), ninguno nuevo.

## Tests
- `IrWaveformTest`: Aiwa 61 entries + header 8800/4400 + LSB-first con inversos + validación de rangos (3 asserts de rechazo).
- `ProtocolCodecGoldenVectorTest`: golden vector Aiwa (device 25, sub 1 — valores reales Konka), dispatch por `IrProtocol.encode`, registry resuelve `AIWA_42` y falla cerrado ante variantes desconocidas.
- Gradle: **no ejecutado en esta fase** (mandato de Jor: solo cuando lo pida explícitamente).

## Files changed
- `apps/android/.../fabric/infrared/IrProtocol.kt` — enum Aiwa + resolve + dispatch
- `apps/android/.../fabric/infrared/IrWaveform.kt` — `encodeAiwa()`
- `apps/android/.../fabric/infrared/ProtocolCodecRegistry.kt` — codec AIWA/AIWA_42
- `apps/android/.../ui/hub/TvControlsSection.kt` — tier-1 ampliado
- `apps/android/.../core/device/DeviceTemplate.kt` — templates AIWA + Telstar
- `tools/ir-data/ingest_v5.py` — PROTOCOL_MAP/VARIANT_NAME_MAP aiwa, `classify_irdb_device_type`, alias TV
- `apps/android/app/src/main/assets/ir/` — catálogo regenerado (DB + manifest + stats)

## Pendiente (siguiente fase)
- Build gradle (`assembleDebug` + `testDebugUnitTest`) e instalación en el Honor Magic V2 con el catálogo de 808 candidatos.
- Telstar sin codeset propio (marca propia de Monge, rebadge): cubierto por el barrido NEC/Aiwa; verificación física con un control Telstar real cuando Jor lo tenga a mano.
