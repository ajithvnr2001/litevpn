# NordVPN API Integration, Cryptography & Security Model

This document details every external API endpoint used by **SwiftTunnel**, how API credentials and WireGuard private keys are protected, how R8 optimization works, and how the app satisfies Google Play Developer Program policies.

---

## 1. NordVPN v1 REST API Integration (`data/NordApiClient.kt`)

SwiftTunnel communicates directly with NordVPN's official `https://api.nordvpn.com/v1` endpoints over HTTPS using Android's built-in `HttpURLConnection` + `org.json` (zero third-party HTTP/JSON bloat).

### 1.1 Fetch All Supported Countries & Cities

- **Endpoint**: `GET https://api.nordvpn.com/v1/servers/countries`
- **Authentication**: None required (public catalog)
- **Usage**:
  - Syncs all live NordVPN countries (`id`, `name`, `code`, `server_count`, and nested `cities[]` with `id`, `name`, `latitude`, `longitude`).
  - Merged with `NordCountryCatalog.fallbackCountries` (118+ countries including Albania `id=2, code=AL`) so the UI works instantly even before network sync completes.

### 1.2 Fetch Recommended / Location-Based NordLynx Servers

- **Endpoint**:
  ```http
  GET https://api.nordvpn.com/v1/servers/recommendations?filters[servers_technologies][identifier]=wireguard_udp&limit=<N>
  ```
  Optional country filter:
  ```http
  &filters[country_id]=<COUNTRY_ID>
  ```
- **Authentication**: None required
- **Parsed Fields**:
  - `id`: Server ID
  - `name`: Display name (e.g., `Albania #17`)
  - `hostname`: FQDN (e.g., `al17.nordvpn.com`)
  - `station`: Server IPv4 WireGuard endpoint address
  - `load`: Real-time percentage load (`0..100`)
  - `locations[0].country`: Country `id`, `name`, `code`, and `city.name`
  - `technologies[]`: Finds technology with `identifier == "wireguard_udp"` and extracts `metadata[name == "public_key"].value` (Base64-encoded 32-byte X25519 server public key).
- **City Prioritization**:
  - When a user selects a specific city (e.g., `Frankfurt`, `Tokyo`, `Tirana`), `NordApiClient` requests a larger recommendation batch (`limit = 20`) for that country, sorts servers matching the target city name first by lowest `load`, and falls back to the lowest-load server in the country if the city pool is saturated.
- **Geo-Nearest Recommendations**:
  - Calling `/recommendations` without `country_id` returns NordVPN's geo-proximate lowest-load WireGuard servers for the user's current region, powering the **Nearest Low-Load Nord Locations** strip.

### 1.2b Full Live NordLynx Server Directory (Server Number / `#Code` Search)

- **Endpoint**:
  ```http
  GET https://api.nordvpn.com/v1/servers?limit=16384&filters[servers_technologies][identifier]=wireguard_udp
  Accept-Encoding: gzip
  ```
  *(Automatic fallback to `limit=0` if fewer than 50 records are returned.)*
- **Authentication**: None required
- **Streaming & Caching**:
  - Decoded on-the-fly via `GZIPInputStream` → `android.util.JsonReader` so the ~20 MB uncompressed payload is never loaded into a single heap `String`.
  - Extracts `id`, `name` (e.g., `United States #13084`), `hostname` (`us13084.nordvpn.com`), `station`, `load`, `locations[0].country.{name, code, city.name}`, and the `wireguard_udp` `public_key` regardless of JSON key ordering.
  - Cached in volatile RAM for 15 minutes (protected by a coroutine `Mutex`) and invalidated manually whenever the user taps **Refresh** / **Resync**.

### 1.3 Fetch User NordLynx Private Key (Credentials Verification)

- **Endpoint**: `GET https://api.nordvpn.com/v1/users/services/credentials`
- **Authentication**: HTTP Basic Auth
  ```http
  Authorization: Basic base64("token:<NORD_API_KEY>")
  ```
- **Response Handling**:
  - `HTTP 200`: Extracts `nordlynx_private_key` (Base64-encoded 32-byte X25519 client private key) and `username`.
  - `HTTP 401` / `403`: Reports invalid or expired NordVPN access token clearly in the UI.
  - Non-2xx / Network failure: Surfaces diagnostic error message without crashing or leaking the token.

### 1.4 NordLynx WireGuard Interface Parameters

| Parameter | Value |
| :--- | :--- |
| Tunnel IPv4 Client Address | `10.5.0.2/32` |
| Tunnel IPv6 Leak-Trap Address | `fd00:5:0::2/128` |
| Server Endpoint Port | `UDP 51820` |
| MTU | `1280` bytes (prevents fragmentation across cellular/Wi-Fi/ISP PPPoE links) |
| AllowedIPs (VPN Modes) | `0.0.0.0/0` and `::/0` |
| Persistent Keepalive | `25 seconds` |
| Session Rekey Interval | `120 seconds` |

---

## 2. Swappable Key Security (`data/SecureKeyStorage.kt`)

