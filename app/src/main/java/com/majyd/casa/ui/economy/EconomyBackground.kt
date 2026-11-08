package com.majyd.casa.ui.economy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun EconomyBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}
