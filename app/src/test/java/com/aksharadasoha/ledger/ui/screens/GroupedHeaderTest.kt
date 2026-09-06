package com.aksharadasoha.ledger.ui.screens

import com.aksharadasoha.ledger.ui.preview.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupedHeaderTest {
    @Test
    fun spansCommodityColumns() {
        val columns = PreviewData.template.columns.filter { it.visible && it.role != com.aksharadasoha.ledger.domain.ColumnRole.DATE && it.valueType != com.aksharadasoha.ledger.domain.ValueType.WEEKDAY }
        val groups = groupedColumns(columns)
        val rice = groups.first { it.label == "Rice" }
        val dal = groups.first { it.label == "Dal" }
        assertEquals(5, rice.columns.size)
        assertEquals(3, dal.columns.size)
        assertTrue(rice.band != dal.band)
        assertEquals(1, groups.first { it.columns.any { column -> column.key == "count" } }.columns.size)
        assertEquals(1, groups.first { it.columns.any { column -> column.key == "notes" } }.columns.size)
    }
}
