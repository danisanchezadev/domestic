package com.majyd.casa.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

        helper.runMigrationsAndValidate(DB_NAME, 31, true, *ALL_MIGRATIONS).use { database ->
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
        }
    }

    @Test
    fun migrateFromRoom2_emptyDatabase_reachesCurrentSchema() {
        helper.createDatabase(ROOM2_DB_NAME, 2).close()

        helper.runMigrationsAndValidate(ROOM2_DB_NAME, 31, true, *ALL_MIGRATIONS).use {
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

        helper.runMigrationsAndValidate(ROOM23_DB_NAME, 31, true, *ALL_MIGRATIONS).use { database ->
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

    companion object {
        private const val DB_NAME = "casa-migration-test"
        private const val ROOM2_DB_NAME = "casa-migration-room2-test"
        private const val ROOM23_DB_NAME = "casa-migration-room23-test"
    }
}
