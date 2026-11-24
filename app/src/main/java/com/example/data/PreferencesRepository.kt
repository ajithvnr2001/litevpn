package com.example.data

import android.content.Context
import com.example.model.ConnectionMode
import com.example.model.NordServer
import com.example.model.SplitTunnelMode
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
