# NordVPN API Integration, Cryptography & Security Model (v7.0)

This document details every external API endpoint used by **SwiftTunnel v7.0**, how API credentials, WireGuard private keys, and exported JSON backups are protected, how per-app multi-location routing works securely via public Android APIs, how R8 optimization maintains the **3.7 MB** APK footprint, and how the app complies with Google Play Developer Program policies.

---

## 1. External API Endpoints (`data/NordApiClient.kt`)

SwiftTunnel communicates over HTTPS using Android's built-in `HttpURLConnection` + `org.json` / `android.util.JsonReader` (zero third-party HTTP/JSON library bloat).

### 1.1 Fetch All Supported Countries & Cities
- **Endpoint**: `GET https://api.nordvpn.com/v1/servers/countries`
- **Authentication**: None required (public catalog)
- **Usage**: Syncs all live NordVPN countries (`id`, `name`, `code`, `cities[]`) and merges them with `NordCountryCatalog.fallbackCountries` (118+ offline fallback countries).

### 1.2 Fetch Recommended / Location-Based NordLynx Servers (Also Powers Failover & Server Hop)
- **Endpoint**:
  ```http
  GET https://api.nordvpn.com/v1/servers/recommendations?filters[servers_technologies][identifier]=wireguard_udp&limit=<N>
  ```
  Optional country filter: `&filters[country_id]=<COUNTRY_ID>`
- **Authentication**: None required
- **Parsed Fields**: `id`, `name`, `hostname`, `station` (IPv4 endpoint), `load` (`0..100`), `locations[0].country.{name, code, city.name}`, and `technologies[identifier == "wireguard_udp"].metadata[name == "public_key"].value`.
- **v6 Usage**:
  - Powers country/city direct connect and the **Nearest Low-Load** strip.
  - Powers **Auto-Failover** in `SwiftTunnelVpnService` (selects the next lowest-load server in the same city/country if the primary WireGuard handshake fails).
  - Powers **1-Tap Lower-Load Server Hop** in `MainViewModel` (excludes current hostname and hops to the lowest-load candidate in the same region).

### 1.3 Full Live NordLynx Server Directory (Server `#Code` Search & Saved Profile Hostname Lookup)
- **Endpoint**:
  ```http
  GET https://api.nordvpn.com/v1/servers?limit=16384&filters[servers_technologies][identifier]=wireguard_udp
  Accept-Encoding: gzip
  ```
  *(Automatic fallback to `limit=0` if fewer than 50 records are returned.)*
- **Streaming & Caching**:
  - Decoded on-the-fly via `GZIPInputStream` → `android.util.JsonReader` so the ~20 MB uncompressed payload is never allocated as a single heap `String`.
  - Cached in volatile RAM for 15 minutes (protected by a coroutine `Mutex`).
  - Reused by `fetchServerByHostname(hostname)` when applying a **1-Tap Saved Profile** with a pinned server `#code` (e.g., `us13084.nordvpn.com`).

### 1.4 Fetch User NordLynx Private Key (Credentials Verification)
- **Endpoint**: `GET https://api.nordvpn.com/v1/users/services/credentials`
- **Authentication**: HTTP Basic Auth (`Authorization: Basic base64("token:<NORD_API_KEY>")`)
- **Response Handling**:
  - `HTTP 200`: Extracts `nordlynx_private_key` (Base64-encoded 32-byte X25519 client private key) and masked username.
  - `HTTP 401` / `403`: Reports invalid or expired NordVPN access token clearly in the UI.

### 1.5 Built-In IP & Leak Verification Trace
- **Endpoint**: `GET https://1.1.1.1/cdn-cgi/trace`
- **Authentication**: None required
- **Parsed Key-Value Fields**: `ip` (observed public exit IP), `loc` (2-letter ISO country code), `colo` (Cloudflare edge POP, e.g., `LAX`, `TIA`, `FRA`), and `tls` (TLS protocol version).
- **Verification Logic (`MainViewModel.runIpAndLeakCheck`)**:
  - Checks whether observed `ip` matches the active NordVPN server's `stationIp`.
  - Verifies whether observed exit IP is IPv4-only / whether the active WireGuard tunnel's `::/0` IPv6 drop trap is engaged.
  - Reports active primary/secondary DNS resolvers (including AdGuard, Quad9, or Custom IPv4 overrides).

