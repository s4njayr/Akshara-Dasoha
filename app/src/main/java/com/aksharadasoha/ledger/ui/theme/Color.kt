package com.aksharadasoha.ledger.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val PrintHeader = Color(0xFF1B4332)

data class LedgerCellColors(
    val input: Color,
    val computed: Color,
    val error: Color,
    val warning: Color,
    val audit: Color,
    val border: Color,
    val warningText: Color,
    val headerText: Color,
    val elevated: Color,
    val selected: Color,
    val divider: Color,
    val groupDivider: Color,
    val success: Color,
    val groupBands: List<Color>,
    val groupOn: Color,
)

val FreshGreenLight = lightColorScheme(
    primary = Color(0xFF1F6B45),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4E8D2),
    onPrimaryContainer = Color(0xFF002111),
    secondary = Color(0xFF4A6456),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8D7),
    onSecondaryContainer = Color(0xFF062017),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFE9F8),
    onTertiaryContainer = Color(0xFF001F27),
    background = Color(0xFFF6F8F6),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFFBFCFB),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val FreshGreenDark = darkColorScheme(
    primary = Color(0xFF8FD5AE),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF005231),
    onPrimaryContainer = Color(0xFFA8F2C8),
    secondary = Color(0xFFB1CCBB),
    onSecondary = Color(0xFF1D3529),
    secondaryContainer = Color(0xFF334B3F),
    onSecondaryContainer = Color(0xFFCCE8D7),
    tertiary = Color(0xFFA3CDDB),
    onTertiary = Color(0xFF033540),
    tertiaryContainer = Color(0xFF214C58),
    onTertiaryContainer = Color(0xFFBFE9F8),
    background = Color(0xFF0F1411),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF151A17),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

val OceanBlueLight = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF00687A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFABEDFF),
    onSecondaryContainer = Color(0xFF001F26),
    tertiary = Color(0xFF4A5D88),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD7E2FF),
    onTertiaryContainer = Color(0xFF001A41),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF171C22),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF171C22),
    surfaceVariant = Color(0xFFDDE3EE),
    onSurfaceVariant = Color(0xFF414751),
    outline = Color(0xFF717783),
    outlineVariant = Color(0xFFC1C7D2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val OceanBlueDark = darkColorScheme(
    primary = Color(0xFFA3C9FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF004884),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFF55D6F2),
    onSecondary = Color(0xFF003640),
    secondaryContainer = Color(0xFF004E5C),
    onSecondaryContainer = Color(0xFFABEDFF),
    tertiary = Color(0xFFB3C6F6),
    onTertiary = Color(0xFF1A2F57),
    tertiaryContainer = Color(0xFF324570),
    onTertiaryContainer = Color(0xFFD7E2FF),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFDEE3EA),
    surface = Color(0xFF151A20),
    onSurface = Color(0xFFDEE3EA),
    surfaceVariant = Color(0xFF414751),
    onSurfaceVariant = Color(0xFFC1C7D2),
    outline = Color(0xFF8B919C),
    outlineVariant = Color(0xFF414751),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

val FreshGreenLightCells = LedgerCellColors(
    input = Color(0xFFDCEFE3),
    computed = Color(0xFFEEF2EF),
    error = Color(0xFFFFDAD6),
    warning = Color(0xFFFFE8DC),
    audit = Color(0xFFD7E8F7),
    border = Color(0xFFB7C4BB),
    warningText = Color(0xFF8C1D18),
    headerText = Color(0xFFFFFFFF),
    elevated = Color(0xFFFFFFFF),
    selected = Color(0xFFD4E9DC),
    divider = Color(0xFFC0C9C1),
    groupDivider = Color(0xFF1F6B45),
    success = Color(0xFF1F6B45),
    groupBands = listOf(
        Color(0xFF1F6B45),
        Color(0xFF2E7D4F),
        Color(0xFF3B6470),
        Color(0xFF4A6456),
        Color(0xFF146C43),
        Color(0xFF2F5D50),
        Color(0xFF3E6B4F),
        Color(0xFF1B5E3B),
    ),
    groupOn = Color(0xFFFFFFFF),
)

val FreshGreenDarkCells = LedgerCellColors(
    input = Color(0xFF24362C),
    computed = Color(0xFF1B2420),
    error = Color(0xFF4A2C2C),
    warning = Color(0xFF3E2E24),
    audit = Color(0xFF1C3142),
    border = Color(0xFF5A665E),
    warningText = Color(0xFFFFB4AB),
    headerText = Color(0xFF003920),
    elevated = Color(0xFF1C2320),
    selected = Color(0xFF24382C),
    divider = Color(0xFF404943),
    groupDivider = Color(0xFF8FD5AE),
    success = Color(0xFF8FD5AE),
    groupBands = listOf(
        Color(0xFF8FD5AE),
        Color(0xFFA3CDDB),
        Color(0xFFB1CCBB),
        Color(0xFF7BB896),
        Color(0xFF9BB8A8),
        Color(0xFF6FA88A),
        Color(0xFF88C4A4),
        Color(0xFFB8D4C4),
    ),
    groupOn = Color(0xFF003920),
)

