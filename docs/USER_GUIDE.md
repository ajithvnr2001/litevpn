# SwiftTunnel End-to-End User Guide (Phone, Tablet & Android TV — v7.0)

This guide covers installing **SwiftTunnel v7.0 (`vpnv7.apk`, 3.7 MB)**, navigating the 4-tab **Swiss Carbon UI** (**Tunnel**, **Servers**, **Apps**, **Tools**), configuring **Simultaneous Per-App Multi-Location Tunnels** (`App 1 → India`, `App 2 → US`), swapping NordVPN API keys, DNS presets (**AdGuard / Quad9 / Custom IPv4**), **1-Tap Saved Profiles**, **LAN Bypass**, **Auto-Failover & Wi-Fi/5G Handover**, **MTU & Server Hop**, **Quick Settings Tile**, **IP & Leak Check**, **`#code` search**, **Always-On Boot**, and **JSON Config Backup**.

---

## 1. Installation

### On Android Phone or Tablet
1. Copy `vpnv7.apk` to your device.
2. Tap `vpnv7.apk` and allow **Install unknown apps** if prompted.
3. Launch **SwiftTunnel** from your home screen.

### On Android TV / Google TV
**Option A — Via ADB (Fastest):**
1. On your TV, go to **Settings → Device Preferences → About → Build** and click 7 times to enable Developer Options.
2. Enable **USB / Network Debugging** under **Developer Options**.
3. From your computer:
   ```bash
   adb connect <YOUR_TV_IP>:5555
   adb install -r vpnv7.apk
   ```

**Option B — Via TV Downloader / USB Drive:**
1. Copy `vpnv7.apk` onto a USB drive (or host it on your LAN/GitHub Release).
2. Install using a file manager or Downloader app on your Android TV.
3. Open **SwiftTunnel** from the Android TV **Apps** row.

---

## 2. Setting Up or Swapping Your NordVPN API Key

> **Note**: If you only want **Cloudflare 1.1.1.1 DNS**, **Google 8.8.8.8 DNS**, or **AdGuard / Quad9 / Custom DNS-Only** routing, you can skip this step completely.

1. Log into your **Nord Account** on a web browser → **NordVPN** → **Set up NordVPN manually** → **Generate new token** (choose non-expiring for Always-On TV usage).
2. Open SwiftTunnel → **Tools** tab → **NORD API KEY** card.
3. Tap **Swap Key**, paste your NordVPN access token, and tap **Save & Verify**.
4. SwiftTunnel encrypts the key in **Android Keystore (AES-256-GCM)** and verifies it live against `api.nordvpn.com/v1/users/services/credentials`.
5. Tap **Swap Key** anytime to rotate tokens, or **Remove Stored Key** to clear it.

---

## 3. Routing Combinations & DNS Ad-Blocking / Custom IPv4 Presets

### Step A — Choose Your Base Routing Combination
In the **Connect** tab, select one of the 5 routing cards:
1. **Cloudflare + VPN (`1.1.1.1` + NordLynx)** *(Recommended)*: Full encrypted WireGuard tunnel + Cloudflare DNS.
2. **Google + VPN (`8.8.8.8` + NordLynx)**: Full encrypted WireGuard tunnel + Google DNS.
3. **NordVPN Default**: Full encrypted WireGuard tunnel + NordVPN native resolvers (`103.86.96.100`).
4. **Cloudflare 1.1.1.1 Only**: Direct low-latency DNS tunnel without changing your ISP IP.
5. **Google 8.8.8.8 Only**: Direct Google DNS tunnel without changing your ISP IP.

### Step B — Optional DNS Filter & Ad-Blocking Override (v6)
In the **DNS Filter & Ad-Blocking** card right below Routing Combinations:
- **Mode Default**: Uses the active mode's built-in DNS (`1.1.1.1`, `8.8.8.8`, or Nord DNS).
- **AdGuard (`94.140.14.14` / `94.140.15.15`)**: Blocks ads, trackers, and telemetry across apps & Android TV — works both **inside WireGuard VPN** and in **DNS-Only mode**.
- **Quad9 (`9.9.9.9` / `149.112.112.112`)**: Blocks malicious, phishing, and spyware domains with DNSSEC.
- **Custom IPv4**: Tap to enter your own validated primary and optional secondary IPv4 DNS resolvers (e.g., NextDNS, ControlD, or local Pi-hole).

> **Tip**: If you change DNS presets, LAN bypass, MTU, or split-tunnel apps while already connected, a blue **"Routing updated. Re-apply to live tunnel?"** banner appears at the top — tap **Apply** to reconfigure the live tunnel immediately.

---

## 4. 1-Tap Saved Profiles (v6)

Save your favorite setups and switch between them in a single tap (or Android TV OK press):
1. Configure your desired **Routing Mode**, **DNS Preset**, **Nord Country/City/Server `#code`**, **Split Tunnel Apps**, **LAN Bypass**, and **MTU**.
2. In the **1-Tap Saved Profiles** card, tap **+ Save Current**.
3. Give it a memorable name (e.g., `"TV US Streaming + AdGuard"`, `"Brave Albania Only"`, `"Low-Ping Gaming MTU 1420"`) and tap **Save Profile**.
4. Tap any saved profile card anytime to automatically restore every setting, resolve its pinned server `#code`, and connect immediately. Tap the trash icon to remove an old profile.

