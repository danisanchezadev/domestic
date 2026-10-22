package com.majyd.casa.data.repository

import com.majyd.casa.data.model.TaskAssignee
import com.majyd.casa.data.model.TaskItem
import com.majyd.casa.data.model.TaskAssignmentType
import com.majyd.casa.data.model.TaskRecurrence
import com.majyd.casa.data.model.TaskSuggestion
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasks(): Flow<List<TaskItem>>
    fun observeSuggestions(): Flow<List<TaskSuggestion>>
    suspend fun ensureDefaults()
    suspend fun upsertTask(
        taskId: String?,
        title: String,
        notes: String,
        assignee: TaskAssignee,
        completed: Boolean,
        dueDate: Long = 0L,
        dueTimeMinutes: Int? = null,
        reminderMinutesBefore: Int? = null,
        assignmentType: TaskAssignmentType = TaskAssignmentType.NONE,
        personIds: Set<String> = emptySet(),
        groupId: String? = null,
        recurrence: TaskRecurrence = TaskRecurrence.NONE,
        recurrenceSeriesId: String? = null,
    ): TaskItem
    suspend fun toggleCompleted(
        taskId: String,
        completed: Boolean,
        completedByPersonId: String? = null,
        completedByGroupId: String? = null,
    ): TaskItem?
    suspend fun deleteTask(taskId: String): TaskItem?
    suspend fun permanentlyDeleteTask(taskId: String): TaskItem?
    suspend fun restoreTask(taskId: String): TaskItem?
    suspend fun postponeTask(taskId: String, days: Long): TaskItem?
    suspend fun archiveCompleted()
}
