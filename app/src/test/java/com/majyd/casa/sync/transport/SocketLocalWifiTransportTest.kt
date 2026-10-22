package com.majyd.casa.sync.transport

import com.majyd.casa.sync.SyncOperation
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketLocalWifiTransportTest {
    @Test
    fun exchangeReturnsAcknowledgementsAndPeerPendingOperations() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val sent = operation("sent", applied = false)
        val peerPending = operation("peer", applied = false)
        val transport = SocketLocalWifiTransport()
        val server = launch(Dispatchers.IO) {
            transport.acceptOnce(
                port = port,
                authToken = TOKEN,
                operationHandler = { it.operationId == sent.operationId },
                operationProvider = { listOf(peerPending) },
            )
        }

        delay(100)
        val received = transport.exchange("127.0.0.1", port, TOKEN, listOf(sent))
        server.join()

        assertEquals(2, received.size)
        assertTrue(received.any { it.operationId == sent.operationId && it.applied })
        assertTrue(received.any { it.operationId == peerPending.operationId && !it.applied })
    }

    @Test
    fun exchangeDoesNotSendAcknowledgedOperationTwice() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val sent = operation("sent-once", applied = false)
        val transport = SocketLocalWifiTransport()
        val server = launch(Dispatchers.IO) {
            transport.acceptOnce(
                port = port,
                authToken = TOKEN,
                operationHandler = { true },
                operationProvider = { listOf(sent) },
            )
        }

        delay(100)
        val received = transport.exchange("127.0.0.1", port, TOKEN, listOf(sent))
        server.join()

        assertEquals(1, received.count { it.operationId == sent.operationId })
        assertTrue(received.single().applied)
    }

    @Test(expected = IllegalArgumentException::class)
    fun exchangeRejectsInvalidPort() {
        runBlocking {
            SocketLocalWifiTransport().exchange("127.0.0.1", 0, TOKEN, emptyList())
        }
    }

    @Test
    fun exchangeRejectsWrongPairingCode() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val transport = SocketLocalWifiTransport()
        val server = launch(Dispatchers.IO) {
            transport.acceptOnce(
                port = port,
                authToken = TOKEN,
                operationHandler = { true },
                operationProvider = { emptyList() },
            )
        }

        delay(100)
        var rejected = false
        try {
            transport.exchange("127.0.0.1", port, "000000", emptyList())
        } catch (_: SecurityException) {
            rejected = true
        }
        server.join()
        assertTrue(rejected)
    }

    @Test
    fun exchangeRejectsUnauthorizedDevice() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val transport = SocketLocalWifiTransport()
        val server = launch(Dispatchers.IO) {
            transport.acceptOnce(
                port = port,
                authToken = TOKEN,
                authorization = { false },
                operationHandler = { true },
                operationProvider = { emptyList() },
            )
        }

        delay(100)
        var rejected = false
        try {
            transport.exchange("127.0.0.1", port, TOKEN, emptyList(), localDeviceId = "third-device")
        } catch (_: SecurityException) {
            rejected = true
        }
        server.join()
        assertTrue(rejected)
    }

    private fun operation(id: String, applied: Boolean) = SyncOperation(
        operationId = id,
        householdId = "home-1",
        entityType = "task",
        entityId = "task-$id",
        payload = "{}",
        version = 1L,
        updatedAt = 1L,
        deviceId = "device-$id",
        deleted = false,
        applied = applied,
    )

    companion object { private const val TOKEN = "123456" }
}
