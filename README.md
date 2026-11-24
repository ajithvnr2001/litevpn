# SwiftTunnel

An ultra-lightweight (**3.6 MB**, strictly under 5 MB) native Android & Android TV VPN and DNS routing client powered by the **NordVPN v1 API** and a zero-dependency pure-Kotlin **WireGuard (`Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s`)** tunnel engine.

Designed for phones, tablets, and Android TV / Google TV boxes with **Always-On boot auto-connect**, **per-app Split Tunneling**, **Cloudflare `1.1.1.1` / Google `8.8.8.8` / AdGuard / Quad9 / Custom IPv4 DNS routing**, **LAN / Local Network Bypass**, **Auto-Failover & Wi-Fi/5G Handover**, **Android Quick Settings Tile**, **1-Tap Saved Profiles**, **Built-In IP & Leak Verification**, **Live Throughput Sparkline Graph**, **Custom MTU + 1-Tap Server Hop**, **Secret-Free JSON Config Backup**, **live server `#code` search**, and **hot-swappable NordVPN API keys** encrypted via Android Keystore.

---

## Table of Contents

1. [Key Features](#key-features)
2. [Routing & DNS Modes + Filter Presets](#routing--dns-modes--filter-presets)
3. [Architecture Overview](#architecture-overview)
4. [Ultra-Lightweight Size (3.6 MB < 5 MB)](#ultra-lightweight-size-36-mb--5-mb)
5. [Project Structure](#project-structure)
6. [Getting Started & Building from Source](#getting-started--building-from-source)
7. [Android TV, Quick Settings Tile & Always-On Power-Up](#android-tv-quick-settings-tile--always-on-power-up)
8. [Testing & Verification](#testing--verification)
9. [Security & Privacy Model](#security--privacy-model)
10. [Documentation Index](#documentation-index)
11. [Release History](#release-history)

---

## Key Features

- **Pure-Kotlin WireGuard (NordLynx) Engine**: Full RFC-compliant `X25519` (RFC 7748), `BLAKE2s` (RFC 7693), `ChaCha20-Poly1305` / `XChaCha20-Poly1305` (RFC 8439), `TAI64N`, and `Noise_IKpsk2` handshake + transport protocol over UDP port `51820` — zero native `.so` binaries required.
- **5 Flexible Routing Modes + DNS Ad-Blocking & Custom IPv4 Presets**:
  - Combine NordVPN WireGuard with Cloudflare (`1.1.1.1`) or Google (`8.8.8.8`), use NordVPN default DNS, or run lightweight DNS-only protection (`1.1.1.1` or `8.8.8.8`).
  - Override any mode with **AdGuard Ad-Block (`94.140.14.14` / `94.140.15.15`)**, **Quad9 Security (`9.9.9.9` / `149.112.112.112`)**, or a validated **Custom IPv4 DNS** pair (e.g., NextDNS, ControlD, Pi-hole).
- **LAN / Local Network Bypass (All Android Versions 7–16)**:
  - Keeps Chromecast, Plex/Jellyfin media servers, NAS/SMB shares, local printers, and ADB over Wi-Fi reachable while 100% of public internet traffic remains encrypted through WireGuard.
  - Uses native `VpnService.Builder.excludeRoute()` on Android 13+ (API 33+) and an exact pure-Kotlin IPv4 CIDR complement route calculator (`LanBypassRouteCalculator`) on Android 7–12 (API 24–32, including legacy Android TV boxes).
- **Auto-Failover & Seamless Wi-Fi ↔ 5G/LTE Handover**:
  - Monitors the underlying active transport via `ConnectivityManager.NetworkCallback` and cleanly re-handshakes WireGuard when switching between Wi-Fi and Cellular without manual intervention.
  - When a pinned NordVPN server times out or goes down for maintenance, automatically fails over to the next lowest-load WireGuard server in the same city/country.
- **Android Quick Settings Tile (`SwiftTunnelTileService`)**:
  - 1-tap connect/disconnect right from the Android notification shade showing live active status plus the current `#code` & city subtitle (e.g., `#13084 • Los Angeles`).
- **1-Tap Saved Profiles**:
  - Save up to 12 complete presets (Routing Mode + DNS Preset/Custom IPv4 + Country/City/Pinned Server `#code` + Split-Tunnel App List + LAN Bypass + Custom MTU) and apply & connect in a single tap or Android TV OK press.
- **Built-In IP & Leak Verification Test**:
  - Live one-tap diagnostic check via `https://1.1.1.1/cdn-cgi/trace` verifying your current Exit IP, Country, Cloudflare Colo, exact NordVPN server IP match, IPv6 leak-block status, and active DNS resolvers.
- **Live Connection Details, Location `#Code` & 30-Second Throughput Sparkline Graph (Phone & Android TV)**:
  - Prominent **Connection Details & Stats** card shows the active **Location Code badge** (`#13084`), resolved **City & Country**, hostname, WireGuard endpoint (`IP:51820`), tunnel IP (`10.5.0.2/32`), protocol, DNS servers, split-tunnel scope, LAN routing status, active MTU, exit IP trace, live **↓ Download / ↑ Upload speeds**, cumulative session totals, latency (`ms`), server load `%`, packet/query counts, and uptime.
  - Includes a pure Jetpack Compose **`Canvas` Dual-Series Throughput Sparkline Graph** plotting a 30-second rolling Download (Emerald gradient) and Upload (Electric Cyan) speed trace with zero charting library bloat.
  - Automatically switches to a **2-Column Command Center** on Android TV & widescreen tablets.
- **Custom MTU (`1280`–`1420`) & 1-Tap Lower-Load Server Hop**:
  - Tune WireGuard interface MTU (`1280` safe mobile/cellular, `1360`, `1400`, `1420` fast Wi-Fi/Ethernet) for maximum throughput or zero fragmentation.
  - Tap **Hop to Lower-Load Server in Same Region** to switch immediately to the least-congested NordLynx server in your current city/country.
- **Secret-Free JSON Config Export & Import (Phone ↔ Android TV)**:
  - Export and import your full configuration (mode, DNS presets, favorites, split-tunnel lists, LAN bypass, auto-failover, MTU, and all Saved Profiles) via zero-permission Android Storage Access Framework (`CreateDocument` / `OpenDocument`) or a built-in **Copy / Paste JSON dialog** tailored for Android TV.
  - **Security Invariant**: Always emits `"secretsExcluded": true` and **never** exports or imports NordVPN API keys or WireGuard private keys.
- **Full NordVPN Global Coverage (118+ Countries) + Server `#Code` Search**:
  - Search by **country**, **city**, **2-letter ISO code**, or **NordVPN server number / `#code`** (`13084`, `#12790`, `#12790-12792`, `us13084`, `uk2410`, `Los Angeles 13084`) across ~6,000+ live NordLynx servers streamed via gzip + `JsonReader`.
- **Per-App Split Tunneling (Include / Exclude / All Apps)**:
  - Enforces full `0.0.0.0/0` IPv4 WireGuard routing and `::/0` IPv6 leak blocking inside the TUN interface so included apps (e.g., Brave Browser or a specific Android TV app) truly exit through the remote NordVPN IP.
- **Hot-Swappable Nord API Key**:
  - Swap your NordVPN access token anytime at runtime inside the app — encrypted at rest with **Android Keystore (`AES/GCM/NoPadding`, 256-bit)**. Never hardcoded in source code.
- **Android TV / Google TV Ready & Always-On Power-Up**:
  - Leanback banner (`tv_banner.xml`), overscan-safe **NavigationRail**, **2-Column Command Center**, glowing D-Pad focus rings (`tvFocusableClickable`), Leanback + Launcher app discovery, and `BootCompletedReceiver` with `NetworkCallback` wait & retry.

---

## Routing & DNS Modes + Filter Presets

### Base Routing Modes

| Mode | Full WireGuard Tunnel (`0.0.0.0/0`) | Default Primary DNS | Default Secondary DNS | Nord API Key Required | Best For |
| :--- | :---: | :--- | :--- | :---: | :--- |
| **Cloudflare + VPN** | Yes (NordLynx UDP:51820) | `1.1.1.1` | `1.0.0.1` | Yes | Maximum privacy + fast DNS + remote IP masking |
| **Google + VPN** | Yes (NordLynx UDP:51820) | `8.8.8.8` | `8.8.4.4` | Yes | High-reliability DNS + remote IP masking |
| **NordVPN Default** | Yes (NordLynx UDP:51820) | `103.86.96.100` | `103.86.99.100` | Yes | Native NordVPN DNS ecosystem |
| **Cloudflare 1.1.1.1 Only** | No (DNS `/32` interception only) | `1.1.1.1` | `1.0.0.1` | No | Zero-overhead fast DNS without changing IP |
| **Google 8.8.8.8 Only** | No (DNS `/32` interception only) | `8.8.8.8` | `8.8.4.4` | No | Zero-overhead reliable DNS without changing IP |

### v6 DNS Filter & Ad-Blocking Presets (Overlay on Any Mode)

| Preset | Primary DNS | Secondary DNS | Purpose |
| :--- | :--- | :--- | :--- |
| **Mode Default** | Active mode default | Active mode default | Standard Cloudflare / Google / Nord resolvers |
| **AdGuard Ad-Block** | `94.140.14.14` | `94.140.15.15` | System-wide ad, tracker & telemetry blocking (great for Android TV!) |
| **Quad9 Security** | `9.9.9.9` | `149.112.112.112` | Malware, phishing & threat-intelligence DNS blocking with DNSSEC |
| **Custom IPv4** | User-validated IPv4 | User-validated IPv4 | NextDNS, ControlD, Pi-hole, or private corporate/home resolvers |

> **Important Note on Split Tunneling & Location Changing**:
> To change an app's public IP address and geolocation (for example, routing Brave Browser through Albania), always select a **VPN mode** (`Cloudflare + VPN`, `Google + VPN`, or `NordVPN Default`). DNS-only modes (`1.1.1.1 Only` / `8.8.8.8 Only`) only forward DNS queries and intentionally preserve your ISP IP address.

---

## Architecture Overview

```
+---------------------------------------------------------------------------------+
|                           Jetpack Compose UI Layer                              |
|  MainActivity (Phone NavBar / Tablet & Android TV Overscan-Safe NavRail + SAF)  |
|  ├── ConnectScreen      (Power Orb, ConnectionDetailsCard + Canvas Sparkline,   |
|  │                       PowerToolsSection: Profiles, DNS Presets, LAN/MTU/Hop, |
|  │                       IP & Leak Check, Secret-Free JSON Backup, API Key)     |
|  ├── LocationsScreen    (Country/City/#Code Search, Nearest Strip, Live Servers)|
|  └── SplitTunnelScreen  (All / Exclude / Include, Phone + TV App Picker)        |
+---------------------------------------+-----------------------------------------+
                                        | StateFlow (MainViewModel)
+---------------------------------------v-----------------------------------------+
|                            Data & Security Layer                                |
|  ├── NordApiClient          (HttpURLConnection -> api.nordvpn.com/v1/* + trace) |
|  ├── ServerCodeMatcher      (Numbers, #codes, ranges, prefixes, city+code)      |
|  ├── NordCountryCatalog     (118+ offline fallback countries + live sync)       |
|  ├── ConfigBackupCodec      (Zero-dep RFC 8259 JSON backup, secretsExcluded)    |
|  ├── SecureKeyStorage       (Android Keystore AES-256-GCM runtime token)        |
|  ├── PreferencesRepository  (Mode, DNS Preset, Profiles, LAN/MTU, Split, Boot)  |
|  └── InstalledAppsRepository(CATEGORY_LAUNCHER + CATEGORY_LEANBACK_LAUNCHER)    |
+---------------------------------------+-----------------------------------------+
                                        | Intents / VpnKeyBridge / VpnStateHolder
+---------------------------------------v-----------------------------------------+
|                    Android VPN, Tile & Boot Resilience Layer                    |
|  ├── SwiftTunnelTileService (Android Quick Settings 1-tap toggle + #code label) |
|  ├── BootCompletedReceiver  (BOOT_COMPLETED / QUICKBOOT / PACKAGE_REPLACED)     |
|  └── SwiftTunnelVpnService  (VpnService + FGS + Handover Callback + Failover)   |
|        ├── LanBypassRouteCalculator (API 33+ excludeRoute / API 24-32 CIDR set) |
|        ├── WireGuardTunnelEngine    (Custom MTU + TUN + ::/0 IPv6 Leak Block)   |
|        │     ├── WireGuardSession   (Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s)     |
|        │     └── WireGuardCrypto    (Pure-Kotlin X25519/BLAKE2s/ChaCha20)       |
|        └── DnsPacketForwarder       (4-worker pool + 256-entry LRU DNS cache)   |
+---------------------------------------------------------------------------------+
```

For deep protocol details, CIDR complement math, handover/failover flows, and threading models, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Ultra-Lightweight Size (3.6 MB < 5 MB)

Even with all 9 v6 power features added, **`vpnv6.apk` measures only 3.6 MB** (1.4 MB below the 5 MB ceiling):

| Technique | Size Impact |
| :--- | :--- |
| Zero native `.so` WireGuard/Go libraries (pure Kotlin crypto) | Saves ~12–25 MB across ABIs |
| Standard `HttpURLConnection` + `org.json` + custom `ConfigBackupCodec` (no Retrofit/OkHttp/Moshi/Gson) | Saves ~3–5 MB |
| Pure Compose `Canvas` throughput sparkline (no Vico/MPAndroidChart) | Saves ~1–3 MB |
| Custom lightweight Compose `ImageVector` icons (`AppIcons.kt`) instead of `material-icons-extended` | Saves ~6–9 MB |
| Local minimal `.ttf` typography (`Space Grotesk` + `JetBrains Mono`) | Only ~89 KB total |
| R8 code shrinking (`isMinifyEnabled = true`) & resource shrinking (`isShrinkResources = true`) | Strips unused Compose & stdlib bytecode |

---

## Project Structure

```text
.
├── README.md                                  # Project overview & quick start
├── docs/
│   ├── ARCHITECTURE.md                        # Deep-dive system, WireGuard, LAN Bypass, Failover & TV architecture
│   ├── API_AND_SECURITY.md                    # NordVPN REST API, Keystore AES-GCM, Backup Security & Play Policy
│   └── USER_GUIDE.md                          # Complete Phone, Tablet & Android TV v6 user manual
├── .env.example                               # Optional build-time NORD_API_KEY template
├── app/
│   ├── build.gradle.kts                       # Android SDK 36, minSdk 24, versionCode 6 (6.0), R8 shrinking
│   ├── proguard-rules.pro                     # Keep rules for VpnService, TileService, BootReceiver, ViewModel
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml            # Permissions, Leanback TV, VpnService, TileService, BootReceiver
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt            # Edge-to-edge host, TV detection, SAF backup launchers, adaptive nav
│       │   │   ├── model/
│       │   │   │   ├── VpnModels.kt           # Routing modes, DnsPreset, SavedVpnProfile, LeakCheckResult, IPv4 check
│       │   │   │   └── ConnectionTelemetry.kt # Live throughput, location #code, LAN/MTU & snapshot formatter
│       │   │   ├── data/
│       │   │   │   ├── NordApiClient.kt       # NordVPN v1 countries, recommendations, server stream, hostname lookup & trace
│       │   │   │   ├── ServerCodeMatcher.kt   # Server number, #code, range & hostname query parser/matcher
│       │   │   │   ├── ConfigBackupCodec.kt   # Pure-Kotlin RFC 8259 JSON config encoder/decoder (secretsExcluded)
│       │   │   │   ├── NordCountryCatalog.kt  # 118+ country offline fallback catalog
│       │   │   │   ├── SecureKeyStorage.kt    # Android Keystore AES-256-GCM token vault
│       │   │   │   ├── PreferencesRepository.kt
│       │   │   │   └── InstalledAppsRepository.kt
│       │   │   ├── vpn/
│       │   │   │   ├── SwiftTunnelVpnService.kt      # VpnService + LAN Bypass + MTU + Auto-Failover + Wi-Fi/5G Handover
│       │   │   │   ├── SwiftTunnelTileService.kt     # Android Quick Settings 1-tap VPN Tile
│       │   │   │   ├── LanBypassRouteCalculator.kt   # IPv4 CIDR complement calculator for LAN bypass
│       │   │   │   ├── BootCompletedReceiver.kt
│       │   │   │   ├── WireGuardTunnelEngine.kt
│       │   │   │   ├── WireGuardSession.kt
│       │   │   │   ├── crypto/WireGuardCrypto.kt
│       │   │   │   ├── DnsPacketForwarder.kt
│       │   │   │   ├── VpnKeyBridge.kt
│       │   │   │   └── VpnStateHolder.kt
│       │   │   └── ui/
│       │   │       ├── MainViewModel.kt
│       │   │       ├── components/
│       │   │       │   ├── ConnectionDetailsCard.kt  # Live #code + stats + 30s Canvas Sparkline Graph
│       │   │       │   ├── PowerToolsSection.kt      # DNS Presets, Profiles, LAN/Failover/MTU/Hop, Leak Test, Backup
│       │   │       │   └── TvFocusModifier.kt
│       │   │       ├── icons/AppIcons.kt
│       │   │       ├── screens/{ConnectScreen,LocationsScreen,SplitTunnelScreen}.kt
│       │   │       └── theme/{Color,Type,Theme}.kt
│       │   └── res/
│       │       ├── drawable/tv_banner.xml     # 320x180 Android TV Leanback banner
│       │       ├── font/{space_grotesk,jetbrains_mono}.ttf
│       │       └── mipmap-*/                  # Custom adaptive launcher icons
│       └── test/java/com/example/
│           ├── data/ServerCodeMatcherTest.kt  # Server number, #code, range & city lookup unit tests
│           ├── model/ConnectionTelemetryTest.kt # Live connection details & telemetry formatting tests
│           ├── v6/V6PowerFeaturesTest.kt      # LAN CIDR complement, DNS presets, IPv4 validator & JSON backup tests
│           └── vpn/WireGuardCryptoTest.kt     # RFC 7748, RFC 7693, RFC 8439 & Noise_IKpsk2 tests
├── vpnv1.apk                                  # v1.0 release artifact (3.4 MB)
├── vpnv2.apk                                  # v2.0 release artifact (3.4 MB — WireGuard + Split Tunnel fix)
├── vpnv3.apk                                  # v3.0 release artifact (3.5 MB — Android TV + Always-On + Search)
├── vpnv4.apk                                  # v4.0 release artifact (3.5 MB — Live Server #Code / Number Search)
├── vpnv5.apk                                  # v5.0 release artifact (3.5 MB — Connection Details + #Code + Live Stats)
└── vpnv6.apk                                  # v6.0 release artifact (3.6 MB — All 9 Zero-Bloat Power Features)
```

---

## Getting Started & Building from Source

### Prerequisites

- **JDK 11+**
- **Android SDK** (`compileSdk 36`, `minSdk 24`) with `ANDROID_SDK_ROOT` configured
- **Gradle** (or Android Studio Ladybug / Meerkat+)
- A valid **NordVPN Manual Access Token** (obtained from your Nord Account dashboard under *Services → NordVPN → Manual setup → Generate new token*)

### Configure an Optional Build-Time API Key

You do **not** need to bake an API key into the APK — you can enter or swap your NordVPN token anytime inside the app UI. If you want a pre-filled fallback key for private builds:

1. Copy `.env.example` to `.env` (gitignored — never commit `.env`):
   ```bash
   cp .env.example .env
   ```
2. Set your token in `.env`:
   ```env
   NORD_API_KEY=<YOUR_NORD_ACCESS_TOKEN>
   ```

### Build the APK

```bash
# Delete stale incremental APK before assembling to avoid ZIP padding bloat (< 5 MB)
rm -f app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleDebug
```

Output APK location: `app/build/outputs/apk/debug/app-debug.apk` (shipped in repo root as `vpnv6.apk`, **3.6 MB**).

For a signed release build (requires `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` environment variables):

```bash
gradle :app:assembleRelease
```

### Install on Android Phone or Android TV via ADB

```bash
# Phone (USB)
adb install -r vpnv6.apk

# Android TV / Google TV (Network ADB)
adb connect <TV_IP_ADDRESS>:5555
adb install -r vpnv6.apk
```

---

## Android TV, Quick Settings Tile & Always-On Power-Up

1. **Android TV Command Center**: Open **SwiftTunnel** from your Android TV Apps row. The **Connect** tab renders in a 2-Column Command Center keeping the Power Orb, **Connection Details & Live Sparkline Stats**, and **IP & Leak Verification** on the left, while **1-Tap Saved Profiles**, Routing Modes, **AdGuard/Quad9/Custom DNS**, **LAN Bypass / Failover / MTU / Server Hop**, Split Tunneling, and **Copy/Paste Config Backup** stay cleanly accessible on the right with D-Pad focus rings.
2. **Android Phone Quick Settings Tile**: Pull down your notification shade twice → tap **Edit (pencil)** → drag **SwiftTunnel** into your active tiles for 1-tap connect/disconnect with live `#code` and city status.
3. **Always-On Boot Auto-Connect**: Connect once interactively so Android grants VPN permission (`VpnService.prepare`), ensure **Auto-Connect on Power-Up** is **ON**, and SwiftTunnel automatically reconnects after every device or TV reboot — restoring your exact mode, DNS preset, pointed server `#code`, split-tunnel apps, LAN bypass, and MTU.

See [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) for full step-by-step instructions.

---

## Testing & Verification

Run all 4 pure-JVM unit test suites:

```bash
gradle :app:testDebugUnitTest \
  --tests "com.example.vpn.WireGuardCryptoTest" \
  --tests "com.example.data.ServerCodeMatcherTest" \
  --tests "com.example.model.ConnectionTelemetryTest" \
  --tests "com.example.v6.V6PowerFeaturesTest"
```

Verified test suites:
1. **`WireGuardCryptoTest`**: RFC 7748 X25519, RFC 7693 BLAKE2s, RFC 8439 ChaCha20-Poly1305 AEAD, and full `Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s` handshake + bidirectional encrypted Type 4 transport round-trip.
2. **`ServerCodeMatcherTest`**: Plain server numbers (`13084`, `9948`, `13229`), `#`-prefixed codes (`#12790`, `#11519`, `#11640`), range expansion (`#12790-12792`, `#12394-12398`), country/hostname prefixes (`us13084`, `uk2410` → `GB`), and prefix autocomplete.
3. **`ConnectionTelemetryTest`**: Connected WireGuard snapshot, DNS-only snapshot, standby snapshot, and byte/rate/duration formatters.
4. **`V6PowerFeaturesTest`**:
   - `LanBypassRouteCalculator` CIDR complement verification (100% of RFC 1918 LAN, loopback, link-local, mDNS/Chromecast multicast, and broadcast IPs excluded; 100% of public internet IPs routed through VPN).
   - `isValidIpv4Address` & `resolveEffectiveDns` (AdGuard, Quad9, Custom IPv4, invalid-input fallback).
   - `ConfigBackupCodec` round-trip serialization, MTU clamping (`1280..1420`), invalid IPv4 sanitization, and strict `"secretsExcluded": true` invariant.
   - v6 telemetry fields (`AdGuard` DNS override, `LAN Bypass ON`, `MTU 1360`).

---

## Security & Privacy Model

- **Zero Hardcoded Secrets**: No API keys or private keys exist anywhere in source code, manifests, docs, or version control.
- **Hardware-Backed Key Encryption**: Swappable NordVPN API keys are encrypted via **Android Keystore (`AES/GCM/NoPadding`, 256-bit)** with unique per-write IVs (`SecureKeyStorage.kt`).
- **Secret-Free Backups**: `ConfigBackupCodec` explicitly omits NordVPN API keys and WireGuard private keys (`"secretsExcluded": true`), making exported JSON files safe to transfer between your phone and Android TV.
- **Memory-Only WireGuard Private Key**: The NordLynx private key fetched from NordVPN's credentials endpoint is passed strictly in-memory via `VpnKeyBridge` and wiped on disconnect.
- **IPv6 Leak Prevention**: In VPN modes, the TUN interface binds `fd00:5:0::2/128` with route `::/0` and drops outbound IPv6 packets so dual-stack browsers cannot bypass the IPv4 WireGuard tunnel.
- **Zero Storage Permissions & Minimal Package Visibility**: Uses zero-permission Android SAF (`CreateDocument` / `OpenDocument`) for JSON backups and scoped `<queries>` (`LAUNCHER` + `LEANBACK_LAUNCHER`) instead of `QUERY_ALL_PACKAGES`.

Full details in [`docs/API_AND_SECURITY.md`](docs/API_AND_SECURITY.md).

---

## Documentation Index

| Document | Contents |
| :--- | :--- |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | System layers, MVVM data flow, WireGuard `Noise_IKpsk2` mechanics, LAN Bypass CIDR complement math, Auto-Failover & Wi-Fi/5G Handover, Quick Settings Tile, Sparkline Canvas, Backup Codec, and Android TV lifecycle |
| [`docs/API_AND_SECURITY.md`](docs/API_AND_SECURITY.md) | NordVPN v1 REST API contracts, Cloudflare Trace leak verification, Keystore AES-256-GCM encryption, Secret-Free JSON Backup security, ProGuard/R8 rules, and Play Policy compliance |
| [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) | Complete end-to-end v6 user manual for Phones, Tablets, and Android TV (installation, key swapping, routing modes, AdGuard/Quad9/Custom DNS, 1-Tap Profiles, LAN Bypass, MTU & Server Hop, Quick Settings Tile, Leak Check, Backup/Restore, troubleshooting) |

---

## Release History

- **v6.0 (`vpnv6.apk`, 3.6 MB)**: Added **all 9 zero-bloat power features** while keeping APK size strictly under 5 MB: (1) **LAN / Local Network Bypass** (`excludeRoute` on Android 13+ & exact IPv4 CIDR complement routes on Android 7–12), (2) **Auto-Failover & Wi-Fi/5G Network Switch Handover**, (3) **Android Quick Settings Tile** (`SwiftTunnelTileService`), (4) **Ad-Blocking & Custom IPv4 DNS Presets** (AdGuard `94.140.14.14`, Quad9 `9.9.9.9`, validated Custom IPv4), (5) **1-Tap Saved Profiles**, (6) **Built-In IP & Leak Verification Test**, (7) **Live 30-Second Canvas Throughput Sparkline Graph**, (8) **Custom WireGuard MTU (`1280`–`1420`) & 1-Tap Lower-Load Server Hop**, and (9) **Secret-Free JSON Config Export / Import** (SAF + Android TV Copy/Paste dialog), plus `V6PowerFeaturesTest` and updated end-to-end docs.
- **v5.0 (`vpnv5.apk`, 3.5 MB)**: Added dedicated **Connection Details & Live Telemetry Stats** dashboard for Android mobile and Android TV showing active **Location `#Code`**, resolved **City & Country**, endpoint, tunnel IP, live ↓/↑ throughput, totals, latency, server load `%`, 2-Column Android TV Command Center, and `ConnectionTelemetryTest`.
- **v4.0 (`vpnv4.apk`, 3.5 MB)**: Added live NordVPN **Server Number / `#Code` Search** across ~6,000+ NordLynx servers (numbers, `#codes`, ranges, country prefixes, city+code), memory-safe gzip `JsonReader` streaming, and `ServerCodeMatcherTest`.
- **v3.0 (`vpnv3.apk`, 3.5 MB)**: Full Android TV / Leanback compatibility, Always-On auto-connect on power-up (`BootCompletedReceiver` + `NetworkCallback`), location-based city/country/live-server search & direct 1-tap connect, and complete end-to-end documentation.
- **v2.0 (`vpnv2.apk`, 3.4 MB)**: Full pure-Kotlin WireGuard (`Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s`) tunnel engine (`0.0.0.0/0` + `::/0` IPv6 leak block), fixed Split Tunneling IP geolocation masking, and RFC unit test suite.
- **v1.0 (`vpnv1.apk`, 3.4 MB)**: Initial release with NordVPN API integration (118+ countries), swappable Keystore-encrypted API keys, Cloudflare `1.1.1.1` / Google `8.8.8.8` + VPN routing modes, Split Tunneling UI, and background optimization under 5 MB.
