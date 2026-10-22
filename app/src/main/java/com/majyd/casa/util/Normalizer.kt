package com.majyd.casa.util

import java.text.Normalizer
import java.util.Locale

object Normalizer {
    fun normalizeText(value: String): String {
        val stripped = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale("es", "ES"))
        return stripped.replace("\\s+".toRegex(), " ")
    }
}

