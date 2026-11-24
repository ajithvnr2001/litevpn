package com.example.data

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import com.example.model.NetworkTraceInfo
import com.example.model.NordCity
import com.example.model.NordCountry
import com.example.model.NordServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

data class NordCredentialResult(
    val nordLynxPrivateKey: String?,
    val keyFingerprint: String,
    val serviceUsernameMasked: String?
)

data class ServerCodeSearchResult(
    val matches: List<NordServer>,
    val totalIndexedServers: Int,
    val fromCache: Boolean
)

class NordApiClient {

    companion object {
        private const val BASE_URL = "https://api.nordvpn.com/v1"
        private const val TRACE_URL = "https://1.1.1.1/cdn-cgi/trace"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val CATALOG_READ_TIMEOUT_MS = 30_000
        private const val CATALOG_CACHE_TTL_MS = 15 * 60 * 1000L

        private val catalogMutex = Mutex()
        @Volatile
        private var cachedWireGuardCatalog: List<NordServer> = emptyList()
        @Volatile
        private var cachedCatalogFetchedAtMs: Long = 0L
    }

    fun hasCachedServerCatalog(): Boolean {
        val now = System.currentTimeMillis()
        return cachedWireGuardCatalog.isNotEmpty() &&
            (now - cachedCatalogFetchedAtMs) < CATALOG_CACHE_TTL_MS
    }

    fun clearServerCatalogCache() {
        cachedWireGuardCatalog = emptyList()
        cachedCatalogFetchedAtMs = 0L
    }

