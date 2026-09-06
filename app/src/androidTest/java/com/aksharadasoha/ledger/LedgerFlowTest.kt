package com.aksharadasoha.ledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsGradesAndOpensRiceLedger() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("8th").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("8th").performClick()
        composeRule.onNodeWithText("8th Rice Bhagya").assertIsDisplayed()
        composeRule.onAllNodesWithText("May 2025", substring = true).onLast().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Rice", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Add day").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Export").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Print").assertIsDisplayed()
        composeRule.onNodeWithText("Share PDF").assertIsDisplayed()
        composeRule.onNodeWithText("Download CSV").assertIsDisplayed()
    }

    @Test
    fun configurationCanOpenAndShowColumns() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("8th").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Configure").performClick()
        composeRule.onNodeWithText("Configure ledgers").assertIsDisplayed()
        composeRule.onNodeWithText("Columns").assertIsDisplayed()
    }

    @Test
    fun backupScreenShowsCorrections() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("8th").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Backup").performClick()
        composeRule.onNodeWithText("Workbook corrections").assertIsDisplayed()
        composeRule.onNodeWithText("Export backup").assertIsDisplayed()
        composeRule.onNodeWithText("Import Excel workbook").assertIsDisplayed()
    }

    @Test
    fun reportsScreenOpensFromNavigation() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("8th").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Reports").onFirst().performClick()
        composeRule.onNodeWithText("All years").assertIsDisplayed()
    }

    @Test
    fun settingsHidesLanguageAndShowsExportLabels() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("8th").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Default export").assertIsDisplayed()
        composeRule.onNodeWithText("System").assertIsDisplayed()
        composeRule.onNodeWithText("Color style").assertIsDisplayed()
        composeRule.onNodeWithText("Fresh Green").assertIsDisplayed()
        composeRule.onNodeWithText("Ocean Blue").assertIsDisplayed()
    }
}
