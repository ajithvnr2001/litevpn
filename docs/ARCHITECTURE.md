# SwiftTunnel System & Protocol Architecture

This document explains the internal software architecture, packet-level networking mechanics, pure-Kotlin WireGuard implementation, Android TV adaptations, and Always-On boot auto-connect lifecycle of **SwiftTunnel**.

---

## 1. Architectural Pattern (MVVM + Unidirectional State)

SwiftTunnel follows a strict **MVVM** architecture with reactive Kotlin `StateFlow` pipelines and zero heavy DI frameworks (lightweight constructor injection):

```
UI Composables (ConnectScreen, LocationsScreen, SplitTunnelScreen)
       │  collectAsStateWithLifecycle()
       ▼
MainViewModel (AndroidViewModel)
       │  Combines:
       │  ├── VpnStateHolder.state            (Live service telemetry & status)
       │  ├── SecureKeyStorage                (Encrypted Nord API key status)
       │  ├── PreferencesRepository           (Persisted mode, location, split apps, boot toggle)
       │  ├── NordApiClient                   (Live countries, nearest servers, per-country servers)
       │  └── InstalledAppsRepository         (Mobile + Android TV launchable apps)
       ▼
SwiftTunnelVpnService (Android VpnService + specialUse Foreground Service)
       ├── WireGuardTunnelEngine              (VPN Modes: Full TUN <-> UDP:51820 WireGuard)
       └── DnsPacketForwarder                 (DNS-Only Modes: UDP:53 <-> 1.1.1.1 / 8.8.8.8)
```

### Core State Singletons
- **`VpnStateHolder`**: Thread-safe `MutableStateFlow<VpnSessionState>` bridging `SwiftTunnelVpnService` and `MainViewModel` without requiring AIDL or bound-service overhead.
- **`VpnKeyBridge`**: Process-local, in-memory holder (`@Volatile`) used to hand the NordLynx WireGuard private key directly from `MainViewModel` / `SwiftTunnelVpnService` into `WireGuardTunnelEngine` without ever serializing sensitive keys into Android `Intent` extras or persistent storage.

---

## 2. Pure-Kotlin WireGuard (NordLynx) Engine

To guarantee an APK strictly **under 5 MB** across all CPU architectures (`arm64-v8a`, `armeabi-v7a`, `x86_64`) without bundling ~15–25 MB of Go/C native `libwg-go.so` binaries, SwiftTunnel implements the WireGuard protocol directly in pure Kotlin.

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
   - Generates fresh ephemeral X25519 keypair $(e_i^{priv}, e_i^{pub})$.
   - Computes `DH(e_i, S_r)` and encrypts client static public key $S_i^{pub}$.
   - Computes `DH(S_i, S_r)` and encrypts `TAI64N` timestamp.
   - Appends `MAC1 = BLAKE2s-128(HASH("mac1----" || S_r^{pub}), msg[0..115])` and `MAC2` (zeroed unless server issued a Type 3 cookie).
2. **Type 2 — Handshake Response (92 bytes)**:
   - Verifies receiver index matches local `senderIndex`.
   - Mixes responder ephemeral $e_r^{pub}$, `DH(e_i, e_r)`, `DH(S_i, e_r)`, and empty preshared key `0^32`.
   - Decrypts empty payload to authenticate the NordVPN server.
   - Derives symmetric transport keys `(sendKey, recvKey) = KDF2(chainingKey, empty)` and initializes 64-bit counters.
3. **Type 3 — Cookie Reply (64 bytes)**:
   - Under high server load, NordVPN may respond with a Type 3 packet instead of Type 2.
   - Decrypts the 16-byte cookie via `XChaCha20-Poly1305(HASH("cookie--" || S_r^{pub}), nonce, encryptedCookie, lastMac1)` and attaches `MAC2` on the next handshake retry.
4. **Type 4 — Transport Data**:
   - **Header (16 bytes)**: `type=4` (1B), reserved (3B), `receiverIndex` (4B LE), `counter` (8B LE).
   - **Payload**: Plaintext IPv4 packet padded to a 16-byte multiple + 16-byte Poly1305 tag.
   - **Anti-Replay Window**: 64-bit sliding bitmap window rejecting duplicate or stale packet counters.
   - **Padding Removal**: On decryption, reads the IPv4 `Total Length` header field (`bytes 2..3`) or IPv6 `Payload Length + 40` to strip WireGuard 16-byte alignment padding before injecting cleanly into Android's TUN file descriptor.

