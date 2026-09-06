package com.aksharadasoha.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportFormatTest {
    @Test
    fun roundsAndKeepsUnits() {
        assertEquals("—", ReportFormat.number(null))
        assertEquals("—", ReportFormat.number(Double.NaN))
        assertEquals("20", ReportFormat.number(20.0))
        assertEquals("477.75", ReportFormat.number(477.748))
        assertEquals("3", ReportFormat.number(3.001))
        assertEquals("474.75 kg", ReportFormat.quantity(474.75, "kg"))
        assertEquals("—", ReportFormat.quantity(null, "kg"))
        assertEquals("12", ReportFormat.quantity(12.0, "  "))
    }
}

class LedgerZoomTest {
    @Test
    fun clampsAndRejectsNonFinite() {
        assertEquals(0.8f, LedgerZoom.clamp(0.1f))
        assertEquals(1.5f, LedgerZoom.clamp(9f))
        assertEquals(1.1f, LedgerZoom.clamp(1.1f), 0.0f)
        assertEquals(LedgerZoom.DEFAULT, LedgerZoom.clamp(Float.NaN))
        assertEquals(LedgerZoom.DEFAULT, LedgerZoom.clamp(Float.POSITIVE_INFINITY))
    }
}