---

## 5. Network Tuning: LAN Bypass, Auto-Failover, Custom MTU & 1-Tap Server Hop (v6)

Find all 4 controls in the **Network Tuning & Resilience** card:

- **LAN / Local Network Bypass**:
  - Toggle **ON** at home to keep **Chromecast**, **Plex / Jellyfin**, **SMB / NAS shares**, local printers, and **ADB over Wi-Fi** reachable while 100% of internet traffic stays encrypted inside WireGuard.
  - Works on **every Android version (7 through 16)** — uses native `excludeRoute` on Android 13+ and an exact IPv4 CIDR complement table on Android 7–12.
  - Toggle **OFF** on untrusted public Wi-Fi for strict tunnel isolation.
- **Auto-Failover & Wi-Fi/5G Handover** *(ON by default)*:
  - Automatically re-handshakes WireGuard when your phone moves between **Wi-Fi and 5G/LTE** (or when Ethernet reconnects on Android TV).
  - If a pinned NordVPN server times out or goes offline for maintenance, automatically falls back to the lowest-load live WireGuard server in the same city/country.
- **Custom WireGuard MTU (`1280` / `1360` / `1400` / `1420`)**:
  - **`1280`** *(Default)*: Most reliable on 4G/5G cellular, carrier CGNAT, and PPPoE DSL links.
  - **`1360` / `1400`**: Balanced overhead reduction for mixed networks.
  - **`1420`**: Maximum throughput on clean gigabit Wi-Fi and wired Android TV Ethernet.
- **1-Tap Lower-Load Server Hop**:
  - Tap **Hop to Lower-Load Server in Same Region** anytime your current server slows down. SwiftTunnel fetches live servers in your selected city/country, picks the least-loaded alternate server, pins it, and reconnects automatically.

---

## 6. Android Quick Settings Tile (v6 — Phones & Tablets)

Connect or disconnect SwiftTunnel without opening the app:
1. Swipe down twice from the top of your Android phone/tablet to open the full Quick Settings panel.
2. Tap the **Edit (pencil)** button.
3. Drag the **SwiftTunnel** tile into your active Quick Settings area.
4. Tap the tile anytime to toggle your VPN on/off — the tile subtitle displays your active server **`#code` and city** (e.g., `#13084 • Los Angeles`).

---

## 7. Built-In IP & Leak Verification Test (v6)

Verify that your VPN and IPv6 leak block are working without opening a browser:
1. Open the **Connect** tab (left column on Android TV) and find **Built-In IP & Leak Verification**.
2. Tap **Run Test**.
3. SwiftTunnel queries `https://1.1.1.1/cdn-cgi/trace` and reports:
   - **Exit IP**, **Country Code**, and **Edge Colo** (e.g., `198.51.100.12 • US • LAX`).
   - **Nord Server Match**: Green checkmark when your observed Exit IP matches your active NordVPN station IP.
   - **IPv6 Leak Status**: Confirms **`IPv6 Leak: BLOCKED`**.
   - **Active DNS**: Confirms your active resolvers (e.g., `94.140.14.14 / 94.140.15.15`).

---

## 8. Live Connection Details, Location `#Code` & 30-Second Sparkline Graph

Both Android mobile and Android TV display the **Connection Details & Stats** card on the **Connect** screen (permanently visible in the left column of the Android TV **2-Column Command Center**):
- **Location Code & Endpoint Banner**: Country flag, Electric Cyan **`#code` badge** (`#13084`), resolved **`City, Country`**, ISO badge (`US`), hostname (`us13084.nordvpn.com`), and endpoint (`IP:51820` or `DNS:53`). Tap or press **OK** to switch locations.
- **2x2 Live Telemetry Grid**: Real-time **↓ Download** & **↑ Upload** speeds + cumulative totals, **Latency (`ms`)** + **Server Load `%`** (or DNS Cache Hits), and **WG Packets / DNS Queries** + total session traffic.
- **Live 30-Second Throughput Sparkline Graph (v6)**: Smooth dual-series real-time graph (Emerald Download fill + Electric Cyan Upload line + peak scale indicator) showing live streaming/download activity at a glance.
- **Connection Spec Grid**: **Location Code**, **Protocol**, **DNS Servers**, **Tunnel IP**, **Split Tunnel**, **Exit / Trace IP**, **LAN Routing** (`LAN Bypass ON` / `Strict Tunnel`), and **Interface MTU**.

---

## 9. Location-Based & Server `#Code` Search + Direct Connect

Open the **Locations** tab to search all **118+ NordVPN countries**, cities, and ~6,000+ live NordLynx servers:

