package com.majyd.casa.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Optional two-installation smoke test. It is enabled with casa_e2e=true and
 * expects the peer APK to be reachable through the emulator host bridge.
 */
@RunWith(AndroidJUnit4::class)
class RealLocalWifiSyncE2eTest {
    @Test
    fun realAppClientReceivesOperationsFromRealPeer() = runTest {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("casa_e2e") == "true")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entryPoint = EntryPointAccessors.fromApplication(context, SyncAppEntryPoint::class.java)
        val local = entryPoint.deviceManager().ensureDevice()
        val host = arguments.getString("casa_host") ?: "10.0.2.2"
        val port = arguments.getString("casa_port")?.toIntOrNull() ?: 45679
        val token = arguments.getString("casa_token") ?: error("casa_token es obligatorio")

        val result = entryPoint.syncSession().synchronize(host, port, local.householdId, token)

        assertTrue("El cliente real no recibio operaciones", result.received > 0)
        assertTrue("El motor real no aplico operaciones", result.applied > 0)
    }
}
