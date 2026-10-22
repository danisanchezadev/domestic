package com.majyd.casa.sync.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class NsdDeviceDiscovery @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val multicastLock = context.getSystemService(WifiManager::class.java)
        ?.createMulticastLock("casa-nsd")
        ?.apply { setReferenceCounted(true) }
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun advertise(deviceName: String, deviceId: String, port: Int, householdId: String) {
        stopAdvertising()
        multicastLock?.acquire()
        val info = NsdServiceInfo().apply {
            serviceName = "Casa-$deviceName-$deviceId"
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute(ATTRIBUTE_HOUSEHOLD, householdId)
            setAttribute(ATTRIBUTE_DEVICE_ID, deviceId)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopAdvertising() {
        registrationListener?.let { listener ->
            runCatching { nsdManager.unregisterService(listener) }
        }
        registrationListener = null
        releaseMulticastLock()
    }

    fun discover(): Flow<List<NearbyDevice>> = callbackFlow {
        multicastLock?.acquire()
        val discovered = linkedMapOf<String, NearbyDevice>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("NSD discovery failed: $errorCode"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.trimEnd('.') == SERVICE_TYPE.trimEnd('.')) {
                    nsdManager.resolveService(serviceInfo, resolver(discovered) { trySend(it) })
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                discovered.remove(serviceInfo.serviceName)
                trySend(discovered.values.toList())
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
            releaseMulticastLock()
        }
    }.distinctUntilChanged()

    private fun resolver(
        discovered: MutableMap<String, NearbyDevice>,
        publish: (List<NearbyDevice>) -> Unit,
    ) = object : NsdManager.ResolveListener {
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val device = NearbyDevice(
                serviceName = serviceInfo.serviceName,
                host = serviceInfo.host.hostAddress.orEmpty(),
                port = serviceInfo.port,
                householdId = serviceInfo.attributes[ATTRIBUTE_HOUSEHOLD]?.toString(Charsets.UTF_8),
                deviceId = serviceInfo.attributes[ATTRIBUTE_DEVICE_ID]?.toString(Charsets.UTF_8),
            )
            discovered[device.serviceName] = device
            publish(discovered.values.toList())
        }
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
    }

    companion object {
        const val SERVICE_TYPE = "_casa._tcp."
        private const val ATTRIBUTE_HOUSEHOLD = "household"
        private const val ATTRIBUTE_DEVICE_ID = "deviceId"
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
    }
}
