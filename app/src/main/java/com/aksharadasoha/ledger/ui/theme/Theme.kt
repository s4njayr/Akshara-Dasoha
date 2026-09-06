package com.aksharadasoha.ledger.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aksharadasoha.ledger.domain.ColorPalette
import com.aksharadasoha.ledger.domain.TableDensity
import com.aksharadasoha.ledger.domain.ThemeMode

private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

val LocalLedgerDimens = staticCompositionLocalOf { CompactDimens }
val LocalLedgerCells = staticCompositionLocalOf { FreshGreenLightCells }

@Composable
fun LedgerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: ColorPalette = ColorPalette.DYNAMIC,
    density: TableDensity = TableDensity.COMPACT,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val dynamicAvailable = palette == ColorPalette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicScheme = if (dynamicAvailable) {
        runCatching {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }.getOrNull()
    } else {
        null
    }
    val resolved = when {
        dynamicScheme != null -> dynamicScheme
        palette == ColorPalette.OCEAN_BLUE && dark -> OceanBlueDark
        palette == ColorPalette.OCEAN_BLUE -> OceanBlueLight
        dark -> FreshGreenDark
        else -> FreshGreenLight
    }
    val cells = when {
        dynamicScheme != null -> cellColorsForScheme(resolved, dark)
        palette == ColorPalette.OCEAN_BLUE && dark -> OceanBlueDarkCells
        palette == ColorPalette.OCEAN_BLUE -> OceanBlueLightCells
        dark -> FreshGreenDarkCells
        else -> FreshGreenLightCells
    }
    CompositionLocalProvider(
        LocalLedgerDimens provides if (density == TableDensity.COMFORTABLE) ComfortableDimens else CompactDimens,
        LocalLedgerCells provides cells,
    ) {
        MaterialTheme(
            colorScheme = resolved,
            typography = LedgerTypography,
            shapes = LedgerShapes,
            content = content,
        )
    }
}
