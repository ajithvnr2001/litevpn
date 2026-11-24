package com.example.data

import com.example.model.AppRouteRule
import com.example.model.ConnectionMode
import com.example.model.DnsPreset
import com.example.model.SavedVpnProfile
import com.example.model.SplitTunnelMode
import com.example.model.isValidIpv4Address

data class AppConfigBackup(
    val schemaVersion: Int = 1,
    val connectionModeId: String = ConnectionMode.CLOUDFLARE_PLUS_VPN.id,
    val dnsPresetId: String = DnsPreset.MODE_DEFAULT.id,
    val customDnsPrimary: String = "",
    val customDnsSecondary: String = "",
    val selectedCountryId: Int? = null,
    val selectedCityName: String? = null,
    val pinnedServerHostname: String? = null,
    val favoriteCountryIds: Set<Int> = emptySet(),
    val splitModeId: String = SplitTunnelMode.ALL_APPS.id,
    val excludedPackages: Set<String> = emptySet(),
    val includedPackages: Set<String> = emptySet(),
    val autoConnectOnBoot: Boolean = true,
    val lanBypassEnabled: Boolean = false,
    val autoFailoverEnabled: Boolean = true,
    val customMtu: Int = 1280,
    val appRouteRules: List<AppRouteRule> = emptyList(),
    val savedProfiles: List<SavedVpnProfile> = emptyList()
)

/**
 * Pure-Kotlin JSON backup codec for transferring SwiftTunnel settings and Saved Profiles
 * between Android Phone and Android TV.
 *
 * SECURITY INVARIANT: Never serializes or reads NordVPN API keys or WireGuard private keys.
 */
object ConfigBackupCodec {

    const val MIN_MTU = 1280
    const val MAX_MTU = 1420

