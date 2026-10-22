package com.majyd.casa.sync

interface SyncEngine {
    suspend fun apply(operation: SyncOperation): Boolean
}