SwiftTunnel allows the user to enter, test, swap, or clear any NordVPN API key at runtime.

### 2.1 Android Keystore Encryption Flow

1. **Master Key**: Generated inside `AndroidKeyStore` under alias `swifttunnel_nord_key_aes` with:
   - Algorithm: `AES` (256-bit)
   - Block mode: `GCM`
   - Padding: `NoPadding`
   - Purpose: `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`
2. **Encryption on Save**:
   - Initializes `Cipher.getInstance("AES/GCM/NoPadding")` in `ENCRYPT_MODE` (Android Keystore generates a fresh cryptographically random 12-byte IV per write).
   - Stores Base64-encoded `iv` and `ciphertext` (including 128-bit GCM authentication tag) in private `SharedPreferences` (`Context.MODE_PRIVATE`).
   - Plaintext API key is **never** written to SharedPreferences, logcat, or files.
3. **Key Resolution Priority**:
   1. Runtime user-saved key decrypted from Android Keystore (`SecureKeyStorage`).
   2. Optional build-time fallback from `BuildConfig.NORD_API_KEY` (injected via `.env` / Secrets Gradle Plugin — ignored if empty or placeholder `"YOUR_NORD_API_KEY"`).
   3. If neither exists, VPN modes prompt the user to paste a NordVPN token; DNS-only modes (`1.1.1.1 Only` / `8.8.8.8 Only`) continue to work with zero key required.
4. **Key Masking in UI**:
   - The UI displays only a masked preview (e.g., `e9f2ab••••••11cf`) and never echoes the full secret back into logs or persistent text fields.

### 2.2 WireGuard Private Key Protection (`vpn/VpnKeyBridge.kt`)

- The `nordlynx_private_key` returned by NordVPN is kept strictly in volatile RAM (`VpnKeyBridge`) and cleared on disconnect (`VpnKeyBridge.clear()`).
- Private key byte arrays in `WireGuardSession` / `WireGuardTunnelEngine` are never logged.

---

## 3. Permissions & Google Play Policy Compliance

| Permission / Feature | Declaration | Justification & Policy Compliance |
| :--- | :--- | :--- |
| `INTERNET` | Normal (install-time) | Required to query NordVPN API and transmit WireGuard UDP/DNS packets |
| `ACCESS_NETWORK_STATE` | Normal (install-time) | Used by `ConnectivityManager.NetworkCallback` to detect Wi-Fi/Ethernet readiness on boot |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Normal + Manifest `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | Keeps `SwiftTunnelVpnService` alive reliably in the background with a transparent user notification |
| `POST_NOTIFICATIONS` | Runtime (Android 13+) | Displays persistent VPN status notification with quick-disconnect action; app degrades gracefully if denied |
| `RECEIVE_BOOT_COMPLETED` | Normal (install-time) | Powers optional **Auto-Connect on Power-Up** on phones and Android TV |
| `android.permission.BIND_VPN_SERVICE` | Service permission on `SwiftTunnelVpnService` | System-only binding requirement for Android `VpnService` |
| `<queries>` (`LAUNCHER` + `LEANBACK_LAUNCHER`) | Scoped package visibility | Discovers installed mobile and Android TV apps for Split Tunneling **without** requesting the restricted `QUERY_ALL_PACKAGES` permission |
| `android.software.leanback` (`required="false"`) & `android.hardware.touchscreen` (`required="false"`) | Manifest `<uses-feature>` | Allows a single universal APK to install seamlessly on Android Phones, Tablets, Foldables, and Android TV / Google TV |

### Battery Optimization Policy
- SwiftTunnel does **not** request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Instead, users can optionally open Android's standard `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` screen via the UI shortcut if their OEM aggressively kills background VPN services.

---

## 4. R8 / ProGuard Configuration (`app/proguard-rules.pro`)

To maintain the **< 5 MB** APK size while keeping reflection/manifest entry points intact:

```proguard
# Keep VPN service, boot receiver, and AndroidViewModel constructor
-keep class com.example.vpn.SwiftTunnelVpnService { *; }
-keep class com.example.vpn.BootCompletedReceiver { *; }
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
}
```

Both `debug` and `release` build types enable `isMinifyEnabled = true` and `isShrinkResources = true` so even development APKs (`vpnv3.apk`) remain ~3.4 MB.

---

## 5. Threat Model & Known Limitations

- **No Traffic Logging**: SwiftTunnel performs zero analytics, telemetry uploads, or DNS query history storage. All session counters live exclusively in memory and reset on disconnect.
- **NordVPN Account Scope**: The NordVPN access token grants access only to your NordVPN service credentials (`nordlynx_private_key`). Treat it like a password and revoke/rotate it anytime from your Nord Account dashboard, then paste the new token into SwiftTunnel.
- **First-Time VPN Consent on Reboot**: Android security architecture requires interactive user approval (`VpnService.prepare`) at least once before any VPN app can establish a tunnel automatically on boot. After granting permission once, boot auto-connect operates unattended.
