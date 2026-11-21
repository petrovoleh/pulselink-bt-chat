# PulseLink — Offline Long-Range Bluetooth LE Mesh Messenger

**PulseLink** is a 100% offline Android messenger built with Kotlin & Jetpack Compose that communicates over **Bluetooth 5 Extended Advertising (`PHY_LE_CODED` S=8 Long Range)** and **Standard `PHY_LE_1M`**, requiring zero internet, zero servers, and zero registration.

## Key Features

- **Dual-PHY & Long-Range Radio Control**: Switch seamlessly between `OFF`, `Standard (1M)`, `Extended (LR Coded S=8)`, and `Both (1M + LR)` right from the top bar.
- **Zero-Config End-to-End Encryption (E2EE)**: Automatic **ECDH (NIST P-256)** public key exchange over BLE beacons + **AES-128-GCM** authenticated encryption for private Direct Messages.
- **Crisp Offline Photos (WebP + Zlib + Selective-ACK)**: Full-resolution camera capture (`FileProvider`) or gallery picker compressed to aspect-ratio-preserving 480px WebP with chunked Selective-ACK (SACK) and automatic background retries every ~0.65s until delivered (`✓✓`).
- **Fair Round-Robin Outbox & Multi-Hop Relay**: Unreachable peers never block chats with other peers or Global Mesh.
- **Live Proximity Radar**: Real-time smoothed distance in meters, 4-bar signal strength meter, PHY badges, and E2EE status for active nearby devices.
- **Offline GPS Coordinates & Emergency SOS**: Share satellite GPS coordinates (tap to open in Maps) or broadcast high-priority red SOS alerts.
- **Real Battery Presets**: `ECO` (`SCAN_MODE_LOW_POWER`), `BALANCED`, and `TURBO` with automatic low-latency boost during active transfers, plus Doze/Battery Optimization helper and **Panic Wipe**.

## Build Instructions

```bash
export ANDROID_HOME=/path/to/Android/Sdk
gradle assembleDebug
```

Requires Android SDK 36 (`minSdk = 26`, `targetSdk = 36`) and JDK 17+.
