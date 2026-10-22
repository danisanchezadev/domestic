package com.majyd.casa.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.majyd.casa.data.model.AppPreferences
import com.majyd.casa.ui.navigation.CasaNavHost
import com.majyd.casa.ui.setup.SetupWizardScreen
import com.majyd.casa.ui.theme.CasaTheme

@Composable
fun CasaApp(
    preferences: AppPreferences,
    modifier: Modifier = Modifier,
) {
    CasaTheme(darkTheme = preferences.darkMode, colorTheme = preferences.colorTheme) {
        if (preferences.setupCompleted) {
            CasaNavHost(preferences = preferences, modifier = modifier)
        } else {
            SetupWizardScreen()
        }
    }
}
