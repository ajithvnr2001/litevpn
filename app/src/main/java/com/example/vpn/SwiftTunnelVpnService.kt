package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ConfigBackupCodec
import com.example.data.NordApiClient
import com.example.data.PreferencesRepository
import com.example.data.SecureKeyStorage
import com.example.model.ConnectionMode
import com.example.model.NordServer
import com.example.model.SplitTunnelMode
import com.example.model.TunnelStatus
import com.example.model.resolveEffectiveDns
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress

class SwiftTunnelVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: PreferencesRepository
    private lateinit var keyStorage: SecureKeyStorage
    private val apiClient = NordApiClient()

    private var tunInterface: ParcelFileDescriptor? = null
    private var dnsForwarder: DnsPacketForwarder? = null
    private var wireGuardEngine: WireGuardTunnelEngine? = null
    private var multiPeerEngine: MultiPeerWireGuardEngine? = null
    private var connectJob: Job? = null
    private var handoverCallback: ConnectivityManager.NetworkCallback? = null
    private var handoverReconnectJob: Job? = null
    @Volatile
    private var activeUnderlyingNetHandle: Long = -1L

    private data class ActiveTunnelConfig(
        val mode: ConnectionMode,
        val primaryDns: String,
        val secondaryDns: String,
        val splitMode: SplitTunnelMode,
        val splitPackages: Set<String>,
        val server: NordServer?,
        val lanBypassEnabled: Boolean,
        val autoFailoverEnabled: Boolean,
        val mtu: Int,
        val latencyMs: Int?
    )

    @Volatile
    private var currentConfig: ActiveTunnelConfig? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesRepository(this)
        keyStorage = SecureKeyStorage(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (intent == null || action == SERVICE_INTERFACE || action == ACTION_BOOT_AUTOCONNECT) {
            val shouldRestore = action == SERVICE_INTERFACE ||
                action == ACTION_BOOT_AUTOCONNECT ||
                prefs.isTunnelWantedActive() ||
                prefs.isAutoConnectOnBootEnabled()
            if (shouldRestore) {
                val restoredMode = prefs.getConnectionMode()
                val (pDns, sDns) = resolveEffectiveDns(
                    mode = restoredMode,
                    preset = prefs.getDnsPreset(),
                    customPrimary = prefs.getCustomDnsPrimary(),
                    customSecondary = prefs.getCustomDnsSecondary()
                )
                val cfg = ActiveTunnelConfig(
                    mode = restoredMode,
                    primaryDns = pDns,
                    secondaryDns = sDns,
                    splitMode = prefs.getSplitTunnelMode(),
                    splitPackages = prefs.getActiveSplitPackages(),
                    server = prefs.getLastActiveServer(),
                    lanBypassEnabled = prefs.isLanBypassEnabled(),
                    autoFailoverEnabled = prefs.isAutoFailoverEnabled(),
                    mtu = prefs.getCustomMtu(),
                    latencyMs = null
                )
                startConnectAsync(cfg, waitForNetwork = true)
                return START_STICKY
            }
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_DISCONNECT -> {
                disconnectTunnel()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val mode = ConnectionMode.fromId(intent.getStringExtra(EXTRA_MODE))
                val (defaultPrimary, defaultSecondary) = resolveEffectiveDns(
                    mode = mode,
                    preset = prefs.getDnsPreset(),
                    customPrimary = prefs.getCustomDnsPrimary(),
                    customSecondary = prefs.getCustomDnsSecondary()
                )
                val primaryDns = intent.getStringExtra(EXTRA_PRIMARY_DNS)?.ifBlank { null } ?: defaultPrimary
                val secondaryDns = intent.getStringExtra(EXTRA_SECONDARY_DNS)?.ifBlank { null } ?: defaultSecondary
                val splitMode = SplitTunnelMode.fromId(intent.getStringExtra(EXTRA_SPLIT_MODE))
                val splitPackages = intent.getStringArrayListExtra(EXTRA_SPLIT_PACKAGES)
                    ?.toSet()
                    ?: emptySet()
                val server = extractServer(intent) ?: prefs.getLastActiveServer()
                val latencyMs = intent.getIntExtra(EXTRA_LATENCY_MS, -1).takeIf { it > 0 }
                val lanBypass = intent.getBooleanExtra(EXTRA_LAN_BYPASS, prefs.isLanBypassEnabled())
                val autoFailover = intent.getBooleanExtra(EXTRA_AUTO_FAILOVER, prefs.isAutoFailoverEnabled())
                val mtu = intent.getIntExtra(EXTRA_MTU, prefs.getCustomMtu())
                    .coerceIn(ConfigBackupCodec.MIN_MTU, ConfigBackupCodec.MAX_MTU)

                val cfg = ActiveTunnelConfig(
                    mode = mode,
                    primaryDns = primaryDns,
                    secondaryDns = secondaryDns,
                    splitMode = splitMode,
                    splitPackages = splitPackages,
                    server = server,
                    lanBypassEnabled = lanBypass,
                    autoFailoverEnabled = autoFailover,
                    mtu = mtu,
                    latencyMs = latencyMs
                )
                startConnectAsync(cfg, waitForNetwork = false)
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startConnectAsync(
        config: ActiveTunnelConfig,
        waitForNetwork: Boolean
    ) {
        teardownActiveInterface()
        currentConfig = config
        prefs.setTunnelWantedActive(true)
        if (config.server != null) {
            prefs.saveLastActiveServer(config.server)
        }

        VpnStateHolder.update {
            it.copy(
                status = TunnelStatus.CONNECTING,
                mode = config.mode,
                primaryDns = config.primaryDns,
                secondaryDns = config.secondaryDns,
                activeServer = if (config.mode.usesVpn) config.server else null,
                splitMode = config.splitMode,
                splitAppCount = config.splitPackages.size,
                latencyMs = config.latencyMs,
                isWireGuardVerified = false,
                lanBypassEnabled = config.lanBypassEnabled,
                mtu = config.mtu,
                errorMessage = null
            )
        }

        ensureNotificationChannel()
        val initialNotification = buildNotification(
            mode = config.mode,
            primaryDns = config.primaryDns,
            server = config.server,
            splitMode = config.splitMode,
            splitCount = config.splitPackages.size,
            connecting = true
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
        notifyQuickSettingsTile()

        connectJob = serviceScope.launch {
            val maxAttempts = if (waitForNetwork) 5 else 1
            var lastError: Exception? = null

            if (waitForNetwork) {
                waitForNetworkAvailable(timeoutMs = 45_000L)
            }

            for (attempt in 1..maxAttempts) {
                try {
                    if (config.splitMode == SplitTunnelMode.INCLUDE_ONLY_APPS && config.splitPackages.isEmpty()) {
                        throw IllegalStateException("Select at least 1 app in Split Apps (Include Only) before connecting.")
                    }

                    if (config.mode.usesVpn) {
                        establishWireGuardTunnel(config)
                    } else {
                        establishDnsOnlyTunnel(config)
                    }
                    registerNetworkHandoverMonitor()
                    notifyQuickSettingsTile()
                    return@launch
                } catch (e: Exception) {
                    lastError = e
                    teardownActiveInterface(cancelConnectJob = false)
                    if (attempt < maxAttempts) {
                        delay(3_000L * attempt)
                    }
                }
            }

            prefs.setTunnelWantedActive(false)
            VpnStateHolder.update {
                it.copy(
                    status = TunnelStatus.ERROR,
                    isWireGuardVerified = false,
                    errorMessage = lastError?.message ?: "Failed to establish tunnel"
                )
            }
            notifyQuickSettingsTile()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun registerNetworkHandoverMonitor() {
        unregisterNetworkHandoverMonitor()
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        activeUnderlyingNetHandle = cm.activeNetwork?.networkHandle ?: -1L
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val handle = network.networkHandle
                if (activeUnderlyingNetHandle == -1L) {
                    activeUnderlyingNetHandle = handle
                    return
                }
                if (handle != activeUnderlyingNetHandle && prefs.isTunnelWantedActive()) {
                    activeUnderlyingNetHandle = handle
                    val cfg = currentConfig ?: return
                    if (!cfg.autoFailoverEnabled) return
                    handoverReconnectJob?.cancel()
                    handoverReconnectJob = serviceScope.launch {
                        delay(1_500L)
                        if (prefs.isTunnelWantedActive()) {
                            startConnectAsync(cfg, waitForNetwork = true)
                        }
                    }
                }
            }
        }
        handoverCallback = callback
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    private fun unregisterNetworkHandoverMonitor() {
        handoverReconnectJob?.cancel()
        handoverReconnectJob = null
        val cb = handoverCallback ?: return
        handoverCallback = null
        val cm = getSystemService(ConnectivityManager::class.java)
        runCatching { cm?.unregisterNetworkCallback(cb) }
    }

    private fun notifyQuickSettingsTile() {
        runCatching {
            TileService.requestListeningState(
                this,
                ComponentName(this, SwiftTunnelTileService::class.java)
            )
        }
    }

    private suspend fun waitForNetworkAvailable(timeoutMs: Long) {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val activeCaps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
            return
        }
        val ready = CompletableDeferred<Unit>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                ready.complete(Unit)
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
        try {
            withTimeoutOrNull(timeoutMs) { ready.await() }
            delay(1_500L) // Brief DHCP/DNS settle window on Android TV & Wi-Fi boot
        } finally {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }

    private suspend fun establishWireGuardTunnel(config: ActiveTunnelConfig) {
        // 1. Ensure we have a valid Nord server with WireGuard public key
        var server = config.server
        if (server == null || server.wireGuardPublicKey.isBlank()) {
            val countryId = prefs.getSelectedCountryId()
            val cityName = prefs.getSelectedCityName()
            server = apiClient.fetchRecommendedServer(countryId, cityName).getOrThrow()
            prefs.saveLastActiveServer(server)
        }
        if (server.wireGuardPublicKey.isBlank()) {
            throw IllegalStateException("Selected Nord server (${server.hostname}) does not advertise a NordLynx WireGuard public key.")
        }

        // 2. Ensure we have the user's NordLynx WireGuard private key from their Nord API key
        var privateKey = VpnKeyBridge.nordLynxPrivateKey
        if (privateKey.isNullOrBlank()) {
            val apiKey = keyStorage.getActiveKeyInfo().key
                ?: throw IllegalStateException("Nord API key required for VPN modes. Tap Swap API Key and save your Nord token.")
            val creds = apiClient.fetchServiceCredentials(apiKey).getOrThrow()
            privateKey = creds.nordLynxPrivateKey
                ?: throw IllegalStateException("Nord account did not return a NordLynx private key.")
            VpnKeyBridge.nordLynxPrivateKey = privateKey
        }

        val appRules = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            prefs.getAppRouteRules().filter { it.packageName != packageName }
        } else {
            emptyList()
        }

        if (appRules.isNotEmpty()) {
            establishMultiPeerWireGuardTunnel(config, server, privateKey, appRules)
            return
        }

        // 3. Perform REAL WireGuard Noise_IKpsk2 handshake BEFORE adding tunnel routes;
        //    if Auto-Failover is ON and the primary server fails to respond, seamlessly
        //    fail over to the next lowest-load server in the same city/country!
        var activeEngine = createWireGuardEngine(server, privateKey)
        wireGuardEngine = activeEngine
        val handshakeResult = activeEngine.performInitialHandshake()
        val handshakeRttMs = if (handshakeResult.isSuccess) {
            handshakeResult.getOrThrow()
        } else if (config.autoFailoverEnabled) {
            activeEngine.stop()
            val failedHost = server.hostname
            val countryId = prefs.getSelectedCountryId()
            val cityName = server.cityName.ifBlank { prefs.getSelectedCityName().orEmpty() }.ifBlank { null }
            val candidates = apiClient.fetchServersForLocation(countryId, cityName, limit = 12).getOrDefault(emptyList())
            val failoverServer = candidates.firstOrNull {
                !it.hostname.equals(failedHost, ignoreCase = true) && it.wireGuardPublicKey.isNotBlank()
            } ?: throw handshakeResult.exceptionOrNull()!!

            server = failoverServer
            prefs.saveLastActiveServer(failoverServer)
            currentConfig = config.copy(server = failoverServer)
            activeEngine = createWireGuardEngine(failoverServer, privateKey)
            wireGuardEngine = activeEngine
            activeEngine.performInitialHandshake().getOrThrow()
        } else {
            throw handshakeResult.exceptionOrNull()!!
        }

        // 4. Handshake succeeded! Build TUN with Custom MTU, LAN Bypass (optional),
        //    IPv6 leak trap (::/0), effective DNS (1.1.1.1 / 8.8.8.8 / AdGuard / Quad9 / Custom),
        //    and exact Split Tunneling enforcement.
        val builder = Builder()
            .setSession(buildSessionLabel(config.mode, config.primaryDns, server))
            .setMtu(config.mtu)
            .addAddress("10.5.0.2", 32)
            // Capture IPv6 into TUN so apps like Brave cannot leak real location over ISP IPv6
            .addAddress("fd00:5:0::2", 128)
            .addRoute("::", 0)
            .addDnsServer(config.primaryDns)

        if (config.secondaryDns.isNotBlank() && config.secondaryDns != config.primaryDns) {
            builder.addDnsServer(config.secondaryDns)
        }

        applyIpv4RoutesWithLanBypass(builder, config.lanBypassEnabled)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        applySplitTunneling(builder, config.splitMode, config.splitPackages)

        val established = builder.establish()
            ?: throw IllegalStateException("Android VPN interface could not be established")
        tunInterface = established

        // 5. Start real bidirectional WireGuard TX/RX/Keepalive loops
        activeEngine.startDataLoops(established)

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            NOTIFICATION_ID,
            buildNotification(
                mode = config.mode,
                primaryDns = config.primaryDns,
                server = server,
                splitMode = config.splitMode,
                splitCount = config.splitPackages.size,
                connecting = false
            )
        )

        VpnStateHolder.update {
            it.copy(
                status = TunnelStatus.CONNECTED,
                mode = config.mode,
                connectedAtMillis = System.currentTimeMillis(),
                dnsQueriesForwarded = 0L,
                dnsCacheHits = 0L,
                bytesTx = 0L,
                bytesRx = 0L,
                primaryDns = config.primaryDns,
                secondaryDns = config.secondaryDns,
                activeServer = server,
                splitMode = config.splitMode,
                splitAppCount = config.splitPackages.size,
                latencyMs = handshakeRttMs,
                isWireGuardVerified = true,
                lanBypassEnabled = config.lanBypassEnabled,
                mtu = config.mtu,
                activePeerTunnels = emptyList(),
                errorMessage = null
            )
        }
    }

    private suspend fun establishMultiPeerWireGuardTunnel(
        config: ActiveTunnelConfig,
        defaultServer: NordServer,
        privateKey: String,
        appRules: List<com.example.model.AppRouteRule>
    ) {
        val defaultCountryId = prefs.getSelectedCountryId() ?: -1
        val specs = mutableListOf<PeerTunnelSpec>()
        val mappedPackages = LinkedHashSet<String>()

        // Default peer always handles unmapped apps
        val defaultSpec = PeerTunnelSpec(
            routeKey = "default",
            countryId = defaultCountryId,
            server = defaultServer,
            appPackages = emptyList(),
            appUids = emptySet(),
            isDefaultPeer = true
        )
        specs.add(defaultSpec)

        // Group apps sharing the exact same target country/city/pinned server onto a single WireGuard peer
        val grouped = appRules.groupBy { it.routeKey }
        for ((routeKey, rulesForRoute) in grouped) {
            val first = rulesForRoute.first()
            val uids = HashSet<Int>()
            val validPkgs = mutableListOf<String>()
            for (rule in rulesForRoute) {
                val uid = runCatching { packageManager.getPackageUid(rule.packageName, 0) }.getOrNull()
                if (uid != null && uid > 0) {
                    uids.add(uid)
                    validPkgs.add(rule.packageName)
                    mappedPackages.add(rule.packageName)
                }
            }
            if (uids.isEmpty()) continue

            val peerServer = first.pinnedServerHostname?.let { host ->
                apiClient.fetchServerByHostname(host).getOrNull()
            } ?: apiClient.fetchRecommendedServer(first.countryId, first.cityName).getOrNull()
                ?: continue

            if (peerServer.wireGuardPublicKey.isBlank()) continue
            specs.add(
                PeerTunnelSpec(
                    routeKey = routeKey,
                    countryId = first.countryId,
                    server = peerServer,
                    appPackages = validPkgs,
                    appUids = uids,
                    isDefaultPeer = false
                )
            )
        }

        val engine = MultiPeerWireGuardEngine(
            vpnService = this@SwiftTunnelVpnService,
            localPrivateKeyBase64 = privateKey,
            peerSpecs = specs,
            scope = serviceScope,
            onStatsFlushed = { pkts, tx, rx, summaries ->
                VpnStateHolder.recordMultiPeerWireGuardStats(pkts, tx, rx, summaries)
            }
        )
        multiPeerEngine = engine
        val (handshakeRttMs, initialSummaries) = engine.performInitialHandshakes().getOrThrow()

        val builder = Builder()
            .setSession("SwiftTunnel Multi-App (${initialSummaries.size} tunnels)")
            .setMtu(config.mtu)
            .addAddress("10.5.0.2", 32)
            .addAddress("fd00:5:0::2", 128)
            .addRoute("::", 0)
            .addDnsServer(config.primaryDns)

        if (config.secondaryDns.isNotBlank() && config.secondaryDns != config.primaryDns) {
            builder.addDnsServer(config.secondaryDns)
        }
        applyIpv4RoutesWithLanBypass(builder, config.lanBypassEnabled)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        // Ensure all apps with explicit country routes enter the TUN interface cleanly
        val effectiveSplitPackages = when (config.splitMode) {
            SplitTunnelMode.INCLUDE_ONLY_APPS -> config.splitPackages + mappedPackages
            SplitTunnelMode.EXCLUDE_APPS -> config.splitPackages - mappedPackages
            SplitTunnelMode.ALL_APPS -> config.splitPackages
        }
        applySplitTunneling(builder, config.splitMode, effectiveSplitPackages)

        val established = builder.establish()
            ?: throw IllegalStateException("Android VPN interface could not be established")
        tunInterface = established

        engine.startDataLoops(established)

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            NOTIFICATION_ID,
            buildNotification(
                mode = config.mode,
                primaryDns = config.primaryDns,
                server = defaultServer,
                splitMode = config.splitMode,
                splitCount = effectiveSplitPackages.size,
                connecting = false
            )
        )

        VpnStateHolder.update {
            it.copy(
                status = TunnelStatus.CONNECTED,
                mode = config.mode,
                connectedAtMillis = System.currentTimeMillis(),
                dnsQueriesForwarded = 0L,
                dnsCacheHits = 0L,
                bytesTx = 0L,
                bytesRx = 0L,
                primaryDns = config.primaryDns,
                secondaryDns = config.secondaryDns,
                activeServer = defaultServer,
                splitMode = config.splitMode,
                splitAppCount = effectiveSplitPackages.size,
                latencyMs = handshakeRttMs,
                isWireGuardVerified = true,
                lanBypassEnabled = config.lanBypassEnabled,
                mtu = config.mtu,
                activePeerTunnels = initialSummaries,
                errorMessage = null
            )
        }
    }

    private fun createWireGuardEngine(
        server: NordServer,
        privateKey: String
    ): WireGuardTunnelEngine = WireGuardTunnelEngine(
        vpnService = this@SwiftTunnelVpnService,
        endpointHost = server.stationIp,
        endpointPort = 51820,
        localPrivateKeyBase64 = privateKey,
        peerPublicKeyBase64 = server.wireGuardPublicKey,
        scope = serviceScope,
        onStatsFlushed = { pkts, tx, rx ->
            VpnStateHolder.recordWireGuardStats(pkts, tx, rx)
        }
    )

    private fun applyIpv4RoutesWithLanBypass(builder: Builder, lanBypassEnabled: Boolean) {
        if (!lanBypassEnabled) {
            builder.addRoute("0.0.0.0", 0)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.addRoute("0.0.0.0", 0)
            LanBypassRouteCalculator.excludedLanSubnets.forEach { cidr ->
                runCatching {
                    builder.excludeRoute(IpPrefix(InetAddress.getByName(cidr.address), cidr.prefixLength))
                }
            }
        } else {
            LanBypassRouteCalculator.publicInternetRoutes.forEach { cidr ->
                builder.addRoute(cidr.address, cidr.prefixLength)
            }
        }
    }

    private fun establishDnsOnlyTunnel(config: ActiveTunnelConfig) {
        val builder = Builder()
            .setSession(buildSessionLabel(config.mode, config.primaryDns, null))
            .setMtu(config.mtu.coerceAtLeast(1280))
            .addAddress("10.215.173.1", 32)
            .addDnsServer(config.primaryDns)
            .addRoute(config.primaryDns, 32)

        if (config.secondaryDns.isNotBlank() && config.secondaryDns != config.primaryDns) {
            builder.addDnsServer(config.secondaryDns)
            builder.addRoute(config.secondaryDns, 32)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        applySplitTunneling(builder, config.splitMode, config.splitPackages)

        val established = builder.establish()
            ?: throw IllegalStateException("Android VPN interface could not be established")
        tunInterface = established

        val activeForwarder = DnsPacketForwarder(
            vpnService = this,
            tunFd = established,
            primaryDnsIp = config.primaryDns,
            secondaryDnsIp = config.secondaryDns,
            scope = serviceScope,
            onStatsFlushed = { fwd, hits, tx, rx ->
                VpnStateHolder.recordDnsStats(fwd, hits, tx, rx)
            }
        )
        dnsForwarder = activeForwarder

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            NOTIFICATION_ID,
            buildNotification(
                mode = config.mode,
                primaryDns = config.primaryDns,
                server = null,
                splitMode = config.splitMode,
                splitCount = config.splitPackages.size,
                connecting = false
            )
        )

        VpnStateHolder.update {
            it.copy(
                status = TunnelStatus.CONNECTED,
                mode = config.mode,
                connectedAtMillis = System.currentTimeMillis(),
                dnsQueriesForwarded = 0L,
                dnsCacheHits = 0L,
                bytesTx = 0L,
                bytesRx = 0L,
                primaryDns = config.primaryDns,
                secondaryDns = config.secondaryDns,
                activeServer = null,
                splitMode = config.splitMode,
                splitAppCount = config.splitPackages.size,
                latencyMs = config.latencyMs,
                isWireGuardVerified = false,
                lanBypassEnabled = config.lanBypassEnabled,
                mtu = config.mtu,
                errorMessage = null
            )
        }

        serviceScope.launch(Dispatchers.IO) {
            activeForwarder.start()
        }
    }

    private fun applySplitTunneling(
        builder: Builder,
        splitMode: SplitTunnelMode,
        packages: Set<String>
    ) {
        when (splitMode) {
            SplitTunnelMode.ALL_APPS -> {
                runCatching { builder.addDisallowedApplication(packageName) }
            }
            SplitTunnelMode.EXCLUDE_APPS -> {
                runCatching { builder.addDisallowedApplication(packageName) }
                packages.forEach { pkg ->
                    if (pkg.isNotBlank()) {
                        runCatching { builder.addDisallowedApplication(pkg) }
                    }
                }
            }
            SplitTunnelMode.INCLUDE_ONLY_APPS -> {
                var addedCount = 0
                packages.forEach { pkg ->
                    if (pkg.isNotBlank() && pkg != packageName) {
                        val ok = runCatching { builder.addAllowedApplication(pkg) }.isSuccess
                        if (ok) addedCount++
                    }
                }
                if (addedCount == 0) {
                    throw IllegalStateException("None of the selected split-tunnel apps are currently installed.")
                }
            }
        }
    }

    private fun disconnectTunnel() {
        prefs.setTunnelWantedActive(false)
        currentConfig = null
        VpnStateHolder.update { it.copy(status = TunnelStatus.DISCONNECTING) }
        teardownActiveInterface()
        VpnStateHolder.update {
            it.copy(
                status = TunnelStatus.DISCONNECTED,
                connectedAtMillis = 0L,
                isWireGuardVerified = false,
                activePeerTunnels = emptyList(),
                errorMessage = null
            )
        }
        notifyQuickSettingsTile()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun teardownActiveInterface(cancelConnectJob: Boolean = true) {
        if (cancelConnectJob) {
            connectJob?.cancel()
            connectJob = null
        }
        unregisterNetworkHandoverMonitor()
        multiPeerEngine?.stop()
        multiPeerEngine = null
        wireGuardEngine?.stop()
        wireGuardEngine = null
        dnsForwarder?.stop()
        dnsForwarder = null
        runCatching { tunInterface?.close() }
        tunInterface = null
    }

    override fun onRevoke() {
        disconnectTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardownActiveInterface()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildSessionLabel(mode: ConnectionMode, primaryDns: String, server: NordServer?): String {
        return if (mode.usesVpn && server != null) {
            "SwiftTunnel WG • ${server.serverCodeTag} ${server.locationLabel} ($primaryDns)"
        } else {
            "SwiftTunnel DNS • $primaryDns"
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        mode: ConnectionMode,
        primaryDns: String,
        server: NordServer?,
        splitMode: SplitTunnelMode,
        splitCount: Int,
        connecting: Boolean
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, SwiftTunnelVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            101,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val splitTag = when (splitMode) {
            SplitTunnelMode.ALL_APPS -> "All Apps"
            SplitTunnelMode.EXCLUDE_APPS -> "$splitCount Excluded"
            SplitTunnelMode.INCLUDE_ONLY_APPS -> "$splitCount Included Only"
        }

        val contentText = when {
            connecting -> "Handshaking with ${server?.let { "${it.serverCodeTag} ${it.locationLabel}" } ?: mode.shortTitle}…"
            mode.usesVpn && server != null ->
                "WG ${server.serverCodeTag} • ${server.locationLabel} (${server.stationIp}) • DNS $primaryDns • $splitTag"
            else ->
                "${mode.title} • DNS $primaryDns • $splitTag"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .addAction(0, getString(R.string.action_disconnect), disconnectPendingIntent)
            .build()
    }

    private fun extractServer(intent: Intent): NordServer? {
        val hostname = intent.getStringExtra(EXTRA_SERVER_HOSTNAME).orEmpty()
        val ip = intent.getStringExtra(EXTRA_SERVER_IP).orEmpty()
        if (hostname.isEmpty() || ip.isEmpty()) return null
        return NordServer(
            id = intent.getIntExtra(EXTRA_SERVER_ID, 0),
            name = intent.getStringExtra(EXTRA_SERVER_NAME).orEmpty(),
            hostname = hostname,
            stationIp = ip,
            loadPercent = intent.getIntExtra(EXTRA_SERVER_LOAD, 0),
            countryName = intent.getStringExtra(EXTRA_SERVER_COUNTRY).orEmpty(),
            countryCode = intent.getStringExtra(EXTRA_SERVER_CODE).orEmpty(),
            cityName = intent.getStringExtra(EXTRA_SERVER_CITY).orEmpty(),
            wireGuardPublicKey = intent.getStringExtra(EXTRA_SERVER_PUBKEY).orEmpty()
        )
    }

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val ACTION_BOOT_AUTOCONNECT = "com.example.vpn.ACTION_BOOT_AUTOCONNECT"

        private const val EXTRA_MODE = "extra_mode"
        private const val EXTRA_PRIMARY_DNS = "extra_primary_dns"
        private const val EXTRA_SECONDARY_DNS = "extra_secondary_dns"
        private const val EXTRA_LAN_BYPASS = "extra_lan_bypass"
        private const val EXTRA_AUTO_FAILOVER = "extra_auto_failover"
        private const val EXTRA_MTU = "extra_mtu"
        private const val EXTRA_SPLIT_MODE = "extra_split_mode"
        private const val EXTRA_SPLIT_PACKAGES = "extra_split_packages"
        private const val EXTRA_LATENCY_MS = "extra_latency_ms"
        private const val EXTRA_SERVER_ID = "extra_server_id"
        private const val EXTRA_SERVER_NAME = "extra_server_name"
        private const val EXTRA_SERVER_HOSTNAME = "extra_server_hostname"
        private const val EXTRA_SERVER_IP = "extra_server_ip"
        private const val EXTRA_SERVER_LOAD = "extra_server_load"
        private const val EXTRA_SERVER_COUNTRY = "extra_server_country"
        private const val EXTRA_SERVER_CODE = "extra_server_code"
        private const val EXTRA_SERVER_CITY = "extra_server_city"
        private const val EXTRA_SERVER_PUBKEY = "extra_server_pubkey"

        private const val CHANNEL_ID = "swifttunnel_vpn_channel"
        private const val NOTIFICATION_ID = 4201

        fun createConnectIntent(
            context: Context,
            mode: ConnectionMode,
            primaryDns: String,
            secondaryDns: String,
            splitMode: SplitTunnelMode,
            splitPackages: Set<String>,
            server: NordServer?,
            latencyMs: Int?,
            lanBypassEnabled: Boolean,
            autoFailoverEnabled: Boolean,
            mtu: Int
        ): Intent = Intent(context, SwiftTunnelVpnService::class.java).apply {
            action = ACTION_CONNECT
            putExtra(EXTRA_MODE, mode.id)
            putExtra(EXTRA_PRIMARY_DNS, primaryDns)
            putExtra(EXTRA_SECONDARY_DNS, secondaryDns)
            putExtra(EXTRA_LAN_BYPASS, lanBypassEnabled)
            putExtra(EXTRA_AUTO_FAILOVER, autoFailoverEnabled)
            putExtra(EXTRA_MTU, mtu)
            putExtra(EXTRA_SPLIT_MODE, splitMode.id)
            putStringArrayListExtra(EXTRA_SPLIT_PACKAGES, ArrayList(splitPackages))
            if (latencyMs != null) putExtra(EXTRA_LATENCY_MS, latencyMs)
            if (server != null) {
                putExtra(EXTRA_SERVER_ID, server.id)
                putExtra(EXTRA_SERVER_NAME, server.name)
                putExtra(EXTRA_SERVER_HOSTNAME, server.hostname)
                putExtra(EXTRA_SERVER_IP, server.stationIp)
                putExtra(EXTRA_SERVER_LOAD, server.loadPercent)
                putExtra(EXTRA_SERVER_COUNTRY, server.countryName)
                putExtra(EXTRA_SERVER_CODE, server.countryCode)
                putExtra(EXTRA_SERVER_CITY, server.cityName)
                putExtra(EXTRA_SERVER_PUBKEY, server.wireGuardPublicKey)
            }
        }

        fun createDisconnectIntent(context: Context): Intent =
            Intent(context, SwiftTunnelVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }

        fun createBootAutoConnectIntent(context: Context): Intent =
            Intent(context, SwiftTunnelVpnService::class.java).apply {
                action = ACTION_BOOT_AUTOCONNECT
            }
    }
}
