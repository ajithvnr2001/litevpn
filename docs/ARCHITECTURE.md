# SwiftTunnel System & Protocol Architecture (v6.0)

This document explains the internal software architecture, packet-level networking mechanics, pure-Kotlin WireGuard implementation, LAN Bypass CIDR complement routing, Auto-Failover & Network Switch Handover, Quick Settings Tile, pure-Compose throughput sparkline, secret-free JSON backup codec, Android TV adaptations, and Always-On boot auto-connect lifecycle of **SwiftTunnel v6.0**.

---

## 1. Architectural Pattern (MVVM + Unidirectional State)

SwiftTunnel follows a strict **MVVM** architecture with reactive Kotlin `StateFlow` pipelines and zero heavy DI frameworks (lightweight constructor injection):

```
UI Composables (ConnectScreen, PowerToolsSection, ConnectionDetailsCard, LocationsScreen, SplitTunnelScreen)
       │  collectAsStateWithLifecycle()
       ▼
MainViewModel (AndroidViewModel)
       │  Combines:
       │  ├── VpnStateHolder.sessionState     (Live service telemetry, status, active DNS/LAN/MTU)
       │  ├── SecureKeyStorage                (Encrypted Nord API key status)
       │  ├── PreferencesRepository           (Mode, DNS Preset, Saved Profiles, LAN/MTU/Failover, Split, Boot)
       │  ├── ConfigBackupCodec               (Pure-Kotlin secret-free JSON import/export)
       │  ├── NordApiClient                   (Live countries, recommendations, server stream, hostname lookup, trace)
       │  └── InstalledAppsRepository         (Mobile + Android TV launchable apps)
       ▼
SwiftTunnelVpnService (Android VpnService + specialUse Foreground Service)
       ├── LanBypassRouteCalculator           (API 33+ excludeRoute / API 24-32 IPv4 CIDR complement routes)
       ├── ConnectivityManager.NetworkCallback(Wi-Fi <-> 5G/LTE Handover + Boot network wait)
       ├── WireGuardTunnelEngine              (VPN Modes: Custom MTU TUN <-> UDP:51820 WireGuard + Auto-Failover)
       └── DnsPacketForwarder                 (DNS-Only Modes: UDP:53 <-> Active DNS Preset)
```

### Core State Singletons
- **`VpnStateHolder`**: Thread-safe `MutableStateFlow<VpnSessionState>` bridging `SwiftTunnelVpnService`, `SwiftTunnelTileService`, and `MainViewModel` without requiring AIDL or bound-service overhead.
- **`VpnKeyBridge`**: Process-local, in-memory holder (`@Volatile`) used to hand the NordLynx WireGuard private key directly from `MainViewModel` / `SwiftTunnelVpnService` into `WireGuardTunnelEngine` without ever serializing sensitive keys into Android `Intent` extras or persistent storage.

---

## 2. Pure-Kotlin WireGuard (NordLynx) Engine

To guarantee an APK strictly **under 5 MB** (**3.6 MB** in v6.0) across all CPU architectures (`arm64-v8a`, `armeabi-v7a`, `x86_64`) without bundling ~15–25 MB of Go/C native `libwg-go.so` binaries, SwiftTunnel implements the WireGuard protocol directly in pure Kotlin.

### 2.1 Cryptographic Primitives (`vpn/crypto/WireGuardCrypto.kt`)

