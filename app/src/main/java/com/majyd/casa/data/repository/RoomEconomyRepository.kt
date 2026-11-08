package com.majyd.casa.data.repository

import androidx.room.withTransaction
import com.majyd.casa.data.local.dao.EconomyDao
import com.majyd.casa.data.local.database.CasaDatabase
import com.majyd.casa.data.local.entities.EconomyCategoryEntity
import com.majyd.casa.data.local.entities.EconomyMovementEntity
import com.majyd.casa.data.local.entities.EconomyMonthlyTargetEntity
import com.majyd.casa.data.local.entities.EconomySettingsEntity
import com.majyd.casa.data.model.EconomyCategory
import com.majyd.casa.data.model.EconomyMonthlyTarget
import com.majyd.casa.data.model.EconomyMovement
import com.majyd.casa.data.model.EconomyMovementType
import com.majyd.casa.sync.DeviceManager
import com.majyd.casa.sync.EconomyCategorySyncPayload
import com.majyd.casa.sync.EconomyMovementSyncPayload
import com.majyd.casa.sync.EconomySyncPayloadCodec
import com.majyd.casa.sync.EconomyTargetSyncPayload
import com.majyd.casa.sync.EconomySettingsSyncPayload
import com.majyd.casa.sync.SyncOperationRepository
import com.majyd.casa.util.Normalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomEconomyRepository @Inject constructor(
    private val database: CasaDatabase,
    private val economyDao: EconomyDao,
    private val deviceManager: DeviceManager,
    private val syncOperationRepository: SyncOperationRepository,
) : EconomyRepository {
    override fun observeCategories(): Flow<List<EconomyCategory>> = economyDao.observeCategories().map { it.map(::categoryModel) }
    override fun observeMovements(): Flow<List<EconomyMovement>> = economyDao.observeMovements().map { it.map(::movementModel) }
    override fun observeMovements(month: java.time.YearMonth): Flow<List<EconomyMovement>> = economyDao.observeMovementsBetween(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()).map { it.map(::movementModel) }
    override suspend fun getMovementsBetween(start: java.time.YearMonth, end: java.time.YearMonth): List<EconomyMovement> = economyDao.getMovementsBetween(start.atDay(1).toEpochDay(), end.atEndOfMonth().toEpochDay()).map(::movementModel)
    override suspend fun getAllMovements(): List<EconomyMovement> = economyDao.getAllMovements().map(::movementModel)

    override suspend fun ensureDefaults() = database.withTransaction {
        val device = deviceManager.ensureDevice()
        listOf(
            EconomyMovementType.INCOME to listOf("Nómina", "Aportación familiar", "Devoluciones", "Otros ingresos"),
            EconomyMovementType.EXPENSE to listOf("Compra", "Vivienda", "Suministros", "Transporte", "Ocio", "Salud", "Otros gastos"),
            EconomyMovementType.INVESTMENT to listOf("Ahorro invertido", "Fondos", "Acciones", "Otros"),
        ).forEach { (type, names) -> names.forEach { name ->
            val normalized = Normalizer.normalizeText(name)
            if (economyDao.getCategoryByName(normalized, type.name) == null) {
                val now = System.currentTimeMillis()
                val entity = EconomyCategoryEntity("default-economy-${type.name}-$normalized", device.householdId, type.name, name, normalized, false, now, now, 1, device.deviceId)
                economyDao.upsertCategory(entity)
                enqueueCategory(entity, "CREATE")
            }
        } }
    }

    override suspend fun saveMovement(id: String?, type: EconomyMovementType, amountCents: Long, categoryId: String, dateEpochDay: Long): EconomyMovement? = database.withTransaction {
        if (amountCents <= 0) return@withTransaction null
        val category = economyDao.getCategory(categoryId) ?: return@withTransaction null
        if (category.deleted || category.type != type.name) return@withTransaction null
        val device = deviceManager.ensureDevice()
        val current = id?.let { economyDao.getMovement(it) }
        val now = System.currentTimeMillis()
        val entity = EconomyMovementEntity(current?.id ?: UUID.randomUUID().toString(), device.householdId, type.name, amountCents, categoryId, dateEpochDay, false, current?.createdAt ?: now, now, (current?.version ?: 0) + 1, device.deviceId)
        economyDao.upsertMovement(entity)
        enqueueMovement(entity, if (current == null) "CREATE" else "UPDATE")
        movementModel(entity)
    }

    override suspend fun deleteMovement(id: String): EconomyMovement? = database.withTransaction {
        val current = economyDao.getMovement(id) ?: return@withTransaction null
        val device = deviceManager.ensureDevice()
        val updated = current.copy(deleted = true, updatedAt = System.currentTimeMillis(), version = current.version + 1, updatedByDevice = device.deviceId)
        economyDao.upsertMovement(updated)
        enqueueMovement(updated, "DELETE")
        movementModel(updated)
    }

    override suspend fun saveCategory(id: String?, type: EconomyMovementType, name: String): EconomyCategory? = database.withTransaction {
        val clean = name.trim()
        if (clean.isBlank()) return@withTransaction null
        val normalized = Normalizer.normalizeText(clean)
        val duplicate = economyDao.getCategoryByName(normalized, type.name)
        if (duplicate != null && duplicate.id != id) return@withTransaction null
        val device = deviceManager.ensureDevice()
        val current = id?.let { economyDao.getCategory(it) }
        val now = System.currentTimeMillis()
        val entity = EconomyCategoryEntity(current?.id ?: UUID.randomUUID().toString(), device.householdId, type.name, clean, normalized, false, current?.createdAt ?: now, now, (current?.version ?: 0) + 1, device.deviceId)
        economyDao.upsertCategory(entity)
        enqueueCategory(entity, if (current == null) "CREATE" else "UPDATE")
        categoryModel(entity)
    }

    override suspend fun archiveCategory(id: String) = database.withTransaction {
        val current = economyDao.getCategory(id) ?: return@withTransaction
        val device = deviceManager.ensureDevice()
        val updated = current.copy(deleted = true, updatedAt = System.currentTimeMillis(), version = current.version + 1, updatedByDevice = device.deviceId)
        economyDao.upsertCategory(updated)
        enqueueCategory(updated, "DELETE")
    }

    override suspend fun getTarget(monthKey: String): EconomyMonthlyTarget = database.withTransaction {
        val device = deviceManager.ensureDevice()
        val settings = economyDao.getSettings(device.householdId)
        EconomyMonthlyTarget(monthKey, settings?.targetCents ?: 0L, settings?.minimumCents ?: 0L)
    }

    override suspend fun saveTarget(monthKey: String, targetCents: Long, minimumCents: Long): EconomyMonthlyTarget? = database.withTransaction {
        if (targetCents < 0 || minimumCents < 0 || minimumCents > targetCents) return@withTransaction null
        val device = deviceManager.ensureDevice()
        val currentSettings = economyDao.getSettings(device.householdId)
        val now = System.currentTimeMillis()
        val settings = EconomySettingsEntity(device.householdId, currentSettings?.fiscalStartMonth ?: 1, currentSettings?.currency ?: "EUR", targetCents, minimumCents, now, (currentSettings?.version ?: 0) + 1, device.deviceId)
        economyDao.upsertSettings(settings)
        enqueueSettings(settings, if (currentSettings == null) "CREATE" else "UPDATE")
        EconomyMonthlyTarget(monthKey, targetCents, minimumCents)
    }

    override suspend fun getSettings(): com.majyd.casa.data.model.EconomySettings = database.withTransaction {
        val device = deviceManager.ensureDevice()
        val existing = economyDao.getSettings(device.householdId)
        if (existing != null) return@withTransaction com.majyd.casa.data.model.EconomySettings(existing.fiscalStartMonth, existing.currency, existing.targetCents, existing.minimumCents)
        val now = System.currentTimeMillis()
        val previousTarget = economyDao.getTargets(device.householdId).maxByOrNull { it.updatedAt }
        val created = EconomySettingsEntity(device.householdId, 1, "EUR", previousTarget?.targetCents ?: 0L, previousTarget?.minimumCents ?: 0L, now, 1, device.deviceId)
        economyDao.upsertSettings(created)
        enqueueSettings(created, "CREATE")
        com.majyd.casa.data.model.EconomySettings(targetCents = created.targetCents, minimumCents = created.minimumCents)
    }

    override suspend fun saveSettings(settings: com.majyd.casa.data.model.EconomySettings): com.majyd.casa.data.model.EconomySettings = database.withTransaction {
        val device = deviceManager.ensureDevice()
        val current = economyDao.getSettings(device.householdId)
        val updated = EconomySettingsEntity(device.householdId, settings.fiscalStartMonth.coerceIn(1, 12), "EUR", settings.targetCents.coerceAtLeast(0L), settings.minimumCents.coerceAtLeast(0L), System.currentTimeMillis(), (current?.version ?: 0) + 1, device.deviceId)
        economyDao.upsertSettings(updated)
        enqueueSettings(updated, if (current == null) "CREATE" else "UPDATE")
        com.majyd.casa.data.model.EconomySettings(updated.fiscalStartMonth, updated.currency, updated.targetCents, updated.minimumCents)
    }

    private suspend fun enqueueCategory(e: EconomyCategoryEntity, operation: String) = syncOperationRepository.enqueue(e.householdId, e.updatedByDevice, "economy_category", e.id, EconomySyncPayloadCodec.encode(EconomyCategorySyncPayload(e.id, e.householdId, e.type, e.name, e.normalizedName, e.deleted, e.createdAt, e.updatedAt, e.version, e.updatedByDevice)), e.version, e.updatedAt, e.deleted, operation)
    private suspend fun enqueueMovement(e: EconomyMovementEntity, operation: String) = syncOperationRepository.enqueue(e.householdId, e.updatedByDevice, "economy_movement", e.id, EconomySyncPayloadCodec.encode(EconomyMovementSyncPayload(e.id, e.householdId, e.type, e.amountCents, e.categoryId, e.dateEpochDay, e.deleted, e.createdAt, e.updatedAt, e.version, e.updatedByDevice)), e.version, e.updatedAt, e.deleted, operation)
    private suspend fun enqueueTarget(e: EconomyMonthlyTargetEntity, operation: String) = syncOperationRepository.enqueue(e.householdId, e.updatedByDevice, "economy_target", "${e.householdId}:${e.monthKey}", EconomySyncPayloadCodec.encode(EconomyTargetSyncPayload(e.householdId, e.monthKey, e.targetCents, e.minimumCents, e.createdAt, e.updatedAt, e.version, e.updatedByDevice)), e.version, e.updatedAt, false, operation)
    private suspend fun enqueueSettings(e: EconomySettingsEntity, operation: String) = syncOperationRepository.enqueue(e.householdId, e.updatedByDevice, "economy_settings", e.householdId, EconomySyncPayloadCodec.encode(EconomySettingsSyncPayload(e.householdId, e.fiscalStartMonth, e.currency, e.targetCents, e.minimumCents, e.updatedAt, e.version, e.updatedByDevice)), e.version, e.updatedAt, false, operation)
    private fun categoryModel(e: EconomyCategoryEntity) = EconomyCategory(e.id, runCatching { EconomyMovementType.valueOf(e.type) }.getOrDefault(EconomyMovementType.EXPENSE), e.name, e.deleted)
    private fun movementModel(e: EconomyMovementEntity) = EconomyMovement(e.id, runCatching { EconomyMovementType.valueOf(e.type) }.getOrDefault(EconomyMovementType.EXPENSE), e.amountCents, e.categoryId, e.dateEpochDay, e.deleted)
}
