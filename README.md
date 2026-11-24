# SwiftTunnel

An ultra-lightweight (**under 5 MB**) native Android & Android TV VPN and DNS routing client powered by the **NordVPN v1 API** and a zero-dependency pure-Kotlin **WireGuard (`Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s`)** tunnel engine.

Designed for phones, tablets, and Android TV / Google TV boxes with **Always-On boot auto-connect**, **per-app Split Tunneling**, **Cloudflare `1.1.1.1` / Google `8.8.8.8` routing combinations**, **location-based country/city/server search**, and **hot-swappable NordVPN API keys** encrypted via Android Keystore.

---

## Table of Contents

1. [Key Features](#key-features)
2. [Routing & DNS Modes](#routing--dns-modes)
3. [Architecture Overview](#architecture-overview)
4. [Ultra-Lightweight Size (< 5 MB)](#ultra-lightweight-size--5-mb)
5. [Project Structure](#project-structure)
6. [Getting Started & Building from Source](#getting-started--building-from-source)
7. [Android TV & Always-On Power-Up](#android-tv--always-on-power-up)
8. [Testing & Verification](#testing--verification)
9. [Security & Privacy Model](#security--privacy-model)
10. [Documentation Index](#documentation-index)
11. [Release History](#release-history)

---

## Key Features

- **Pure-Kotlin WireGuard (NordLynx) Engine**: Full RFC-compliant `X25519` (RFC 7748), `BLAKE2s` (RFC 7693), `ChaCha20-Poly1305` / `XChaCha20-Poly1305` (RFC 8439), `TAI64N`, and `Noise_IKpsk2` handshake + transport protocol over UDP port `51820` — zero native `.so` binaries required.
- **5 Flexible Routing Modes**: Combine NordVPN WireGuard with Cloudflare (`1.1.1.1`) or Google (`8.8.8.8`), use NordVPN default DNS, or run lightweight DNS-only protection (`1.1.1.1` or `8.8.8.8`).
- **Full NordVPN Global Coverage (118+ Countries)**: Automatically syncs all supported countries, cities, and live NordLynx servers from the NordVPN v1 REST API, with an offline fallback catalog of 118+ countries.
- **Location-Based & Server Code Search + Direct Connect**:
  - Search by **country name**, **city name**, **2-letter ISO code**, or **NordVPN server number / `#code`** — accepts plain numbers (`13084`, `9948`, `13229`), `#`-prefixed numbers (`#13084`, `#12790`, `#11519`, `#11640`), ranges (`#12790-12792`, `#9898-9899`, `#12394-12398`), country-prefixed hostnames (`us13084`, `uk2410`, `al17`), and city+code queries (`Los Angeles 13084`).
  - Resolves and displays the server's exact **City & Country location**, hostname, station IP, and live load `%` from the live NordLynx fleet (~6,000+ servers streamed via gzip + `JsonReader`) with 1-tap connect.
  - **Quick Connect by City** chips filter live as you type and connect directly to a low-load server in that city.
  - **Nearest Low-Load Nord Locations** strip recommends optimal geo-proximate servers with live load `%`.
  - **Live Server Browser** per country lets you inspect individual NordLynx hostnames, `#codes`, station IPs, cities, and load percentages, then pin & connect in one tap.
- **Per-App Split Tunneling (Include / Exclude / All Apps)**:
  - Route **All Apps**, **Exclude Selected Apps** (bypass VPN), or **Include Only Selected Apps** (e.g., route only Brave or a specific streaming app through Albania while keeping all other apps on your local network).
  - Enforces full `0.0.0.0/0` IPv4 WireGuard routing and `::/0` IPv6 leak blocking inside the TUN interface so included apps truly exit through the remote NordVPN IP.
- **Hot-Swappable Nord API Key**: Swap your NordVPN access token anytime at runtime inside the app — encrypted at rest with **Android Keystore (`AES/GCM/NoPadding`, 256-bit)**. Never hardcoded in source code.
- **Android TV / Google TV Ready**:
  - Leanback launcher icon & `320x180` TV banner (`tv_banner.xml`).
  - Automatic TV detection via `UiModeManager` switches to an overscan-safe **NavigationRail** layout.
  - Custom D-Pad focus rings (`tvFocusableClickable`) with `DPAD_CENTER` / `ENTER` support across all primary controls.
  - Discovers both mobile (`CATEGORY_LAUNCHER`) and Android TV (`CATEGORY_LEANBACK_LAUNCHER`) apps for Split Tunneling.
- **Always-On Auto-Connect on Power-Up**:
  - `BootCompletedReceiver` automatically reconnects your saved routing mode, pointed country/city/server, and split-tunnel configuration on device or TV reboot.
  - Waits intelligently for Wi-Fi/Ethernet availability via `ConnectivityManager.NetworkCallback` (up to 45s) with exponential backoff retry so slow-booting TVs still establish the tunnel reliably.
  - Direct shortcut to Android OS **Always-On VPN** settings (`Settings.ACTION_VPN_SETTINGS`).
- **Background Battery Optimization**:
  - Minimal foreground service footprint (`specialUse` FGS), 500 ms batched UI telemetry (eliminates per-packet recomposition), bounded 4-worker DNS coroutine pool with 256-entry LRU cache (45s TTL), and 25s WireGuard keepalive + 120s zero-downtime rekeying.

---

## Routing & DNS Modes

| Mode | Full WireGuard Tunnel (`0.0.0.0/0`) | Primary DNS | Secondary DNS | Nord API Key Required | Best For |
| :--- | :---: | :--- | :--- | :---: | :--- |
| **Cloudflare + VPN** | Yes (NordLynx UDP:51820) | `1.1.1.1` | `1.0.0.1` | Yes | Maximum privacy + fast DNS resolution + remote IP masking |
| **Google + VPN** | Yes (NordLynx UDP:51820) | `8.8.8.8` | `8.8.4.4` | Yes | High-reliability DNS + remote IP masking |
| **NordVPN Default** | Yes (NordLynx UDP:51820) | `103.86.96.100` | `103.86.99.100` | Yes | Native NordVPN DNS ecosystem |
| **Cloudflare 1.1.1.1 Only** | No (DNS `/32` interception only) | `1.1.1.1` | `1.0.0.1` | No | Zero-overhead fast DNS without changing IP |
| **Google 8.8.8.8 Only** | No (DNS `/32` interception only) | `8.8.8.8` | `8.8.4.4` | No | Zero-overhead reliable DNS without changing IP |

> **Important Note on Split Tunneling & Location Changing**:
> To change an app's public IP address and geolocation (for example, routing Brave Browser through Albania), always select a **VPN mode** (`Cloudflare + VPN`, `Google + VPN`, or `NordVPN Default`). DNS-only modes (`1.1.1.1 Only` / `8.8.8.8 Only`) only forward DNS queries and intentionally preserve your ISP IP address.

---

## Architecture Overview

```
+-----------------------------------------------------------------------------+
|                         Jetpack Compose UI Layer                            |
|  MainActivity (Phone NavBar / Tablet & Android TV Overscan-Safe NavRail)    |
|  ├── ConnectScreen      (Power Orb, Mode Tiles, Always-On Boot, API Key)    |
|  ├── LocationsScreen    (Country/City Search, Nearest Strip, Live Servers)  |
|  └── SplitTunnelScreen  (All / Exclude / Include, Phone + TV App Picker)    |
+--------------------------------------+--------------------------------------+
                                       | StateFlow (MainViewModel)
+--------------------------------------v--------------------------------------+
|                          Data & Security Layer                              |
|  ├── NordApiClient          (HttpURLConnection -> api.nordvpn.com/v1/*)     |
|  ├── NordCountryCatalog     (118+ offline fallback countries + live sync)   |
|  ├── SecureKeyStorage       (Android Keystore AES-256-GCM runtime token)    |
|  ├── PreferencesRepository  (Mode, Country/City/Server, Split Apps, Boot)   |
|  └── InstalledAppsRepository(CATEGORY_LAUNCHER + CATEGORY_LEANBACK_LAUNCHER)|
+--------------------------------------+--------------------------------------+
                                       | Intents / VpnKeyBridge / VpnStateHolder
+--------------------------------------v--------------------------------------+
|                         Android VPN & Boot Layer                            |
|  ├── BootCompletedReceiver  (BOOT_COMPLETED / QUICKBOOT / PACKAGE_REPLACED) |
|  └── SwiftTunnelVpnService  (VpnService + ForegroundService + NetCallback)  |
|        ├── WireGuardTunnelEngine  (Full TUN 0.0.0.0/0 + ::/0 IPv6 Block)    |
|        │     ├── WireGuardSession (Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s)   |
|        │     └── WireGuardCrypto  (Pure-Kotlin X25519/BLAKE2s/ChaCha20)     |
|        └── DnsPacketForwarder     (4-worker pool + 256-entry LRU DNS cache) |
+-----------------------------------------------------------------------------+
```

For deep protocol details, handshake state diagrams, and threading models, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Ultra-Lightweight Size (< 5 MB)

SwiftTunnel is engineered from the ground up to stay **under 5 MB** (~3.4 MB debug APK, ~1.4 MB release APK):

| Technique | Size Impact |
| :--- | :--- |
| Zero native `.so` WireGuard/Go libraries (pure Kotlin crypto) | Saves ~12–25 MB across ABIs |
| Standard `HttpURLConnection` + `org.json` instead of Retrofit/OkHttp/Moshi | Saves ~3–5 MB |
| Custom lightweight Compose `ImageVector` icons (`AppIcons.kt`) instead of `material-icons-extended` | Saves ~6–9 MB |
| Local minimal `.ttf` typography (`Space Grotesk` + `JetBrains Mono`) | Only ~89 KB total |
| R8 code shrinking (`isMinifyEnabled = true`) & resource shrinking (`isShrinkResources = true`) | Strips unused Compose & stdlib bytecode |

---

## Project Structure

```text
.
├── README.md                                  # Project overview & quick start
├── docs/
│   ├── ARCHITECTURE.md                        # Deep-dive system, WireGuard & TV architecture
│   ├── API_AND_SECURITY.md                    # NordVPN REST API, Keystore AES-GCM & Play Policy
│   └── USER_GUIDE.md                          # Phone, Tablet & Android TV user manual
├── .env.example                               # Optional build-time NORD_API_KEY template
├── app/
│   ├── build.gradle.kts                       # Android SDK 36, minSdk 24, R8 shrinking config
│   ├── proguard-rules.pro                     # Keep rules for VpnService, BootReceiver, ViewModel
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml            # Permissions, Leanback TV, VpnService, BootReceiver
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt            # Edge-to-edge host, TV detection, adaptive nav
│       │   │   ├── model/VpnModels.kt         # Domain models & immutable state classes
│       │   │   ├── data/
│       │   │   │   ├── NordApiClient.kt       # NordVPN v1 countries, recommendations, full server stream & credentials
│       │   │   │   ├── ServerCodeMatcher.kt   # Server number, #code, range & hostname query parser/matcher
│       │   │   │   ├── NordCountryCatalog.kt  # 118+ country offline fallback catalog
│       │   │   │   ├── SecureKeyStorage.kt    # Android Keystore AES-256-GCM token vault
│       │   │   │   ├── PreferencesRepository.kt
│       │   │   │   └── InstalledAppsRepository.kt
│       │   │   ├── vpn/
│       │   │   │   ├── SwiftTunnelVpnService.kt
│       │   │   │   ├── BootCompletedReceiver.kt
│       │   │   │   ├── WireGuardTunnelEngine.kt
│       │   │   │   ├── WireGuardSession.kt
│       │   │   │   ├── crypto/WireGuardCrypto.kt
│       │   │   │   ├── DnsPacketForwarder.kt
│       │   │   │   ├── VpnKeyBridge.kt
│       │   │   │   └── VpnStateHolder.kt
│       │   │   └── ui/
│       │   │       ├── MainViewModel.kt
│       │   │       ├── components/TvFocusModifier.kt
│       │   │       ├── icons/AppIcons.kt
│       │   │       ├── screens/{ConnectScreen,LocationsScreen,SplitTunnelScreen}.kt
│       │   │       └── theme/{Color,Type,Theme}.kt
│       │   └── res/
│       │       ├── drawable/tv_banner.xml     # 320x180 Android TV Leanback banner
│       │       ├── font/{space_grotesk,jetbrains_mono}.ttf
│       │       └── mipmap-*/                  # Custom adaptive launcher icons
│   │   └── test/java/com/example/
│   │   │   ├── data/ServerCodeMatcherTest.kt  # Server number, #code, range & city lookup unit tests
│   │   │   └── vpn/WireGuardCryptoTest.kt     # RFC 7748, RFC 7693, RFC 8439 & Noise_IKpsk2 tests
├── vpnv1.apk                                  # v1 release artifact
├── vpnv2.apk                                  # v2 release artifact (WireGuard + Split Tunnel fix)
├── vpnv3.apk                                  # v3 release artifact (Android TV + Always-On + Search)
└── vpnv4.apk                                  # v4 release artifact (Live Server #Code / Number Search)
```

---

## Getting Started & Building from Source

### Prerequisites

- **JDK 11+**
- **Android SDK** (compileSdk 36, minSdk 24) with `ANDROID_SDK_ROOT` configured
- **Gradle** (or Android Studio Ladybug / Meerkat+)
- A valid **NordVPN Manual Access Token** (obtained from your Nord Account dashboard under *Services → NordVPN → Manual setup → Generate new token*)

### Configure an Optional Build-Time API Key

You do **not** need to bake an API key into the APK — you can enter or swap your NordVPN token anytime inside the app UI. If you want a pre-filled fallback key for your own private builds:

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
# Clean stale incremental APK packaging before assembling to guarantee optimal size (< 5 MB)
rm -f app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleDebug
```

Output APK location: `app/build/outputs/apk/debug/app-debug.apk` (shipped in repo root as `vpnv4.apk`).

For a release build (requires `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` environment variables):

```bash
gradle :app:assembleRelease
```

### Install on Android Phone or Android TV via ADB

```bash
# Phone (USB)
adb install -r vpnv4.apk

# Android TV / Google TV (Network ADB)
adb connect <TV_IP_ADDRESS>:5555
adb install -r vpnv4.apk
```

---

## Android TV & Always-On Power-Up

1. Open **SwiftTunnel** on your Android TV from the TV Apps row (displayed with the SwiftTunnel TV Banner).
2. Navigate using your TV remote **D-Pad** — the side **NavigationRail** switches between **Connect**, **Locations**, and **Split Tunnel**.
3. Enter your NordVPN API key in the **NordVPN Credentials** card, choose your **Routing Mode** (e.g., `Cloudflare + VPN`), pick your **Country / City / Server `#code`** (e.g., `13084` or `#12790`), and optionally configure **Split Tunneling**.
4. Connect once so Android grants VPN consent (`VpnService.prepare`).
5. Ensure **Auto-Connect on Power-Up** is switched **ON** in the Connect screen. Every time your phone or Android TV reboots, `BootCompletedReceiver` waits for Wi-Fi/Ethernet to come up and automatically reconnects to your exact pointed location, routing mode, and split-tunneling rules.
6. On Android phones and supported TV builds, you can also tap **OS Always-On** to enable Android's system-level Always-On VPN lock.

See [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) for full step-by-step instructions.

---

## Testing & Verification

Run the pure-JVM WireGuard cryptographic and server-code lookup unit tests:

```bash
gradle :app:testDebugUnitTest \
  --tests "com.example.vpn.WireGuardCryptoTest" \
  --tests "com.example.data.ServerCodeMatcherTest"
```

Verified test suites:
1. **`WireGuardCryptoTest`**:
   - **RFC 7748 X25519** official test vectors (clamping, scalar multiplication, shared secret agreement).
   - **RFC 7693 BLAKE2s** unkeyed & keyed digest verification.
   - **RFC 8439 ChaCha20-Poly1305 AEAD** official encryption & authentication tag test vector.
   - **End-to-end `Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s`** initiator ↔ responder handshake (Type 1 → Type 2) + bidirectional Type 4 encrypted transport payload round-trip.
2. **`ServerCodeMatcherTest`**:
   - Plain server numbers (`13084`, `9948`, `13229`) and `#`-prefixed codes (`#13084`, `#12790`, `#11519`, `#11640`) resolving exact city/country locations.
   - Range expansion (`#12790-12792`, `#9898-9899`, `#12394-12398`), comma lists, country/hostname prefixes (`us13084.nordvpn.com`, `uk2410` → `GB`), prefix autocomplete, and non-code query passthrough.

---

## Security & Privacy Model

- **Zero Hardcoded Secrets**: No API keys or private keys exist anywhere in source code, manifests, or version control.
- **Hardware-Backed Key Encryption**: Swappable NordVPN API keys are encrypted via **Android Keystore (`AES/GCM/NoPadding`, 256-bit)** with unique per-write IVs (`SecureKeyStorage.kt`).
- **Memory-Only WireGuard Private Key**: The NordLynx private key fetched from NordVPN's credentials endpoint is passed strictly in-memory via `VpnKeyBridge` — never written to disk or Intent extras.
- **IPv6 Leak Prevention**: In VPN modes, the TUN interface binds `fd00:5:0::2/128` with route `::/0` and drops outbound IPv6 packets so dual-stack browsers (like Brave/Chrome) cannot bypass the IPv4 WireGuard tunnel.
- **Minimal Permissions**: Uses scoped `<queries>` (`LAUNCHER` + `LEANBACK_LAUNCHER`) instead of `QUERY_ALL_PACKAGES` for Split Tunneling app discovery.

Full details in [`docs/API_AND_SECURITY.md`](docs/API_AND_SECURITY.md).

---

## Documentation Index

| Document | Contents |
| :--- | :--- |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | System layers, MVVM data flow, WireGuard `Noise_IKpsk2` packet mechanics, DNS LRU worker pool, Split Tunneling TUN builder logic, Android TV D-Pad & Boot Auto-Connect lifecycle |
| [`docs/API_AND_SECURITY.md`](docs/API_AND_SECURITY.md) | NordVPN v1 REST API contracts, JSON field extraction, Keystore AES-256-GCM encryption, `VpnKeyBridge`, ProGuard/R8 rules, and Play Policy compliance |
| [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) | Complete end-to-end usage guide for Phones, Tablets, and Android TV (installation, key swapping, routing modes, location/city/server search, split tunneling, Always-On boot setup, troubleshooting) |

---

## Release History

- **v4.0 (`vpnv4.apk`)**: Added live NordVPN **Server Number / `#Code` Search** across the full NordLynx fleet (accepts plain numbers like `13084`, `#` codes like `#12790`, ranges like `#12790-12792` / `#12394-12398`, country prefixes like `us13084`/`uk2410`, and city+code queries), displays the resolved **City & Country** location with 1-tap connect, streams gzip JSON via `android.util.JsonReader` for low-RAM Android TV safety, added `ServerCodeMatcherTest`, and updated full documentation.
- **v3.0 (`vpnv3.apk`)**: Full Android TV / Leanback compatibility (TV banner, overscan-safe NavigationRail, D-Pad focus rings, TV app discovery), Always-On auto-connect on power-up (`BootCompletedReceiver` + `NetworkCallback` wait & retry + OS VPN settings shortcut), location-based city/country/live-server search & direct 1-tap connect, and complete end-to-end Git documentation.
- **v2.0 (`vpnv2.apk`)**: Replaced DNS-only VPN path with full pure-Kotlin WireGuard (`Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s`) tunnel engine (`0.0.0.0/0` + `::/0` IPv6 leak block), fixed Split Tunneling IP geolocation masking (e.g., Brave routed through Albania), added RFC unit test suite, maintained < 5 MB APK size.
- **v1.0 (`vpnv1.apk`)**: Initial release with NordVPN API integration (118+ countries), swappable Keystore-encrypted API keys, Cloudflare `1.1.1.1` / Google `8.8.8.8` + VPN routing modes, Split Tunneling UI, and background optimization under 5 MB.