| Primitive | Standard | Implementation Details |
| :--- | :--- | :--- |
| **X25519** | RFC 7748 | Montgomery ladder over $\mathbb{F}_{2^{255}-19}$ using 16-limb `LongArray` arithmetic with constant-time `cswap` and RFC clamping |
| **BLAKE2s** | RFC 7693 | 32-byte unkeyed hash, keyed MAC, HMAC-BLAKE2s, and WireGuard `KDF1` / `KDF2` / `KDF3` |
| **ChaCha20 / HChaCha20** | RFC 8439 | Quarter-round ARX stream cipher + 32-byte subkey derivation for Type 3 Cookie Reply (`XChaCha20-Poly1305`) |
| **Poly1305** | RFC 8439 | 130-bit polynomial MAC using unsigned 32-bit little-endian decoding (`readLE32U`) and 26-bit limb radix accumulation |
| **AEAD / XAEAD** | RFC 8439 | Authenticated encryption with associated data for Noise handshake fields and Type 4 transport frames |
| **TAI64N** | WireGuard Spec | 12-byte strictly monotonic timestamp (`8-byte UTC epoch + 0x400000000000000a`, `4-byte nanos`) |

### 2.2 Noise Handshake & Transport (`vpn/WireGuardSession.kt`)

Implements **`Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s`** with identifier `"WireGuard v1 zx2c4 Jason@zx2c4.com"`:

1. **Type 1 — Handshake Initiation (148 bytes)**:
   - Generates fresh ephemeral X25519 keypair $(e_i^{priv}, e_i^{pub})$, computes `DH(e_i, S_r)`, encrypts static public key $S_i^{pub}$, computes `DH(S_i, S_r)`, encrypts `TAI64N` timestamp, and appends `MAC1` + optional cookie `MAC2`.
2. **Type 2 — Handshake Response (92 bytes)**:
   - Verifies receiver index, mixes responder ephemeral $e_r^{pub}$, `DH(e_i, e_r)`, `DH(S_i, e_r)`, and empty PSK `0^32`, authenticates server, and derives symmetric `(sendKey, recvKey) = KDF2(chainingKey, empty)`.
3. **Type 3 — Cookie Reply (64 bytes)**:
   - Decrypts server DoS-mitigation cookie via `XChaCha20-Poly1305(HASH("cookie--" || S_r^{pub}), nonce, encryptedCookie, lastMac1)` and attaches `MAC2` on retry.
4. **Type 4 — Transport Data**:
   - 16-byte header + 16-byte-padded IPv4 payload + 16-byte Poly1305 tag, 64-bit sliding anti-replay window, and exact IPv4 `Total Length` unpadding before TUN write.

### 2.3 Tunnel Engine & Custom MTU Lifecycle (`vpn/WireGuardTunnelEngine.kt`)

```
Android Apps (e.g. Brave, Netflix TV)
      │  Raw IPv4 packets (MTU configurable: 1280 / 1360 / 1400 / 1420)
      ▼
  [ TUN fd ]  (10.5.0.2/32, routes 0.0.0.0/0 [or LAN-bypass CIDR complement] & ::/0)
      │
      ├──► TX Coroutine (Dispatchers.IO)
      │      • IPv6 (version == 6): Dropped immediately (prevents IPv6 leaks!)
      │      • IPv4 (version == 4): Encrypted via WireGuardSession -> UDP:51820
      │      • Protected DatagramSocket (VpnService.protect(socket) prevents routing loops)
      │
      ├──► RX Coroutine (Dispatchers.IO)
      │      • Reads UDP datagrams from NordVPN station IP:51820
      │      • Dispatches Type 2 (Handshake), Type 3 (Cookie), Type 4 (Data)
      │      • Writes decrypted, unpadded IPv4 packets back to TUN fd
      │
      └──► Maintenance Coroutine
             • 25s Persistent Keepalive (empty Type 4 packet) to maintain NAT mappings
             • 120s Zero-Downtime Rekey (keeps previous session keys alive during handshake)
             • 500ms Batched Telemetry updates to VpnStateHolder (zero per-packet UI churn)
```

---

## 3. LAN / Local Network Bypass Architecture (`vpn/LanBypassRouteCalculator.kt`)

When **LAN / Local Network Bypass** is enabled, local home and office devices (Chromecast, Plex/Jellyfin, SMB/NAS, printers, ADB over Wi-Fi) must bypass the VPN tunnel while 100% of public internet traffic remains encrypted.

