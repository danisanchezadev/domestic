package com.majyd.casa.data.repository

import com.majyd.casa.data.model.HouseholdState
import com.majyd.casa.data.model.Person
import com.majyd.casa.data.model.HouseholdGroup
import com.majyd.casa.data.model.PersonDeletionResult
import kotlinx.coroutines.flow.Flow

interface HouseholdRepository {
    fun observeState(): Flow<HouseholdState>
    suspend fun ensureDefaults(): HouseholdState
    suspend fun addPerson(name: String): Person?
    suspend fun renamePerson(personId: String, name: String): Person?
    suspend fun deletePerson(personId: String): PersonDeletionResult
    suspend fun resolvePersonTasks(personId: String, replacementPersonId: String?): PersonDeletionResult
    suspend fun addGroup(name: String, memberIds: Set<String> = emptySet()): HouseholdGroup?
    suspend fun renameGroup(groupId: String, name: String): HouseholdGroup?
    suspend fun setGroupMembers(groupId: String, memberIds: Set<String>): HouseholdGroup?
    suspend fun deleteGroup(groupId: String): HouseholdGroup?
}
