package com.example.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.example.data.PreferencesRepository

/**
 * Automatically reconnects SwiftTunnel to the user's pointed Nord location, DNS combination,
 * and split-tunneling configuration whenever the phone or Android TV powers on.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in ALLOWED_ACTIONS) return

        val prefs = PreferencesRepository(context)
        if (!prefs.isAutoConnectOnBootEnabled()) return

        // VpnService.prepare returns null once the user has previously granted VPN permission.
        if (VpnService.prepare(context) != null) return

        val serviceIntent = SwiftTunnelVpnService.createBootAutoConnectIntent(context)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    companion object {
        private val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