### 1.6 NordLynx WireGuard Interface Parameters

| Parameter | Value |
| :--- | :--- |
| Tunnel IPv4 Client Address | `10.5.0.2/32` |
| Tunnel IPv6 Leak-Trap Address | `fd00:5:0::2/128` |
| Server Endpoint Port | `UDP 51820` |
| Configurable MTU (v6) | `1280` (default safe mobile), `1360`, `1400`, or `1420` (fast Wi-Fi/Ethernet); clamped to `1280..1420` |
| AllowedIPs (Strict VPN Mode) | `0.0.0.0/0` and `::/0` |
| AllowedIPs (LAN Bypass ON) | API 33+: `0.0.0.0/0` minus RFC 1918 / link-local / multicast via `excludeRoute()`; API 24–32: exact IPv4 CIDR complement routes + `::/0` |
| Persistent Keepalive | `25 seconds` |
| Session Rekey Interval | `120 seconds` |

---

## 2. Swappable Key Security (`data/SecureKeyStorage.kt`)

### 2.1 Android Keystore AES-256-GCM Vault
1. **Master Key**: Generated inside hardware-backed `AndroidKeyStore` under alias `swifttunnel_nord_key_aes` (`AES`, 256-bit, `GCM`, `NoPadding`).
2. **Encryption on Save**: Generates a fresh 12-byte random IV per write and stores Base64 `iv` + `ciphertext` (with 128-bit GCM auth tag) in `Context.MODE_PRIVATE` `SharedPreferences`. Plaintext tokens are never written to disk or logs.
3. **Key Resolution Priority**:
   1. Runtime user-saved key decrypted from Android Keystore (`SecureKeyStorage`).
   2. Optional build-time fallback from `BuildConfig.NORD_API_KEY` (injected via `.env` / Secrets Gradle Plugin — ignored if blank or placeholder).
   3. If neither exists, VPN modes prompt the user to enter a NordVPN token; DNS-only modes (`1.1.1.1 Only` / `8.8.8.8 Only`, plus AdGuard/Quad9/Custom DNS) work with zero key required.

### 2.2 WireGuard Private Key Protection (`vpn/VpnKeyBridge.kt`)
- The `nordlynx_private_key` returned by NordVPN lives strictly in volatile RAM (`VpnKeyBridge`) and is cleared on disconnect.

### 2.3 Secret-Free Config Backup Invariant (`data/ConfigBackupCodec.kt`)
- Exported JSON files contain **zero secrets** and explicitly declare `"secretsExcluded": true` (see [`import/export_eg.json`](../import/export_eg.json)).
- Neither the encrypted/decrypted NordVPN API key nor the WireGuard private key is ever serialized into or parsed from backup JSON.
- Imported fields are strictly validated:
  - `customDnsPrimary` / `customDnsSecondary` must pass `isValidIpv4Address()` or are sanitized to `""`.
  - `customMtu` and per-profile `mtu` are clamped to `[1280, 1420]`.
  - `appRouteRules` entries require a non-blank `packageName` and valid positive `countryId`.
  - Unknown enum IDs safely fall back to defaults (`CLOUDFLARE_PLUS_VPN`, `MODE_DEFAULT`, `ALL_APPS`).

### 2.4 Per-App Multi-Location Routing Security (`ConnectivityManager.getConnectionOwnerUid`)
- On Android 10+ (API 29+), Android grants the active `VpnService` access to `ConnectivityManager.getConnectionOwnerUid(protocol, local, remote)` **strictly for connections traversing its own TUN interface**.
- Requires **zero root**, **zero Accessibility permissions**, and **zero `PACKAGE_USAGE_STATS`**.
- Only packet #1 of each TCP/UDP flow triggers the OS UID check; subsequent packets resolve from the bounded 2,048-entry in-memory `AppPeerRouteTable` cache, and all state is cleared on disconnect.

