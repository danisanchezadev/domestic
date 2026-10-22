package com.majyd.casa.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow

@Singleton
class SyncChangeNotifier @Inject constructor() {
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    fun request() {
        changes.tryEmit(Unit)
    }
}
