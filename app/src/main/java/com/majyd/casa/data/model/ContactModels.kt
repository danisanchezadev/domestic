package com.majyd.casa.data.model

data class ContactItem(
    val id: String,
    val category: String,
    val name: String,
    val phone: String,
    val deleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long,
)

data class ContactCategoryItem(
    val id: String,
    val name: String,
)

val contactCategories = listOf("Emergencias", "Seguro del hogar", "Fontanero", "Electricista", "Cerrajero", "Comunidad", "Otros")
