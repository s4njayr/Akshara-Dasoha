package com.aksharadasoha.ledger.data

import com.aksharadasoha.ledger.domain.AppSettings
import com.aksharadasoha.ledger.domain.ColorPalette
import com.aksharadasoha.ledger.domain.LedgerZoom
import com.aksharadasoha.ledger.domain.SchoolProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsArchiveTest {
    @Test
    fun missingPaletteAndZoomUseDefaults() {
        val json = JSONObject()
            .put("school", JSONObject().put("name", "Akshara").put("program", "MDM"))
            .put("themeMode", "DARK")
        val settings = SeedParser.parseSettings(json)!!
        assertEquals(ColorPalette.DYNAMIC, settings.colorPalette)
        assertEquals(LedgerZoom.DEFAULT, settings.ledgerZoom)
        assertEquals(com.aksharadasoha.ledger.domain.ThemeMode.DARK, settings.themeMode)
    }

    @Test
    fun unknownPaletteDoesNotFailRestore() {
        val json = JSONObject()
            .put("school", JSONObject().put("name", "Akshara").put("program", "MDM"))
            .put("colorPalette", "NEON_PINK")
            .put("ledgerZoom", 4.0)
        val settings = SeedParser.parseSettings(json)!!
        assertEquals(ColorPalette.DYNAMIC, settings.colorPalette)
        assertEquals(LedgerZoom.MAX, settings.ledgerZoom)
    }

    @Test
    fun settingsRoundTripKeepsPaletteAndZoom() {
        val original = AppSettings(
            school = SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger"),
            colorPalette = ColorPalette.OCEAN_BLUE,
            ledgerZoom = 1.2f,
        )
        val again = SeedParser.parseSettings(SeedParser.settingsJson(original))!!
        assertEquals(ColorPalette.OCEAN_BLUE, again.colorPalette)
        assertEquals(1.2f, again.ledgerZoom, 0.0f)
        assertTrue(SeedParser.settingsJson(original).has("colorPalette"))
        assertTrue(SeedParser.settingsJson(original).has("ledgerZoom"))
    }
}
