package com.majyd.casa.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskAssignee(val label: String) {
    UNASSIGNED("Sin asignar"),
    PERSON_ONE("Persona 1"),
    PERSON_TWO("Persona 2"),
    BOTH("Ambos"),
}

@Serializable
enum class TaskAssignmentType {
    NONE,
    PERSON,
    MULTIPLE_PEOPLE,
    GROUP,
    EVERYONE,
}

data class NormalizedTaskAssignment(
    val type: TaskAssignmentType,
    val personIds: Set<String>,
    val groupId: String?,
)

fun normalizeTaskAssignment(
    type: TaskAssignmentType,
    personIds: Set<String>,
    groupId: String?,
): NormalizedTaskAssignment = when (type) {
    TaskAssignmentType.NONE -> NormalizedTaskAssignment(TaskAssignmentType.NONE, emptySet(), null)
    TaskAssignmentType.PERSON -> personIds.filter(String::isNotBlank).sorted().firstOrNull()
        ?.let { NormalizedTaskAssignment(TaskAssignmentType.PERSON, setOf(it), null) }
        ?: NormalizedTaskAssignment(TaskAssignmentType.NONE, emptySet(), null)
    TaskAssignmentType.MULTIPLE_PEOPLE -> {
        val normalized = personIds.filter(String::isNotBlank).toSortedSet()
        if (normalized.isEmpty()) NormalizedTaskAssignment(TaskAssignmentType.NONE, emptySet(), null)
        else NormalizedTaskAssignment(TaskAssignmentType.MULTIPLE_PEOPLE, normalized, null)
    }
    TaskAssignmentType.GROUP -> groupId?.takeIf(String::isNotBlank)
        ?.let { NormalizedTaskAssignment(TaskAssignmentType.GROUP, emptySet(), it) }
        ?: NormalizedTaskAssignment(TaskAssignmentType.NONE, emptySet(), null)
    TaskAssignmentType.EVERYONE -> NormalizedTaskAssignment(TaskAssignmentType.EVERYONE, emptySet(), null)
}

data class TaskItem(
    val id: String,
    val title: String,
    val normalizedTitle: String,
    val notes: String,
    val assignee: TaskAssignee,
    val completed: Boolean,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
    val assignmentType: TaskAssignmentType = TaskAssignmentType.NONE,
    val dueDate: Long = 0L,
    val dueTimeMinutes: Int? = null,
    val reminderMinutesBefore: Int? = null,
    val completedAt: Long? = null,
    val completedByPersonId: String? = null,
    val completedByGroupId: String? = null,
    val personIds: Set<String> = emptySet(),
    val groupId: String? = null,
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val recurrenceSeriesId: String? = null,
)
