# SwiftTunnel End-to-End User Guide (Phone, Tablet & Android TV)

This guide walks through installing **SwiftTunnel (`vpnv4.apk`)**, swapping NordVPN API keys, choosing DNS + VPN combinations, searching and connecting by country, city, or server `#code` (e.g. `13084`, `#12790`), configuring Split Tunneling, and setting up Always-On power-up protection on both mobile devices and Android TV.

---

## 1. Installation

### On Android Phone or Tablet
1. Copy `vpnv4.apk` to your device (or download from your repository release).
2. Tap `vpnv4.apk` and allow **Install unknown apps** if prompted.
3. Launch **SwiftTunnel** from your home screen.

### On Android TV / Google TV
**Option A — Via ADB (Fastest):**
1. On your TV, go to **Settings → Device Preferences → About → Build** and click 7 times to enable Developer Options.
2. Enable **USB / Network Debugging** under **Developer Options**.
3. From your computer:
   ```bash
   adb connect <YOUR_TV_IP>:5555
   adb install -r vpnv4.apk
   ```

**Option B — Via TV Downloader / USB Drive:**
1. Copy `vpnv4.apk` onto a USB drive (or host it on your LAN/GitHub Release).
2. Install using a file manager or Downloader app on your Android TV.
3. Open **SwiftTunnel** from the Android TV **Apps** row (look for the SwiftTunnel dark navy TV banner).

---

## 2. Setting Up or Swapping Your NordVPN API Key

> **Note**: If you only want **Cloudflare 1.1.1.1 Only** or **Google 8.8.8.8 Only** DNS routing, you can skip this step completely.

1. Log into your **Nord Account** on a web browser → **NordVPN** → **Set up NordVPN manually** → **Generate new token** (choose non-expiring for Always-On TV usage).
2. Open SwiftTunnel → **Connect** tab → **NordVPN API Key** card.
3. Tap **Swap Key** (or **Add Key**), paste your NordVPN access token, and tap **Save & Verify**.
4. SwiftTunnel encrypts the key in **Android Keystore (AES-256-GCM)** and verifies it live against `api.nordvpn.com/v1/users/services/credentials`.
5. Once verified, you will see a green **NordLynx Ready** badge and a masked preview of your key.

### Swapping to a Different Key Anytime
- Tap **Swap Key** at any time to replace the stored token with a new NordVPN key, or tap **Clear** to wipe the stored key from Android Keystore.

---

## 3. Choosing Your Routing Combination

In the **Connect** tab, select one of the 5 routing cards:

1. **Cloudflare + VPN (`1.1.1.1` + NordLynx)** *(Recommended)*: Full encrypted WireGuard tunnel to your chosen NordVPN location + Cloudflare DNS.
2. **Google + VPN (`8.8.8.8` + NordLynx)**: Full encrypted WireGuard tunnel to your chosen NordVPN location + Google DNS.
3. **NordVPN Default**: Full encrypted WireGuard tunnel using NordVPN's native resolvers (`103.86.96.100`).
4. **Cloudflare 1.1.1.1 Only**: Fast Cloudflare DNS resolution without VPN routing (keeps your local ISP IP).
5. **Google 8.8.8.8 Only**: Google DNS resolution without VPN routing (keeps your local ISP IP).

> **Tip**: Switching routing modes while connected? Disconnect and tap the Power Orb again (or tap **Connect** on any location) to immediately apply the new route table.

---

## 4. Location-Based & Server Code Search + Direct Connect

Open the **Locations** tab to browse all **118+ NordVPN countries**, cities, and live WireGuard servers:

### A. Search by NordVPN Server Number or `#Code` (Shows Exact City & Country)
Type any NordVPN server number or code into the search bar — SwiftTunnel searches the live NordLynx fleet and immediately displays the resolved **City & Country**, hostname, IP, and load `%`:

| Format | Examples | Result |
| :--- | :--- | :--- |
| **Plain Number** | `13084`, `13229`, `9948`, `13524` | Shows exact server & location (e.g., `#13084 • Los Angeles, United States`) |
| **Hash + Number** | `#12790`, `#9898`, `#11814`, `#11519`, `#11640` | Same exact lookup with `#` prefix |
| **Number Range** | `#12790-12792`, `#9898-9899`, `#12394-12398`, `12179-12181` | Expands and lists every live server in the range |
| **Multiple Codes** | `13084, 13089` or `#11519, #11527, #11530` | Lists all matching servers sorted by code & load |
| **Country Prefix / Hostname** | `us13084`, `us13084.nordvpn.com`, `uk2410`, `al17` | Filters to that country prefix and exact server number |
| **City + Code** | `Los Angeles 13084`, `Dallas #9898`, `Ashburn #11640` | Shows exact matching server + matching city card below |

Tap **Connect** (or press **OK** on your Android TV remote) on any matched server card to pin that exact server (persisted for Always-On boot auto-connect) and connect immediately.

### B. Search by Country, City, or ISO Code
- Type in the search bar:
  - Country name: `Albania`, `Switzerland`, `Japan`, `United States`
  - City name: `Tirana`, `Los Angeles`, `Charlotte`, `Dallas`, `Miami`, `Phoenix`, `Denver`, `Seattle`, `Chicago`, `Atlanta`, `Ashburn`
  - 2-letter ISO code: `AL`, `CH`, `JP`, `US`, `DE`
