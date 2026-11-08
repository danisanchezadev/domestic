package com.majyd.casa.data.repository

import com.majyd.casa.data.model.EconomyCategory
import com.majyd.casa.data.model.EconomyMonthlyTarget
import com.majyd.casa.data.model.EconomyMovement
import com.majyd.casa.data.model.EconomyMovementType
import com.majyd.casa.data.model.EconomySettings
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface EconomyRepository {
    fun observeCategories(): Flow<List<EconomyCategory>>
    fun observeMovements(): Flow<List<EconomyMovement>>
    fun observeMovements(month: YearMonth): Flow<List<EconomyMovement>>
    suspend fun getMovementsBetween(start: YearMonth, end: YearMonth): List<EconomyMovement>
    suspend fun getAllMovements(): List<EconomyMovement>
    suspend fun ensureDefaults()
    suspend fun saveMovement(id: String?, type: EconomyMovementType, amountCents: Long, categoryId: String, dateEpochDay: Long = java.time.LocalDate.now().toEpochDay()): EconomyMovement?
    suspend fun deleteMovement(id: String): EconomyMovement?
    suspend fun saveCategory(id: String?, type: EconomyMovementType, name: String): EconomyCategory?
    suspend fun archiveCategory(id: String)
    suspend fun getTarget(monthKey: String): EconomyMonthlyTarget
    suspend fun saveTarget(monthKey: String, targetCents: Long, minimumCents: Long): EconomyMonthlyTarget?
    suspend fun getSettings(): EconomySettings
    suspend fun saveSettings(settings: EconomySettings): EconomySettings
}
