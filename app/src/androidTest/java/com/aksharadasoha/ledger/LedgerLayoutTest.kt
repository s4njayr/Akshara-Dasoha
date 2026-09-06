package com.aksharadasoha.ledger

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aksharadasoha.ledger.domain.ColorPalette
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.aksharadasoha.ledger.domain.ThemeMode
import com.aksharadasoha.ledger.ui.preview.PreviewData
import com.aksharadasoha.ledger.ui.screens.HomeScreen
import com.aksharadasoha.ledger.ui.screens.LedgerScreen
import com.aksharadasoha.ledger.ui.screens.ReportsScreen
import com.aksharadasoha.ledger.ui.theme.CompactDimens
import com.aksharadasoha.ledger.ui.theme.LedgerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LedgerLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun combinedDateCellAndOverflowAt360dp() {
        composeRule.setContent {
            LedgerTheme {
                Box(Modifier.requiredWidth(360.dp).fillMaxHeight()) {
                    LedgerScreen(
                        state = PreviewData.ledger,
                        onBack = {},
                        onUpdate = { _, _, _ -> },
                        onAddDay = {},
                        onRequestDelete = {},
                        onConfirmDelete = {},
                        onDismissDelete = {},
                        onPrint = {},
                        onSharePdf = {},
                        onConfigure = {},
                        onDismissMessage = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("Date").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add day").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More").assertIsDisplayed()
        composeRule.onNodeWithText("01 May", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("header-actions").assertDoesNotExist()
        composeRule.onAllNodesWithTag("group-Rice").assertCountEquals(1)
        composeRule.onAllNodesWithTag("group-Dal").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Rice").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Dal").assertCountEquals(1)
        composeRule.onNodeWithTag("group-Rice").assertWidthIsEqualTo(CompactDimens.cellWidth * 5)
        composeRule.onNodeWithTag("group-Dal").assertWidthIsEqualTo(CompactDimens.cellWidth * 3)
        val dateHeader = composeRule.onNodeWithTag("header-date").getUnclippedBoundsInRoot()
        val countHeader = composeRule.onNodeWithTag("header-col-count").getUnclippedBoundsInRoot()
        val commodityHeader = composeRule.onNodeWithTag("group-Rice").getUnclippedBoundsInRoot()
        val mergedHeight = dateHeader.bottom - dateHeader.top
        assertTrue(kotlin.math.abs(mergedHeight.value - (countHeader.bottom - countHeader.top).value) < 1f)
        assertTrue(kotlin.math.abs(mergedHeight.value - ((commodityHeader.bottom - commodityHeader.top) * 2).value) < 1f)
        val rice = composeRule.onNodeWithTag("group-Rice").getUnclippedBoundsInRoot()
        val riceHeader = composeRule.onNodeWithTag("header-col-rice_opening").getUnclippedBoundsInRoot()
        val opening = composeRule.onNodeWithTag("group-start-rice_opening", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(opening.left.value + 1f >= rice.left.value)
        assertTrue(opening.right.value <= rice.right.value + 1f)
        assertTrue(kotlin.math.abs(rice.left.value - riceHeader.left.value) < 8f)
        assertTrue(kotlin.math.abs(riceHeader.left.value - opening.left.value) < 8f)
        composeRule.onNodeWithContentDescription("Add day").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("More").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("cell-target-count", useUnmergedTree = true).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("negative warning", substring = true).assertExists()
    }

    @Test
    fun groupedHeadersAndTabletActionAlignment() {
        composeRule.setContent {
            LedgerTheme {
                Box(Modifier.requiredWidth(840.dp).fillMaxHeight()) {
                    LedgerScreen(
                        state = PreviewData.ledger,
                        onBack = {},
                        onUpdate = { _, _, _ -> },
                        onAddDay = {},
                        onRequestDelete = {},
                        onConfirmDelete = {},
                        onDismissDelete = {},
                        onPrint = {},
                        onSharePdf = {},
                        onConfigure = {},
                        onDismissMessage = {},
                    )
                }
            }
        }
        composeRule.onAllNodesWithTag("group-Rice").assertCountEquals(1)
        composeRule.onAllNodesWithTag("group-Dal").assertCountEquals(1)
        composeRule.onAllNodesWithTag("header-actions").assertCountEquals(1)
        composeRule.onAllNodesWithTag("totals-actions").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Row actions").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Rice").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Dal").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("More").assertCountEquals(1)
        val header = composeRule.onNodeWithTag("header-actions").getUnclippedBoundsInRoot()
        val totals = composeRule.onNodeWithTag("totals-actions").getUnclippedBoundsInRoot()
        assertEquals(header.left, totals.left)
        assertEquals(header.right - header.left, totals.right - totals.left)
        composeRule.onNodeWithContentDescription("Row actions").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("group-Rice").assertWidthIsEqualTo(CompactDimens.cellWidth * 5)
    }

    @Test
    fun textCellAcceptsInput() {
        var saved: Triple<String, String, String>? = null
        composeRule.setContent {
            LedgerTheme {
                LedgerScreen(
                    state = PreviewData.ledger,
                    onBack = {},
                    onUpdate = { row, key, value -> saved = Triple(row, key, value) },
                    onAddDay = {},
                    onRequestDelete = {},
                    onConfirmDelete = {},
                    onDismissDelete = {},
                    onPrint = {},
                    onSharePdf = {},
                    onConfigure = {},
                    onDismissMessage = {},
                    onShowError = {},
                )
            }
        }
        composeRule.onAllNodesWithTag("cell-notes").assertCountEquals(1)
        composeRule.onNodeWithTag("cell-notes").performTextReplacement("Kitchen note")
        composeRule.onNodeWithTag("cell-notes").performImeAction()
        composeRule.onAllNodesWithTag("cell-count").assertCountEquals(1)
        composeRule.waitUntil(3_000) { saved?.third == "Kitchen note" }
        composeRule.onNodeWithTag("cell-notes").assertIsNotFocused()
    }

    @Test
    fun saveCancelAndInvalidNumericInput() {
        var saved: Triple<String, String, String>? = null
        composeRule.setContent {
            LedgerTheme {
                LedgerScreen(
                    state = PreviewData.ledger,
                    onBack = {},
                    onUpdate = { row, key, value -> saved = Triple(row, key, value) },
                    onAddDay = {},
                    onRequestDelete = {},
                    onConfirmDelete = {},
                    onDismissDelete = {},
                    onPrint = {},
                    onSharePdf = {},
                    onConfigure = {},
                    onDismissMessage = {},
                    onShowError = {},
                )
            }
        }
        composeRule.onNodeWithTag("cell-count").performClick()
        composeRule.onNodeWithTag("cell-count").performTextReplacement("21")
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithTag("cell-save").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cell-save").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("cell-save").performClick()
        composeRule.waitUntil(3_000) { saved?.third == "21" }
        saved = null
        composeRule.onNodeWithTag("cell-count").performTextReplacement("88")
        composeRule.onNodeWithTag("cell-cancel").performClick()
        composeRule.waitUntil(3_000) { saved == null }
        composeRule.onNodeWithTag("cell-count").performClick()
        composeRule.onNodeWithTag("cell-count").performTextReplacement("-")
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithContentDescription("invalid input", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cell-save").performClick()
        assertTrue(saved == null)
    }

    @Test
    fun darkThemeKeepsGroupAndAuditMeaning() {
        composeRule.setContent {
            LedgerTheme(themeMode = ThemeMode.DARK) {
                LedgerScreen(
                    state = PreviewData.ledger,
                    onBack = {},
                    onUpdate = { _, _, _ -> },
                    onAddDay = {},
                    onRequestDelete = {},
                    onConfirmDelete = {},
                    onDismissDelete = {},
                    onPrint = {},
                    onSharePdf = {},
                    onConfigure = {},
                    onDismissMessage = {},
                )
            }
        }
        composeRule.onAllNodesWithTag("group-Rice").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Add day").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("editable", substring = true)[0].assertIsDisplayed()
        composeRule.onNodeWithContentDescription("negative warning", substring = true).assertExists()
        composeRule.onNodeWithContentDescription("differs from imported Excel", substring = true).assertExists()
        composeRule.onNodeWithTag("group-start-rice_opening", useUnmergedTree = true).assertExists()
    }

    @Test
    fun homeButtonHierarchyKeepsDeleteSecondary() {
        composeRule.setContent {
            LedgerTheme {
                HomeScreen(
                    state = PreviewData.home,
                    onSelectGrade = {},
                    onSelectTemplate = {},
                    onOpenInstance = {},
                    onCreateMonth = {},
                    onOpenConfig = {},
                    onDismissMessage = {},
                )
            }
        }
        composeRule.onNodeWithText("Add month").assertIsDisplayed()
        composeRule.onNodeWithText("Open latest").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete month").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete month").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Configure").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Configure").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun homeActionsDoNotOverlapOnPhone() {
        composeRule.setContent {
            LedgerTheme {
                Box(Modifier.requiredWidth(360.dp).fillMaxHeight()) {
                    HomeScreen(
                        state = PreviewData.home,
                        onSelectGrade = {},
                        onSelectTemplate = {},
                        onOpenInstance = {},
                        onCreateMonth = {},
                        onOpenConfig = {},
                        onDismissMessage = {},
                    )
                }
            }
        }
        val open = composeRule.onNodeWithTag("home-month-open").getUnclippedBoundsInRoot()
        val delete = composeRule.onNodeWithTag("home-month-delete").getUnclippedBoundsInRoot()
        assertTrue(open.right.value <= delete.left.value + 1f)
        composeRule.onNodeWithText("Add month").assertIsDisplayed()
        composeRule.onNodeWithText("Open latest").assertIsDisplayed()
    }

    @Test
    fun reportsUseStructuredRowsAndDistinctDownloads() {
        composeRule.setContent {
            LedgerTheme {
                Box(Modifier.requiredWidth(360.dp).fillMaxHeight()) {
                    ReportsScreen(
                        state = PreviewData.reports,
                        templates = listOf(PreviewData.template),
                        onBack = {},
                        onFilter = { _, _, _ -> },
                        onDownloadPdf = {},
                        onDownloadExcel = {},
                        onDownloadCsv = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("All years").assertIsDisplayed()
        composeRule.onNodeWithText("Opening").assertIsDisplayed()
        composeRule.onNodeWithText("Supply").assertIsDisplayed()
        composeRule.onNodeWithText("Spent").assertIsDisplayed()
        composeRule.onNodeWithText("Closing").assertIsDisplayed()
        composeRule.onNodeWithText("477 kg").assertIsDisplayed()
        composeRule.onNodeWithText("20 children").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Download PDF").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Download Excel").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Download CSV").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("report-filters").assertExists()
        composeRule.onNodeWithText("Open").assertIsDisplayed()
    }

    @Test
    fun zoomButtonsScaleCellsAndStayClamped() {
        composeRule.setContent {
            var zoom by remember { mutableFloatStateOf(1f) }
            LedgerTheme {
                LedgerScreen(
                    state = PreviewData.ledger,
                    onBack = {},
                    onUpdate = { _, _, _ -> },
                    onAddDay = {},
                    onRequestDelete = {},
                    onConfirmDelete = {},
                    onDismissDelete = {},
                    onPrint = {},
                    onSharePdf = {},
                    onConfigure = {},
                    onDismissMessage = {},
                    zoom = zoom,
                    onZoomChange = { zoom = it },
                )
            }
        }
        composeRule.onNodeWithTag("group-Rice").assertWidthIsEqualTo(CompactDimens.cellWidth * 5)
        composeRule.onNodeWithContentDescription("Zoom in").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Zoom in").performClick()
        composeRule.onNodeWithTag("group-Rice").assertWidthIsEqualTo(CompactDimens.cellWidth * 1.1f * 5)
        repeat(10) { composeRule.onNodeWithContentDescription("Zoom in").performClick() }
        composeRule.onNodeWithTag("group-Rice").assertWidthIsEqualTo(CompactDimens.cellWidth * 1.5f * 5)
        composeRule.onNodeWithContentDescription("Reset zoom").performClick()
        composeRule.onNodeWithTag("group-Rice").assertWidthIsEqualTo(CompactDimens.cellWidth * 5)
        repeat(10) { composeRule.onNodeWithContentDescription("Zoom out").performClick() }
        composeRule.onNodeWithTag("group-Rice").assertWidthIsEqualTo(CompactDimens.cellWidth * 0.8f * 5)
    }

    @Test
    fun palettesKeepLedgerMeaningInLightAndDark() {
        val palette = mutableStateOf(ColorPalette.FRESH_GREEN)
        val mode = mutableStateOf(ThemeMode.LIGHT)
        composeRule.setContent {
            LedgerTheme(themeMode = mode.value, palette = palette.value) {
                LedgerScreen(
                    state = PreviewData.ledger,
                    onBack = {},
                    onUpdate = { _, _, _ -> },
                    onAddDay = {},
                    onRequestDelete = {},
                    onConfirmDelete = {},
                    onDismissDelete = {},
                    onPrint = {},
                    onSharePdf = {},
                    onConfigure = {},
                    onDismissMessage = {},
                )
            }
        }
        val palettes = listOf(ColorPalette.FRESH_GREEN, ColorPalette.OCEAN_BLUE, ColorPalette.DYNAMIC)
        val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK)
        palettes.forEach { nextPalette ->
            modes.forEach { nextMode ->
                palette.value = nextPalette
                mode.value = nextMode
                composeRule.waitForIdle()
                composeRule.onAllNodesWithTag("group-Rice").assertCountEquals(1)
                composeRule.onNodeWithContentDescription("negative warning", substring = true).assertExists()
                composeRule.onNodeWithContentDescription("differs from imported Excel", substring = true).assertExists()
                composeRule.onNodeWithContentDescription("Add day").assertHeightIsAtLeast(48.dp)
            }
        }
    }
}
