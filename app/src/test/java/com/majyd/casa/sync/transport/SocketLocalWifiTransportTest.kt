package com.majyd.casa.sync.transport

import com.majyd.casa.sync.SyncOperation
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.net.ServerSocket
import java.io.EOFException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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

    @Test
    fun exchangeRejectsResponseClosedBeforeEndMarker() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val transport = SocketLocalWifiTransport()
        val server = launch(Dispatchers.IO) {
            ServerSocket(port).use { listener ->
                listener.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    val hello = reader.readLine() ?: error("Missing hello")
                    val clientNonce = hello.removePrefix("CASA_HELLO:").substringAfter(":")
                    val serverNonce = SyncCrypto.nonce()
                    writer.println("CASA_CHALLENGE:$serverNonce")
                    reader.readLine()
                    writer.println("CASA_AUTH_OK")
                    while (reader.readLine() != "__CASA_SYNC_END__") {
                        // Consume the request before closing without a response marker.
                    }
                    check(clientNonce.isNotBlank())
                }
            }
        }

        delay(100)
        var rejected = false
        try {
            transport.exchange("127.0.0.1", port, TOKEN, listOf(operation("truncated-response", false)))
        } catch (_: EOFException) {
            rejected = true
        } catch (_: IOException) {
            rejected = true
        }
        server.join()

        assertTrue(rejected)
    }

    @Test
    fun acceptRejectsRequestClosedBeforeEndMarker() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val transport = SocketLocalWifiTransport()
        val server = async(Dispatchers.IO) {
            try {
                transport.acceptOnce(
                    port = port,
                    authToken = TOKEN,
                    operationHandler = { true },
                    operationProvider = { emptyList() },
                )
                false
            } catch (_: EOFException) {
                true
            } catch (_: IOException) {
                true
            }
        }

        delay(100)
        Socket("127.0.0.1", port).use { socket ->
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val crypto = SyncCrypto(TOKEN)
            val clientNonce = SyncCrypto.nonce()
            writer.println("CASA_HELLO:truncated-client:$clientNonce")
            val challenge = reader.readLine() ?: error("Missing challenge")
            val serverNonce = challenge.removePrefix("CASA_CHALLENGE:")
            writer.println("CASA_RESPONSE:${crypto.proof(clientNonce, serverNonce)}")
            check(reader.readLine() == "CASA_AUTH_OK")
            writer.println(crypto.encrypt(Json.encodeToString(operation("truncated-request", false))))
        }

        assertTrue(server.await())
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
