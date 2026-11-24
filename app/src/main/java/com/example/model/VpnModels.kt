package com.example.model

import android.graphics.Bitmap

enum class ConnectionMode(
    val id: String,
    val title: String,
    val shortTitle: String,
    val subtitle: String,
    val primaryDns: String,
    val secondaryDns: String,
    val usesVpn: Boolean,
    val dnsProviderLabel: String
) {
    CLOUDFLARE_PLUS_VPN(
        id = "cf_vpn",
        title = "Cloudflare + VPN",
        shortTitle = "1.1.1.1 + VPN",
        subtitle = "NordLynx tunnel + Cloudflare DNS",
        primaryDns = "1.1.1.1",
        secondaryDns = "1.0.0.1",
        usesVpn = true,
        dnsProviderLabel = "Cloudflare 1.1.1.1"
    ),
    GOOGLE_PLUS_VPN(
        id = "google_vpn",
        title = "Google + VPN",
        shortTitle = "8.8.8.8 + VPN",
        subtitle = "NordLynx tunnel + Google DNS",
        primaryDns = "8.8.8.8",
        secondaryDns = "8.8.4.4",
        usesVpn = true,
        dnsProviderLabel = "Google 8.8.8.8"
    ),
    CLOUDFLARE_ONLY(
        id = "cf_only",
        title = "Cloudflare Only",
        shortTitle = "1.1.1.1 DNS",
        subtitle = "Direct encrypted DNS tunnel (no VPN hop)",
        primaryDns = "1.1.1.1",
        secondaryDns = "1.0.0.1",
        usesVpn = false,
        dnsProviderLabel = "Cloudflare 1.1.1.1"
    ),
    GOOGLE_ONLY(
        id = "google_only",
        title = "Google Only",
        shortTitle = "8.8.8.8 DNS",
        subtitle = "Direct low-latency Google DNS tunnel",
        primaryDns = "8.8.8.8",
        secondaryDns = "8.8.4.4",
        usesVpn = false,
        dnsProviderLabel = "Google 8.8.8.8"
    ),
    NORD_VPN_DEFAULT(
        id = "nord_default",
        title = "Nord VPN + Nord DNS",
        shortTitle = "Nord Default",
        subtitle = "NordLynx tunnel + Nord DNS resolvers",
        primaryDns = "103.86.96.100",
        secondaryDns = "103.86.99.100",
        usesVpn = true,
        dnsProviderLabel = "Nord 103.86.96.100"
    );

    companion object {
        fun fromId(id: String?): ConnectionMode =
            entries.firstOrNull { it.id == id } ?: CLOUDFLARE_PLUS_VPN
    }
}

enum class SplitTunnelMode(
    val id: String,
    val label: String,
    val description: String
) {
    ALL_APPS(
        id = "all",
        label = "All Apps",
        description = "Every app on your device routes through the active tunnel."
    ),
    EXCLUDE_APPS(
        id = "exclude",
        label = "Exclude",
        description = "Selected apps bypass the tunnel and use your direct connection."
    ),
    INCLUDE_ONLY_APPS(
        id = "include",
        label = "Include Only",
        description = "Only selected apps use the tunnel; all other apps stay direct."
    );

    companion object {
        fun fromId(id: String?): SplitTunnelMode =
            entries.firstOrNull { it.id == id } ?: ALL_APPS
    }
}

enum class TunnelStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

enum class RegionFilter(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    EUROPE("Europe"),
    AMERICAS("Americas"),
    ASIA_PACIFIC("Asia-Pacific"),
    MEA("Middle East & Africa")
}

data class NordCity(
    val id: Int,
    val name: String,
    val dnsName: String,
    val latitude: Double,
    val longitude: Double
)

data class NordCountry(
    val id: Int,
    val name: String,
    val code: String,
    val cities: List<NordCity>
) {
    val flagEmoji: String
        get() = countryCodeToFlag(code)

    val region: RegionFilter
        get() = inferRegion(code)
}

data class NordServer(
    val id: Int,
    val name: String,
    val hostname: String,
    val stationIp: String,
    val loadPercent: Int,
    val countryName: String,
    val countryCode: String,
    val cityName: String,
    val wireGuardPublicKey: String
) {
    val flagEmoji: String
        get() = countryCodeToFlag(countryCode)

    val serverNumber: Int?
        get() = com.example.data.ServerCodeMatcher.extractServerNumber(name, hostname)

    val serverCodeTag: String
        get() = serverNumber?.let { "#$it" } ?: hostname.substringBefore('.')

    val locationLabel: String
        get() = when {
            cityName.isNotBlank() && countryName.isNotBlank() -> "$cityName, $countryName"
            countryName.isNotBlank() -> countryName
            cityName.isNotBlank() -> cityName
            else -> hostname
        }
}

sealed interface NordCredentialsState {
    data object NotConfigured : NordCredentialsState
    data object Loading : NordCredentialsState
    data class Verified(
        val keyFingerprint: String,
        val hasPrivateKey: Boolean,
        val serviceUsernameMasked: String?
    ) : NordCredentialsState

    data class Error(val message: String) : NordCredentialsState
}

data class NetworkTraceInfo(
    val ip: String = "",
    val countryCode: String = "",
    val colo: String = "",
    val tls: String = ""
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val icon: Bitmap?
)

