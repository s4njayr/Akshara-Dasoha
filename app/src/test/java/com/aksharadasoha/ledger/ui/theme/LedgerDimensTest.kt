package com.aksharadasoha.ledger.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerDimensTest {
    @Test
    fun scaledKeepsFractionalWidth() {
        val scaled = CompactDimens.scaled(1.1f)
        assertEquals(CompactDimens.cellWidth.value * 1.1f, scaled.cellWidth.value, 0.001f)
        assertEquals(CompactDimens.dateWidth.value * 1.1f, scaled.dateWidth.value, 0.001f)
        assertTrue(scaled.rowHeight.value >= CompactDimens.minTouch.value)
        assertEquals(CompactDimens.minTouch.value, scaled.actionWidth.value, 0.001f)
    }

    @Test
    fun fontScaleRaisesHeightsWithoutCapping() {
        val compact = CompactDimens.scaled(1f, fontScale = 1.3f)
        assertEquals(CompactDimens.cellWidth.value, compact.cellWidth.value, 0.001f)
        assertTrue(compact.rowHeight.value >= CompactDimens.rowHeight.value * 1.3f - 0.01f)
        assertTrue(compact.headerHeight.value >= CompactDimens.headerHeight.value * 1.3f - 0.01f)
    }
}