### 2.3 Tunnel Engine & Lifecycle (`vpn/WireGuardTunnelEngine.kt`)

```
Android Apps (e.g. Brave)
      │  Raw IPv4 packets
      ▼
  [ TUN fd ]  (10.5.0.2/32, MTU 1280, routes 0.0.0.0/0 & ::/0)
      │
      ├──► TX Coroutine (Dispatchers.IO)
      │      • Checks IP version:
      │          - IPv6 (version == 6): Dropped immediately (prevents IPv6 leaks!)
      │          - IPv4 (version == 4): Encrypted via WireGuardSession -> UDP:51820
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

## 3. DNS-Only Engine (`vpn/DnsPacketForwarder.kt`)

When the user selects **Cloudflare 1.1.1.1 Only** or **Google 8.8.8.8 Only**:

- `SwiftTunnelVpnService` installs `/32` routes **only** for the selected DNS servers (`1.1.1.1/32` & `1.0.0.1/32` or `8.8.8.8/32` & `8.8.4.4/32`) — general TCP/UDP traffic stays completely off the TUN interface for near-zero battery and latency overhead.
- `DnsPacketForwarder` parses raw IPv4 + UDP port 53 frames from TUN, checks a **256-entry LRU DNS cache** (45-second TTL, patching the 2-byte Transaction ID on cache hit), and dispatches cache misses across a **bounded 4-worker coroutine pool** over `VpnService.protect()`-ed UDP sockets.
- Reconstructs IPv4 + UDP response headers (including valid IPv4 header checksums) and writes replies back to the TUN interface.

---

## 4. Split Tunneling Mechanics

Android's `VpnService.Builder` enforces mutual exclusion between allow-lists and disallow-lists:

| SplitTunnelMode | `VpnService.Builder` Calls | Resulting Behavior |
| :--- | :--- | :--- |
| `ALL_APPS` | `addDisallowedApplication(context.packageName)` | Every app on the device uses the VPN tunnel; SwiftTunnel itself bypasses the tunnel to prevent control-plane loops. |
| `EXCLUDE_APPS` | `addDisallowedApplication(context.packageName)` + `addDisallowedApplication(pkg)` for each selected app | All apps use the VPN **except** the user-selected excluded apps. |
| `INCLUDE_ONLY_APPS` | `addAllowedApplication(pkg)` for each selected app (excluding `context.packageName`) | **Only** the user-selected apps route through the WireGuard tunnel (or DNS-only tunnel). All other apps use the direct ISP connection. |

### Why Split Tunneling Guarantees Real Location Masking
In `INCLUDE_ONLY_APPS` mode (e.g., only Brave Browser selected + Albania):
1. `VpnService.Builder` sets `addAllowedApplication("com.brave.browser")`.
2. In VPN modes, the TUN installs `0.0.0.0/0` (full IPv4 default route) and `::/0` (IPv6 leak trap).
3. All Brave TCP/UDP/QUIC/DNS packets enter the TUN interface, get wrapped in WireGuard Type 4 packets, and exit from NordVPN's Albania station IP.
4. Any IPv6 socket Brave attempts is silently dropped inside `WireGuardTunnelEngine`, forcing immediate Happy Eyeballs fallback to IPv4 through Albania.

---

## 5. Android TV & Adaptive Form-Factor Architecture

### 5.1 Automatic TV Detection (`MainActivity.kt`)
- Inspects `UiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION`.
- When running on Android TV (or wide tablets `screenWidthDp >= 600`), switches from bottom `NavigationBar` to a side **`NavigationRail`**.
- On Android TV, applies **overscan-safe container padding** (`24.dp` horizontal, `14.dp` vertical) so no UI elements are clipped by TV bezels.

### 5.2 D-Pad Focus System (`ui/components/TvFocusModifier.kt`)
- Provides a lightweight, zero-dependency `Modifier.tvFocusableClickable(...)` used across the Power Orb, routing tiles, server cards, city chips, and Split Tunnel app rows.
- Tracks `onFocusChanged`, animates a subtle spring scale (`1.025f`), draws a high-contrast `2.dp` Electric Cyan border when D-Pad focused, and listens to hardware `Key.DirectionCenter` / `Key.Enter` / `Key.NumPadEnter` key-up events.

### 5.3 TV App Discovery (`data/InstalledAppsRepository.kt`)
- Queries both `Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER)` and `Intent(ACTION_MAIN).addCategory(CATEGORY_LEANBACK_LAUNCHER)` and merges results by `packageName`.
- Allows Android TV users to split-tunnel TV-only streaming apps (Netflix, YouTube TV, Kodi, SmartTube, Plex, etc.) without requesting the restricted `QUERY_ALL_PACKAGES` permission.

---

## 6. Always-On Boot Auto-Connect Sequence

```
Device / Android TV Powers On
          │
          ▼  ACTION_BOOT_COMPLETED / QUICKBOOT_POWERON / MY_PACKAGE_REPLACED
  BootCompletedReceiver
          │  1. Checks prefs.isAutoConnectOnBootEnabled() == true
          │  2. Checks VpnService.prepare(context) == null (consent already granted)
          │  3. Calls ContextCompat.startForegroundService(ACTION_BOOT_AUTOCONNECT)
          ▼
  SwiftTunnelVpnService
          │  1. Immediately promotes to Foreground Service notification
          │  2. Calls waitForNetworkAvailable(45_000 ms) via ConnectivityManager.NetworkCallback
          │     (waits for NET_CAPABILITY_INTERNET on Wi-Fi / Ethernet / Cellular)
          │  3. Loads saved:
          │       - ConnectionMode (e.g. CLOUDFLARE_PLUS_VPN)
          │       - Pointed Country / City / Pinned Server Hostname
          │       - SplitTunnelMode + Selected App Packages
          │       - Decrypted Nord API Key from SecureKeyStorage
          │  4. Fetches fresh NordLynx credentials & server metadata (up to 5x exponential backoff)
          │  5. Establishes WireGuardTunnelEngine (or DnsPacketForwarder)
          ▼
   Tunnel Active & Protected Automatically
