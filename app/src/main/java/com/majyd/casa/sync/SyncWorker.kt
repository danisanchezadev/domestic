package com.majyd.casa.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/** Periodic client-side fallback; the foreground coordinator remains responsible for fast sync. */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncAppEntryPoint::class.java,
        )
        return if (entryPoint.syncCoordinator().synchronizeAutomatically()) Result.success() else Result.retry()
    }
}
