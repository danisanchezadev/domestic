package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.GroupMemberEntity
import com.majyd.casa.data.local.entities.HouseholdGroupEntity
import com.majyd.casa.data.local.entities.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseholdDao {
    @Query("SELECT * FROM people WHERE householdId = :householdId AND deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observePeople(householdId: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAllPeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM household_groups WHERE householdId = :householdId AND deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeGroups(householdId: String): Flow<List<HouseholdGroupEntity>>

    @Query("SELECT * FROM household_groups WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAllGroups(): Flow<List<HouseholdGroupEntity>>

    @Query("SELECT * FROM group_members")
    fun observeMembers(): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM people WHERE id = :personId LIMIT 1")
    suspend fun getPerson(personId: String): PersonEntity?

    @Query("SELECT COUNT(*) FROM people WHERE householdId = :householdId AND deleted = 0")
    suspend fun countActivePeople(householdId: String): Int

    @Query("SELECT * FROM household_groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroup(groupId: String): HouseholdGroupEntity?

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getMembers(groupId: String): List<GroupMemberEntity>

    @Query("SELECT * FROM household_groups WHERE id IN (SELECT groupId FROM group_members WHERE personId = :personId)")
    suspend fun getGroupsForMember(personId: String): List<HouseholdGroupEntity>

    @Upsert suspend fun upsertPerson(person: PersonEntity)
    @Upsert suspend fun upsertGroup(group: HouseholdGroupEntity)
    @Upsert suspend fun upsertMember(member: GroupMemberEntity)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND personId = :personId")
    suspend fun deleteMember(groupId: String, personId: String)

    @Query("UPDATE people SET name = :name, normalizedName = :normalizedName, updatedAt = :updatedAt, version = version + 1 WHERE id = :personId")
    suspend fun renamePerson(personId: String, name: String, normalizedName: String, updatedAt: Long)

    @Query("UPDATE people SET deleted = 1, updatedAt = :updatedAt, version = version + 1 WHERE id = :personId")
    suspend fun deletePerson(personId: String, updatedAt: Long)

    @Query("UPDATE household_groups SET name = :name, normalizedName = :normalizedName, updatedAt = :updatedAt, version = version + 1 WHERE id = :groupId")
    suspend fun renameGroup(groupId: String, name: String, normalizedName: String, updatedAt: Long)

    @Query("UPDATE household_groups SET deleted = 1, updatedAt = :updatedAt, version = version + 1 WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String, updatedAt: Long)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun clearMembers(groupId: String)

    @Query("DELETE FROM group_members WHERE personId = :personId")
    suspend fun clearPersonMemberships(personId: String)
}