### Excluded Local & Multicast Subnets
| Subnet | Purpose |
| :--- | :--- |
| `10.0.0.0/8` | RFC 1918 Class A private LAN |
| `127.0.0.0/8` | IPv4 loopback |
| `169.254.0.0/16` | Link-local addressing (APIPA / mDNS / Bonjour) |
| `172.16.0.0/12` | RFC 1918 Class B private LAN |
| `192.168.0.0/16` | RFC 1918 Class C home & Android TV LAN |
| `224.0.0.0/4` | Multicast (Chromecast mDNS `224.0.0.251`, DLNA/SSDP `239.255.255.250`) |
| `240.0.0.0/4` | Reserved & broadcast (`255.255.255.255`) |

### Dual-Path Android Implementation (`SwiftTunnelVpnService.kt`)
1. **Android 13+ (API 33+)**: Installs `addRoute("0.0.0.0", 0)` and natively calls `builder.excludeRoute(IpPrefix(InetAddress.getByName(cidr.address), cidr.prefixLength))` for every subnet in `excludedLanSubnets`.
2. **Android 7–12 (API 24–32, including many Android TV boxes)**: `excludeRoute()` does not exist prior to API 33. `LanBypassRouteCalculator.publicInternetRoutes` computes the exact mathematical IPv4 CIDR complement of `0.0.0.0/0 \ excludedLanSubnets` by merging excluded `[start, end]` intervals over unsigned 32-bit `Long` space and decomposing every remaining public range into minimal power-of-two aligned CIDRs via `Long.numberOfTrailingZeros()`. Every public IPv4 address (including `1.1.1.1`, `8.8.8.8`, `94.140.14.14`, `9.9.9.9`) is covered, while all private/multicast traffic falls directly through to the local LAN interface.

---

## 4. Auto-Failover & Seamless Wi-Fi ↔ 5G Handover (`vpn/SwiftTunnelVpnService.kt`)

### 4.1 Automatic WireGuard Server Failover
- During `connectWireGuard()`, SwiftTunnel attempts the WireGuard handshake against the user's primary target server (pinned `#code` or recommended server).
- If the handshake fails or times out **and** `autoFailoverEnabled == true`:
  1. Queries `NordApiClient.fetchServersForLocation(countryId, cityName, limit = 12)` for live NordLynx servers in the same city/country.
  2. Filters out the failed server hostname and picks the lowest-load alternative with a valid WireGuard public key.
  3. Persists the failover server in `PreferencesRepository` and establishes the WireGuard tunnel transparently, updating the UI, notification, and Quick Settings Tile with the new `#code`.

