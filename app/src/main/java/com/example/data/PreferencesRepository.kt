package com.example.data

import android.content.Context
import com.example.model.ConnectionMode
import com.example.model.DnsPreset
import com.example.model.NordServer
import com.example.model.SavedVpnProfile
import com.example.model.SplitTunnelMode
import com.example.model.isValidIpv4Address
import org.json.JSONArray
import org.json.JSONObject

class PreferencesRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getConnectionMode(): ConnectionMode =
        ConnectionMode.fromId(prefs.getString(KEY_CONNECTION_MODE, null))

    fun setConnectionMode(mode: ConnectionMode) {
        prefs.edit().putString(KEY_CONNECTION_MODE, mode.id).apply()
    }

    fun getDnsPreset(): DnsPreset =
        DnsPreset.fromId(prefs.getString(KEY_DNS_PRESET, null))

    fun setDnsPreset(preset: DnsPreset) {
        prefs.edit().putString(KEY_DNS_PRESET, preset.id).apply()
    }

    fun getCustomDnsPrimary(): String =
        prefs.getString(KEY_CUSTOM_DNS_PRIMARY, "").orEmpty()

    fun getCustomDnsSecondary(): String =
        prefs.getString(KEY_CUSTOM_DNS_SECONDARY, "").orEmpty()

    fun setCustomDns(primary: String, secondary: String) {
        prefs.edit()
            .putString(KEY_CUSTOM_DNS_PRIMARY, primary.trim())
            .putString(KEY_CUSTOM_DNS_SECONDARY, secondary.trim())
            .apply()
    }

    fun isLanBypassEnabled(): Boolean =
        prefs.getBoolean(KEY_LAN_BYPASS, false)

    fun setLanBypassEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LAN_BYPASS, enabled).apply()
    }

    fun isAutoFailoverEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTO_FAILOVER, true)

    fun setAutoFailoverEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FAILOVER, enabled).apply()
    }

    fun getCustomMtu(): Int =
        prefs.getInt(KEY_CUSTOM_MTU, 1280).coerceIn(ConfigBackupCodec.MIN_MTU, ConfigBackupCodec.MAX_MTU)

    fun setCustomMtu(mtu: Int) {
        prefs.edit()
            .putInt(KEY_CUSTOM_MTU, mtu.coerceIn(ConfigBackupCodec.MIN_MTU, ConfigBackupCodec.MAX_MTU))
            .apply()
    }

    fun getSplitTunnelMode(): SplitTunnelMode =
        SplitTunnelMode.fromId(prefs.getString(KEY_SPLIT_MODE, null))

    fun setSplitTunnelMode(mode: SplitTunnelMode) {
        prefs.edit().putString(KEY_SPLIT_MODE, mode.id).apply()
    }

    fun getExcludedPackages(): Set<String> =
        prefs.getStringSet(KEY_EXCLUDED_APPS, emptySet())?.toSet() ?: emptySet()

    fun setExcludedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_EXCLUDED_APPS, HashSet(packages)).apply()
    }

    fun getIncludedPackages(): Set<String> =
        prefs.getStringSet(KEY_INCLUDED_APPS, emptySet())?.toSet() ?: emptySet()

    fun setIncludedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_INCLUDED_APPS, HashSet(packages)).apply()
    }

    fun getActiveSplitPackages(): Set<String> = when (getSplitTunnelMode()) {
        SplitTunnelMode.ALL_APPS -> emptySet()
        SplitTunnelMode.EXCLUDE_APPS -> getExcludedPackages()
        SplitTunnelMode.INCLUDE_ONLY_APPS -> getIncludedPackages()
    }

    fun getFavoriteCountryIds(): Set<Int> =
        prefs.getStringSet(KEY_FAVORITE_COUNTRIES, emptySet())
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    fun setFavoriteCountryIds(ids: Set<Int>) {
        prefs.edit()
            .putStringSet(KEY_FAVORITE_COUNTRIES, ids.map { it.toString() }.toHashSet())
            .apply()
    }

    fun getSelectedCountryId(): Int? {
        val v = prefs.getInt(KEY_SELECTED_COUNTRY_ID, -1)
        return if (v > 0) v else null
    }

    fun getSelectedCityName(): String? =
        prefs.getString(KEY_SELECTED_CITY_NAME, null)?.ifBlank { null }

    fun setSelectedLocation(countryId: Int?, cityName: String?) {
        prefs.edit()
            .putInt(KEY_SELECTED_COUNTRY_ID, countryId ?: -1)
            .putString(KEY_SELECTED_CITY_NAME, cityName.orEmpty())
            .apply()
    }

    fun saveLastActiveServer(server: NordServer?) {
        if (server == null) {
            prefs.edit().remove(KEY_LAST_SERVER_JSON).apply()
            return
        }
        val obj = JSONObject().apply {
            put("id", server.id)
            put("name", server.name)
            put("hostname", server.hostname)
            put("stationIp", server.stationIp)
            put("loadPercent", server.loadPercent)
            put("countryName", server.countryName)
            put("countryCode", server.countryCode)
            put("cityName", server.cityName)
            put("wireGuardPublicKey", server.wireGuardPublicKey)
        }
        prefs.edit().putString(KEY_LAST_SERVER_JSON, obj.toString()).apply()
    }

    fun getLastActiveServer(): NordServer? {
        val raw = prefs.getString(KEY_LAST_SERVER_JSON, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val hostname = obj.optString("hostname", "")
            val ip = obj.optString("stationIp", "")
            if (hostname.isEmpty() || ip.isEmpty()) return null
            NordServer(
                id = obj.optInt("id", 0),
                name = obj.optString("name", ""),
                hostname = hostname,
                stationIp = ip,
                loadPercent = obj.optInt("loadPercent", 0),
                countryName = obj.optString("countryName", ""),
                countryCode = obj.optString("countryCode", ""),
                cityName = obj.optString("cityName", ""),
                wireGuardPublicKey = obj.optString("wireGuardPublicKey", "")
            )
        }.getOrNull()
    }

    fun getSavedProfiles(): List<SavedVpnProfile> {
        val raw = prefs.getString(KEY_SAVED_PROFILES_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<SavedVpnProfile>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                val name = obj.optString("name", "").trim()
                if (id.isEmpty() || name.isEmpty()) continue
                val pkgsArr = obj.optJSONArray("splitPackages")
                val pkgs = LinkedHashSet<String>()
                if (pkgsArr != null) {
                    for (j in 0 until pkgsArr.length()) {
                        val pkg = pkgsArr.optString(j, "").trim()
                        if (pkg.isNotEmpty()) pkgs.add(pkg)
                    }
                }
                val cid = obj.optInt("countryId", -1)
                out.add(
                    SavedVpnProfile(
                        id = id,
                        name = name,
                        modeId = ConnectionMode.fromId(obj.optString("modeId")).id,
                        dnsPresetId = DnsPreset.fromId(obj.optString("dnsPresetId")).id,
                        customDnsPrimary = obj.optString("customDnsPrimary", "")
                            .takeIf { isValidIpv4Address(it) }.orEmpty(),
                        customDnsSecondary = obj.optString("customDnsSecondary", "")
                            .takeIf { isValidIpv4Address(it) }.orEmpty(),
                        countryId = if (cid > 0) cid else null,
                        cityName = obj.optString("cityName", "").ifBlank { null },
                        pinnedServerHostname = obj.optString("pinnedServerHostname", "").ifBlank { null },
                        pinnedServerCode = obj.optString("pinnedServerCode", "").ifBlank { null },
                        splitModeId = SplitTunnelMode.fromId(obj.optString("splitModeId")).id,
                        splitPackages = pkgs,
                        lanBypassEnabled = obj.optBoolean("lanBypassEnabled", false),
                        mtu = obj.optInt("mtu", 1280).coerceIn(ConfigBackupCodec.MIN_MTU, ConfigBackupCodec.MAX_MTU)
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    fun saveProfiles(profiles: List<SavedVpnProfile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("modeId", p.modeId)
                put("dnsPresetId", p.dnsPresetId)
                put("customDnsPrimary", p.customDnsPrimary)
                put("customDnsSecondary", p.customDnsSecondary)
                put("countryId", p.countryId ?: -1)
                put("cityName", p.cityName.orEmpty())
                put("pinnedServerHostname", p.pinnedServerHostname.orEmpty())
                put("pinnedServerCode", p.pinnedServerCode.orEmpty())
                put("splitModeId", p.splitModeId)
                put("splitPackages", JSONArray(p.splitPackages.toList()))
                put("lanBypassEnabled", p.lanBypassEnabled)
                put("mtu", p.mtu.coerceIn(ConfigBackupCodec.MIN_MTU, ConfigBackupCodec.MAX_MTU))
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_PROFILES_JSON, arr.toString()).apply()
    }

    fun exportConfigBackup(): AppConfigBackup = AppConfigBackup(
        connectionModeId = getConnectionMode().id,
        dnsPresetId = getDnsPreset().id,
        customDnsPrimary = getCustomDnsPrimary(),
        customDnsSecondary = getCustomDnsSecondary(),
        selectedCountryId = getSelectedCountryId(),
        selectedCityName = getSelectedCityName(),
        pinnedServerHostname = getPinnedServerHostname(),
        favoriteCountryIds = getFavoriteCountryIds(),
        splitModeId = getSplitTunnelMode().id,
        excludedPackages = getExcludedPackages(),
        includedPackages = getIncludedPackages(),
        autoConnectOnBoot = isAutoConnectOnBootEnabled(),
        lanBypassEnabled = isLanBypassEnabled(),
        autoFailoverEnabled = isAutoFailoverEnabled(),
        customMtu = getCustomMtu(),
        savedProfiles = getSavedProfiles()
    )

    fun importConfigBackup(backup: AppConfigBackup) {
        setConnectionMode(ConnectionMode.fromId(backup.connectionModeId))
        setDnsPreset(DnsPreset.fromId(backup.dnsPresetId))
        setCustomDns(backup.customDnsPrimary, backup.customDnsSecondary)
        setSelectedLocation(backup.selectedCountryId, backup.selectedCityName)
        setPinnedServerHostname(backup.pinnedServerHostname)
        setFavoriteCountryIds(backup.favoriteCountryIds)
        setSplitTunnelMode(SplitTunnelMode.fromId(backup.splitModeId))
        setExcludedPackages(backup.excludedPackages)
        setIncludedPackages(backup.includedPackages)
        setAutoConnectOnBootEnabled(backup.autoConnectOnBoot)
        setLanBypassEnabled(backup.lanBypassEnabled)
        setAutoFailoverEnabled(backup.autoFailoverEnabled)
        setCustomMtu(backup.customMtu)
        saveProfiles(backup.savedProfiles)
    }

    fun setTunnelWantedActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_TUNNEL_WANTED_ACTIVE, active).apply()
    }

    fun isTunnelWantedActive(): Boolean =
        prefs.getBoolean(KEY_TUNNEL_WANTED_ACTIVE, false)

    fun isAutoConnectOnBootEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTO_CONNECT_ON_BOOT, true)

    fun setAutoConnectOnBootEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CONNECT_ON_BOOT, enabled).apply()
    }

    fun getPinnedServerHostname(): String? =
        prefs.getString(KEY_PINNED_SERVER_HOSTNAME, null)?.ifBlank { null }

    fun setPinnedServerHostname(hostname: String?) {
        prefs.edit().putString(KEY_PINNED_SERVER_HOSTNAME, hostname.orEmpty()).apply()
    }

    fun getCachedCountriesJson(): String? =
        prefs.getString(KEY_CACHED_COUNTRIES_JSON, null)

    fun saveCachedCountriesJson(rawJson: String) {
        prefs.edit()
            .putString(KEY_CACHED_COUNTRIES_JSON, rawJson)
            .putLong(KEY_CACHED_COUNTRIES_TS, System.currentTimeMillis())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "swifttunnel_prefs"
        private const val KEY_CONNECTION_MODE = "connection_mode"
        private const val KEY_DNS_PRESET = "dns_preset"
        private const val KEY_CUSTOM_DNS_PRIMARY = "custom_dns_primary"
        private const val KEY_CUSTOM_DNS_SECONDARY = "custom_dns_secondary"
        private const val KEY_LAN_BYPASS = "lan_bypass"
        private const val KEY_AUTO_FAILOVER = "auto_failover"
        private const val KEY_CUSTOM_MTU = "custom_mtu"
        private const val KEY_SAVED_PROFILES_JSON = "saved_profiles_json"
        private const val KEY_SPLIT_MODE = "split_mode"
        private const val KEY_EXCLUDED_APPS = "excluded_apps"
        private const val KEY_INCLUDED_APPS = "included_apps"
        private const val KEY_FAVORITE_COUNTRIES = "favorite_countries"
        private const val KEY_SELECTED_COUNTRY_ID = "selected_country_id"
        private const val KEY_SELECTED_CITY_NAME = "selected_city_name"
        private const val KEY_LAST_SERVER_JSON = "last_server_json"
        private const val KEY_TUNNEL_WANTED_ACTIVE = "tunnel_wanted_active"
        private const val KEY_AUTO_CONNECT_ON_BOOT = "auto_connect_on_boot"
        private const val KEY_PINNED_SERVER_HOSTNAME = "pinned_server_hostname"
        private const val KEY_CACHED_COUNTRIES_JSON = "cached_countries_json"
        private const val KEY_CACHED_COUNTRIES_TS = "cached_countries_ts"
    }
}
