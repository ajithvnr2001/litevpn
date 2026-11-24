package com.example.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.PreferencesRepository
import com.example.model.TunnelStatus

/**
 * Android Quick Settings Tile allowing 1-tap connect/disconnect directly from the
 * notification shade while showing the active location #code & city.
 */
class SwiftTunnelTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        val session = VpnStateHolder.sessionState.value
        val isActive = session.status == TunnelStatus.CONNECTED ||
            session.status == TunnelStatus.CONNECTING

        if (isActive) {
            startService(SwiftTunnelVpnService.createDisconnectIntent(this))
            refreshTileState(overrideStatus = TunnelStatus.DISCONNECTED)
            return
        }

        // If Android VPN consent hasn't been granted yet, open MainActivity so user can approve
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this,
                    501,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(launchIntent)
            }
            return
        }

        val connectIntent = SwiftTunnelVpnService.createBootAutoConnectIntent(this)
        ContextCompat.startForegroundService(this, connectIntent)
        refreshTileState(overrideStatus = TunnelStatus.CONNECTING)
    }

    private fun refreshTileState(overrideStatus: TunnelStatus? = null) {
        val tile = qsTile ?: return
        val session = VpnStateHolder.sessionState.value
        val status = overrideStatus ?: session.status
        val prefs = PreferencesRepository(this)
        val server = session.activeServer ?: prefs.getLastActiveServer()
        val mode = if (status == TunnelStatus.CONNECTED) session.mode else prefs.getConnectionMode()

        tile.label = "SwiftTunnel"
        tile.state = when (status) {
            TunnelStatus.CONNECTED, TunnelStatus.CONNECTING -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (status) {
                TunnelStatus.CONNECTED -> {
                    if (mode.usesVpn && server != null) {
                        "${server.serverCodeTag} ${server.cityName.ifBlank { server.countryCode }}"
                    } else {
                        "DNS ${session.primaryDns}"
                    }
                }
                TunnelStatus.CONNECTING -> "Connecting…"
                else -> {
                    if (mode.usesVpn && server != null) {
                        "Ready • ${server.serverCodeTag}"
                    } else {
                        "Ready • ${mode.shortTitle}"
                    }
                }
            }
        }
        tile.updateTile()
    }
}
