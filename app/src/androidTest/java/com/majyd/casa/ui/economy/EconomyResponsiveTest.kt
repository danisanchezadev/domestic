package com.majyd.casa.ui.economy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.majyd.casa.ui.theme.CasaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class EconomyResponsiveTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun movementButtonsStayVisibleAndInsideBoundsAcrossWidthsAndFontScales() {
        listOf(320, 360, 393, 411).forEach { width ->
            listOf(1f, 1.3f, 1.5f).forEach { fontScale ->
                composeRule.setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                        CasaTheme {
                            Box(Modifier.width(width.dp)) {
                                EconomyEntryButtons(onSelect = {})
                            }
                        }
                    }
                }
                composeRule.waitForIdle()
                listOf("Ingreso", "Gasto", "Inversión").forEach { label ->
                    val bounds = composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
                    composeRule.onNodeWithText(label).assertIsDisplayed()
                    assertTrue("$label sale por la izquierda a $width dp / $fontScale", bounds.left >= -0.5f)
                    assertTrue("$label sale por la derecha a $width dp / $fontScale", bounds.right <= width + 0.5f)
                    assertTrue("$label no tiene ancho visible", bounds.width > 0f)
                }
            }
        }
    }
}