    fun encode(backup: AppConfigBackup): String {
        val sb = StringBuilder(1024)
        sb.append("{\n")
        sb.append("  \"schemaVersion\": ").append(backup.schemaVersion).append(",\n")
        sb.append("  \"secretsExcluded\": true,\n")
        sb.append("  \"connectionModeId\": ").append(quote(backup.connectionModeId)).append(",\n")
        sb.append("  \"dnsPresetId\": ").append(quote(backup.dnsPresetId)).append(",\n")
        sb.append("  \"customDnsPrimary\": ").append(quote(backup.customDnsPrimary)).append(",\n")
        sb.append("  \"customDnsSecondary\": ").append(quote(backup.customDnsSecondary)).append(",\n")
        sb.append("  \"selectedCountryId\": ").append(backup.selectedCountryId ?: -1).append(",\n")
        sb.append("  \"selectedCityName\": ").append(quote(backup.selectedCityName.orEmpty())).append(",\n")
        sb.append("  \"pinnedServerHostname\": ").append(quote(backup.pinnedServerHostname.orEmpty())).append(",\n")
        sb.append("  \"favoriteCountryIds\": ").append(encodeIntArray(backup.favoriteCountryIds.sorted())).append(",\n")
        sb.append("  \"splitModeId\": ").append(quote(backup.splitModeId)).append(",\n")
        sb.append("  \"excludedPackages\": ").append(encodeStringArray(backup.excludedPackages.sorted())).append(",\n")
        sb.append("  \"includedPackages\": ").append(encodeStringArray(backup.includedPackages.sorted())).append(",\n")
        sb.append("  \"autoConnectOnBoot\": ").append(backup.autoConnectOnBoot).append(",\n")
        sb.append("  \"lanBypassEnabled\": ").append(backup.lanBypassEnabled).append(",\n")
        sb.append("  \"autoFailoverEnabled\": ").append(backup.autoFailoverEnabled).append(",\n")
        sb.append("  \"customMtu\": ").append(backup.customMtu.coerceIn(MIN_MTU, MAX_MTU)).append(",\n")
        sb.append("  \"appRouteRules\": [\n")
        backup.appRouteRules.forEachIndexed { idx, r ->
            sb.append("    {\n")
            sb.append("      \"packageName\": ").append(quote(r.packageName)).append(",\n")
            sb.append("      \"countryId\": ").append(r.countryId).append(",\n")
            sb.append("      \"countryName\": ").append(quote(r.countryName)).append(",\n")
            sb.append("      \"countryCode\": ").append(quote(r.countryCode)).append(",\n")
            sb.append("      \"cityName\": ").append(quote(r.cityName.orEmpty())).append(",\n")
            sb.append("      \"pinnedServerHostname\": ").append(quote(r.pinnedServerHostname.orEmpty())).append(",\n")
            sb.append("      \"pinnedServerCode\": ").append(quote(r.pinnedServerCode.orEmpty())).append("\n")
            sb.append("    }")
            if (idx < backup.appRouteRules.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append("  ],\n")
        sb.append("  \"savedProfiles\": [\n")
        backup.savedProfiles.forEachIndexed { idx, p ->
            sb.append("    {\n")
            sb.append("      \"id\": ").append(quote(p.id)).append(",\n")
            sb.append("      \"name\": ").append(quote(p.name)).append(",\n")
            sb.append("      \"modeId\": ").append(quote(p.modeId)).append(",\n")
            sb.append("      \"dnsPresetId\": ").append(quote(p.dnsPresetId)).append(",\n")
            sb.append("      \"customDnsPrimary\": ").append(quote(p.customDnsPrimary)).append(",\n")
            sb.append("      \"customDnsSecondary\": ").append(quote(p.customDnsSecondary)).append(",\n")
            sb.append("      \"countryId\": ").append(p.countryId ?: -1).append(",\n")
            sb.append("      \"cityName\": ").append(quote(p.cityName.orEmpty())).append(",\n")
            sb.append("      \"pinnedServerHostname\": ").append(quote(p.pinnedServerHostname.orEmpty())).append(",\n")
            sb.append("      \"pinnedServerCode\": ").append(quote(p.pinnedServerCode.orEmpty())).append(",\n")
            sb.append("      \"splitModeId\": ").append(quote(p.splitModeId)).append(",\n")
            sb.append("      \"splitPackages\": ").append(encodeStringArray(p.splitPackages.sorted())).append(",\n")
            sb.append("      \"lanBypassEnabled\": ").append(p.lanBypassEnabled).append(",\n")
            sb.append("      \"mtu\": ").append(p.mtu.coerceIn(MIN_MTU, MAX_MTU)).append("\n")
            sb.append("    }")
            if (idx < backup.savedProfiles.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    fun decode(rawJson: String): Result<AppConfigBackup> = runCatching {
        val root = MiniJsonParser(rawJson).parseValue() as? Map<*, *>
            ?: throw IllegalArgumentException("Invalid SwiftTunnel backup JSON")

        val mode = ConnectionMode.fromId(root["connectionModeId"] as? String)
        val dnsPreset = DnsPreset.fromId(root["dnsPresetId"] as? String)
        val customPrimary = (root["customDnsPrimary"] as? String).orEmpty().trim()
            .takeIf { isValidIpv4Address(it) }.orEmpty()
        val customSecondary = (root["customDnsSecondary"] as? String).orEmpty().trim()
            .takeIf { isValidIpv4Address(it) }.orEmpty()

        val countryIdRaw = (root["selectedCountryId"] as? Number)?.toInt() ?: -1
        val selectedCountryId = if (countryIdRaw > 0) countryIdRaw else null
        val selectedCity = (root["selectedCityName"] as? String)?.trim()?.ifBlank { null }
        val pinnedHost = (root["pinnedServerHostname"] as? String)?.trim()?.ifBlank { null }

        val favIds = (root["favoriteCountryIds"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toInt() }
            ?.filter { it > 0 }
            ?.toSet()
            ?: emptySet()

        val splitMode = SplitTunnelMode.fromId(root["splitModeId"] as? String)
        val excluded = (root["excludedPackages"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.ifBlank { null } }
            ?.toSet()
            ?: emptySet()
        val included = (root["includedPackages"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.ifBlank { null } }
            ?.toSet()
            ?: emptySet()

        val autoBoot = (root["autoConnectOnBoot"] as? Boolean) ?: true
        val lanBypass = (root["lanBypassEnabled"] as? Boolean) ?: false
        val autoFailover = (root["autoFailoverEnabled"] as? Boolean) ?: true
        val mtu = ((root["customMtu"] as? Number)?.toInt() ?: 1280).coerceIn(MIN_MTU, MAX_MTU)

        val profiles = (root["savedProfiles"] as? List<*>)
            ?.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val id = (map["id"] as? String)?.trim()?.ifBlank { null } ?: return@mapNotNull null
                val name = (map["name"] as? String)?.trim()?.ifBlank { null } ?: return@mapNotNull null
                val pCountryRaw = (map["countryId"] as? Number)?.toInt() ?: -1
                val pPrimary = (map["customDnsPrimary"] as? String).orEmpty().trim()
                    .takeIf { isValidIpv4Address(it) }.orEmpty()
                val pSecondary = (map["customDnsSecondary"] as? String).orEmpty().trim()
                    .takeIf { isValidIpv4Address(it) }.orEmpty()
                SavedVpnProfile(
                    id = id,
                    name = name,
                    modeId = ConnectionMode.fromId(map["modeId"] as? String).id,
                    dnsPresetId = DnsPreset.fromId(map["dnsPresetId"] as? String).id,
                    customDnsPrimary = pPrimary,
                    customDnsSecondary = pSecondary,
                    countryId = if (pCountryRaw > 0) pCountryRaw else null,
                    cityName = (map["cityName"] as? String)?.trim()?.ifBlank { null },
                    pinnedServerHostname = (map["pinnedServerHostname"] as? String)?.trim()?.ifBlank { null },
                    pinnedServerCode = (map["pinnedServerCode"] as? String)?.trim()?.ifBlank { null },
                    splitModeId = SplitTunnelMode.fromId(map["splitModeId"] as? String).id,
                    splitPackages = (map["splitPackages"] as? List<*>)
                        ?.mapNotNull { (it as? String)?.trim()?.ifBlank { null } }
                        ?.toSet()
                        ?: emptySet(),
                    lanBypassEnabled = (map["lanBypassEnabled"] as? Boolean) ?: false,
                    mtu = ((map["mtu"] as? Number)?.toInt() ?: 1280).coerceIn(MIN_MTU, MAX_MTU)
                )
            }
            ?: emptyList()

        val appRules = (root["appRouteRules"] as? List<*>)
            ?.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val pkg = (map["packageName"] as? String)?.trim()?.ifBlank { null } ?: return@mapNotNull null
                val cid = (map["countryId"] as? Number)?.toInt() ?: return@mapNotNull null
                if (cid <= 0) return@mapNotNull null
                val cname = (map["countryName"] as? String)?.trim().orEmpty()
                val ccode = (map["countryCode"] as? String)?.trim()?.uppercase().orEmpty()
                AppRouteRule(
                    packageName = pkg,
                    countryId = cid,
                    countryName = cname.ifBlank { ccode },
                    countryCode = ccode,
                    cityName = (map["cityName"] as? String)?.trim()?.ifBlank { null },
                    pinnedServerHostname = (map["pinnedServerHostname"] as? String)?.trim()?.ifBlank { null },
                    pinnedServerCode = (map["pinnedServerCode"] as? String)?.trim()?.ifBlank { null }
                )
            }
            ?.distinctBy { it.packageName }
            ?: emptyList()

        AppConfigBackup(
            schemaVersion = (root["schemaVersion"] as? Number)?.toInt() ?: 1,
            connectionModeId = mode.id,
            dnsPresetId = dnsPreset.id,
            customDnsPrimary = customPrimary,
            customDnsSecondary = customSecondary,
            selectedCountryId = selectedCountryId,
            selectedCityName = selectedCity,
            pinnedServerHostname = pinnedHost,
            favoriteCountryIds = favIds,
            splitModeId = splitMode.id,
            excludedPackages = excluded,
            includedPackages = included,
            autoConnectOnBoot = autoBoot,
            lanBypassEnabled = lanBypass,
            autoFailoverEnabled = autoFailover,
            customMtu = mtu,
            appRouteRules = appRules,
            savedProfiles = profiles
        )
    }

    private fun encodeStringArray(items: List<String>): String =
        items.joinToString(prefix = "[", postfix = "]", separator = ", ") { quote(it) }

    private fun encodeIntArray(items: List<Int>): String =
        items.joinToString(prefix = "[", postfix = "]", separator = ", ") { it.toString() }

    private fun quote(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u%04x".format(ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /** Minimal zero-dependency RFC 8259 JSON parser (100% pure JVM & Android compatible). */
    private class MiniJsonParser(private val src: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> consumeLiteral("true", true)
                'f' -> consumeLiteral("false", false)
                'n' -> consumeLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected char '${src[pos]}' at $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // '{'
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == '}') {
                pos++
                return map
            }
            while (pos < src.length) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                if (pos >= src.length || src[pos] != ':') {
                    throw IllegalArgumentException("Expected ':' at $pos")
                }
                pos++
                map[key] = parseValue()
                skipWhitespace()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == '}') {
                    pos++
                    return map
                } else {
                    throw IllegalArgumentException("Expected ',' or '}' at $pos")
                }
            }
            throw IllegalArgumentException("Unterminated JSON object")
        }

        private fun parseArray(): List<Any?> {
            pos++ // '['
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == ']') {
                pos++
                return list
            }
            while (pos < src.length) {
                list.add(parseValue())
                skipWhitespace()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == ']') {
                    pos++
                    return list
                } else {
                    throw IllegalArgumentException("Expected ',' or ']' at $pos")
                }
            }
            throw IllegalArgumentException("Unterminated JSON array")
        }

        private fun parseString(): String {
            if (pos >= src.length || src[pos] != '"') {
                throw IllegalArgumentException("Expected '\"' at $pos")
            }
            pos++
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) throw IllegalArgumentException("Unterminated escape")
                        when (val esc = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                val hex = src.substring(pos, (pos + 4).coerceAtMost(src.length))
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> sb.append(esc)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated JSON string")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && src[pos].isDigit()) pos++
            var isFloat = false
            if (pos < src.length && src[pos] == '.') {
                isFloat = true
                pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos].isDigit()) pos++
            }
            val token = src.substring(start, pos)
            return if (isFloat) token.toDouble() else token.toLong()
        }

        private fun <T> consumeLiteral(expected: String, result: T): T {
            if (!src.startsWith(expected, pos)) {
                throw IllegalArgumentException("Expected '$expected' at $pos")
            }
            pos += expected.length
            return result
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }
    }
}
