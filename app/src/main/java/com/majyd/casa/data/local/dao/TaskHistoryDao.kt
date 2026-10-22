package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.TaskHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskHistoryDao {
    @Query("SELECT * FROM task_history ORDER BY usageCount DESC, lastUsedAt DESC")
    fun observeAll(): Flow<List<TaskHistoryEntity>>

    @Upsert
    suspend fun upsert(history: TaskHistoryEntity)
}
