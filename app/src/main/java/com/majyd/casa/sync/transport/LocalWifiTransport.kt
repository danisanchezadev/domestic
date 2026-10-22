package com.majyd.casa.sync.transport

import com.majyd.casa.sync.SyncOperation

interface LocalWifiTransport : SyncTransport {
    suspend fun acceptOnce(
        port: Int,
        authToken: String,
        localDeviceId: String = "local",
        operationHandler: suspend (SyncOperation) -> Boolean,
        operationProvider: suspend () -> List<SyncOperation>,
        authorization: suspend (String) -> Boolean = { true },
        onAuthorized: suspend (String) -> Unit = {},
    ): Int
}
