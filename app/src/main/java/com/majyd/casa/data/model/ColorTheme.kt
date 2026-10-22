package com.majyd.casa.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ColorTheme(val label: String) {
    GREEN("Verde"),
    PURPLE("Morado"),
    ULTRAMAR("Ultramar"),
}
