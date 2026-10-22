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

    @Query("SELECT * FROM task_history WHERE normalizedTitle = :normalizedTitle LIMIT 1")
    suspend fun getByNormalizedTitle(normalizedTitle: String): TaskHistoryEntity?

    @Upsert
    suspend fun upsert(history: TaskHistoryEntity)
}
