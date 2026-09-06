package com.aksharadasoha.ledger.ui.screens

import com.aksharadasoha.ledger.domain.AppSettings
import com.aksharadasoha.ledger.domain.ColorPalette
import com.aksharadasoha.ledger.domain.ExportFormat
import com.aksharadasoha.ledger.domain.LedgerZoom
import com.aksharadasoha.ledger.domain.SchoolProfile
import com.aksharadasoha.ledger.domain.TableDensity
import com.aksharadasoha.ledger.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDraftTest {
    private val initial = AppSettings(
        school = SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger"),
        themeMode = ThemeMode.SYSTEM,
        tableDensity = TableDensity.COMPACT,
        defaultExport = ExportFormat.PDF,
        colorPalette = ColorPalette.DYNAMIC,
        ledgerZoom = LedgerZoom.DEFAULT,
    )

    @Test
    fun saveThenEditIsDirtyAgain() {
        var draft = SettingsDraft(initial).copy(name = "New School")
        assertTrue(draft.dirty)
        draft = draft.markSaved()
        assertFalse(draft.dirty)
        draft = draft.copy(theme = ThemeMode.DARK)
        assertTrue(draft.dirty)
        assertEquals(ThemeMode.SYSTEM, draft.persisted.themeMode)
    }

    @Test
    fun failedSaveRestoresPersisted() {
        var draft = SettingsDraft(initial).copy(theme = ThemeMode.DARK, export = ExportFormat.EXCEL)
        draft = draft.restore()
        assertFalse(draft.dirty)
        assertEquals(ThemeMode.SYSTEM, draft.theme)
        assertEquals(ExportFormat.PDF, draft.export)
        assertEquals(initial, draft.current())
    }

    @Test
    fun unsavedNavigationUsesPersistedSnapshot() {
        val draft = SettingsDraft(initial).copy(density = TableDensity.COMFORTABLE, program = "Edited")
        assertTrue(draft.dirty)
        val restored = draft.restore()
        assertEquals(initial, restored.current())
        assertEquals(TableDensity.COMPACT, restored.density)
    }

    @Test
    fun failedSaveRestoresPaletteAndKeepsZoom() {
        var draft = SettingsDraft(initial).copy(palette = ColorPalette.OCEAN_BLUE)
        assertTrue(draft.dirty)
        draft = draft.restore()
        assertFalse(draft.dirty)
        assertEquals(ColorPalette.DYNAMIC, draft.palette)
        assertEquals(LedgerZoom.DEFAULT, draft.current().ledgerZoom)
    }
}
