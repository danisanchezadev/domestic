package com.majyd.casa.notifications

import com.majyd.casa.data.model.HouseholdState
import com.majyd.casa.data.model.TaskAssignmentType
import com.majyd.casa.data.model.TaskItem

object TaskNotificationPolicy {
    fun isRelevant(task: TaskItem, household: HouseholdState): Boolean {
        val personId = household.assignedPersonId ?: return task.assignmentType == TaskAssignmentType.EVERYONE
        return when (task.assignmentType) {
            TaskAssignmentType.EVERYONE -> true
            TaskAssignmentType.PERSON, TaskAssignmentType.MULTIPLE_PEOPLE -> task.personIds.contains(personId)
            TaskAssignmentType.GROUP -> task.groupId?.let { groupId ->
                household.groups.firstOrNull { it.id == groupId }?.memberIds?.contains(personId) == true
            } == true
            TaskAssignmentType.NONE -> false
        }
    }
}