### 4.2 Underlying Network Switch Handover (`registerHandoverMonitor`)
- Once a tunnel is active (`CONNECTED`), `SwiftTunnelVpnService` registers a `ConnectivityManager.NetworkCallback` filtered to `NET_CAPABILITY_INTERNET` + `NET_CAPABILITY_NOT_VPN`.
- Tracks `activeUnderlyingNetHandle` (`network.networkHandle`).
- When the device roams from Wi-Fi to 5G/LTE (or Ethernet reconnects on Android TV) and a new underlying `networkHandle` becomes active:
  - Debounces for **1,200 ms** (so brief transient flaps don't cause churn), then triggers a clean re-handshake/reconnect on the active `currentConfig`, preserving the exact mode, DNS preset, server `#code`, split-tunnel apps, LAN bypass, and MTU.

---

## 5. DNS-Only Engine & v6 DNS Filter Presets (`vpn/DnsPacketForwarder.kt`, `model/VpnModels.kt`)

### Effective DNS Resolution (`resolveEffectiveDns`)
Every tunnel session resolves `(primaryDns, secondaryDns)` through a single deterministic function:
- `DnsPreset.MODE_DEFAULT`: Uses the selected `ConnectionMode`'s native pair (`1.1.1.1/1.0.0.1`, `8.8.8.8/8.8.4.4`, or `103.86.96.100/103.86.99.100`).
- `DnsPreset.ADGUARD_ADS`: Overrides DNS with `94.140.14.14` / `94.140.15.15` (blocks ads, trackers, and telemetry inside VPN **or** DNS-only mode).
- `DnsPreset.QUAD9_SECURITY`: Overrides DNS with `9.9.9.9` / `149.112.112.112` (malware/phishing blocking + DNSSEC).
- `DnsPreset.CUSTOM`: Validates user-supplied IPv4 addresses via strict `isValidIpv4Address()` (rejects leading zeroes, out-of-range octets `>255`, non-IPv4 strings) with automatic fallback to `mode.primaryDns`.

### DNS-Only Tunnel Mechanics
When using **Cloudflare Only** or **Google Only** (with any DNS preset):
- Installs `/32` routes **only** for the resolved `primaryDns` and `secondaryDns` — general TCP/UDP traffic stays completely off the TUN interface for near-zero battery and latency overhead.
- `DnsPacketForwarder` parses raw IPv4 + UDP port 53 frames from TUN, checks a **256-entry LRU DNS cache** (45-second TTL, patching the 2-byte Transaction ID on cache hit), and dispatches misses across a **bounded 4-worker coroutine pool** over `VpnService.protect()`-ed UDP sockets.

---

## 6. Android Quick Settings Tile (`vpn/SwiftTunnelTileService.kt`)

- Registered in `AndroidManifest.xml` with `android.permission.BIND_QUICK_SETTINGS_TILE` and `android.service.quicksettings.action.QS_TILE`.
- Syncs its tile state (`STATE_ACTIVE`, `STATE_INACTIVE`, `STATE_UNAVAILABLE`) directly from `VpnStateHolder.sessionState` whenever `onStartListening()` runs or whenever `SwiftTunnelVpnService` calls `TileService.requestListeningState()`.
- On Android 10+ (API 29+), sets `tile.subtitle` to the live `#code • City` (e.g., `#13084 • Los Angeles`) when connected, or the standby DNS/mode hint when disconnected.
- On tap (`onClick()`):
  - If connected → dispatches `ACTION_DISCONNECT` directly.
  - If disconnected and `VpnService.prepare(this) == null` → starts `SwiftTunnelVpnService` directly in the foreground using the persisted mode, DNS preset, server, split-tunnel apps, LAN bypass, auto-failover, and MTU.
  - If VPN consent or Nord API key is missing → opens `MainActivity` cleanly (`startActivityAndCollapse`).

---

## 7. Split Tunneling Mechanics

| SplitTunnelMode | `VpnService.Builder` Calls | Resulting Behavior |
| :--- | :--- | :--- |
| `ALL_APPS` | `addDisallowedApplication(context.packageName)` | Every app uses the tunnel; SwiftTunnel itself bypasses the tunnel to prevent control-plane loops. |
| `EXCLUDE_APPS` | `addDisallowedApplication(context.packageName)` + `addDisallowedApplication(pkg)` | All apps use the VPN **except** user-selected excluded apps. |
| `INCLUDE_ONLY_APPS` | `addAllowedApplication(pkg)` (excluding `context.packageName`) | **Only** selected apps route through WireGuard (or DNS-only tunnel). All other apps stay on the direct ISP connection. |

---

## 8. Live Server `#Code` Search & 1-Tap Server Hop

1. **Query Detection (`data/ServerCodeMatcher.kt`)**:
   - Plain text queries stay in the instant local country/city filter.
   - Digit-containing queries (`13084`, `#12790-12792`, `us13084`, `Dallas #9898`) parse into exact target numbers, inclusive ranges (up to 50 servers), country hints (`UK` ↔ `GB`), and city hints.
2. **Memory-Safe Gzip Streaming (`data/NordApiClient.kt`)**:
   - Streams `GET https://api.nordvpn.com/v1/servers?limit=16384&filters[servers_technologies][identifier]=wireguard_udp` via `GZIPInputStream` → `android.util.JsonReader`, caching ~6,000+ servers (~1.3 MB RAM) behind a coroutine `Mutex` with a 15-minute TTL.
   - `fetchServerByHostname(hostname)` reuses this cache to resolve pinned servers inside **1-Tap Saved Profiles** in < 5 ms.
3. **1-Tap Lower-Load Server Hop (`MainViewModel.hopToLowerLoadServer`)**:
   - Queries live NordLynx servers for the active `selectedCountryId` + `selectedCityName`, excludes the current server hostname, selects the minimum `loadPercent` candidate, pins it, and seamlessly reconnects if the tunnel is active.

---

## 9. Connection Details, Sparkline Graph & Secret-Free Backup Codec

### 9.1 Pure-Compose Throughput Sparkline (`ui/components/ConnectionDetailsCard.kt`)
- `MainViewModel`'s 1-second session timer maintains 30-sample rolling `rxRateHistory` and `txRateHistory` ring buffers (`List<Long>`), cleared on disconnect.
- `ThroughputSparklineCard` draws a dual-series Jetpack Compose `Canvas` (Download in `EmeraldProtected` with vertical gradient area fill; Upload in `ElectricCyan` stroke; 3-tier subtle gridlines; dynamic auto-scaling peak label) with **zero third-party charting libraries**.

### 9.2 Pure-Kotlin JSON Backup Codec (`data/ConfigBackupCodec.kt`)
- Zero-dependency RFC 8259 JSON encoder + recursive-descent `MiniJsonParser` that runs identically on Android and pure JVM JUnit tests without Android framework stubs.
- Encodes and decodes `AppConfigBackup` (routing mode, DNS preset, custom IPv4 DNS, selected country/city, pinned hostname, favorites, split-tunnel mode + excluded/included packages, boot auto-connect, LAN bypass, auto-failover, custom MTU, and up to 12 `SavedVpnProfile` entries).
- Clamps imported MTU strictly to `[1280, 1420]`, sanitizes invalid IPv4 strings, and enforces `"secretsExcluded": true`.

---

## 10. Android TV & Always-On Boot Auto-Connect Lifecycle

### Android TV Command Center
- `MainActivity` detects `UiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION`, switches to an overscan-safe side `NavigationRail`, and `ConnectScreen` activates a **2-Column Command Center** (`isAndroidTv || maxWidth >= 840.dp`).
- `Modifier.tvFocusableClickable(...)` draws glowing `2.dp` Electric Cyan focus rings with spring scale and hardware `DPAD_CENTER` / `ENTER` support across every interactive card, chip, profile, and toggle.
- `InstalledAppsRepository` merges `CATEGORY_LAUNCHER` + `CATEGORY_LEANBACK_LAUNCHER` so TV-only apps appear in Split Tunneling without `QUERY_ALL_PACKAGES`.

### Boot Auto-Connect Sequence
```
Device / Android TV Powers On
          │
          ▼  ACTION_BOOT_COMPLETED / QUICKBOOT_POWERON / MY_PACKAGE_REPLACED
  BootCompletedReceiver
          │  1. Checks prefs.isAutoConnectOnBootEnabled() == true
          │  2. Checks VpnService.prepare(context) == null (consent already granted)
          │  3. Starts SwiftTunnelVpnService (ACTION_BOOT_AUTOCONNECT)
          ▼
  SwiftTunnelVpnService
          │  1. Promotes to Foreground Service notification
          │  2. Waits up to 45s via ConnectivityManager.NetworkCallback for Wi-Fi / Ethernet
          │  3. Restores Mode, DNS Preset, Pointed Country/City/#Code, Split Apps, LAN Bypass, Auto-Failover & MTU
          │  4. Fetches NordLynx credentials & establishes WireGuard (with automatic failover if needed)
          │  5. Registers underlying NetworkCallback for future Wi-Fi <-> 5G handovers
          ▼
   Tunnel Active & Protected Automatically
```
