package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.NordApiClient
import com.example.data.PreferencesRepository
import com.example.data.SecureKeyStorage
import com.example.model.ConnectionMode
import com.example.model.NordServer
import com.example.model.SplitTunnelMode
import com.example.model.TunnelStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SwiftTunnelVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: PreferencesRepository
    private lateinit var keyStorage: SecureKeyStorage
    private val apiClient = NordApiClient()

    private var tunInterface: ParcelFileDescriptor? = null
    private var dnsForwarder: DnsPacketForwarder? = null
    private var wireGuardEngine: WireGuardTunnelEngine? = null
    private var connectJob: Job? = null

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
                val restoredSplitMode = prefs.getSplitTunnelMode()
                val restoredPackages = prefs.getActiveSplitPackages()
                val restoredServer = prefs.getLastActiveServer()
                startConnectAsync(
                    mode = restoredMode,
                    splitMode = restoredSplitMode,
                    splitPackages = restoredPackages,
                    server = restoredServer,
                    latencyMs = null,
                    waitForNetwork = true
                )
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
                val splitMode = SplitTunnelMode.fromId(intent.getStringExtra(EXTRA_SPLIT_MODE))
                val splitPackages = intent.getStringArrayListExtra(EXTRA_SPLIT_PACKAGES)
                    ?.toSet()
                    ?: emptySet()
                val server = extractServer(intent) ?: prefs.getLastActiveServer()
                val latencyMs = intent.getIntExtra(EXTRA_LATENCY_MS, -1).takeIf { it > 0 }
                startConnectAsync(
                    mode = mode,
                    splitMode = splitMode,
                    splitPackages = splitPackages,
                    server = server,
                    latencyMs = latencyMs,
                    waitForNetwork = false
                )
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startConnectAsync(
        mode: ConnectionMode,
        splitMode: SplitTunnelMode,
        splitPackages: Set<String>,
        server: NordServer?,
        latencyMs: Int?,
        waitForNetwork: Boolean
    ) {
        teardownActiveInterface()
        prefs.setTunnelWantedActive(true)
        if (server != null) {
            prefs.saveLastActiveServer(server)
        }

        VpnStateHolder.update {
            it.copy(
                status = TunnelStatus.CONNECTING,
                mode = mode,
                primaryDns = mode.primaryDns,
                secondaryDns = mode.secondaryDns,
                activeServer = if (mode.usesVpn) server else null,
                splitMode = splitMode,
                splitAppCount = splitPackages.size,
                latencyMs = latencyMs,
                isWireGuardVerified = false,
                errorMessage = null
            )
        }

        ensureNotificationChannel()
        val initialNotification = buildNotification(mode, server, splitMode, splitPackages.size, connecting = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        connectJob = serviceScope.launch {
            val maxAttempts = if (waitForNetwork) 5 else 1
            var lastError: Exception? = null

            if (waitForNetwork) {
                waitForNetworkAvailable(timeoutMs = 45_000L)
            }

            for (attempt in 1..maxAttempts) {
                try {
                    if (splitMode == SplitTunnelMode.INCLUDE_ONLY_APPS && splitPackages.isEmpty()) {
                        throw IllegalStateException("Select at least 1 app in Split Apps (Include Only) before connecting.")
                    }

                    if (mode.usesVpn) {
                        establishWireGuardTunnel(mode, splitMode, splitPackages, server)
                    } else {
                        establishDnsOnlyTunnel(mode, splitMode, splitPackages, latencyMs)
                    }
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
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
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

    private suspend fun establishWireGuardTunnel(
        mode: ConnectionMode,
        splitMode: SplitTunnelMode,
        splitPackages: Set<String>,
        requestedServer: NordServer?
    ) {
        // 1. Ensure we have a valid Nord server with WireGuard public key
        var server = requestedServer
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

        // 3. Perform REAL WireGuard Noise_IKpsk2 handshake with Nord server BEFORE adding 0.0.0.0/0 route
        val engine = WireGuardTunnelEngine(
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
        wireGuardEngine = engine

        val handshakeRttMs = engine.performInitialHandshake().getOrThrow()

        // 4. Handshake succeeded! Establish full IPv4 (0.0.0.0/0) + IPv6 leak-blocked (::/0) TUN
        //    with exact Split Tunneling enforcement (Include Only / Exclude / All Apps).
        val builder = Builder()
            .setSession(buildSessionLabel(mode, server))
            .setMtu(1280)
            .addAddress("10.5.0.2", 32)
            .addRoute("0.0.0.0", 0)
            // Capture IPv6 into TUN so apps like Brave cannot leak real location over ISP IPv6
            .addAddress("fd00:5:0::2", 128)
            .addRoute("::", 0)
            .addDnsServer(mode.primaryDns)
            .addDnsServer(mode.secondaryDns)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        applySplitTunneling(builder, splitMode, splitPackages)

        val established = builder.establish()
            ?: throw IllegalStateException("Android VPN interface could not be established")
        tunInterface = established

        // 5. Start real bidirectional WireGuard TX/RX/Keepalive loops
        engine.startDataLoops(established)

        // Update notification with verified live server
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(mode, server, splitMode, splitPackages.size, connecting = false))

        VpnStateHolder.update {
            it.copy(
                status = TunnelStatus.CONNECTED,
                mode = mode,
                connectedAtMillis = System.currentTimeMillis(),
                dnsQueriesForwarded = 0L,
                dnsCacheHits = 0L,
                bytesTx = 0L,
                bytesRx = 0L,
                primaryDns = mode.primaryDns,
                secondaryDns = mode.secondaryDns,
                activeServer = server,
                splitMode = splitMode,
                splitAppCount = splitPackages.size,
                latencyMs = handshakeRttMs,
                isWireGuardVerified = true,
                errorMessage = null
            )
        }
    }

    private fun establishDnsOnlyTunnel(
        mode: ConnectionMode,
        splitMode: SplitTunnelMode,
        splitPackages: Set<String>,
        latencyMs: Int?
    ) {
        val builder = Builder()
            .setSession(buildSessionLabel(mode, null))
            .setMtu(1420)
            .addAddress("10.215.173.1", 32)
            .addDnsServer(mode.primaryDns)
            .addDnsServer(mode.secondaryDns)
            .addRoute(mode.primaryDns, 32)
            .addRoute(mode.secondaryDns, 32)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        applySplitTunneling(builder, splitMode, splitPackages)

        val established = builder.establish()
            ?: throw IllegalStateException("Android VPN interface could not be established")
        tunInterface = established

        val activeForwarder = DnsPacketForwarder(
            vpnService = this,
            tunFd = established,
            primaryDnsIp = mode.primaryDns,
            secondaryDnsIp = mode.secondaryDns,
            scope = serviceScope,
            onStatsFlushed = { fwd, hits, tx, rx ->
                VpnStateHolder.recordDnsStats(fwd, hits, tx, rx)
            }
        )
        dnsForwarder = activeForwarder

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(mode, null, splitMode, splitPackages.size, connecting = false))

        VpnStateHolder.update {
            it.copy(
                status = TunnelStatus.CONNECTED,
                mode = mode,
                connectedAtMillis = System.currentTimeMillis(),
                dnsQueriesForwarded = 0L,
                dnsCacheHits = 0L,
                bytesTx = 0L,
                bytesRx = 0L,
                primaryDns = mode.primaryDns,
                secondaryDns = mode.secondaryDns,
                activeServer = null,
                splitMode = splitMode,
                splitAppCount = splitPackages.size,
                latencyMs = latencyMs,
                isWireGuardVerified = false,
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
        VpnStateHolder.update { it.copy(status = TunnelStatus.DISCONNECTING) }
        teardownActiveInterface()
        VpnStateHolder.update {
            it.copy(
                status = TunnelStatus.DISCONNECTED,
                connectedAtMillis = 0L,
                isWireGuardVerified = false,
                errorMessage = null
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun teardownActiveInterface(cancelConnectJob: Boolean = true) {
        if (cancelConnectJob) {
            connectJob?.cancel()
            connectJob = null
        }
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

    private fun buildSessionLabel(mode: ConnectionMode, server: NordServer?): String {
        return if (mode.usesVpn && server != null) {
            "SwiftTunnel WG • ${server.countryName} (${mode.primaryDns})"
        } else {
            "SwiftTunnel DNS • ${mode.shortTitle}"
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
            connecting -> "Handshaking with ${server?.countryName ?: mode.shortTitle}…"
            mode.usesVpn && server != null ->
                "WireGuard • ${server.countryName} (${server.stationIp}) • DNS ${mode.primaryDns} • $splitTag"
            else ->
                "${mode.title} • DNS ${mode.primaryDns} • $splitTag"
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
            splitMode: SplitTunnelMode,
            splitPackages: Set<String>,
            server: NordServer?,
            latencyMs: Int?
        ): Intent = Intent(context, SwiftTunnelVpnService::class.java).apply {
            action = ACTION_CONNECT
            putExtra(EXTRA_MODE, mode.id)
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
