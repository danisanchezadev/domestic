package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.TaskEntity
import com.majyd.casa.data.local.entities.TaskGroupAssignmentEntity
import com.majyd.casa.data.local.entities.TaskPersonAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY deleted ASC, completed ASC, updatedAt DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getById(taskId: String): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun permanentlyDelete(taskId: String)

    @Query("UPDATE tasks SET completed = :completed, updatedAt = :updatedAt, version = version + 1 WHERE id = :taskId")
    suspend fun updateCompleted(taskId: String, completed: Boolean, updatedAt: Long)

    @Query("UPDATE tasks SET deleted = 1, updatedAt = :updatedAt, version = version + 1 WHERE id = :taskId")
    suspend fun softDelete(taskId: String, updatedAt: Long)

    @Query("UPDATE tasks SET deleted = 0, updatedAt = :updatedAt, version = version + 1 WHERE id = :taskId")
    suspend fun restore(taskId: String, updatedAt: Long)

    @Query("UPDATE tasks SET deleted = 1, updatedAt = :updatedAt, version = version + 1 WHERE completed = 1 AND deleted = 0")
    suspend fun archiveCompleted(updatedAt: Long)

    @Upsert suspend fun upsertPersonAssignment(assignment: TaskPersonAssignmentEntity)
    @Upsert suspend fun upsertGroupAssignment(assignment: TaskGroupAssignmentEntity)

    @Query("DELETE FROM task_person_assignments WHERE taskId = :taskId")
    suspend fun clearPersonAssignments(taskId: String)

    @Query("DELETE FROM task_group_assignments WHERE taskId = :taskId")
    suspend fun clearGroupAssignments(taskId: String)

    @Query("SELECT COUNT(*) FROM tasks WHERE deleted = 0 AND completed = 0 AND id IN (SELECT taskId FROM task_person_assignments WHERE personId = :personId)")
    suspend fun countActiveTasksForPerson(personId: String): Int

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND completed = 0 AND id IN (SELECT taskId FROM task_person_assignments WHERE personId = :personId)")
    suspend fun getActiveTasksForPerson(personId: String): List<TaskEntity>

    @Query("SELECT * FROM task_person_assignments WHERE taskId = :taskId")
    suspend fun getPersonAssignments(taskId: String): List<TaskPersonAssignmentEntity>

    @Query("SELECT * FROM task_group_assignments WHERE taskId = :taskId")
    suspend fun getGroupAssignments(taskId: String): List<TaskGroupAssignmentEntity>

    @Query("SELECT * FROM task_person_assignments")
    fun observePersonAssignments(): Flow<List<TaskPersonAssignmentEntity>>

    @Query("SELECT * FROM task_group_assignments")
    fun observeGroupAssignments(): Flow<List<TaskGroupAssignmentEntity>>
}
