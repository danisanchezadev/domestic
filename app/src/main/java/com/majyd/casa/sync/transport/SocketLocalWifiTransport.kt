package com.majyd.casa.sync.transport

import com.majyd.casa.sync.SyncOperation
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class SocketLocalWifiTransport @Inject constructor() : LocalWifiTransport {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun exchange(
        host: String,
        port: Int,
        authToken: String,
        operations: List<SyncOperation>,
        localDeviceId: String,
        onProgress: (TransportPhase) -> Unit,
    ): List<SyncOperation> = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "Host vacío" }
        require(port in 1..65535) { "Puerto no válido" }
        onProgress(TransportPhase.CONNECTING)
        Socket().apply {
            connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            soTimeout = READ_TIMEOUT_MS
        }.use { socket ->
            val crypto = SyncCrypto(authToken)
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val clientNonce = SyncCrypto.nonce()
            writer.println(HELLO_PREFIX + localDeviceId + HELLO_SEPARATOR + clientNonce)
            onProgress(TransportPhase.AUTHENTICATING)
            val challenge = reader.readLine().orEmpty()
            require(challenge.startsWith(CHALLENGE_PREFIX)) { "Handshake inválido" }
            val serverNonce = challenge.removePrefix(CHALLENGE_PREFIX)
            writer.println(RESPONSE_PREFIX + crypto.proof(clientNonce, serverNonce))
            if (reader.readLine() != AUTH_OK) {
                throw SecurityException("Código de emparejamiento incorrecto")
            }
            onProgress(TransportPhase.SYNCHRONIZING)
            operations.forEach { writer.println(crypto.encrypt(json.encodeToString(it))) }
            writer.println(END_MARKER)
            readOperations(reader, crypto)
        }
    }

    override suspend fun acceptOnce(
        port: Int,
        authToken: String,
        localDeviceId: String,
        operationHandler: suspend (SyncOperation) -> Boolean,
        operationProvider: suspend () -> List<SyncOperation>,
        authorization: suspend (String) -> Boolean,
        onAuthorized: suspend (String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        require(port in 1..65535) { "Puerto no válido" }
        ServerSocket(port).use { server ->
            server.soTimeout = ACCEPT_TIMEOUT_MS
            server.accept().use { socket ->
                socket.soTimeout = READ_TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)
                val hello = reader.readLine().orEmpty()
                if (!hello.startsWith(HELLO_PREFIX)) {
                    writer.println(AUTH_FAILED)
                    writer.println(END_MARKER)
                    return@use 0
                }
                val helloParts = hello.removePrefix(HELLO_PREFIX).split(HELLO_SEPARATOR, limit = 2)
                if (helloParts.size != 2 || helloParts[0].isBlank() || helloParts[1].isBlank()) {
                    writer.println(AUTH_FAILED)
                    writer.println(END_MARKER)
                    return@use 0
                }
                val remoteDeviceId = helloParts[0]
                val clientNonce = helloParts[1]
                val serverNonce = SyncCrypto.nonce()
                writer.println(CHALLENGE_PREFIX + serverNonce)
                val response = reader.readLine().orEmpty().removePrefix(RESPONSE_PREFIX)
                val crypto = SyncCrypto(authToken)
                if (!SyncCrypto.constantTimeEquals(response, crypto.proof(clientNonce, serverNonce))) {
                    writer.println(AUTH_FAILED)
                    writer.println(END_MARKER)
                    return@use 0
                }
                if (!authorization(remoteDeviceId)) {
                    writer.println(AUTH_FAILED)
                    writer.println(END_MARKER)
                    return@use 0
                }
                onAuthorized(remoteDeviceId)
                writer.println(AUTH_OK)
                val acknowledgements = buildList {
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line == END_MARKER) break
                        if (line.length > MAX_LINE_LENGTH) {
                            throw IllegalArgumentException("Operacion de sincronizacion demasiado grande")
                        }
                        val operation = try {
                            json.decodeFromString<SyncOperation>(crypto.decrypt(line))
                        } catch (error: Exception) {
                            throw IllegalArgumentException("Payload de sincronizacion invalido", error)
                        }
                        if (operation.householdId.isBlank() || operation.operationId.isBlank()) {
                            throw IllegalArgumentException("Operacion de sincronizacion invalida")
                        }
                        if (operationHandler(operation)) add(operation.copy(applied = true))
                    }
                }
                acknowledgements.forEach { writer.println(crypto.encrypt(json.encodeToString(it))) }
                val acknowledgedIds = acknowledgements.mapTo(hashSetOf()) { it.operationId }
                operationProvider()
                    .filterNot { it.operationId in acknowledgedIds }
                    .forEach { writer.println(crypto.encrypt(json.encodeToString(it))) }
                writer.println(END_MARKER)
                acknowledgements.size
            }
        }
    }

    private fun readOperations(reader: BufferedReader, crypto: SyncCrypto): List<SyncOperation> = buildList {
        while (true) {
            val line = reader.readLine() ?: break
            if (line == END_MARKER) break
            if (line == AUTH_FAILED) throw SecurityException("Código de emparejamiento incorrecto")
            if (line == AUTH_OK) continue
            if (line.length > MAX_LINE_LENGTH) {
                throw IllegalArgumentException("Operacion de sincronizacion demasiado grande")
            }
            try {
                json.decodeFromString<SyncOperation>(crypto.decrypt(line)).let(::add)
            } catch (error: Exception) {
                throw IllegalArgumentException("Payload de sincronizacion invalido", error)
            }
        }
    }

    companion object {
        private const val END_MARKER = "__CASA_SYNC_END__"
        private const val HELLO_PREFIX = "CASA_HELLO:"
        private const val HELLO_SEPARATOR = ":"
        private const val CHALLENGE_PREFIX = "CASA_CHALLENGE:"
        private const val RESPONSE_PREFIX = "CASA_RESPONSE:"
        private const val AUTH_OK = "CASA_AUTH_OK"
        private const val AUTH_FAILED = "CASA_AUTH_FAILED"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val ACCEPT_TIMEOUT_MS = 30_000
        // Shopping photos are transported in the encrypted JSON operation.
        // Keep a bounded limit, but allow normal camera photos to sync.
        private const val MAX_LINE_LENGTH = 16 * 1024 * 1024
    }
}
