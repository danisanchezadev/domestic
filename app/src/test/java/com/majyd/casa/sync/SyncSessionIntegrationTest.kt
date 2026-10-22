package com.majyd.casa.sync

import com.majyd.casa.data.model.DeviceIdentity
import com.majyd.casa.data.model.AppPreferences
import com.majyd.casa.data.repository.SettingsRepository
import com.majyd.casa.sync.transport.SocketLocalWifiTransport
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSessionIntegrationTest {
    @Test
    fun twoSessionsExchangeAcknowledgedAndIncomingOperations() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val clientRepository = MemoryOperationRepository()
        val serverRepository = MemoryOperationRepository()
        val clientEngine = RecordingEngine(clientRepository)
        val serverEngine = RecordingEngine(serverRepository)
        val localOperation = operation("local-op", "device-a")
        val remoteOperation = operation("remote-op", "device-b")
        clientRepository.record(localOperation)
        val transport = SocketLocalWifiTransport()
        val server = launch(Dispatchers.IO) {
            transport.acceptOnce(
                port = port,
                authToken = TOKEN,
                localDeviceId = "device-b",
                operationHandler = serverEngine::apply,
                operationProvider = { listOf(remoteOperation) },
                authorization = { it == "device-a" },
            )
        }

        delay(100)
        val settings = FakeSettingsRepository()
        val session = SyncSession(clientRepository, clientEngine, transport, FakeDeviceManager("device-a"), settings)
        val phases = mutableListOf<SyncPhase>()
        val result = session.synchronize("127.0.0.1", port, "home-1", TOKEN) { phases += it.phase }
        server.join()

        assertEquals(1, result.sent)
        assertEquals(1, result.acknowledged)
        assertEquals(1, result.received)
        assertEquals(1, result.applied)
        assertTrue(serverEngine.appliedIds.contains(localOperation.operationId))
        assertTrue(clientEngine.appliedIds.contains(remoteOperation.operationId))
        assertTrue(clientRepository.get(localOperation.operationId)?.applied == true)
        assertTrue(settings.preferences.first().lastSyncAt != null)
        assertTrue(phases.contains(SyncPhase.CONNECTING))
        assertTrue(phases.contains(SyncPhase.AUTHENTICATING))
        assertTrue(phases.contains(SyncPhase.SYNCHRONIZING))
    }

    private fun operation(id: String, deviceId: String) = SyncOperation(
        operationId = id,
        householdId = "home-1",
        entityType = "task",
        entityId = "task-$id",
        payload = "{}",
        version = 1L,
        createdAt = 1L,
        updatedAt = 1L,
        deviceId = deviceId,
        deleted = false,
        applied = false,
    )

    private class RecordingEngine(private val repository: MemoryOperationRepository) : SyncEngine {
        val appliedIds = mutableSetOf<String>()

        override suspend fun apply(operation: SyncOperation): Boolean {
            appliedIds += operation.operationId
            repository.record(operation.copy(applied = true))
            return true
        }
    }

    private class MemoryOperationRepository : SyncOperationRepository {
        private val operations = linkedMapOf<String, SyncOperation>()

        override suspend fun enqueue(householdId: String, deviceId: String, entityType: String, entityId: String, payload: String, version: Long, updatedAt: Long, deleted: Boolean, operationType: String, createdAt: Long) {
            record(SyncOperation(UUID_PLACEHOLDER, householdId, entityType, entityId, operationType, payload, version, createdAt, updatedAt, deviceId, deleted, false))
        }

        override fun observePending(householdId: String): Flow<List<SyncOperation>> = MutableStateFlow(operations.values.filter { it.householdId == householdId && !it.applied })
        override suspend fun markApplied(operationId: String) { operations[operationId]?.let { operations[operationId] = it.copy(applied = true) } }
        override suspend fun get(operationId: String): SyncOperation? = operations[operationId]
        override suspend fun record(operation: SyncOperation) { operations[operation.operationId] = operation }
    }

    private class FakeDeviceManager(private val id: String) : DeviceManager {
        private val identity = DeviceIdentity(id, "home-1", null, id, 1L, 1L)
        override fun observeDevice(): Flow<DeviceIdentity?> = MutableStateFlow(identity)
        override suspend fun ensureDevice(): DeviceIdentity = identity
        override suspend fun renameDevice(displayName: String): DeviceIdentity = identity.copy(displayName = displayName)
        override suspend fun assignPerson(personId: String?): DeviceIdentity = identity.copy(assignedPersonId = personId)
        override suspend fun joinHousehold(householdId: String): DeviceIdentity = identity.copy(householdId = householdId)
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(AppPreferences())
        override val preferences: Flow<AppPreferences> = state
        override suspend fun update(block: (AppPreferences) -> AppPreferences) { state.value = block(state.value) }
        override suspend fun setDarkMode(enabled: Boolean) { update { it.copy(darkMode = enabled) } }
        override suspend fun getPairingCode(): String = "123456"
        override suspend fun setPairingCode(code: String) = Unit
    }

    companion object {
        private const val TOKEN = "123456"
        private const val UUID_PLACEHOLDER = "generated"
    }
}
