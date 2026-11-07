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
        pairingCode: String = authToken,
        pairingCodeValid: suspend () -> Boolean = { true },
        onPairingFailure: suspend () -> Unit = {},
        authorizedAuthToken: suspend (String) -> String? = { null },
        onAuthorized: suspend (String) -> Unit = {},
        onAuthorizedWithAlias: suspend (String, String) -> Unit = { _, _ -> },
        onPaired: suspend (String, String) -> Unit = { _, _ -> },
    ): Int
}
