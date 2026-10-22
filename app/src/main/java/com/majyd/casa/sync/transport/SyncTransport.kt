package com.majyd.casa.sync.transport

import com.majyd.casa.sync.SyncOperation

interface SyncTransport {
    suspend fun exchange(
        host: String,
        port: Int,
        authToken: String,
        operations: List<SyncOperation>,
        localDeviceId: String = "local",
        onProgress: (TransportPhase) -> Unit = {},
    ): List<SyncOperation>
}

enum class TransportPhase {
    CONNECTING,
    AUTHENTICATING,
    SYNCHRONIZING,
}