- Matching cities appear immediately in the **Quick Connect by City** horizontal strip — tap any city chip to jump straight into a WireGuard connection in that city.

### C. Nearest Low-Load Nord Locations
- When the search bar is empty, the **Nearest Low-Load Nord Locations** strip displays geo-optimal NordVPN WireGuard servers along with their current server load (`%`).
- Tap any card to connect directly to that exact low-load server.

### D. Browse & Pin Live Servers Inside Any Country
1. Find any country card (e.g., **Albania** or **United States**).
2. Tap **Connect** on the country card to connect immediately to NordVPN's recommended lowest-load server in that country.
3. Or tap **Live Servers** to expand the live server list for that country (showing `#code`, city, hostname, station IP, and load `%`).
4. Tap any specific server row to pin and connect directly to it.

---

## 5. Per-App Split Tunneling (Phone & Android TV)

Open the **Split Tunnel** tab to control exactly which apps go through SwiftTunnel:

| Option | Behavior | Example Use Case |
| :--- | :--- | :--- |
| **All Apps** | Every app on your device uses SwiftTunnel | Full-device protection |
| **Exclude Apps** | All apps use SwiftTunnel **except** checked apps | Exclude local banking apps or local LAN casting apps |
| **Include Only** | **Only** checked apps use SwiftTunnel; all others stay on local ISP | Route only **Brave Browser** through Albania, or route only a specific Android TV streaming app through UK/US |

### Verifying Split Tunneling (Example: Brave on Albania)
1. Go to **Connect** tab → select **Cloudflare + VPN** (or **Google + VPN**).
2. Go to **Locations** tab → search `Albania` → tap **Albania** (or tap **Connect** directly).
3. Go to **Split Tunnel** tab → select **Include Only** → search `Brave` → check **Brave**.
4. Return to **Connect** tab and tap the **Power Orb** to connect (or reconnect if already connected).
5. Open Brave and visit `https://ipleak.net` or `https://nordvpn.com/what-is-my-ip/` — Brave will show your **Albania NordVPN IP** while other browsers/apps remain on your local ISP IP.

> **Important**: Always reconnect after changing Split Tunneling apps or switching between Include/Exclude modes because Android applies per-app routing rules when the TUN interface is built.

---

## 6. Always-On Auto-Connect on Power-Up

SwiftTunnel supports two complementary layers of Always-On persistence:

### Layer 1: Built-In Boot Auto-Connect (Works on Phones & Android TV)
1. Configure your desired **Routing Mode**, **Location / Pinned Server**, and **Split Tunneling** rules.
2. Connect **at least once** and tap **OK** on Android's system VPN permission dialog.
3. In the **Connect** tab, verify that **Auto-Connect on Power-Up** is toggled **ON** (enabled by default).
4. Whenever your phone or Android TV powers off and boots back up:
   - SwiftTunnel launches automatically in the background.
   - Waits up to 45 seconds for Wi-Fi or Ethernet connectivity.
   - Reconnects automatically to your exact pointed location, routing mode, and split-tunnel configuration (with automatic retries if the network is still settling).

### Layer 2: Android OS Always-On VPN (Phones & Supported TVs)
1. In the **Connect** tab, tap **OS Always-On** (opens Android's system VPN settings).
2. Tap the gear icon next to **SwiftTunnel**.
3. Toggle **Always-on VPN** ON.
   - *(Optional)* Enable **Block connections without VPN** only when using **All Apps** mode (do **not** enable "Block connections without VPN" if you use Split Tunneling Exclude/Include modes, as Android blocks bypassed apps when lockdown is active).

### Background Battery Optimization (Phones)
- In the **Connect** tab, tap **Battery Settings** → find **SwiftTunnel** → set to **Unrestricted** to prevent aggressive OEM battery savers from pausing background tunnels during long screen-off periods.

---

## 7. Android TV Remote Navigation Tips

- **Left / Right / Up / Down D-Pad**: Moves the glowing **Electric Cyan** focus ring across the side NavigationRail, Power Orb, routing cards, search bar, country/city cards, and Split Tunnel app rows.
- **OK / Center Button**: Activates the focused tile, toggles checkboxes/switches, or connects to the focused server.
- **Back Button**: Moves focus back or exits text input fields cleanly.

---

## 8. Troubleshooting

| Symptom | Cause | Fix |
| :--- | :--- | :--- |
| Included app (e.g., Brave) still shows local IP | Using `Cloudflare Only` / `Google Only` mode, or didn't reconnect after adding the app | Switch to `Cloudflare + VPN` or `Google + VPN`, ensure the app is checked under **Include Only**, then disconnect and reconnect |
| "Invalid or expired NordVPN API key" | Token was typed incorrectly or revoked | Generate a fresh manual token in your Nord Account dashboard and tap **Swap Key** |
| WireGuard handshake timed out | Local network blocks UDP port `51820` or server is overloaded | Try another city or live server in the **Locations** tab, or verify UDP `51820` is permitted on your router |
| TV didn't auto-connect after reboot | VPN consent dialog was never approved before reboot, or **Auto-Connect on Power-Up** was toggled off | Connect once interactively to grant Android VPN permission and confirm **Auto-Connect on Power-Up** is ON |
| Android TV app missing in Split Tunnel list | App has no launcher or Leanback activity | Only launchable apps (mobile + TV Leanback) are listed to remain compliant with Android package visibility policies |