val OceanBlueLightCells = LedgerCellColors(
    input = Color(0xFFD9E8FA),
    computed = Color(0xFFEEF2F6),
    error = Color(0xFFFFDAD6),
    warning = Color(0xFFFFE8DC),
    audit = Color(0xFFD4F1F7),
    border = Color(0xFFB4C0CE),
    warningText = Color(0xFF8C1D18),
    headerText = Color(0xFFFFFFFF),
    elevated = Color(0xFFFFFFFF),
    selected = Color(0xFFD3E4FF),
    divider = Color(0xFFC1C7D2),
    groupDivider = Color(0xFF1565C0),
    success = Color(0xFF0B5E3A),
    groupBands = listOf(
        Color(0xFF1565C0),
        Color(0xFF00687A),
        Color(0xFF4A5D88),
        Color(0xFF0D47A1),
        Color(0xFF0277BD),
        Color(0xFF006064),
        Color(0xFF283593),
        Color(0xFF1565C0),
    ),
    groupOn = Color(0xFFFFFFFF),
)

val OceanBlueDarkCells = LedgerCellColors(
    input = Color(0xFF223348),
    computed = Color(0xFF1A222B),
    error = Color(0xFF4A2C2C),
    warning = Color(0xFF3E2E24),
    audit = Color(0xFF1A3A42),
    border = Color(0xFF556070),
    warningText = Color(0xFFFFB4AB),
    headerText = Color(0xFF00315C),
    elevated = Color(0xFF1C232B),
    selected = Color(0xFF223A54),
    divider = Color(0xFF414751),
    groupDivider = Color(0xFFA3C9FF),
    success = Color(0xFF8FD5AE),
    groupBands = listOf(
        Color(0xFFA3C9FF),
        Color(0xFF55D6F2),
        Color(0xFFB3C6F6),
        Color(0xFF8BB6E8),
        Color(0xFF7EC8D9),
        Color(0xFF9AB4E0),
        Color(0xFF6EB4D4),
        Color(0xFFB8D4F0),
    ),
    groupOn = Color(0xFF00315C),
)

fun cellColorsForScheme(scheme: ColorScheme, dark: Boolean): LedgerCellColors {
    return LedgerCellColors(
        input = scheme.secondaryContainer,
        computed = scheme.surfaceVariant,
        error = scheme.errorContainer,
        warning = scheme.tertiaryContainer,
        audit = scheme.primaryContainer,
        border = scheme.outlineVariant,
        warningText = scheme.error,
        headerText = if (dark) scheme.onPrimary else scheme.onPrimary,
        elevated = scheme.surface,
        selected = scheme.primaryContainer,
        divider = scheme.outlineVariant,
        groupDivider = scheme.outline,
        success = scheme.primary,
        groupBands = listOf(
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
            scheme.primary,
            scheme.secondary,
        ),
        groupOn = scheme.onPrimary,
    )
}

val LightCellColors = FreshGreenLightCells
val DarkCellColors = FreshGreenDarkCells

@Deprecated("Use LocalLedgerCells")
val CellInput = LightCellColors.input
@Deprecated("Use LocalLedgerCells")
val CellComputed = LightCellColors.computed
@Deprecated("Use LocalLedgerCells")
val CellError = LightCellColors.error
@Deprecated("Use LocalLedgerCells")
val CellWarning = LightCellColors.warning
@Deprecated("Use LocalLedgerCells")
val CellAudit = LightCellColors.audit
@Deprecated("Use LocalLedgerCells")
val CellBorder = LightCellColors.border
@Deprecated("Use LocalLedgerCells")
val WarningText = LightCellColors.warningText

val ForestPrimary = FreshGreenLight.primary
val ForestOnPrimary = FreshGreenLight.onPrimary
val ForestContainer = FreshGreenLight.primaryContainer
val ForestOnContainer = FreshGreenLight.onPrimaryContainer
val GoldAccent = FreshGreenLight.secondary
val GoldOn = FreshGreenLight.onSecondary
val GoldContainer = FreshGreenLight.secondaryContainer
val IvoryBackground = FreshGreenLight.background
val IvorySurface = FreshGreenLight.surface
val IvoryElevated = FreshGreenLightCells.elevated
val IvorySelected = FreshGreenLightCells.selected
val IvoryVariant = FreshGreenLight.surfaceVariant
val DarkForest = FreshGreenDark.primary
val DarkOnForest = FreshGreenDark.onPrimary
val DarkForestContainer = FreshGreenDark.primaryContainer
val DarkOnContainer = FreshGreenDark.onPrimaryContainer
val DarkGold = FreshGreenDark.secondary
val DarkGoldContainer = FreshGreenDark.secondaryContainer
val DarkBackground = FreshGreenDark.background
val DarkSurface = FreshGreenDark.surface
val DarkElevated = FreshGreenDarkCells.elevated
val DarkSelected = FreshGreenDarkCells.selected
val DarkVariant = FreshGreenDark.surfaceVariant

@Deprecated("Use ForestPrimary")
val GreenPrimary = ForestPrimary
@Deprecated("Use DarkForest")
val GreenPrimaryDark = DarkForest
@Deprecated("Use ForestContainer")
val GreenContainer = ForestContainer
@Deprecated("Use DarkForestContainer")
val GreenContainerDark = DarkForestContainer
@Deprecated("Use GoldAccent")
val OrangeSecondary = GoldAccent
@Deprecated("Use GoldContainer")
val OrangeContainer = GoldContainer
@Deprecated("Use IvoryBackground")
val CreamBackground = IvoryBackground
