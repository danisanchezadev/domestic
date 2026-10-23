package com.majyd.casa.data.repository

import com.majyd.casa.data.model.MenuTemplate
import com.majyd.casa.data.model.WeeklyMenuItem
import kotlinx.coroutines.flow.Flow

interface MenuTemplateRepository {
    fun observeTemplates(): Flow<List<MenuTemplate>>
    suspend fun saveTemplate(name: String, items: List<WeeklyMenuItem>): MenuTemplate
    suspend fun deleteTemplate(templateId: String)
    suspend fun loadTemplate(templateId: String): List<MenuTemplateItemValue>
}

data class MenuTemplateItemValue(
    val dayOfWeek: Int,
    val mealSlot: com.majyd.casa.data.model.MealSlot,
    val dishId: String?,
    val variantId: String?,
    val title: String,
    val notes: String,
)
