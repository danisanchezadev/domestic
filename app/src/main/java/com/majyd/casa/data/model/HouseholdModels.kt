package com.majyd.casa.data.model

data class Person(
    val id: String,
    val householdId: String,
    val name: String,
    val updatedAt: Long,
)

data class HouseholdGroup(
    val id: String,
    val householdId: String,
    val name: String,
    val memberIds: Set<String>,
    val updatedAt: Long,
)

data class HouseholdState(
    val householdId: String = "",
    val assignedPersonId: String? = null,
    val people: List<Person> = emptyList(),
    val groups: List<HouseholdGroup> = emptyList(),
)

data class PersonDeletionResult(
    val deleted: Boolean,
    val activeTaskCount: Int,
)
