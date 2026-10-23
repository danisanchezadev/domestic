package com.majyd.casa.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.majyd.casa.data.local.entities.MenuTemplateEntity
import com.majyd.casa.data.local.entities.MenuTemplateItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuTemplateDao {
    @Query("SELECT * FROM menu_templates WHERE deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeTemplates(): Flow<List<MenuTemplateEntity>>

    @Query("SELECT * FROM menu_templates WHERE id = :templateId LIMIT 1")
    suspend fun getById(templateId: String): MenuTemplateEntity?

    @Query("SELECT * FROM menu_template_items WHERE templateId = :templateId AND deleted = 0 ORDER BY dayOfWeek ASC, mealSlot ASC")
    suspend fun getItems(templateId: String): List<MenuTemplateItemEntity>

    @Query("SELECT * FROM menu_template_items WHERE templateId = :templateId AND dayOfWeek = :dayOfWeek AND mealSlot = :mealSlot LIMIT 1")
    suspend fun getItem(templateId: String, dayOfWeek: Int, mealSlot: String): MenuTemplateItemEntity?

    @Upsert
    suspend fun upsertTemplate(template: MenuTemplateEntity)

    @Upsert
    suspend fun upsertItem(item: MenuTemplateItemEntity)

    @Query("UPDATE menu_templates SET deleted = 1, updatedAt = :updatedAt, version = version + 1, updatedByDevice = :deviceId WHERE id = :templateId")
    suspend fun softDeleteTemplate(templateId: String, updatedAt: Long, deviceId: String)
}