```

> **System Always-On Integration**: If Android OS **Always-On VPN** invokes `SwiftTunnelVpnService.onStartCommand` with a `null` intent or `VpnService.SERVICE_INTERFACE` action, `SwiftTunnelVpnService` treats it identically to `ACTION_BOOT_AUTOCONNECT` and restores the user's exact persisted configuration.

---

## 7. Live Server Number / `#Code` Search Architecture

NordVPN's `/v1/servers` endpoint does not expose server-side filtering by server number or hostname substring. To resolve queries like `13084`, `#12790`, `#12790-12792`, `us13084`, or `Ashburn #11640` accurately without OOM crashes on low-memory Android TV devices:

1. **Query Detection (`data/ServerCodeMatcher.kt`)**:
   - Plain text queries with no digits (e.g., `"Albania"`, `"Los Angeles"`, `"US"`) return `null` and stay strictly in the fast local country/city filter.
   - Queries containing digits are parsed into a `ParsedServerCodeQuery` containing exact target `numbers` (expanding inclusive ranges up to 50 servers, e.g., `#12394-12398`), optional 2-letter `countryCodeHint` (normalizing `UK` ↔ `GB`), and optional `locationTextHint` (e.g., `"Dallas"`).
2. **Memory-Safe Gzip Streaming (`data/NordApiClient.kt`)**:
   - Requests `GET https://api.nordvpn.com/v1/servers?limit=16384&filters[servers_technologies][identifier]=wireguard_udp` (with automatic `limit=0` fallback) with `Accept-Encoding: gzip`.
   - Wraps `GZIPInputStream` directly in Android's streaming `android.util.JsonReader`, parsing only the essential fields (`id`, `name`, `hostname`, `station`, `load`, `locations[0].country.{name,code,city.name}`, and `wireguard_udp` `public_key`) while calling `reader.skipValue()` on all unused metadata.
   - Caches the parsed `List<NordServer>` (~6,000+ servers, ~1.3 MB RAM) behind a coroutine `Mutex` with a 15-minute TTL so subsequent code lookups complete in **< 5 ms**.
3. **Relevance Ranking & Location Display**:
   - Ranks exact number matches matching any city/country hint first, followed by exact number matches, followed by 3+ digit live prefix autocomplete (e.g., typing `1308` previews `#13084` and `#13089`), tie-broken by lowest server load.
   - `LocationsScreen` renders each match in a D-Pad-focusable `ServerCodeMatchCard` highlighting the `#code` badge, resolved **`City, Country`** location, hostname, IP, load `%`, and 1-tap **Connect** (which also pins the server for Always-On boot auto-connect).