enum class DnsPreset(
    val id: String,
    val title: String,
    val shortLabel: String,
    val primaryIp: String,
    val secondaryIp: String,
    val description: String
) {
    MODE_DEFAULT(
        id = "mode_default",
        title = "Mode Default DNS",
        shortLabel = "Mode DNS",
        primaryIp = "",
        secondaryIp = "",
        description = "Uses 1.1.1.1, 8.8.8.8, or Nord DNS from your selected routing mode."
    ),
    ADGUARD_ADS(
        id = "adguard",
        title = "AdGuard Ad-Block",
        shortLabel = "AdGuard",
        primaryIp = "94.140.14.14",
        secondaryIp = "94.140.15.15",
        description = "Blocks ads, trackers, and telemetry across apps & Android TV."
    ),
    QUAD9_SECURITY(
        id = "quad9",
        title = "Quad9 Security",
        shortLabel = "Quad9",
        primaryIp = "9.9.9.9",
        secondaryIp = "149.112.112.112",
        description = "Malware & phishing domain blocking with DNSSEC."
    ),
    CUSTOM(
        id = "custom",
        title = "Custom IPv4 DNS",
        shortLabel = "Custom DNS",
        primaryIp = "1.1.1.1",
        secondaryIp = "1.0.0.1",
        description = "Use your own primary and secondary IPv4 resolvers (e.g. NextDNS)."
    );

    companion object {
        fun fromId(id: String?): DnsPreset =
            entries.firstOrNull { it.id == id } ?: MODE_DEFAULT
    }
}

fun isValidIpv4Address(input: String): Boolean {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return false
    val parts = trimmed.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return false
        if (part.length > 1 && part.startsWith('0')) return false
        val num = part.toIntOrNull() ?: return false
        num in 0..255
    }
}

fun resolveEffectiveDns(
    mode: ConnectionMode,
    preset: DnsPreset,
    customPrimary: String,
    customSecondary: String
): Pair<String, String> {
    return when (preset) {
        DnsPreset.MODE_DEFAULT -> mode.primaryDns to mode.secondaryDns
        DnsPreset.ADGUARD_ADS -> preset.primaryIp to preset.secondaryIp
        DnsPreset.QUAD9_SECURITY -> preset.primaryIp to preset.secondaryIp
        DnsPreset.CUSTOM -> {
            val primary = customPrimary.trim().takeIf { isValidIpv4Address(it) } ?: mode.primaryDns
            val secondary = customSecondary.trim().takeIf { isValidIpv4Address(it) } ?: primary
            primary to secondary
        }
    }
}

data class SavedVpnProfile(
    val id: String,
    val name: String,
    val modeId: String,
    val dnsPresetId: String = DnsPreset.MODE_DEFAULT.id,
    val customDnsPrimary: String = "",
    val customDnsSecondary: String = "",
    val countryId: Int? = null,
    val cityName: String? = null,
    val pinnedServerHostname: String? = null,
    val pinnedServerCode: String? = null,
    val splitModeId: String = SplitTunnelMode.ALL_APPS.id,
    val splitPackages: Set<String> = emptySet(),
    val lanBypassEnabled: Boolean = false,
    val mtu: Int = 1280
)

data class LeakCheckResult(
    val exitIp: String,
    val countryCode: String,
    val colo: String,
    val tlsVersion: String,
    val ipv6LeakBlocked: Boolean,
    val expectedServerIp: String?,
    val ipMatchesNordServer: Boolean,
    val activeDnsSummary: String,
    val checkedAtMillis: Long,
    val errorMessage: String? = null
)

data class VpnSessionState(
    val status: TunnelStatus = TunnelStatus.DISCONNECTED,
    val mode: ConnectionMode = ConnectionMode.CLOUDFLARE_PLUS_VPN,
    val connectedAtMillis: Long = 0L,
    val dnsQueriesForwarded: Long = 0L,
    val dnsCacheHits: Long = 0L,
    val bytesTx: Long = 0L,
    val bytesRx: Long = 0L,
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "1.0.0.1",
    val activeServer: NordServer? = null,
    val splitMode: SplitTunnelMode = SplitTunnelMode.ALL_APPS,
    val splitAppCount: Int = 0,
    val latencyMs: Int? = null,
    val isWireGuardVerified: Boolean = false,
    val lanBypassEnabled: Boolean = false,
    val mtu: Int = 1280,
    val errorMessage: String? = null
)

fun countryCodeToFlag(isoCode: String): String {
    val clean = isoCode.trim().uppercase()
    if (clean.length != 2) return "\uD83C\uDF10"
    val first = Character.codePointAt(clean, 0) - 0x41 + 0x1F1E6
    val second = Character.codePointAt(clean, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

private val AMERICAS_CODES = setOf(
    "US", "CA", "MX", "BR", "AR", "CL", "CO", "CR", "PE", "UY", "VE", "PA", "EC", "GT",
    "HN", "BO", "PY", "DO", "PR", "BS", "BZ", "JM", "TT", "SV", "NI", "GL", "BM", "KY"
)

private val ASIA_PACIFIC_CODES = setOf(
    "JP", "SG", "AU", "NZ", "KR", "HK", "TW", "IN", "ID", "MY", "TH", "VN", "PH", "PK",
    "BD", "LK", "NP", "KH", "LA", "MM", "MN", "BN", "GU", "PG", "MO", "BT", "KZ", "UZ"
)

private val MEA_CODES = setOf(
    "AE", "IL", "ZA", "EG", "SA", "QA", "KW", "BH", "OM", "JO", "LB", "MA", "DZ", "TN",
    "NG", "KE", "GH", "SN", "AO", "MZ", "MU", "TZ", "UG", "ET", "TR", "IQ", "AZ", "AM", "GE"
)

fun inferRegion(isoCode: String): RegionFilter {
    val c = isoCode.uppercase()
    return when {
        c in AMERICAS_CODES -> RegionFilter.AMERICAS
        c in ASIA_PACIFIC_CODES -> RegionFilter.ASIA_PACIFIC
        c in MEA_CODES -> RegionFilter.MEA
        else -> RegionFilter.EUROPE
    }
}
