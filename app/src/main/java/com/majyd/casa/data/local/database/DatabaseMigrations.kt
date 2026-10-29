package com.majyd.casa.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shopping_lists` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `version` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shopping_categories` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `normalizedName` TEXT NOT NULL,
                `icon` TEXT NOT NULL,
                `color` TEXT,
                `sortOrder` INTEGER NOT NULL,
                `hidden` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `version` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_shopping_categories_normalizedName`
            ON `shopping_categories` (`normalizedName`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shopping_products` (
                `id` TEXT NOT NULL,
                `listId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `normalizedName` TEXT NOT NULL,
                `categoryId` TEXT,
                `quantity` REAL NOT NULL,
                `unit` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `purchased` INTEGER NOT NULL,
                `purchasedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `createdByDevice` TEXT NOT NULL,
                `updatedByDevice` TEXT NOT NULL,
                `deleted` INTEGER NOT NULL,
                `version` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_shopping_products_listId`
            ON `shopping_products` (`listId`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_shopping_products_normalizedName`
            ON `shopping_products` (`normalizedName`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shopping_catalog` (
                `id` TEXT NOT NULL,
                `normalizedName` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `categoryId` TEXT,
                `preferredUnit` TEXT NOT NULL,
                `preferredQuantity` REAL,
                `usageCount` INTEGER NOT NULL,
                `lastUsedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_shopping_catalog_normalizedName`
            ON `shopping_catalog` (`normalizedName`)
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tasks` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `normalizedTitle` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `assignee` TEXT,
                `completed` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `version` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_tasks_completed`
            ON `tasks` (`completed`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_tasks_deleted`
            ON `tasks` (`deleted`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_tasks_normalizedTitle`
            ON `tasks` (`normalizedTitle`)
            """.trimIndent()
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calendar_events` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `normalizedTitle` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `location` TEXT NOT NULL,
                `startAt` INTEGER NOT NULL,
                `endAt` INTEGER NOT NULL,
                `allDay` INTEGER NOT NULL,
                `reminderMinutesBefore` INTEGER,
                `deleted` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `version` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_calendar_events_deleted`
            ON `calendar_events` (`deleted`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_calendar_events_startAt`
            ON `calendar_events` (`startAt`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_calendar_events_normalizedTitle`
            ON `calendar_events` (`normalizedTitle`)
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `weekly_menu_items` (
                `id` TEXT NOT NULL,
                `dayOfWeek` INTEGER NOT NULL,
                `mealSlot` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `normalizedTitle` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `deleted` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `version` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_weekly_menu_items_deleted`
            ON `weekly_menu_items` (`deleted`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_weekly_menu_items_dayOfWeek`
            ON `weekly_menu_items` (`dayOfWeek`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_weekly_menu_items_mealSlot`
            ON `weekly_menu_items` (`mealSlot`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_weekly_menu_items_normalizedTitle`
            ON `weekly_menu_items` (`normalizedTitle`)
            """.trimIndent()
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE device_identity ADD COLUMN householdId TEXT NOT NULL DEFAULT 'household-default'")
        db.execSQL("ALTER TABLE device_identity ADD COLUMN assignedPersonId TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS people (id TEXT NOT NULL, householdId TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_people_householdId ON people (householdId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_people_normalizedName ON people (normalizedName)")
        db.execSQL("CREATE TABLE IF NOT EXISTS household_groups (id TEXT NOT NULL, householdId TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_household_groups_householdId ON household_groups (householdId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_household_groups_normalizedName ON household_groups (normalizedName)")
        db.execSQL("CREATE TABLE IF NOT EXISTS group_members (groupId TEXT NOT NULL, personId TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(groupId, personId))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_group_members_personId ON group_members (personId)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN dueDate INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN dueTimeMinutes INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN reminderMinutesBefore INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN completedAt INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN completedByPersonId TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN createdByDeviceId TEXT")
        db.execSQL("ALTER TABLE tasks ADD COLUMN updatedByDeviceId TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS task_person_assignments (taskId TEXT NOT NULL, personId TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(taskId, personId))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_task_person_assignments_personId ON task_person_assignments (personId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS task_group_assignments (taskId TEXT NOT NULL, groupId TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(taskId, groupId))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_task_group_assignments_groupId ON task_group_assignments (groupId)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN assignmentType TEXT NOT NULL DEFAULT 'NONE'")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_operations (operationId TEXT NOT NULL, householdId TEXT NOT NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL, payload TEXT NOT NULL, version INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deviceId TEXT NOT NULL, deleted INTEGER NOT NULL, applied INTEGER NOT NULL, PRIMARY KEY(operationId))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_householdId ON sync_operations (householdId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_entityId ON sync_operations (entityId)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_operations ADD COLUMN operationType TEXT NOT NULL DEFAULT 'UPDATE'")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS authorized_devices (deviceId TEXT NOT NULL, householdId TEXT NOT NULL, displayName TEXT NOT NULL, lastSeenAt INTEGER NOT NULL, revoked INTEGER NOT NULL, PRIMARY KEY(deviceId))")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'NONE'")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_operations ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS task_history (normalizedTitle TEXT NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL, assignmentType TEXT NOT NULL, personIdsJson TEXT NOT NULL, groupId TEXT, dueTimeMinutes INTEGER, reminderMinutesBefore INTEGER, recurrence TEXT NOT NULL, usageCount INTEGER NOT NULL, lastUsedAt INTEGER NOT NULL, PRIMARY KEY(normalizedTitle))")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceSeriesId TEXT")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN completedByGroupId TEXT")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_products ADD COLUMN imageBase64 TEXT")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS contacts (id TEXT NOT NULL, householdId TEXT NOT NULL, category TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, phone TEXT NOT NULL, alternatePhone TEXT NOT NULL, notes TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, PRIMARY KEY(id))")
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE contacts_new (id TEXT NOT NULL, householdId TEXT NOT NULL, category TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, phone TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("INSERT INTO contacts_new (id, householdId, category, name, normalizedName, phone, deleted, createdAt, updatedAt, version, updatedByDevice) SELECT id, householdId, category, name, normalizedName, phone, deleted, createdAt, updatedAt, version, updatedByDevice FROM contacts")
        db.execSQL("DROP TABLE contacts")
        db.execSQL("ALTER TABLE contacts_new RENAME TO contacts")
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS contact_categories (id TEXT NOT NULL, householdId TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, PRIMARY KEY(id))")
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS calendar_places (id TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, PRIMARY KEY(id))")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN householdId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN updatedByDevice TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE calendar_places ADD COLUMN householdId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE calendar_places ADD COLUMN updatedByDevice TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE weekly_menu_items ADD COLUMN householdId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE weekly_menu_items ADD COLUMN updatedByDevice TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events RENAME COLUMN reminderMinutesBefore TO reminderDaysBefore")
        db.execSQL("UPDATE calendar_events SET reminderDaysBefore = CASE WHEN reminderDaysBefore IS NULL THEN NULL WHEN reminderDaysBefore <= 0 THEN 0 ELSE CAST((reminderDaysBefore + 1439) / 1440 AS INTEGER) END")
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_lists ADD COLUMN updatedByDevice TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE shopping_catalog ADD COLUMN updatedByDevice TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE people ADD COLUMN updatedByDevice TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE household_groups ADD COLUMN updatedByDevice TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS dishes (id TEXT NOT NULL, householdId TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, mealType TEXT NOT NULL, imageBase64 TEXT, recipe TEXT NOT NULL, preparationMinutes INTEGER, servings INTEGER, notes TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dishes_householdId ON dishes (householdId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dishes_normalizedName ON dishes (normalizedName)")
        db.execSQL("CREATE TABLE IF NOT EXISTS dish_ingredients (id TEXT NOT NULL, dishId TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, quantity REAL, unit TEXT NOT NULL, sortOrder INTEGER NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dish_ingredients_dishId ON dish_ingredients (dishId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dish_ingredients_normalizedName ON dish_ingredients (normalizedName)")
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE weekly_menu_items ADD COLUMN dishId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_weekly_menu_items_dishId ON weekly_menu_items (dishId)")
    }
}

val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dish_ingredients ADD COLUMN variantId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dish_ingredients_variantId ON dish_ingredients (variantId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS dish_variants (id TEXT NOT NULL, dishId TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, recipe TEXT NOT NULL, notes TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dish_variants_dishId ON dish_variants (dishId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dish_variants_normalizedName ON dish_variants (normalizedName)")
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE weekly_menu_items ADD COLUMN variantId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_weekly_menu_items_variantId ON weekly_menu_items (variantId)")
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS menu_templates (id TEXT NOT NULL, householdId TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_menu_templates_householdId ON menu_templates (householdId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_menu_templates_normalizedName ON menu_templates (normalizedName)")
        db.execSQL("CREATE TABLE IF NOT EXISTS menu_template_items (templateId TEXT NOT NULL, dayOfWeek INTEGER NOT NULL, mealSlot TEXT NOT NULL, dishId TEXT, variantId TEXT, title TEXT NOT NULL, notes TEXT NOT NULL, deleted INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, PRIMARY KEY(templateId, dayOfWeek, mealSlot))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_menu_template_items_templateId ON menu_template_items (templateId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_menu_template_items_dishId ON menu_template_items (dishId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_menu_template_items_variantId ON menu_template_items (variantId)")
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS menu_history_items (id TEXT NOT NULL, householdId TEXT NOT NULL, weekStartEpochDay INTEGER NOT NULL, dayOfWeek INTEGER NOT NULL, mealSlot TEXT NOT NULL, dishId TEXT, variantId TEXT, title TEXT NOT NULL, notes TEXT NOT NULL, completed INTEGER NOT NULL, completedAt INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_menu_history_items_householdId ON menu_history_items (householdId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_menu_history_items_weekStartEpochDay ON menu_history_items (weekStartEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_menu_history_items_dishId ON menu_history_items (dishId)")
    }
}

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dishes ADD COLUMN category TEXT NOT NULL DEFAULT 'Sin categoria'")
        db.execSQL("ALTER TABLE dishes ADD COLUMN recipeSteps TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN participantIdsJson TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN recurrenceType TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN recurrenceWeekdays TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN recurrenceUntil INTEGER")
    }
}

val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS calendar_event_exceptions (seriesId TEXT NOT NULL, occurrenceDate INTEGER NOT NULL, householdId TEXT NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL, location TEXT NOT NULL, startAt INTEGER NOT NULL, endAt INTEGER NOT NULL, allDay INTEGER NOT NULL, participantIdsJson TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, PRIMARY KEY(seriesId, occurrenceDate))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_event_exceptions_seriesId ON calendar_event_exceptions (seriesId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_event_exceptions_householdId ON calendar_event_exceptions (householdId)")
    }
}

val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN recurrenceCustomUnit TEXT NOT NULL DEFAULT 'DAYS'")
    }
}

val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dishes ADD COLUMN collection TEXT NOT NULL DEFAULT 'MENU'")
        normalizeDishesTable(db)
    }
}

val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Also repair databases that were already left at version 35 by the
        // previous migration before the app was updated.
        normalizeDishesTable(db)
        db.execSQL("CREATE TABLE IF NOT EXISTS recipe_categories (id TEXT NOT NULL, householdId TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recipe_categories_normalizedName ON recipe_categories (normalizedName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_categories_householdId ON recipe_categories (householdId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS recipe_category_links (dishId TEXT NOT NULL, categoryId TEXT NOT NULL, deleted INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, PRIMARY KEY(dishId, categoryId))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_category_links_categoryId ON recipe_category_links (categoryId)")
    }
}

val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        normalizeDishesTable(db)
    }
}

val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dishes ADD COLUMN preparationRequired INTEGER NOT NULL DEFAULT 0")
    }
}

private fun normalizeDishesTable(db: SupportSQLiteDatabase) {
    // Rebuild the table so SQLite does not retain migration-only defaults
    // that Room does not declare in DishEntity.
    db.execSQL("CREATE TABLE dishes_new (id TEXT NOT NULL, householdId TEXT NOT NULL, name TEXT NOT NULL, normalizedName TEXT NOT NULL, category TEXT NOT NULL, mealType TEXT NOT NULL, imageBase64 TEXT, recipe TEXT NOT NULL, recipeSteps TEXT NOT NULL, preparationMinutes INTEGER, servings INTEGER, notes TEXT NOT NULL, deleted INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, version INTEGER NOT NULL, updatedByDevice TEXT NOT NULL, collection TEXT NOT NULL, PRIMARY KEY(id))")
    db.execSQL("INSERT INTO dishes_new (id, householdId, name, normalizedName, category, mealType, imageBase64, recipe, recipeSteps, preparationMinutes, servings, notes, deleted, createdAt, updatedAt, version, updatedByDevice, collection) SELECT id, householdId, name, normalizedName, category, mealType, imageBase64, recipe, recipeSteps, preparationMinutes, servings, notes, deleted, createdAt, updatedAt, version, updatedByDevice, collection FROM dishes")
    db.execSQL("DROP TABLE dishes")
    db.execSQL("ALTER TABLE dishes_new RENAME TO dishes")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_dishes_householdId ON dishes (householdId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_dishes_normalizedName ON dishes (normalizedName)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_dishes_collection ON dishes (collection)")
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38)
