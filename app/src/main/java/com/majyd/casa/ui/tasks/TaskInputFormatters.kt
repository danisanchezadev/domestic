package com.majyd.casa.ui.tasks

import com.majyd.casa.data.model.TaskAssignmentType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object TaskInputFormatters {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-uuuu")
        .withResolverStyle(ResolverStyle.STRICT)

    fun formatDate(date: LocalDate): String = date.format(dateFormatter)

    fun parseDate(value: String): LocalDate? = runCatching {
        LocalDate.parse(value.trim(), dateFormatter)
    }.getOrNull()
}

fun toggleTaskPerson(
    assignmentType: TaskAssignmentType,
    selectedPersonIds: Set<String>,
    personId: String,
): Set<String> {
    if (assignmentType == TaskAssignmentType.PERSON) {
        return if (selectedPersonIds.contains(personId)) emptySet() else setOf(personId)
    }
    return if (selectedPersonIds.contains(personId)) {
        selectedPersonIds - personId
    } else {
        selectedPersonIds + personId
    }
}
