package com.majyd.casa.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CasaMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CasaDatabase::class.java,
    )

    @Test
    fun migrateFromRoom8_preservesShoppingAndTasks() {
        helper.createDatabase(DB_NAME, 8).apply {
            execSQL("INSERT INTO tasks (id, title, normalizedTitle, notes, assignee, completed, deleted, createdAt, updatedAt, version, dueDate, dueTimeMinutes, reminderMinutesBefore, completedAt, completedByPersonId, createdByDeviceId, updatedByDeviceId, assignmentType) VALUES ('task-old', 'Tarea antigua', 'tarea antigua', '', 'PERSON_ONE', 0, 0, 1, 1, 1, 20000, NULL, NULL, NULL, NULL, NULL, NULL, 'NONE')")
            execSQL("INSERT INTO shopping_lists (id, name, sortOrder, deleted, version, createdAt, updatedAt) VALUES ('list-old', 'Compra antigua', 0, 0, 1, 1, 1)")
            execSQL("INSERT INTO shopping_products (id, listId, name, normalizedName, categoryId, quantity, unit, notes, purchased, purchasedAt, createdAt, updatedAt, createdByDevice, updatedByDevice, deleted, version) VALUES ('product-old', 'list-old', 'Leche', 'leche', NULL, 1.0, 'l', '', 0, NULL, 1, 1, 'device-old', 'device-old', 0, 1)")
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME, 42, true, *ALL_MIGRATIONS).use { database ->
            database.query("SELECT COUNT(*) FROM tasks WHERE id = 'task-old'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM shopping_products WHERE id = 'product-old'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            database.query("SELECT updatedByDevice FROM shopping_lists WHERE id = 'list-old'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("", cursor.getString(0))
            }
            database.query("SELECT collection FROM dishes LIMIT 1").use { cursor ->
                assertEquals(1, cursor.columnCount)
            }
            database.query("SELECT name FROM recipe_categories LIMIT 1").use { cursor ->
                assertEquals(1, cursor.columnCount)
            }
        }
    }

    @Test
    fun migrateFromRoom2_emptyDatabase_reachesCurrentSchema() {
        helper.createDatabase(ROOM2_DB_NAME, 2).close()

        helper.runMigrationsAndValidate(ROOM2_DB_NAME, 42, true, *ALL_MIGRATIONS).use {
            // Schema validation is the assertion for the complete migration chain.
        }
    }

    @Test
    fun migrateFromRoom23_preservesNewEntityDataAndPendingOperations() {
        helper.createDatabase(ROOM23_DB_NAME, 23).apply {
            execSQL("INSERT INTO shopping_lists (id, name, sortOrder, deleted, version, createdAt, updatedAt) VALUES ('list-23', 'Compra', 0, 0, 2, 10, 20)")
            execSQL("INSERT INTO shopping_catalog (id, normalizedName, displayName, categoryId, preferredUnit, preferredQuantity, usageCount, lastUsedAt, createdAt, updatedAt, deleted) VALUES ('catalog-23', 'leche', 'Leche', NULL, 'l', 1.0, 2, 20, 10, 20, 0)")
            execSQL("INSERT INTO people (id, householdId, name, normalizedName, deleted, createdAt, updatedAt, version) VALUES ('person-23', 'household-default', 'Dani', 'dani', 0, 10, 20, 1)")
            execSQL("INSERT INTO household_groups (id, householdId, name, normalizedName, deleted, createdAt, updatedAt, version) VALUES ('group-23', 'household-default', 'Casa', 'casa', 0, 10, 20, 1)")
            execSQL("INSERT INTO sync_operations (operationId, householdId, entityType, entityId, operationType, payload, version, createdAt, updatedAt, deviceId, deleted, applied) VALUES ('operation-23', 'household-default', 'task', 'task-23', 'UPDATE', '{}', 1, 10, 20, 'device-23', 0, 0)")
            close()
        }

        helper.runMigrationsAndValidate(ROOM23_DB_NAME, 42, true, *ALL_MIGRATIONS).use { database ->
            database.query("SELECT updatedByDevice FROM shopping_lists WHERE id = 'list-23'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("", cursor.getString(0))
            }
            database.query("SELECT updatedByDevice FROM shopping_catalog WHERE id = 'catalog-23'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("", cursor.getString(0))
            }
            database.query("SELECT updatedByDevice FROM people WHERE id = 'person-23'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("", cursor.getString(0))
            }
            database.query("SELECT updatedByDevice FROM household_groups WHERE id = 'group-23'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("", cursor.getString(0))
            }
            database.query("SELECT applied FROM sync_operations WHERE operationId = 'operation-23'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrateFromRoom39_preservesAuthorizedDeviceAndAddsAssociationColumns() {
        helper.createDatabase(ROOM39_DB_NAME, 39).apply {
            execSQL("INSERT INTO authorized_devices (deviceId, householdId, displayName, lastSeenAt, revoked) VALUES ('device-39', 'house-1', 'Movil', 10, 0)")
            close()
        }

        helper.runMigrationsAndValidate(ROOM39_DB_NAME, 42, true, *ALL_MIGRATIONS).use { database ->
            database.query("SELECT deviceId, householdId, displayName, assignedPersonId, associationUpdatedAt FROM authorized_devices WHERE deviceId = 'device-39'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("device-39", cursor.getString(0))
                assertEquals("house-1", cursor.getString(1))
                assertEquals("Movil", cursor.getString(2))
                assertTrue(cursor.isNull(3))
                assertEquals(0L, cursor.getLong(4))
            }
        }
    }

    @Test
    fun migrateFromRoom41_copiesLatestTargetIntoExistingSettings() {
        helper.createDatabase(ROOM41_SETTINGS_DB_NAME, 41).apply {
            execSQL("INSERT INTO economy_settings (householdId, fiscalStartMonth, currency, updatedAt, version, updatedByDevice) VALUES ('house-41', 4, 'EUR', 10, 1, 'device-41')")
            execSQL("INSERT INTO economy_monthly_targets (householdId, monthKey, targetCents, minimumCents, createdAt, updatedAt, version, updatedByDevice) VALUES ('house-41', '2026-01', 10000, 5000, 1, 20, 1, 'device-41')")
            execSQL("INSERT INTO economy_monthly_targets (householdId, monthKey, targetCents, minimumCents, createdAt, updatedAt, version, updatedByDevice) VALUES ('house-41', '2026-02', 20000, 8000, 1, 30, 2, 'device-41')")
            close()
        }
        helper.runMigrationsAndValidate(ROOM41_SETTINGS_DB_NAME, 42, true, *ALL_MIGRATIONS).use { database ->
            database.query("SELECT fiscalStartMonth, targetCents, minimumCents FROM economy_settings WHERE householdId = 'house-41'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(4, cursor.getInt(0))
                assertEquals(20000L, cursor.getLong(1))
                assertEquals(8000L, cursor.getLong(2))
            }
        }
    }

    @Test
    fun migrateFromRoom41_createsSettingsWhenOnlyTargetsExist() {
        helper.createDatabase(ROOM41_TARGET_ONLY_DB_NAME, 41).apply {
            execSQL("INSERT INTO economy_monthly_targets (householdId, monthKey, targetCents, minimumCents, createdAt, updatedAt, version, updatedByDevice) VALUES ('house-target', '2026-03', 15000, 6000, 1, 40, 1, 'device-target')")
            close()
        }
        helper.runMigrationsAndValidate(ROOM41_TARGET_ONLY_DB_NAME, 42, true, *ALL_MIGRATIONS).use { database ->
            database.query("SELECT targetCents, minimumCents FROM economy_settings WHERE householdId = 'house-target'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(15000L, cursor.getLong(0))
                assertEquals(6000L, cursor.getLong(1))
            }
        }
    }

    @Test
    fun migrateFromRoom41_keepsZeroDefaultsWithoutPreviousTargets() {
        helper.createDatabase(ROOM41_EMPTY_ECONOMY_DB_NAME, 41).apply {
            execSQL("INSERT INTO economy_settings (householdId, fiscalStartMonth, currency, updatedAt, version, updatedByDevice) VALUES ('house-empty', 1, 'EUR', 10, 1, 'device-empty')")
            close()
        }
        helper.runMigrationsAndValidate(ROOM41_EMPTY_ECONOMY_DB_NAME, 42, true, *ALL_MIGRATIONS).use { database ->
            database.query("SELECT targetCents, minimumCents FROM economy_settings WHERE householdId = 'house-empty'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0L, cursor.getLong(0))
                assertEquals(0L, cursor.getLong(1))
            }
        }
    }

    @Test
    fun migrateFromRoom41_preservesMovementsAndUsesNewestTarget() {
        helper.createDatabase(ROOM41_MOVEMENTS_DB_NAME, 41).apply {
            execSQL("INSERT INTO economy_settings (householdId, fiscalStartMonth, currency, updatedAt, version, updatedByDevice) VALUES ('house-movements', 1, 'EUR', 10, 1, 'device-movements')")
            execSQL("INSERT INTO economy_monthly_targets (householdId, monthKey, targetCents, minimumCents, createdAt, updatedAt, version, updatedByDevice) VALUES ('house-movements', '2026-01', 9000, 3000, 1, 20, 1, 'device-movements')")
            execSQL("INSERT INTO economy_monthly_targets (householdId, monthKey, targetCents, minimumCents, createdAt, updatedAt, version, updatedByDevice) VALUES ('house-movements', '2026-02', 11000, 4000, 1, 30, 2, 'device-movements')")
            execSQL("INSERT INTO economy_movements (id, householdId, type, amountCents, categoryId, dateEpochDay, deleted, createdAt, updatedAt, version, updatedByDevice) VALUES ('movement-41', 'house-movements', 'INCOME', 25000, 'category-41', 20000, 0, 1, 2, 1, 'device-movements')")
            close()
        }
        helper.runMigrationsAndValidate(ROOM41_MOVEMENTS_DB_NAME, 42, true, *ALL_MIGRATIONS).use { database ->
            database.query("SELECT COUNT(*) FROM economy_movements WHERE id = 'movement-41'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            database.query("SELECT targetCents, minimumCents FROM economy_settings WHERE householdId = 'house-movements'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(11000L, cursor.getLong(0))
                assertEquals(4000L, cursor.getLong(1))
            }
        }
    }

    companion object {
        private const val DB_NAME = "casa-migration-test"
        private const val ROOM2_DB_NAME = "casa-migration-room2-test"
        private const val ROOM23_DB_NAME = "casa-migration-room23-test"
        private const val ROOM39_DB_NAME = "casa-migration-room39-test"
        private const val ROOM41_SETTINGS_DB_NAME = "casa-migration-room41-settings-test"
        private const val ROOM41_TARGET_ONLY_DB_NAME = "casa-migration-room41-target-only-test"
        private const val ROOM41_EMPTY_ECONOMY_DB_NAME = "casa-migration-room41-empty-economy-test"
        private const val ROOM41_MOVEMENTS_DB_NAME = "casa-migration-room41-movements-test"
    }
}
