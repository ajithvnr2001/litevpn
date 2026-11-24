package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ApiKeySource
import com.example.data.InstalledAppsRepository
import com.example.data.NordApiClient
import com.example.data.NordCountryCatalog
import com.example.data.PreferencesRepository
import com.example.data.SecureKeyStorage
import com.example.data.ServerCodeMatcher
import com.example.model.ConnectionMode
import com.example.model.InstalledAppInfo
import com.example.model.NetworkTraceInfo
import com.example.model.NordCountry
import com.example.model.NordCredentialsState
import com.example.model.NordServer
import com.example.model.RegionFilter
import com.example.model.SplitTunnelMode
import com.example.model.TunnelStatus
import com.example.model.VpnSessionState
import com.example.vpn.SwiftTunnelVpnService
import com.example.vpn.VpnKeyBridge
import com.example.vpn.VpnStateHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppTab(val route: String) {
    CONNECT("connect"),
    LOCATIONS("locations"),
    SPLIT_TUNNEL("split_tunnel")
}

data class MainUiState(
    val selectedTab: AppTab = AppTab.CONNECT,
    val selectedMode: ConnectionMode = ConnectionMode.CLOUDFLARE_PLUS_VPN,
    val vpnSession: VpnSessionState = VpnSessionState(),
    val elapsedSeconds: Long = 0L,
    val countries: List<NordCountry> = emptyList(),
    val isLiveDirectorySynced: Boolean = false,
    val isLoadingCountries: Boolean = false,
    val countriesError: String? = null,
    val locationQuery: String = "",
    val selectedRegion: RegionFilter = RegionFilter.ALL,
    val favoriteCountryIds: Set<Int> = emptySet(),
    val selectedCountryId: Int? = null,
    val selectedCityName: String? = null,
    val recommendedServer: NordServer? = null,
    val nearestServers: List<NordServer> = emptyList(),
    val browsedCountryId: Int? = null,
    val browsedServers: List<NordServer> = emptyList(),
    val isLoadingBrowsedServers: Boolean = false,
    val activeServerCodeQuery: String? = null,
    val serverCodeResults: List<NordServer> = emptyList(),
    val isSearchingServerCode: Boolean = false,
    val serverCodeSearchError: String? = null,
    val indexedServerCount: Int = 0,
    val isLoadingServer: Boolean = false,
    val serverError: String? = null,
    val measuredLatencyMs: Int? = null,
    val credentialsState: NordCredentialsState = NordCredentialsState.NotConfigured,
    val apiKeySource: ApiKeySource = ApiKeySource.NONE,
    val maskedApiKeyPreview: String? = null,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val autoConnectOnBoot: Boolean = true,
    val networkTrace: NetworkTraceInfo? = null,
    val splitMode: SplitTunnelMode = SplitTunnelMode.ALL_APPS,
    val excludedPackages: Set<String> = emptySet(),
    val includedPackages: Set<String> = emptySet(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = false,
    val appSearchQuery: String = "",
    val showSelectedAppsOnly: Boolean = false,
    val hasPendingReconfigure: Boolean = false,
    val snackbarMessage: String? = null
) {
    val activeSplitPackages: Set<String>
        get() = when (splitMode) {
            SplitTunnelMode.ALL_APPS -> emptySet()
            SplitTunnelMode.EXCLUDE_APPS -> excludedPackages
            SplitTunnelMode.INCLUDE_ONLY_APPS -> includedPackages
        }

    val isConnected: Boolean
        get() = vpnSession.status == TunnelStatus.CONNECTED

    val isBusy: Boolean
        get() = vpnSession.status == TunnelStatus.CONNECTING ||
            vpnSession.status == TunnelStatus.DISCONNECTING
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = NordApiClient()
    private val prefs = PreferencesRepository(application)
    private val keyStorage = SecureKeyStorage(application)
    private val appsRepo = InstalledAppsRepository(application)

    @Volatile
    private var inMemoryNordLynxPrivateKey: String? = null

    private val _uiState = MutableStateFlow(
        run {
            val keyInfo = keyStorage.getActiveKeyInfo()
            MainUiState(
                selectedMode = prefs.getConnectionMode(),
                countries = NordCountryCatalog.fallbackCountries,
                favoriteCountryIds = prefs.getFavoriteCountryIds(),
                selectedCountryId = prefs.getSelectedCountryId(),
                selectedCityName = prefs.getSelectedCityName(),
                recommendedServer = prefs.getLastActiveServer(),
                apiKeySource = keyInfo.source,
                maskedApiKeyPreview = keyInfo.maskedPreview,
                isIgnoringBatteryOptimizations = checkBatteryOptimizationExempt(application),
                autoConnectOnBoot = prefs.isAutoConnectOnBootEnabled(),
                splitMode = prefs.getSplitTunnelMode(),
                excludedPackages = prefs.getExcludedPackages(),
                includedPackages = prefs.getIncludedPackages(),
                vpnSession = VpnStateHolder.sessionState.value
            )
        }
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var serverCodeSearchJob: Job? = null

    init {
        observeVpnSession()
        loadInitialData()
    }

    private fun observeVpnSession() {
        viewModelScope.launch {
            VpnStateHolder.sessionState.collect { session ->
                _uiState.update { state ->
                    val stillPending = state.hasPendingReconfigure &&
                        session.status == TunnelStatus.CONNECTED &&
                        (session.mode != state.selectedMode ||
                            session.splitMode != state.splitMode ||
                            session.splitAppCount != state.activeSplitPackages.size ||
                            (state.selectedMode.usesVpn &&
                                state.recommendedServer != null &&
                                session.activeServer?.hostname != state.recommendedServer.hostname))
                    state.copy(
                        vpnSession = session,
                        hasPendingReconfigure = stillPending
                    )
                }
                if (session.status == TunnelStatus.CONNECTED && session.connectedAtMillis > 0L) {
                    startElapsedTimer(session.connectedAtMillis)
                } else {
                    stopElapsedTimer()
                }
            }
        }
    }

    private fun startElapsedTimer(connectedAt: Long) {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (isActive) {
                val secs = ((System.currentTimeMillis() - connectedAt) / 1000L).coerceAtLeast(0L)
                _uiState.update { it.copy(elapsedSeconds = secs) }
                delay(1_000L)
            }
        }
    }

    private fun stopElapsedTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(elapsedSeconds = 0L) }
    }

    private fun loadInitialData() {
        prefs.getCachedCountriesJson()?.let { cached ->
            runCatching { apiClient.parseCountriesJson(cached) }.getOrNull()?.let { parsed ->
                if (parsed.isNotEmpty()) {
                    _uiState.update { it.copy(countries = parsed, isLiveDirectorySynced = true) }
                }
            }
        }

        refreshCountries()
        refreshNearestServers()
        refreshRecommendedServer(
            countryId = _uiState.value.selectedCountryId,
            cityName = _uiState.value.selectedCityName
        )
        verifyNordCredentials()
        refreshNetworkTrace()
        loadInstalledApps()
    }

    fun setAutoConnectOnBoot(enabled: Boolean) {
        prefs.setAutoConnectOnBootEnabled(enabled)
        _uiState.update { it.copy(autoConnectOnBoot = enabled) }
        showMessage(
            if (enabled) "Always-On boot auto-connect enabled for your pointed location"
            else "Auto-connect on device power-up disabled"
        )
    }

    fun refreshNearestServers() {
        viewModelScope.launch {
            apiClient.fetchServersForLocation(countryId = null, preferredCityName = null, limit = 6)
                .onSuccess { list ->
                    _uiState.update { it.copy(nearestServers = list) }
                }
        }
    }

    fun loadServersForCountry(countryId: Int) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    browsedCountryId = countryId,
                    browsedServers = emptyList(),
                    isLoadingBrowsedServers = true
                )
            }
            apiClient.fetchServersForLocation(countryId = countryId, preferredCityName = null, limit = 15)
                .onSuccess { servers ->
                    _uiState.update {
                        it.copy(
                            browsedCountryId = countryId,
                            browsedServers = servers,
                            isLoadingBrowsedServers = false
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoadingBrowsedServers = false) }
                    showMessage(err.message ?: "Could not load live servers for this country")
                }
        }
    }

    fun selectSpecificServer(
        server: NordServer,
        countryId: Int?,
        switchToConnect: Boolean = true,
        onReadyToConnect: (() -> Unit)? = null
    ) {
        prefs.setSelectedLocation(countryId, server.cityName.ifBlank { null })
        prefs.setPinnedServerHostname(server.hostname)
        prefs.saveLastActiveServer(server)
        _uiState.update { state ->
            state.copy(
                selectedCountryId = countryId,
                selectedCityName = server.cityName.ifBlank { null },
                recommendedServer = server,
                isLoadingServer = false,
                serverError = null,
                selectedTab = if (switchToConnect) AppTab.CONNECT else state.selectedTab,
                hasPendingReconfigure = state.isConnected && state.selectedMode.usesVpn && onReadyToConnect == null
            )
        }
        probeActiveLatency()
        onReadyToConnect?.invoke()
    }

    fun selectLocationAndConnect(
        countryId: Int?,
        cityName: String?,
        onTriggerConnect: () -> Unit
    ) {
        prefs.setSelectedLocation(countryId, cityName)
        prefs.setPinnedServerHostname(null)
        prefs.saveLastActiveServer(null)
        _uiState.update { state ->
            state.copy(
                selectedCountryId = countryId,
                selectedCityName = cityName,
                recommendedServer = null,
                isLoadingServer = true,
                serverError = null,
                selectedTab = AppTab.CONNECT
            )
        }
        viewModelScope.launch {
            apiClient.fetchRecommendedServer(countryId, cityName)
                .onSuccess { server ->
                    prefs.saveLastActiveServer(server)
                    _uiState.update {
                        it.copy(
                            recommendedServer = server,
                            isLoadingServer = false,
                            serverError = null
                        )
                    }
                    probeActiveLatency()
                    onTriggerConnect()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingServer = false,
                            serverError = err.message ?: "Could not fetch Nord server"
                        )
                    }
                    showMessage(err.message ?: "Could not resolve Nord server for location")
                }
        }
    }

    fun refreshBatteryOptimizationStatus(context: Context) {
        val exempt = checkBatteryOptimizationExempt(context)
        _uiState.update { it.copy(isIgnoringBatteryOptimizations = exempt) }
    }

    private fun checkBatteryOptimizationExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectConnectionMode(mode: ConnectionMode) {
        prefs.setConnectionMode(mode)
        _uiState.update { state ->
            val dirty = state.isConnected && state.vpnSession.mode != mode
            state.copy(
                selectedMode = mode,
                hasPendingReconfigure = dirty || state.hasPendingReconfigure
            )
        }
        probeActiveLatency()
    }

    fun refreshCountries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCountries = true, countriesError = null) }
            apiClient.fetchAllCountriesRaw()
                .onSuccess { rawJson ->
                    val parsed = runCatching { apiClient.parseCountriesJson(rawJson) }.getOrNull()
                    if (!parsed.isNullOrEmpty()) {
                        prefs.saveCachedCountriesJson(rawJson)
                        _uiState.update {
                            it.copy(
                                countries = parsed,
                                isLiveDirectorySynced = true,
                                isLoadingCountries = false,
                                countriesError = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoadingCountries = false,
                                countriesError = "Could not parse live Nord server directory"
                            )
                        }
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingCountries = false,
                            countriesError = err.message ?: "Unable to sync live Nord directory"
                        )
                    }
                }
        }
    }

    fun refreshLocationsDirectory() {
        apiClient.clearServerCatalogCache()
        refreshCountries()
        refreshNearestServers()
        if (_uiState.value.activeServerCodeQuery != null) {
            retryServerCodeSearch(forceRefresh = true)
        }
    }

    fun updateLocationQuery(query: String) {
        val parsed = ServerCodeMatcher.parseQuery(query)
        serverCodeSearchJob?.cancel()
        if (parsed == null) {
            _uiState.update {
                it.copy(
                    locationQuery = query,
                    activeServerCodeQuery = null,
                    serverCodeResults = emptyList(),
                    isSearchingServerCode = false,
                    serverCodeSearchError = null
                )
            }
            return
        }

        val trimmed = query.trim()
        _uiState.update {
            it.copy(
                locationQuery = query,
                activeServerCodeQuery = trimmed,
                isSearchingServerCode = true,
                serverCodeSearchError = null
            )
        }

        val debounceMs = if (apiClient.hasCachedServerCatalog()) 60L else 240L
        serverCodeSearchJob = viewModelScope.launch {
            delay(debounceMs)
            executeServerCodeSearch(parsed, forceRefresh = false)
        }
    }

    fun retryServerCodeSearch(forceRefresh: Boolean = true) {
        val query = _uiState.value.locationQuery
        val parsed = ServerCodeMatcher.parseQuery(query) ?: return
        serverCodeSearchJob?.cancel()
        _uiState.update {
            it.copy(
                activeServerCodeQuery = query.trim(),
                isSearchingServerCode = true,
                serverCodeSearchError = null
            )
        }
        serverCodeSearchJob = viewModelScope.launch {
            executeServerCodeSearch(parsed, forceRefresh = forceRefresh)
        }
    }

    private suspend fun executeServerCodeSearch(
        parsed: com.example.data.ParsedServerCodeQuery,
        forceRefresh: Boolean
    ) {
        apiClient.searchServersByCode(parsed, forceRefresh = forceRefresh)
            .onSuccess { res ->
                _uiState.update {
                    it.copy(
                        serverCodeResults = res.matches,
                        indexedServerCount = res.totalIndexedServers,
                        isSearchingServerCode = false,
                        serverCodeSearchError = null
                    )
                }
            }
            .onFailure { err ->
                _uiState.update {
                    it.copy(
                        serverCodeResults = emptyList(),
                        isSearchingServerCode = false,
                        serverCodeSearchError = err.message ?: "Could not search NordVPN server directory"
                    )
                }
            }
    }

    fun selectRegionFilter(region: RegionFilter) {
        _uiState.update { it.copy(selectedRegion = region) }
    }

    fun toggleFavoriteCountry(countryId: Int) {
        val updated = _uiState.value.favoriteCountryIds.toMutableSet()
        if (!updated.add(countryId)) {
            updated.remove(countryId)
        }
        prefs.setFavoriteCountryIds(updated)
        _uiState.update { it.copy(favoriteCountryIds = updated) }
    }

    fun selectLocation(countryId: Int?, cityName: String?, switchToConnect: Boolean = true) {
        prefs.setSelectedLocation(countryId, cityName)
        prefs.saveLastActiveServer(null)
        _uiState.update { state ->
            state.copy(
                selectedCountryId = countryId,
                selectedCityName = cityName,
                recommendedServer = null,
                selectedTab = if (switchToConnect) AppTab.CONNECT else state.selectedTab,
                hasPendingReconfigure = state.isConnected && state.selectedMode.usesVpn
            )
        }
        refreshRecommendedServer(countryId, cityName)
    }

    fun refreshRecommendedServer(
        countryId: Int? = _uiState.value.selectedCountryId,
        cityName: String? = _uiState.value.selectedCityName
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingServer = true, serverError = null) }
            apiClient.fetchRecommendedServer(countryId, cityName)
                .onSuccess { server ->
                    prefs.saveLastActiveServer(server)
                    _uiState.update {
                        it.copy(
                            recommendedServer = server,
                            isLoadingServer = false,
                            serverError = null
                        )
                    }
                    probeActiveLatency()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingServer = false,
                            serverError = err.message ?: "Could not fetch Nord server"
                        )
                    }
                }
        }
    }

    fun saveAndVerifyApiKey(rawKey: String) {
        val cleaned = rawKey.trim()
        if (cleaned.length < 16) {
            showMessage("Please enter a valid Nord access token")
            return
        }
        val saved = keyStorage.saveCustomKey(cleaned)
        if (!saved) {
            showMessage("Could not encrypt API key in Android Keystore")
            return
        }
        val info = keyStorage.getActiveKeyInfo()
        _uiState.update {
            it.copy(
                apiKeySource = info.source,
                maskedApiKeyPreview = info.maskedPreview
            )
        }
        showMessage("API key saved securely — verifying with Nord…")
        verifyNordCredentials()
    }

    fun clearCustomApiKey() {
        keyStorage.clearCustomKey()
        inMemoryNordLynxPrivateKey = null
        VpnKeyBridge.nordLynxPrivateKey = null
        val info = keyStorage.getActiveKeyInfo()
        _uiState.update {
            it.copy(
                apiKeySource = info.source,
                maskedApiKeyPreview = info.maskedPreview
            )
        }
        showMessage("Custom API key removed")
        verifyNordCredentials()
    }

    fun verifyNordCredentials() {
        val keyInfo = keyStorage.getActiveKeyInfo()
        _uiState.update {
            it.copy(
                apiKeySource = keyInfo.source,
                maskedApiKeyPreview = keyInfo.maskedPreview
            )
        }
        if (keyInfo.key.isNullOrBlank()) {
            inMemoryNordLynxPrivateKey = null
            VpnKeyBridge.nordLynxPrivateKey = null
            _uiState.update { it.copy(credentialsState = NordCredentialsState.NotConfigured) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(credentialsState = NordCredentialsState.Loading) }
            apiClient.fetchServiceCredentials(keyInfo.key)
                .onSuccess { result ->
                    inMemoryNordLynxPrivateKey = result.nordLynxPrivateKey
                    VpnKeyBridge.nordLynxPrivateKey = result.nordLynxPrivateKey
                    _uiState.update {
                        it.copy(
                            credentialsState = NordCredentialsState.Verified(
                                keyFingerprint = result.keyFingerprint,
                                hasPrivateKey = result.nordLynxPrivateKey != null,
                                serviceUsernameMasked = result.serviceUsernameMasked
                            )
                        )
                    }
                }
                .onFailure { err ->
                    inMemoryNordLynxPrivateKey = null
                    VpnKeyBridge.nordLynxPrivateKey = null
                    _uiState.update {
                        it.copy(
                            credentialsState = NordCredentialsState.Error(
                                err.message ?: "Credential verification failed"
                            )
                        )
                    }
                }
        }
    }

    fun refreshNetworkTrace() {
        viewModelScope.launch {
            apiClient.fetchNetworkTrace()
                .onSuccess { trace ->
                    _uiState.update { it.copy(networkTrace = trace) }
                }
        }
    }

    private fun probeActiveLatency() {
        viewModelScope.launch {
            val state = _uiState.value
            val targetHost = if (state.selectedMode.usesVpn && state.recommendedServer != null) {
                state.recommendedServer.stationIp
            } else {
                state.selectedMode.primaryDns
            }
            val ms = apiClient.measureLatencyMs(targetHost, 443)
                ?: apiClient.measureLatencyMs(state.selectedMode.primaryDns, 443)
            _uiState.update { it.copy(measuredLatencyMs = ms) }
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = appsRepo.loadLaunchableApps()
            _uiState.update { it.copy(installedApps = apps, isLoadingApps = false) }
        }
    }

    fun setSplitTunnelMode(mode: SplitTunnelMode) {
        prefs.setSplitTunnelMode(mode)
        _uiState.update { state ->
            state.copy(
                splitMode = mode,
                hasPendingReconfigure = state.isConnected
            )
        }
    }

    fun toggleAppInSplitList(packageName: String) {
        val state = _uiState.value
        when (state.splitMode) {
            SplitTunnelMode.ALL_APPS -> {
                val updated = state.excludedPackages.toMutableSet().apply { add(packageName) }
                prefs.setSplitTunnelMode(SplitTunnelMode.EXCLUDE_APPS)
                prefs.setExcludedPackages(updated)
                _uiState.update {
                    it.copy(
                        splitMode = SplitTunnelMode.EXCLUDE_APPS,
                        excludedPackages = updated,
                        hasPendingReconfigure = it.isConnected
                    )
                }
            }
            SplitTunnelMode.EXCLUDE_APPS -> {
                val updated = state.excludedPackages.toMutableSet()
                if (!updated.add(packageName)) updated.remove(packageName)
                prefs.setExcludedPackages(updated)
                _uiState.update {
                    it.copy(
                        excludedPackages = updated,
                        hasPendingReconfigure = it.isConnected
                    )
                }
            }
            SplitTunnelMode.INCLUDE_ONLY_APPS -> {
                val updated = state.includedPackages.toMutableSet()
                if (!updated.add(packageName)) updated.remove(packageName)
                prefs.setIncludedPackages(updated)
                _uiState.update {
                    it.copy(
                        includedPackages = updated,
                        hasPendingReconfigure = it.isConnected
                    )
                }
            }
        }
    }

    fun clearActiveSplitApps() {
        val state = _uiState.value
        when (state.splitMode) {
            SplitTunnelMode.EXCLUDE_APPS -> {
                prefs.setExcludedPackages(emptySet())
                _uiState.update {
                    it.copy(excludedPackages = emptySet(), hasPendingReconfigure = it.isConnected)
                }
            }
            SplitTunnelMode.INCLUDE_ONLY_APPS -> {
                prefs.setIncludedPackages(emptySet())
                _uiState.update {
                    it.copy(includedPackages = emptySet(), hasPendingReconfigure = it.isConnected)
                }
            }
            SplitTunnelMode.ALL_APPS -> Unit
        }
    }

    fun updateAppSearchQuery(query: String) {
        _uiState.update { it.copy(appSearchQuery = query) }
    }

    fun toggleShowSelectedAppsOnly() {
        _uiState.update { it.copy(showSelectedAppsOnly = !it.showSelectedAppsOnly) }
    }

    fun validateBeforeConnect(): String? {
        val state = _uiState.value
        if (state.splitMode == SplitTunnelMode.INCLUDE_ONLY_APPS && state.includedPackages.isEmpty()) {
            return "Select at least 1 app in Split Apps (Include Only) before connecting."
        }
        if (state.selectedMode.usesVpn && keyStorage.getActiveKeyInfo().key.isNullOrBlank()) {
            return "Tap Swap API Key to add your Nord token before connecting to VPN."
        }
        return null
    }

    fun startVpnService(context: Context) {
        val validationError = validateBeforeConnect()
        if (validationError != null) {
            showMessage(validationError)
            return
        }
        val state = _uiState.value
        VpnKeyBridge.nordLynxPrivateKey = inMemoryNordLynxPrivateKey
        val intent = SwiftTunnelVpnService.createConnectIntent(
            context = context,
            mode = state.selectedMode,
            splitMode = state.splitMode,
            splitPackages = state.activeSplitPackages,
            server = state.recommendedServer,
            latencyMs = state.measuredLatencyMs
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _uiState.update { it.copy(hasPendingReconfigure = false) }
        refreshNetworkTrace()
    }

    fun stopVpnService(context: Context) {
        val intent = SwiftTunnelVpnService.createDisconnectIntent(context)
        context.startService(intent)
        _uiState.update { it.copy(hasPendingReconfigure = false) }
    }

    fun buildWireGuardConfigText(): String? {
        val state = _uiState.value
        val server = state.recommendedServer ?: return null
        val privateKey = inMemoryNordLynxPrivateKey ?: "<SET_NORD_API_KEY_IN_APP>"
        return buildString {
            appendLine("# SwiftTunnel NordLynx Profile • ${server.countryName} (${server.hostname})")
            appendLine("[Interface]")
            appendLine("PrivateKey = $privateKey")
            appendLine("Address = 10.5.0.2/32")
            appendLine("DNS = ${state.selectedMode.primaryDns}, ${state.selectedMode.secondaryDns}")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${server.wireGuardPublicKey.ifBlank { "<UNAVAILABLE>" }}")
            appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
            appendLine("Endpoint = ${server.stationIp}:51820")
            appendLine("PersistentKeepalive = 25")
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
