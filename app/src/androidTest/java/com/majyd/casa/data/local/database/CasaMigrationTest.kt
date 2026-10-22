package com.majyd.casa.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
        CasaDatabase::class.java.name,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrateFromRoom8_preservesShoppingAndTasks() {
        helper.createDatabase(DB_NAME, 8).apply {
            execSQL("INSERT INTO tasks (id, title, normalizedTitle, notes, assignee, completed, deleted, createdAt, updatedAt, version, dueDate, dueTimeMinutes, reminderMinutesBefore, completedAt, completedByPersonId, createdByDeviceId, updatedByDeviceId, assignmentType) VALUES ('task-old', 'Tarea antigua', 'tarea antigua', '', 'PERSON_ONE', 0, 0, 1, 1, 1, 20000, NULL, NULL, NULL, NULL, NULL, NULL, 'NONE')")
            execSQL("INSERT INTO shopping_lists (id, name, sortOrder, deleted, version, createdAt, updatedAt) VALUES ('list-old', 'Compra antigua', 0, 0, 1, 1, 1)")
            execSQL("INSERT INTO shopping_products (id, listId, name, normalizedName, categoryId, quantity, unit, notes, purchased, purchasedAt, createdAt, updatedAt, createdByDevice, updatedByDevice, deleted, version) VALUES ('product-old', 'list-old', 'Leche', 'leche', NULL, 1.0, 'l', '', 0, NULL, 1, 1, 'device-old', 'device-old', 0, 1)")
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME, 24, true, *ALL_MIGRATIONS).use { database ->
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

        helper.runMigrationsAndValidate(ROOM2_DB_NAME, 24, true, *ALL_MIGRATIONS).use {
            // Schema validation is the assertion for the complete migration chain.
        }
    }

    companion object {
        private const val DB_NAME = "casa-migration-test"
        private const val ROOM2_DB_NAME = "casa-migration-room2-test"
    }
}
