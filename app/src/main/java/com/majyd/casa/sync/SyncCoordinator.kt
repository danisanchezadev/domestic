package com.majyd.casa.sync

import com.majyd.casa.data.repository.SettingsRepository
import com.majyd.casa.data.repository.ShoppingRepository
import com.majyd.casa.data.local.dao.AuthorizedDeviceDao
import com.majyd.casa.data.local.entities.AuthorizedDeviceEntity
import com.majyd.casa.sync.transport.LocalWifiTransport
import com.majyd.casa.sync.transport.NsdDeviceDiscovery
import com.majyd.casa.sync.transport.NearbyDevice
import com.majyd.casa.sync.transport.WifiNetworkPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.FlowPreview

@Singleton
@OptIn(FlowPreview::class)
class SyncCoordinator @Inject constructor(
    private val deviceManager: DeviceManager,
    private val discovery: NsdDeviceDiscovery,
    private val transport: LocalWifiTransport,
    private val syncSession: SyncSession,
    private val settingsRepository: SettingsRepository,
    private val shoppingRepository: ShoppingRepository,
    private val authorizedDeviceDao: AuthorizedDeviceDao,
    private val wifiNetworkPolicy: WifiNetworkPolicy,
    private val changeNotifier: SyncChangeNotifier,
) {
    private val mutex = Mutex()
    private var job: Job? = null
    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val nearbyDevices = _nearbyDevices.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            val device = deviceManager.ensureDevice()
            shoppingRepository.ensureCatalogSyncOperations()
            launch {
                changeNotifier.changes
                    .debounce(AUTO_SYNC_DEBOUNCE_MS)
                    .collect {
                        synchronizeAutomatically()
                    }
            }
            authorizedDeviceDao.upsert(
                AuthorizedDeviceEntity(device.deviceId, device.householdId, device.displayName, System.currentTimeMillis())
            )
            settingsRepository.getPairingCode()
            launch {
                combine(
                    deviceManager.observeDevice().filterNotNull(),
                    settingsRepository.preferences,
                ) { current, preferences -> current to preferences.domesticWifiSsid }
                    .distinctUntilChangedBy { (current, ssid) -> Triple(current.householdId, current.deviceId, ssid) }
                    .collect { (current, ssid) ->
                        if (wifiNetworkPolicy.isAllowed(ssid)) {
                            discovery.advertise(current.displayName, current.deviceId, PORT, current.householdId)
                        } else {
                            discovery.stopAdvertising()
                        }
                    }
            }
            launch {
                while (currentCoroutineContext().isActive) {
                    try {
                        transport.acceptOnce(
                            port = PORT,
                            authToken = settingsRepository.getPairingCode(),
                            localDeviceId = device.deviceId,
                            operationHandler = { operation ->
                                operation.householdId == deviceManager.ensureDevice().householdId &&
                                    syncSession.applyIncoming(operation)
                            },
                            operationProvider = {
                                val current = deviceManager.ensureDevice()
                                // Send the complete local history so a newly paired device can bootstrap
                                // data already acknowledged by an earlier synchronization.
                                syncSession.all(current.householdId)
                            },
                            authorization = { remoteDeviceId ->
                                val current = deviceManager.ensureDevice()
                                val network = settingsRepository.preferences.first().domesticWifiSsid
                                if (!wifiNetworkPolicy.isAllowed(network)) {
                                    false
                                } else {
                                    authorizedDeviceDao.upsert(
                                        AuthorizedDeviceEntity(current.deviceId, current.householdId, current.displayName, System.currentTimeMillis())
                                    )
                                    val existing = authorizedDeviceDao.get(remoteDeviceId)
                                    AuthorizedDevicePolicy.canAccept(
                                        existing = existing,
                                        householdId = current.householdId,
                                        activeDeviceCount = authorizedDeviceDao.activeForHousehold(current.householdId).size,
                                        maxDevices = MAX_DEVICES,
                                    )
                                }
                            },
                            onAuthorized = { remoteDeviceId ->
                                val current = deviceManager.ensureDevice()
                                val existing = authorizedDeviceDao.get(remoteDeviceId)
                                authorizedDeviceDao.upsert(
                                    AuthorizedDeviceEntity(
                                        deviceId = remoteDeviceId,
                                        householdId = current.householdId,
                                        displayName = existing?.displayName ?: remoteDeviceId,
                                        lastSeenAt = System.currentTimeMillis(),
                                        revoked = false,
                                    )
                                )
                            },
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // The listener is long-lived; a broken peer must not stop it.
                    }
                    delay(RETRY_DELAY_MS)
                }
            }
            while (currentCoroutineContext().isActive) {
                try {
                    discovery.discover().collectLatest { devices ->
                        val current = deviceManager.ensureDevice()
                        val peers = devices.filter {
                            it.householdId == current.householdId &&
                                wifiNetworkPolicy.isAllowed(settingsRepository.preferences.first().domesticWifiSsid) &&
                                it.host.isNotBlank() &&
                                it.deviceId != current.deviceId
                        }
                        _nearbyDevices.value = peers
                        peers.forEach { peer ->
                            mutex.withLock {
                                try {
                                    syncSession.synchronize(
                                        peer.host,
                                        peer.port,
                                        current.householdId,
                                        settingsRepository.getPairingCode(),
                                    )
                                    val peerId = peer.deviceId ?: peer.serviceName
                                    authorizedDeviceDao.upsert(
                                        authorizedDeviceDao.get(peerId)?.copy(
                                            lastSeenAt = System.currentTimeMillis(),
                                            displayName = peer.serviceName,
                                        ) ?: AuthorizedDeviceEntity(peerId, current.householdId, peer.serviceName, System.currentTimeMillis())
                                    )
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    // Discovery remains active when one peer is unavailable.
                                }
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }
    }

    fun syncNow(scope: CoroutineScope) {
        scope.launch {
            runCatching { synchronizeManually() }
        }
    }

    suspend fun synchronizeManually(onProgress: (SyncProgress) -> Unit = {}): SyncAttemptResult {
        val startedAt = System.currentTimeMillis()
        val current = deviceManager.ensureDevice()
        shoppingRepository.ensureCatalogSyncOperations()
        if (!wifiNetworkPolicy.isAllowed(settingsRepository.preferences.first().domesticWifiSsid)) {
            throw SyncFailureException(SyncError.NetworkUnavailable)
        }
        return mutex.withLock {
            onProgress(SyncProgress(SyncPhase.DISCOVERING))
            val peer = try {
                withTimeout(DISCOVERY_TIMEOUT_MS) {
                    nearbyDevices
                        .filter { it.isNotEmpty() }
                        .first()
                        .first()
                }
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                throw SyncFailureException(SyncError.DeviceNotFound, error)
            } catch (error: Exception) {
                throw SyncFailureException(SyncError.DiscoveryFailed, error)
            }
            onProgress(SyncProgress(SyncPhase.CONNECTING, remoteDevice = peer.serviceName))
            val result = try {
                syncSession.synchronize(
                    peer.host,
                    peer.port,
                    current.householdId,
                    settingsRepository.getPairingCode(),
                    manual = true,
                ) { progress -> onProgress(progress.copy(remoteDevice = peer.serviceName)) }
            } catch (error: SyncFailureException) {
                throw error
            } catch (error: java.net.SocketTimeoutException) {
                throw SyncFailureException(SyncError.ReadTimeout, error)
            } catch (error: java.net.ConnectException) {
                throw SyncFailureException(SyncError.ConnectionTimeout, error)
            } catch (error: java.security.GeneralSecurityException) {
                throw SyncFailureException(SyncError.InvalidCredentials, error)
            } catch (error: SecurityException) {
                throw SyncFailureException(SyncError.UnauthorizedDevice, error)
            } catch (error: java.io.IOException) {
                throw SyncFailureException(SyncError.TransportInterrupted, error)
            } catch (error: IllegalArgumentException) {
                throw SyncFailureException(SyncError.ProtocolError, error)
            } catch (error: Exception) {
                throw SyncFailureException(SyncError.Unknown, error)
            }
            val finishedAt = System.currentTimeMillis()
            SyncAttemptResult(result, peer.serviceName, startedAt, finishedAt)
        }
    }

    suspend fun synchronizeAutomatically(): Boolean {
        return try {
            val current = deviceManager.ensureDevice()
            if (!wifiNetworkPolicy.isAllowed(settingsRepository.preferences.first().domesticWifiSsid)) return false
            val peer = withTimeout(DISCOVERY_TIMEOUT_MS) {
                nearbyDevices.filter { it.isNotEmpty() }.first().first()
            }
            mutex.withLock {
                syncSession.synchronize(
                    peer.host,
                    peer.port,
                    current.householdId,
                    settingsRepository.getPairingCode(),
                )
            }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val PORT = 45678
        private const val MAX_DEVICES = 2
        private const val RETRY_DELAY_MS = 1_000L
        private const val AUTO_SYNC_DEBOUNCE_MS = 250L
        private const val DISCOVERY_TIMEOUT_MS = 10_000L
    }
}