---

## 3. Permissions & Google Play Policy Compliance

| Permission / Feature | Declaration | Justification & Policy Compliance |
| :--- | :--- | :--- |
| `INTERNET` | Normal (install-time) | NordVPN API calls, Cloudflare trace leak check, and WireGuard UDP/DNS transport |
| `ACCESS_NETWORK_STATE` | Normal (install-time) | `ConnectivityManager.NetworkCallback` for Boot network wait, Wi-Fi ↔ 5G Handover, and `getConnectionOwnerUid()` inside `VpnService` |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Normal + Manifest `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | Keeps `SwiftTunnelVpnService` alive reliably with a transparent user notification |
| `POST_NOTIFICATIONS` | Runtime (Android 13+) | Persistent VPN status notification with `#code` & quick-disconnect action; degrades gracefully if denied |
| `RECEIVE_BOOT_COMPLETED` | Normal (install-time) | Powers optional **Auto-Connect on Power-Up** on phones and Android TV |
| `android.permission.BIND_VPN_SERVICE` | Service permission on `SwiftTunnelVpnService` | System-only binding requirement for Android `VpnService` |
| `android.permission.BIND_QUICK_SETTINGS_TILE` | Service permission on `SwiftTunnelTileService` | System-only binding requirement for Android Quick Settings Tile |
| **Zero Storage Permissions** | `ActivityResultContracts.CreateDocument` & `OpenDocument` | Export/Import JSON backups via Android Storage Access Framework without requesting `READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE` |
| `<queries>` (`LAUNCHER` + `LEANBACK_LAUNCHER`) | Scoped package visibility | Discovers installed mobile and Android TV apps for Split Tunneling & Per-App Country Routing **without** requesting `QUERY_ALL_PACKAGES` |
| `android.software.leanback` (`required="false"`) & `android.hardware.touchscreen` (`required="false"`) | Manifest `<uses-feature>` | Allows a single universal 3.7 MB APK to install across Phones, Tablets, Foldables, and Android TV / Google TV |

### Battery Optimization Policy
- SwiftTunnel does **not** request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Users can optionally open Android's standard `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` screen via the UI shortcut if their OEM aggressively kills background VPN services.

---

## 4. R8 / ProGuard Configuration (`app/proguard-rules.pro`)

```proguard
# Keep VPN service, Quick Settings tile, boot receiver, and AndroidViewModel constructor
-keep class com.example.vpn.SwiftTunnelVpnService { *; }
-keep class com.example.vpn.SwiftTunnelTileService { *; }
-keep class com.example.vpn.BootCompletedReceiver { *; }
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
}
```

Both `debug` and `release` build types enable `isMinifyEnabled = true` and `isShrinkResources = true`, keeping `vpnv6.apk` at **3.6 MB**.

---

## 5. Threat Model & Known Limitations

- **No Traffic Logging**: SwiftTunnel performs zero analytics, telemetry uploads, or DNS query history storage. All byte counters and 30-second sparkline samples live exclusively in volatile RAM and reset on disconnect.
- **LAN Bypass Trade-Off**: Enabling **LAN / Local Network Bypass** intentionally allows traffic destined to private RFC 1918 subnets (`192.168.0.0/16`, `10.0.0.0/8`, `172.16.0.0/12`) and multicast (`224.0.0.0/4`) to bypass the WireGuard tunnel so local casting/NAS/Plex work. Keep it **OFF** on untrusted public Wi-Fi networks (airports, hotels, cafés) when strict isolation is preferred.
- **First-Time VPN Consent on Reboot / Quick Settings**: Android requires interactive approval of the OS VPN consent dialog (`VpnService.prepare`) at least once before unattended boot auto-connect or 1-tap Quick Settings activation can start the tunnel directly.
