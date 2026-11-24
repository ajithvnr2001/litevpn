package com.example.vpn

/**
 * Memory-only bridge for the verified NordLynx WireGuard private key so it is never passed
 * through Intent extras or written in plaintext to disk.
 */
object VpnKeyBridge {
    @Volatile
    var nordLynxPrivateKey: String? = null
}
