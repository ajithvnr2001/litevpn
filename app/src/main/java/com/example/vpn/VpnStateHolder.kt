package com.example.vpn

import com.example.model.VpnSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object VpnStateHolder {
    private val _sessionState = MutableStateFlow(VpnSessionState())
    val sessionState: StateFlow<VpnSessionState> = _sessionState.asStateFlow()

    fun update(transform: (VpnSessionState) -> VpnSessionState) {
        _sessionState.update(transform)
    }

    fun recordDnsStats(
        forwardedDelta: Long,
        cacheHitDelta: Long,
        txBytesDelta: Long,
        rxBytesDelta: Long
    ) {
        if (forwardedDelta == 0L && cacheHitDelta == 0L) return
        _sessionState.update { current ->
            current.copy(
                dnsQueriesForwarded = current.dnsQueriesForwarded + forwardedDelta,
                dnsCacheHits = current.dnsCacheHits + cacheHitDelta,
                bytesTx = current.bytesTx + txBytesDelta,
                bytesRx = current.bytesRx + rxBytesDelta
            )
        }
    }

    fun recordWireGuardStats(
        packetsDelta: Long,
        txBytesDelta: Long,
        rxBytesDelta: Long
    ) {
        if (packetsDelta == 0L && txBytesDelta == 0L && rxBytesDelta == 0L) return
        _sessionState.update { current ->
            current.copy(
                dnsQueriesForwarded = current.dnsQueriesForwarded + packetsDelta,
                bytesTx = current.bytesTx + txBytesDelta,
                bytesRx = current.bytesRx + rxBytesDelta
            )
        }
    }
}