| Format | Examples | Result |
| :--- | :--- | :--- |
| **Plain Number** | `13084`, `13229`, `9948`, `13524` | Exact server & location (`#13084 • Los Angeles, United States`) |
| **Hash + Number** | `#12790`, `#9898`, `#11814`, `#11519`, `#11640` | Exact lookup with `#` prefix |
| **Number Range** | `#12790-12792`, `#9898-9899`, `#12394-12398` | Expands and lists every live server in the range |
| **Multiple Codes** | `13084, 13089` or `#11519, #11527, #11530` | Lists all matching servers sorted by code & load |
| **Country Prefix / Hostname** | `us13084`, `uk2410`, `al17` | Filters to that country prefix and exact server number |
| **City + Code / Name** | `Los Angeles 13084`, `Tirana`, `Albania` | Exact server + matching Quick Connect city chips |

Tap **Connect** (or press **OK** on your TV remote) to pin and connect immediately.

---

## 10. Per-App Split Tunneling & Simultaneous Multi-Location Tunnels (v7.0 — Phone & Android TV)

Open the **Apps** tab to configure both standard Split Tunneling and **Simultaneous Per-App Country Tunnels**:

### Split Tunnel Mode
- **All Apps**: Every app routes through SwiftTunnel.
- **Exclude Apps**: Selected apps bypass the tunnel (e.g., local banking apps).
- **Include Only**: **Only** selected apps (and any apps with a per-app country route) route through SwiftTunnel.

### Per-App Country Tunnels (`App 1 → India`, `App 2 → US` Simultaneously — Android 10+)
1. In the **Apps** tab, locate an app (e.g., **Brave** or **Netflix TV**).
2. Tap its **`Default`** route button on the right side of the row (or press **OK** on your Android TV remote).
3. Search and pick any of the **118+ NordVPN countries** (e.g., **India** for Brave, **United States** for Netflix).
4. Active rules appear as compact chips at the top (`Brave → 🇮🇳 IN`, `Netflix → 🇺🇸 US`) — tap any chip to reset it to Default.
5. Return to **Tunnel** and tap **Connect** (or tap **Apply** if already connected).
6. In the **TELEMETRY** card, **ACTIVE MULTI-APP TUNNELS** lists every concurrent WireGuard peer with its live server `#code`, country, routed app count, and per-peer traffic.

---

## 11. Export & Import Config JSON (Phone ↔ Android TV)

Clone your setup (Saved Profiles, **Per-App Country Routes**, Split-Tunnel lists, DNS presets, LAN bypass, MTU, and favorites) between Phone and Android TV (see `import/export_eg.json` for a complete sample):

1. Open the **Tools** tab → **BACKUP JSON** card (badged **`SECRETS EXCLUDED`**).
2. **On Android Phone/Tablet**:
   - Tap **Export** to save `swifttunnel-config.json` via Android's file picker (zero storage permissions).
   - Tap **Import** on another device to restore.
3. **On Android TV (No File Manager Required)**:
   - Tap **Copy / Paste JSON (TV)** to open the built-in JSON transfer dialog.
   - Copy or paste the JSON text and tap **Import**.
4. **Security Reminder**: Your NordVPN API key and WireGuard private keys are **never** included in exported JSON — enter your NordVPN key once in **Tools → NORD API KEY** on the new device.

---

## 12. Always-On Auto-Connect on Power-Up

1. Configure your preferred **Routing Mode**, **DNS Preset**, **Location / `#code`**, **Per-App Routes / Split Tunnel**, **LAN Bypass**, and **MTU**.
2. Connect **at least once** interactively and tap **OK** on Android's system VPN consent dialog.
3. Open **Tools** → **ALWAYS-ON & BACKGROUND** and verify **Auto-Connect on Boot** is toggled **ON**.
4. On phones/supported TVs, optionally tap **OS Always-On** to enable Android's native Always-On VPN lock *(do not enable "Block connections without VPN" if you use Split Tunnel Exclude/Include modes or LAN Bypass)*.

---

## 13. Troubleshooting

| Symptom | Cause | Fix |
| :--- | :--- | :--- |
| Chromecast / Plex / NAS unreachable while VPN is on | Strict tunnel routing is active | Enable **LAN / Local Network Bypass** in **Network Tuning & Resilience** and tap **Apply** |
| Slow throughput or stalled websites on mobile data | MTU too large for carrier CGNAT | Set **WireGuard Packet MTU** to **`1280`** and tap **Apply** |
| Included app (e.g., Brave) still shows local IP | Using DNS-only mode (`1.1.1.1 Only` / `8.8.8.8 Only`) or didn't re-apply after editing Split Apps | Switch to `Cloudflare + VPN` or `Google + VPN`, check the app under **Include Only**, and tap **Apply** / reconnect |
| Pinned server stopped responding | Server under NordVPN maintenance or overloaded | Enable **Auto-Failover** or tap **Hop to Lower-Load Server in Same Region** |
| Android TV has no file picker for JSON backup | Minimal TV OS without DocumentsUI | Tap **Copy / Paste JSON (Android TV Friendly)** inside the **Backup & Transfer Config** card |
| Quick Settings Tile opens app instead of connecting directly | First-time VPN consent or Nord API key not yet configured | Approve Android's VPN dialog once (and add your Nord API key for VPN modes); subsequent tile taps toggle immediately |
