# ADR-IR-001: Canonical Infrared Signal Representation & Raw Duration Rules

* Status: Accepted
* Deciders: Antigravity Engineering, Elysium Core Architecture
* Date: 2026-08-05

## Context

Android's `ConsumerIrManager.transmit(carrierHz, pattern)` takes an array of microsecond durations (`int[] pattern`) representing alternating mark (LED-on) and space (LED-off) intervals. 

The previous prototype contained two fatal assumptions:
1. `pattern` arrays were forced to be even-length by appending `pattern.add(0)` at the end of frames.
2. `IrWaveform` permitted zero-duration slices (`it >= 0`).

However, Android framework's `ConsumerIrService.java` enforces `require(pattern.all { it > 0 })`. Any duration $\le 0$ throws `IllegalArgumentException: Non-positive IR slice`. Android's underlying IR HAL accepts odd-length pattern arrays and automatically disables the carrier LED at the end of the last mark slice.

Furthermore, standard NEC physical frames consist of 32 bits sent LSB-first (Address, Address XOR 0xFF, Command, Command XOR 0xFF) ending with a 560 µs mark, rather than 16-bit MSB-first truncated shapes.

## Decision

1. **Strictly Positive Slices**: `IrWaveform` enforces `pattern.all { it > 0 }`. All trailing `pattern.add(0)` calls are removed across encoders.
2. **Odd-Length Pattern Support**: `IrWaveform` allows odd-length pattern arrays. The final element of an odd-length pattern is the trailing mark.
3. **2-Second Duration Cap**: `IrWaveform` validates `pattern.sumOf { it.toLong() } < 2_000_000L` (2,000,000 µs limit enforced by Android OS).
4. **Canonical 32-Bit LSB-First NEC**: Standard NEC is encoded as 32 bits LSB-first with `Address`, `Address XOR 0xFF`, `Command`, `Command XOR 0xFF` plus a 560 µs stop mark.
5. **No Fallback to NEC**: Protocol dispatching must be explicit. Unsupported protocols return `UnsupportedProtocol`.

## Consequences

- Android framework accepts all generated waveforms without throwing `IllegalArgumentException`.
- Physical NEC receivers decodable by Linux `rc-core` and Arduino `IRremote` can correctly decode transmitted NEC frames.
- Protocol errors fail fast and cleanly rather than transmitting corrupt NEC bursts.