    suspend fun fetchAllCountriesRaw(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            httpGet("$BASE_URL/servers/countries")
        }
    }

    fun parseCountriesJson(rawJson: String): List<NordCountry> {
        val array = JSONArray(rawJson)
        val result = ArrayList<NordCountry>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optInt("id", -1)
            val name = obj.optString("name", "").trim()
            val code = obj.optString("code", "").trim().uppercase()
            if (id < 0 || name.isEmpty()) continue

            val citiesArray = obj.optJSONArray("cities")
            val cities = ArrayList<NordCity>()
            if (citiesArray != null) {
                for (j in 0 until citiesArray.length()) {
                    val cityObj = citiesArray.optJSONObject(j) ?: continue
                    val cityId = cityObj.optInt("id", -1)
                    val cityName = cityObj.optString("name", "").trim()
                    if (cityId < 0 || cityName.isEmpty()) continue
                    cities.add(
                        NordCity(
                            id = cityId,
                            name = cityName,
                            dnsName = cityObj.optString("dns_name", ""),
                            latitude = cityObj.optDouble("latitude", 0.0),
                            longitude = cityObj.optDouble("longitude", 0.0)
                        )
                    )
                }
            }
            result.add(
                NordCountry(
                    id = id,
                    name = name,
                    code = code,
                    cities = cities.sortedBy { it.name }
                )
            )
        }
        return result.sortedBy { it.name }
    }

    suspend fun fetchServersForLocation(
        countryId: Int? = null,
        preferredCityName: String? = null,
        limit: Int = 20
    ): Result<List<NordServer>> = withContext(Dispatchers.IO) {
        runCatching {
            val wgQuery = buildString {
                append("$BASE_URL/servers/recommendations?")
                append("filters[servers_technologies][identifier]=wireguard_udp")
                if (countryId != null && countryId > 0) {
                    append("&filters[country_id]=")
                    append(countryId)
                }
                append("&limit=")
                append(limit.coerceIn(1, 50))
            }
            var servers = runCatching { parseServersJson(httpGet(wgQuery)) }.getOrDefault(emptyList())

            // Fallback for newly added or virtual Nord countries without strict wireguard_udp filter
            if (servers.isEmpty() && countryId != null && countryId > 0) {
                val fallbackQuery = "$BASE_URL/servers/recommendations?filters[country_id]=$countryId&limit=${limit.coerceIn(1, 50)}"
                servers = parseServersJson(httpGet(fallbackQuery))
            }

            if (servers.isEmpty()) {
                throw IllegalStateException("No Nord servers currently available for this location")
            }

            if (!preferredCityName.isNullOrBlank()) {
                val cityMatches = servers.filter {
                    it.cityName.equals(preferredCityName, ignoreCase = true)
                }
                if (cityMatches.isNotEmpty()) cityMatches else servers
            } else {
                servers
            }
        }
    }

    suspend fun fetchRecommendedServer(
        countryId: Int? = null,
        preferredCityName: String? = null
    ): Result<NordServer> = fetchServersForLocation(
        countryId = countryId,
        preferredCityName = preferredCityName,
        limit = 12
    ).map { it.first() }

    /**
     * Searches NordVPN's live WireGuard (NordLynx) server directory by server number or #code
     * (e.g. "13084", "#12790-12792", "us13084", "Los Angeles 13084").
     *
     * Streams the gzip-compressed /v1/servers directory via Android's JsonReader to keep peak
     * memory low on mobile and Android TV devices, and caches the directory for 15 minutes.
     */
    suspend fun searchServersByCode(
        parsed: ParsedServerCodeQuery,
        forceRefresh: Boolean = false
    ): Result<ServerCodeSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val wasCached = !forceRefresh && hasCachedServerCatalog()
            val catalog = getOrFetchWireGuardCatalog(forceRefresh)
            val matches = ServerCodeMatcher.matchServers(catalog, parsed)
            ServerCodeSearchResult(
                matches = matches,
                totalIndexedServers = catalog.size,
                fromCache = wasCached
            )
        }
    }

    suspend fun fetchServerByHostname(hostname: String): Result<NordServer> = withContext(Dispatchers.IO) {
        runCatching {
            val target = hostname.trim()
            require(target.isNotEmpty()) { "Empty server hostname" }
            val catalog = getOrFetchWireGuardCatalog(forceRefresh = false)
            catalog.firstOrNull { it.hostname.equals(target, ignoreCase = true) }
                ?: throw IllegalStateException("Pinned server $target is currently unavailable")
        }
    }

    private suspend fun getOrFetchWireGuardCatalog(forceRefresh: Boolean): List<NordServer> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedWireGuardCatalog.isNotEmpty() &&
            (now - cachedCatalogFetchedAtMs) < CATALOG_CACHE_TTL_MS
        ) {
            return cachedWireGuardCatalog
        }

        return catalogMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!forceRefresh && cachedWireGuardCatalog.isNotEmpty() &&
                (lockedNow - cachedCatalogFetchedAtMs) < CATALOG_CACHE_TTL_MS
            ) {
                return@withLock cachedWireGuardCatalog
            }

            // Primary query: high limit returns entire active NordLynx fleet (~6,000+ servers)
            var servers = runCatching {
                streamServersFromUrl(
                    "$BASE_URL/servers?limit=16384&filters[servers_technologies][identifier]=wireguard_udp"
                )
            }.getOrDefault(emptyList())

            // Fallback query: limit=0 in case Nord changes high-limit handling
            if (servers.size < 50) {
                val alt = runCatching {
                    streamServersFromUrl(
                        "$BASE_URL/servers?limit=0&filters[servers_technologies][identifier]=wireguard_udp"
                    )
                }.getOrDefault(emptyList())
                if (alt.size > servers.size) {
                    servers = alt
                }
            }

            if (servers.isEmpty()) {
                throw IllegalStateException("Could not load live NordVPN server directory. Check your connection and retry.")
            }

            cachedWireGuardCatalog = servers
            cachedCatalogFetchedAtMs = System.currentTimeMillis()
            servers
        }
    }

    private fun streamServersFromUrl(urlStr: String): List<NordServer> {
        val connection = (URL(urlStr).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = CATALOG_READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", "SwiftTunnel-Android/4.0")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Nord server directory error (HTTP $code)")
            }
            val rawStream = connection.inputStream
            val isGzip = connection.contentEncoding.equals("gzip", ignoreCase = true)
            val decodedStream = if (isGzip) GZIPInputStream(rawStream) else rawStream
            JsonReader(BufferedReader(InputStreamReader(decodedStream, Charsets.UTF_8), 16 * 1024)).use { reader ->
                parseServersStream(reader)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseServersStream(reader: JsonReader): List<NordServer> {
        val result = ArrayList<NordServer>(4096)
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return result
        }
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                readServerObject(reader)?.let { result.add(it) }
            } else {
                reader.skipValue()
            }
        }
        reader.endArray()
        return result
    }

    private fun readServerObject(reader: JsonReader): NordServer? {
        var id = -1
        var name = ""
        var hostname = ""
        var station = ""
        var load = 0
        var countryName = ""
        var countryCode = ""
        var cityName = ""
        var wgPublicKey = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = nextIntSafe(reader, -1)
                "name" -> name = nextStringSafe(reader)
                "hostname" -> hostname = nextStringSafe(reader)
                "station" -> station = nextStringSafe(reader)
                "load" -> load = nextIntSafe(reader, 0)
                "locations" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (countryName.isEmpty() && reader.peek() == JsonToken.BEGIN_OBJECT) {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    if (reader.nextName() == "country" && reader.peek() == JsonToken.BEGIN_OBJECT) {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "name" -> countryName = nextStringSafe(reader)
                                                "code" -> countryCode = nextStringSafe(reader)
                                                "city" -> {
                                                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                                        reader.beginObject()
                                                        while (reader.hasNext()) {
                                                            if (reader.nextName() == "name") {
                                                                cityName = nextStringSafe(reader)
                                                            } else {
                                                                reader.skipValue()
                                                            }
                                                        }
                                                        reader.endObject()
                                                    } else {
                                                        reader.skipValue()
                                                    }
                                                }
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                    } else {
                                        reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                "technologies" -> {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (wgPublicKey.isEmpty() && reader.peek() == JsonToken.BEGIN_OBJECT) {
                                var techIdentifier = ""
                                var techPubKey = ""
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "identifier" -> techIdentifier = nextStringSafe(reader)
                                        "metadata" -> {
                                            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                                reader.beginArray()
                                                while (reader.hasNext()) {
                                                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                                        var metaName = ""
                                                        var metaValue = ""
                                                        reader.beginObject()
                                                        while (reader.hasNext()) {
                                                            when (reader.nextName()) {
                                                                "name" -> metaName = nextStringSafe(reader)
                                                                "value" -> metaValue = nextStringSafe(reader)
                                                                else -> reader.skipValue()
                                                            }
                                                        }
                                                        reader.endObject()
                                                        if (metaName == "public_key" && metaValue.isNotEmpty()) {
                                                            techPubKey = metaValue
                                                        }
                                                    } else {
                                                        reader.skipValue()
                                                    }
                                                }
                                                reader.endArray()
                                            } else {
                                                reader.skipValue()
                                            }
                                        }
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (techIdentifier == "wireguard_udp" && techPubKey.isNotEmpty()) {
                                    wgPublicKey = techPubKey
                                }
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (hostname.isEmpty() || station.isEmpty()) return null
        return NordServer(
            id = id,
            name = name,
            hostname = hostname,
            stationIp = station,
            loadPercent = load,
            countryName = countryName,
            countryCode = countryCode,
            cityName = cityName,
            wireGuardPublicKey = wgPublicKey
        )
    }

    private fun nextStringSafe(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString()
            JsonToken.NULL -> {
                reader.nextNull()
                ""
            }
            else -> {
                reader.skipValue()
                ""
            }
        }
    }

    private fun nextIntSafe(reader: JsonReader, defaultValue: Int): Int {
        return when (reader.peek()) {
            JsonToken.NUMBER -> runCatching { reader.nextInt() }.getOrDefault(defaultValue)
            JsonToken.STRING -> reader.nextString().toIntOrNull() ?: defaultValue
            JsonToken.NULL -> {
                reader.nextNull()
                defaultValue
            }
            else -> {
                reader.skipValue()
                defaultValue
            }
        }
    }

    suspend fun fetchServiceCredentials(apiKey: String?): Result<NordCredentialResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanedKey = apiKey?.trim().orEmpty()
                if (cleanedKey.isEmpty()) {
                    throw IllegalStateException("No Nord API key provided")
                }
                val rawCreds = "token:$cleanedKey"
                val encoded = Base64.encodeToString(
                    rawCreds.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                val body = httpGet(
                    urlStr = "$BASE_URL/users/services/credentials",
                    authHeader = "Basic $encoded"
                )
                val json = JSONObject(body)
                val privateKey = json.optString("nordlynx_private_key", "").ifBlank { null }
                val username = json.optString("username", "").ifBlank { null }
                val fingerprint = if (privateKey != null) {
                    sha256Short(privateKey)
                } else {
                    "Account Verified"
                }
                val maskedUser = username?.let { u ->
                    if (u.length > 6) "${u.take(3)}•••${u.takeLast(3)}" else "•••"
                }
                NordCredentialResult(
                    nordLynxPrivateKey = privateKey,
                    keyFingerprint = fingerprint,
                    serviceUsernameMasked = maskedUser
                )
            }
        }

    suspend fun fetchNetworkTrace(): Result<NetworkTraceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = httpGet(TRACE_URL)
            val map = HashMap<String, String>()
            raw.lineSequence().forEach { line ->
                val idx = line.indexOf('=')
                if (idx > 0) {
                    map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
                }
            }
            NetworkTraceInfo(
                ip = map["ip"].orEmpty(),
                countryCode = map["loc"].orEmpty(),
                colo = map["colo"].orEmpty(),
                tls = map["tls"].orEmpty()
            )
        }
    }

    suspend fun measureLatencyMs(host: String, port: Int = 443): Int? = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext null
        val start = System.nanoTime()
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2_500)
            }
            ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
        }.getOrNull()
    }

    private fun parseServersJson(rawJson: String): List<NordServer> {
        val array = JSONArray(rawJson)
        val result = ArrayList<NordServer>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optInt("id", -1)
            val name = obj.optString("name", "")
            val hostname = obj.optString("hostname", "")
            val station = obj.optString("station", "")
            val load = obj.optInt("load", 0)
            if (hostname.isEmpty() || station.isEmpty()) continue

            var countryName = ""
            var countryCode = ""
            var cityName = ""
            val locations = obj.optJSONArray("locations")
            if (locations != null && locations.length() > 0) {
                val loc = locations.optJSONObject(0)
                val country = loc?.optJSONObject("country")
                if (country != null) {
                    countryName = country.optString("name", "")
                    countryCode = country.optString("code", "")
                    val city = country.optJSONObject("city")
                    cityName = city?.optString("name", "").orEmpty()
                }
            }

            var wgPublicKey = ""
            val technologies = obj.optJSONArray("technologies")
            if (technologies != null) {
                for (t in 0 until technologies.length()) {
                    val tech = technologies.optJSONObject(t) ?: continue
                    if (tech.optString("identifier") == "wireguard_udp") {
                        val metadata = tech.optJSONArray("metadata")
                        if (metadata != null) {
                            for (m in 0 until metadata.length()) {
                                val meta = metadata.optJSONObject(m) ?: continue
                                if (meta.optString("name") == "public_key") {
                                    wgPublicKey = meta.optString("value", "")
                                    break
                                }
                            }
                        }
                        break
                    }
                }
            }

            result.add(
                NordServer(
                    id = id,
                    name = name,
                    hostname = hostname,
                    stationIp = station,
                    loadPercent = load,
                    countryName = countryName,
                    countryCode = countryCode,
                    cityName = cityName,
                    wireGuardPublicKey = wgPublicKey
                )
            )
        }
        return result
    }

    private fun httpGet(urlStr: String, authHeader: String? = null): String {
        val connection = (URL(urlStr).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "SwiftTunnel-Android/1.0")
            if (authHeader != null) {
                setRequestProperty("Authorization", authHeader)
            }
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }.orEmpty()
            if (code !in 200..299) {
                val hint = when (code) {
                    401, 403 -> "Invalid or expired Nord API key (HTTP $code)"
                    429 -> "Nord API rate limit reached (HTTP 429). Retry shortly."
                    else -> "Nord API error (HTTP $code)"
                }
                throw IllegalStateException(hint)
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256Short(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.take(6).joinToString(":") { "%02X".format(it) }
    }
}
