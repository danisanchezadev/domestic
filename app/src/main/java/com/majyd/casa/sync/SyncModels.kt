package com.majyd.casa.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncOperation(
    val operationId: String,
    val householdId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String = "UPDATE",
    val payload: String,
    val version: Long,
    val createdAt: Long = 0L,
    val updatedAt: Long,
    val deviceId: String,
    val deleted: Boolean,
    val applied: Boolean,
)

enum class SyncPhase {
    IDLE,
    DISCOVERING,
    CONNECTING,
    AUTHENTICATING,
    SYNCHRONIZING,
    SUCCESS,
    NO_CHANGES,
    FAILED,
}

sealed interface SyncError {
    data object NetworkUnavailable : SyncError
    data object DiscoveryFailed : SyncError
    data object DeviceNotFound : SyncError
    data object ConnectionTimeout : SyncError
    data object ReadTimeout : SyncError
    data object UnauthorizedDevice : SyncError
    data object InvalidCredentials : SyncError
    data object HouseholdMismatch : SyncError
    data object ProtocolError : SyncError
    data object TransportInterrupted : SyncError
    data object DatabaseApplyError : SyncError
    data object AlreadyRunning : SyncError
    data object Cancelled : SyncError
    data object Unknown : SyncError
}

class SyncFailureException(
    val error: SyncError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

data class SyncProgress(
    val phase: SyncPhase,
    val remoteDevice: String? = null,
    val sent: Int = 0,
    val received: Int = 0,
    val acknowledged: Int = 0,
)

data class SyncAttemptResult(
    val result: SyncResult,
    val remoteDevice: String,
    val startedAt: Long,
    val finishedAt: Long,
) {
    val durationMs: Long get() = (finishedAt - startedAt).coerceAtLeast(0L)
    val phase: SyncPhase get() = if (result.sent == 0 && result.received == 0) SyncPhase.NO_CHANGES else SyncPhase.SUCCESS
}
