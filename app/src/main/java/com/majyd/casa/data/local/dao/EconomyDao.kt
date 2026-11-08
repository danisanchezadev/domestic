package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.EconomyCategoryEntity
import com.majyd.casa.data.local.entities.EconomyMovementEntity
import com.majyd.casa.data.local.entities.EconomyMonthlyTargetEntity
import com.majyd.casa.data.local.entities.EconomySettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EconomyDao {
    @Query("SELECT * FROM economy_categories WHERE householdId = (SELECT householdId FROM device_identity LIMIT 1) ORDER BY type ASC, name COLLATE NOCASE ASC")
    fun observeCategories(): Flow<List<EconomyCategoryEntity>>

    @Query("SELECT * FROM economy_categories WHERE householdId = (SELECT householdId FROM device_identity LIMIT 1) ORDER BY type ASC, name COLLATE NOCASE ASC")
    suspend fun getAllCategories(): List<EconomyCategoryEntity>

    @Query("SELECT * FROM economy_categories WHERE id = :id AND householdId = (SELECT householdId FROM device_identity LIMIT 1) LIMIT 1")
    suspend fun getCategory(id: String): EconomyCategoryEntity?

    @Query("SELECT * FROM economy_categories WHERE normalizedName = :normalizedName AND type = :type AND householdId = (SELECT householdId FROM device_identity LIMIT 1) LIMIT 1")
    suspend fun getCategoryByName(normalizedName: String, type: String): EconomyCategoryEntity?

    @Upsert suspend fun upsertCategory(value: EconomyCategoryEntity)

    @Query("SELECT * FROM economy_movements WHERE deleted = 0 AND householdId = (SELECT householdId FROM device_identity LIMIT 1) ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeMovements(): Flow<List<EconomyMovementEntity>>

    @Query("SELECT * FROM economy_movements WHERE deleted = 0 AND householdId = (SELECT householdId FROM device_identity LIMIT 1) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeMovementsBetween(startEpochDay: Long, endEpochDay: Long): Flow<List<EconomyMovementEntity>>

    @Query("SELECT * FROM economy_movements WHERE deleted = 0 AND householdId = (SELECT householdId FROM device_identity LIMIT 1) AND dateEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY dateEpochDay DESC, createdAt DESC")
    suspend fun getMovementsBetween(startEpochDay: Long, endEpochDay: Long): List<EconomyMovementEntity>

    @Query("SELECT * FROM economy_movements WHERE deleted = 0 AND householdId = (SELECT householdId FROM device_identity LIMIT 1) ORDER BY dateEpochDay DESC, createdAt DESC")
    suspend fun getAllMovements(): List<EconomyMovementEntity>

    @Query("SELECT * FROM economy_movements WHERE id = :id AND householdId = (SELECT householdId FROM device_identity LIMIT 1) LIMIT 1")
    suspend fun getMovement(id: String): EconomyMovementEntity?

    @Upsert suspend fun upsertMovement(value: EconomyMovementEntity)

    @Query("SELECT * FROM economy_monthly_targets WHERE householdId = :householdId ORDER BY monthKey DESC")
    suspend fun getTargets(householdId: String): List<EconomyMonthlyTargetEntity>

    @Query("SELECT * FROM economy_monthly_targets WHERE householdId = :householdId AND monthKey = :monthKey LIMIT 1")
    suspend fun getTarget(householdId: String, monthKey: String): EconomyMonthlyTargetEntity?

    @Upsert suspend fun upsertTarget(value: EconomyMonthlyTargetEntity)

    @Query("SELECT * FROM economy_settings WHERE householdId = :householdId LIMIT 1")
    suspend fun getSettings(householdId: String): EconomySettingsEntity?

    @Upsert suspend fun upsertSettings(value: EconomySettingsEntity)
}
