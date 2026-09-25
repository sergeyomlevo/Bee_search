package org.beesearch.app.ui.physicalobjects

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.flowOf
import org.beesearch.app.domain.heading.HeadingAccuracy
import org.beesearch.app.domain.heading.HeadingProvider
import org.beesearch.app.domain.heading.HeadingState
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PhysicalObjectEditorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hollowShowsLiveCompassFixesHeadingAndAllowsManualReplacement() {
        var submitted: HollowProperties? = null
        val heading = HeadingProvider {
            flowOf(HeadingState.Available(123, HeadingAccuracy.LOW, Instant.EPOCH))
        }
        composeRule.setContent {
            Bee_searchTheme {
                HollowForm(
                    headingProvider = heading,
                    onSubmit = { properties, _ -> submitted = properties },
                )
            }
        }

        composeRule.onNodeWithTag("physical-object-live-azimuth")
            .assertTextContains("123° · ЮВ")
        composeRule.onNodeWithText("Направьте верх телефона в сторону летка").assertIsDisplayed()
        composeRule.onNodeWithText("Точность компаса низкая").assertIsDisplayed()
        composeRule.onNodeWithTag("physical-object-fix-azimuth").performClick()
        composeRule.onNodeWithTag("physical-object-manual-azimuth")
            .performTextReplacement("321")
        composeRule.onNodeWithTag("physical-object-field-tree").performTextInput("дуб")
        composeRule.onNodeWithTag("physical-object-field-entranceHeightCm").performTextInput("180")
        composeRule.onNodeWithTag("physical-object-field-outerDiameterCm").performTextInput("40")
        composeRule.onNodeWithTag("physical-object-create").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(321, submitted?.entranceAzimuthDeg) }
    }

    @Test
    fun invalidHollowSubmitShowsSpecificErrorsAndDoesNotSubmit() {
        var submitCount = 0
        composeRule.setContent {
            Bee_searchTheme {
                HollowForm(onSubmit = { _, _ -> submitCount++ })
            }
        }

        composeRule.onNodeWithTag("physical-object-create").performScrollTo().performClick()
        composeRule.onNodeWithText("Укажите дерево").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Введите положительное число").assertCountEquals(2)
        composeRule.onNodeWithText("Введите целое число от 0 до 359°").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, submitCount) }
    }

    @Test
    fun logHiveRequiresAndAcceptsMaterialAndInternalDimensions() {
        var submitted: LogHiveProperties? = null
        composeRule.setContent {
            Bee_searchTheme {
                LogHiveForm(onSubmit = { properties, _ -> submitted = properties })
            }
        }

        composeRule.onNodeWithTag("physical-object-create").performScrollTo().performClick()
        composeRule.onNodeWithText("Укажите материал").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Введите положительное число").assertCountEquals(4)
        composeRule.onNodeWithTag("physical-object-field-tree").performTextInput("сосна")
        composeRule.onNodeWithTag("physical-object-field-entranceHeightCm").performTextInput("150")
        composeRule.onNodeWithTag("physical-object-manual-azimuth").performTextInput("90")
        composeRule.onNodeWithTag("physical-object-field-outerDiameterCm").performTextInput("50")
        composeRule.onNodeWithTag("physical-object-field-material").performScrollTo().performTextInput("липа")
        composeRule.onNodeWithTag("physical-object-field-internalDiameterCm").performScrollTo().performTextInput("30")
        composeRule.onNodeWithTag("physical-object-field-internalHeightCm").performScrollTo().performTextInput("80")
        composeRule.onNodeWithTag("physical-object-create").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals("липа", submitted?.material)
            assertEquals(30.0, submitted?.internalDiameterCm ?: 0.0, 0.0)
            assertEquals(80.0, submitted?.internalHeightCm ?: 0.0, 0.0)
        }
    }

    @Test
    fun cancelInvokesCancelAndNeverSubmits() {
        var cancelled = false
        var submitted = false
        composeRule.setContent {
            Bee_searchTheme {
                HollowForm(
                    onSubmit = { _, _ -> submitted = true },
                    onCancel = { cancelled = true },
                )
            }
        }

        composeRule.onNodeWithTag("physical-object-cancel").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertTrue(cancelled)
            assertTrue(!submitted)
        }
    }
}
