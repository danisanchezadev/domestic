package com.majyd.casa.ui.economy

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.majyd.casa.R

@Composable
internal fun EconomyBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.economy_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        content()
    }
}
