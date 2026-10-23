package com.majyd.casa.data.model

data class MenuTemplate(
    val id: String,
    val name: String,
    val items: List<MenuTemplateItem>,
)

data class MenuTemplateItem(
    val dayOfWeek: Int,
    val mealSlot: MealSlot,
    val dishId: String?,
    val variantId: String?,
    val title: String,
    val notes: String,
)
