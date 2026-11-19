package com.majyd.casa.ui.economy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.majyd.casa.ui.theme.CasaTheme
import org.junit.Rule
import org.junit.Test

class EconomyResponsiveTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun narrowLayoutKeepsAllMovementButtonsVisible() {
        composeRule.setContent {
            CasaTheme {
                Box(Modifier.width(320.dp)) {
                    EconomyEntryButtons(onSelect = {})
                }
            }
        }

        composeRule.onNodeWithText("Ingreso").assertIsDisplayed()
        composeRule.onNodeWithText("Gasto").assertIsDisplayed()
        composeRule.onNodeWithText("Inversión").assertIsDisplayed()
    }
}
