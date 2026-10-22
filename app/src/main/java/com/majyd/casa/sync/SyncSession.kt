package com.majyd.casa.sync

import com.majyd.casa.data.repository.SettingsRepository
import com.majyd.casa.sync.transport.LocalWifiTransport
import com.majyd.casa.sync.transport.TransportPhase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class SyncResult(
    val sent: Int,
    val acknowledged: Int,
    val received: Int,
    val applied: Int,
)

@Singleton
class SyncSession @Inject constructor(
    private val operationRepository: SyncOperationRepository,
    private val syncEngine: SyncEngine,
    private val transport: LocalWifiTransport,
    private val deviceManager: DeviceManager,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun pending(householdId: String): List<SyncOperation> = operationRepository.observePending(householdId).first()

    suspend fun all(householdId: String): List<SyncOperation> = operationRepository.observeAll(householdId).first()

    suspend fun applyIncoming(operation: SyncOperation): Boolean = syncEngine.apply(operation)

    suspend fun synchronize(
        host: String,
        port: Int,
        householdId: String,
        authToken: String,
        manual: Boolean = false,
        onProgress: (SyncProgress) -> Unit = {},
    ): SyncResult {
        val pending = operationRepository.observePending(householdId).first()
        val incoming = transport.exchange(
            host,
            port,
            authToken,
            pending,
            deviceManager.ensureDevice().deviceId,
        ) { phase ->
            onProgress(
                SyncProgress(
                    phase = phase.toSyncPhase(),
                    sent = pending.size,
                ),
            )
        }
        var applied = 0
        var acknowledged = 0
        incoming.forEach { operation ->
            if (operation.applied && pending.any { it.operationId == operation.operationId }) {
                operationRepository.markApplied(operation.operationId)
                acknowledged++
            } else if (operation.householdId == householdId) {
                if (!syncEngine.apply(operation)) {
                    throw SyncFailureException(SyncError.DatabaseApplyError)
                }
                applied++
            } else {
                throw SyncFailureException(SyncError.HouseholdMismatch)
            }
        }
        val completedAt = System.currentTimeMillis()
        settingsRepository.update {
            it.copy(
                lastSyncAt = completedAt,
                lastManualSyncAt = if (manual) completedAt else it.lastManualSyncAt,
            )
        }
        val received = incoming.count { !it.applied }
        onProgress(SyncProgress(SyncPhase.SYNCHRONIZING, sent = pending.size, received = received, acknowledged = acknowledged))
        return SyncResult(pending.size, acknowledged, received, applied)
    }

    private fun TransportPhase.toSyncPhase(): SyncPhase = when (this) {
        TransportPhase.CONNECTING -> SyncPhase.CONNECTING
        TransportPhase.AUTHENTICATING -> SyncPhase.AUTHENTICATING
        TransportPhase.SYNCHRONIZING -> SyncPhase.SYNCHRONIZING
    }
}
