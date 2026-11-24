package com.example.data

import com.example.model.NordServer

data class ParsedServerCodeQuery(
    val rawQuery: String,
    val numbers: Set<Int>,
    val numberTokens: List<String>,
    val countryCodeHint: String?,
    val locationTextHint: String?
)

object ServerCodeMatcher {

    private const val MAX_RANGE_EXPANSION = 50
    private val RANGE_REGEX = Regex("#?\\s*(\\d{1,6})\\s*[-–—]\\s*#?\\s*(\\d{1,6})")
    private val PREFIX_NUM_REGEX = Regex("\\b([a-zA-Z]{2})\\s*[-#]?\\s*(\\d{1,6})(?:\\.nordvpn\\.com)?\\b", RegexOption.IGNORE_CASE)
    private val STANDALONE_NUM_REGEX = Regex("#?\\s*(\\d{1,6})")
    private val NAME_HASH_NUM_REGEX = Regex("#(\\d{1,6})")
    private val HOSTNAME_NUM_REGEX = Regex("^[a-zA-Z]{2,3}(\\d{1,6})(?:\\.|$)")
    private val NON_ALPHA_CLEAN_REGEX = Regex("[^a-zA-Z\\s]")

    /**
     * Parses a user search query to determine whether it targets one or more NordVPN server
     * numbers / location codes (e.g. "13084", "#13084", "#12790-12792", "us13084",
     * "Los Angeles 13084", "Dallas (#9898-9899)", "#11519, #11527").
     *
     * Returns null for plain country/city/ISO text queries with no digits.
     */
    fun parseQuery(raw: String): ParsedServerCodeQuery? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.none { it.isDigit() }) {
            return null
        }

        val numbers = LinkedHashSet<Int>()
        val numberTokens = ArrayList<String>()
        var countryCodeHint: String? = null

        var remaining = trimmed

        // 1. Extract explicit ranges like "#12790-12792", "9898-9899", "#12394-12398"
        val rangeMatches = RANGE_REGEX.findAll(remaining).toList()
        for (match in rangeMatches) {
            val startStr = match.groupValues[1]
            val endStr = match.groupValues[2]
            val start = startStr.toIntOrNull()
            val end = endStr.toIntOrNull()
            if (start != null && end != null && start > 0 && end >= start) {
                val cappedEnd = minOf(end, start + MAX_RANGE_EXPANSION - 1)
                for (n in start..cappedEnd) {
                    numbers.add(n)
                }
                numberTokens.add(startStr)
                if (endStr != startStr) numberTokens.add(endStr)
            }
        }
        remaining = RANGE_REGEX.replace(remaining, " ")

        // 2. Extract country-prefixed hostnames/codes like "us13084", "us#13084", "al17.nordvpn.com"
        val prefixMatches = PREFIX_NUM_REGEX.findAll(remaining).toList()
        for (match in prefixMatches) {
            val prefix = match.groupValues[1].uppercase()
            val numStr = match.groupValues[2]
            val num = numStr.toIntOrNull()
            if (num != null && num > 0) {
                numbers.add(num)
                numberTokens.add(numStr)
                if (countryCodeHint == null) {
                    countryCodeHint = prefix
                }
            }
        }
        remaining = PREFIX_NUM_REGEX.replace(remaining, " ")

        // 3. Extract any remaining plain or #-prefixed server numbers like "13084", "#11519", "# 11640"
        val standaloneMatches = STANDALONE_NUM_REGEX.findAll(remaining).toList()
        for (match in standaloneMatches) {
            val numStr = match.groupValues[1]
            val num = numStr.toIntOrNull()
            if (num != null && num > 0) {
                numbers.add(num)
                numberTokens.add(numStr)
            }
        }
        remaining = STANDALONE_NUM_REGEX.replace(remaining, " ")

        if (numbers.isEmpty()) {
            return null
        }

        // 4. Preserve optional city/country text hint (e.g. "Los Angeles" from "Los Angeles 13084")
        val cleanedText = NON_ALPHA_CLEAN_REGEX
            .replace(remaining.replace(".nordvpn.com", "", ignoreCase = true), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        val locationTextHint = cleanedText.takeIf { it.length >= 2 }

        return ParsedServerCodeQuery(
            rawQuery = trimmed,
            numbers = numbers,
            numberTokens = numberTokens.distinct(),
            countryCodeHint = countryCodeHint,
            locationTextHint = locationTextHint
        )
    }

    /**
     * Extracts the numeric server code from a NordVPN server (e.g. "United States #13084" -> 13084,
     * "us13084.nordvpn.com" -> 13084).
     */
    fun extractServerNumber(server: NordServer): Int? {
        return extractServerNumber(server.name, server.hostname)
    }

    fun extractServerNumber(name: String, hostname: String): Int? {
        NAME_HASH_NUM_REGEX.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        HOSTNAME_NUM_REGEX.find(hostname.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        return null
    }

    /**
     * Matches live NordVPN servers against a parsed server-code query, ranking exact server-number
     * matches first, followed by live prefix autocomplete matches (for 3+ digit partial codes) and
     * sorted by lowest server load.
     */
    fun matchServers(
        servers: List<NordServer>,
        parsed: ParsedServerCodeQuery,
        maxResults: Int = 40
    ): List<NordServer> {
        if (servers.isEmpty() || parsed.numbers.isEmpty()) return emptyList()

        val singlePrefixToken = if (parsed.numbers.size == 1 && parsed.numberTokens.size == 1) {
            parsed.numberTokens.first().takeIf { it.length >= 3 }
        } else {
            null
        }
        val normalizedRaw = parsed.rawQuery.lowercase().removePrefix("#").trim()

        data class Scored(val server: NordServer, val rank: Int, val number: Int)

        val scored = ArrayList<Scored>()
        for (server in servers) {
            val srvNum = extractServerNumber(server)
            val srvNumStr = srvNum?.toString()
            val countryOk = matchesCountryHint(server, parsed.countryCodeHint)
            val locationOk = matchesLocationHint(server, parsed.locationTextHint)
            val hintsOk = countryOk && locationOk

            val rank = when {
                srvNum != null && srvNum in parsed.numbers && hintsOk -> 0
                srvNum != null && srvNum in parsed.numbers -> 1
                singlePrefixToken != null && srvNumStr != null && srvNumStr.startsWith(singlePrefixToken) && hintsOk -> 2
                singlePrefixToken != null && srvNumStr != null && srvNumStr.startsWith(singlePrefixToken) -> 3
                normalizedRaw.length >= 4 && (
                    server.hostname.contains(normalizedRaw, ignoreCase = true) ||
                        server.name.contains(normalizedRaw, ignoreCase = true)
                    ) -> 4
                else -> -1
            }

            if (rank >= 0) {
                scored.add(Scored(server = server, rank = rank, number = srvNum ?: Int.MAX_VALUE))
            }
        }

        // If we have exact number matches and the user searched multiple numbers/ranges,
        // keep exact matches crisp without noisy prefix matches.
        val hasExactMatches = scored.any { it.rank <= 1 }
        val filtered = if (hasExactMatches && parsed.numbers.size > 1) {
            scored.filter { it.rank <= 1 }
        } else {
            scored
        }

        return filtered
            .sortedWith(
                compareBy<Scored> { it.rank }
                    .thenBy { it.number }
                    .thenBy { it.server.loadPercent }
            )
            .take(maxResults)
            .map { it.server }
    }

    private fun matchesCountryHint(server: NordServer, hint: String?): Boolean {
        if (hint.isNullOrBlank()) return true
        val normalizedHint = hint.uppercase()
        val serverIso = server.countryCode.uppercase()
        if (serverIso == normalizedHint) return true
        // NordVPN hostnames use "uk####.nordvpn.com" while ISO 3166-1 code is "GB"
        if ((normalizedHint == "UK" && serverIso == "GB") || (normalizedHint == "GB" && serverIso == "UK")) {
            return true
        }
        return server.hostname.startsWith(normalizedHint.lowercase(), ignoreCase = true)
    }

    private fun matchesLocationHint(server: NordServer, hint: String?): Boolean {
        if (hint.isNullOrBlank()) return true
        return server.cityName.contains(hint, ignoreCase = true) ||
            server.countryName.contains(hint, ignoreCase = true) ||
            server.name.contains(hint, ignoreCase = true) ||
            server.hostname.contains(hint, ignoreCase = true)
    }
}
