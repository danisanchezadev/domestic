package com.majyd.casa.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majyd.casa.data.model.TaskAssignee
import com.majyd.casa.data.model.TaskAssignmentType
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Seeds one real operation only when explicitly requested by the two-installation smoke test. */
@RunWith(AndroidJUnit4::class)
class RealLocalWifiSyncSeederTest {
    @Test
    fun seedOperationForPeer() = runTest {
        assumeTrue(InstrumentationRegistry.getArguments().getString("casa_seed_e2e") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entryPoint = EntryPointAccessors.fromApplication(context, SyncAppEntryPoint::class.java)
        val local = entryPoint.deviceManager().ensureDevice()
        entryPoint.authorizedDeviceDao().activeForHousehold(local.householdId)
            .filter { it.deviceId != local.deviceId }
            .forEach { entryPoint.authorizedDeviceDao().delete(it.deviceId) }
        entryPoint.taskRepository().upsertTask(
            taskId = null,
            title = "E2E sync ${System.currentTimeMillis()}",
            notes = "Operación de prueba",
            assignee = TaskAssignee.UNASSIGNED,
            completed = false,
            dueDate = LocalDate.now().plusDays(1).toEpochDay(),
            assignmentType = TaskAssignmentType.EVERYONE,
        )
    }
}
