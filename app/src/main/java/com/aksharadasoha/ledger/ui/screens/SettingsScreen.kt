package com.aksharadasoha.ledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aksharadasoha.ledger.R
import com.aksharadasoha.ledger.domain.AppSettings
import com.aksharadasoha.ledger.domain.ColorPalette
import com.aksharadasoha.ledger.domain.ExportFormat
import com.aksharadasoha.ledger.domain.SchoolProfile
import com.aksharadasoha.ledger.domain.TableDensity
import com.aksharadasoha.ledger.domain.ThemeMode
import com.aksharadasoha.ledger.ui.components.PrimaryActionButton
import com.aksharadasoha.ledger.ui.components.ScrollChips
import com.aksharadasoha.ledger.ui.components.SectionCard
import com.aksharadasoha.ledger.ui.components.ToolbarIconButton
import com.aksharadasoha.ledger.ui.theme.LedgerTheme
import com.aksharadasoha.ledger.ui.theme.LocalLedgerCells

internal data class SettingsDraft(
    val persisted: AppSettings,
    val name: String = persisted.school.name,
    val program: String = persisted.school.program,
    val theme: ThemeMode = persisted.themeMode,
    val density: TableDensity = persisted.tableDensity,
    val export: ExportFormat = persisted.defaultExport,
    val palette: ColorPalette = persisted.colorPalette,
) {
    fun current(): AppSettings = persisted.copy(
        school = SchoolProfile(name, program),
        themeMode = theme,
        tableDensity = density,
        language = "en",
        defaultExport = export,
        colorPalette = palette,
    )

    val dirty: Boolean get() = current() != persisted

    fun markSaved(saved: AppSettings = current()): SettingsDraft = copy(
        persisted = saved,
        name = saved.school.name,
        program = saved.school.program,
        theme = saved.themeMode,
        density = saved.tableDensity,
        export = saved.defaultExport,
        palette = saved.colorPalette,
    )

    fun restore(): SettingsDraft = copy(
        name = persisted.school.name,
        program = persisted.school.program,
        theme = persisted.themeMode,
        density = persisted.tableDensity,
        export = persisted.defaultExport,
        palette = persisted.colorPalette,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSave: (AppSettings, (Boolean) -> Unit) -> Unit,
    onPreview: (AppSettings) -> Unit = {},
    onLeaveUnsaved: () -> Unit = {},
    showBack: Boolean = false,
) {
    var draft by remember { mutableStateOf(SettingsDraft(settings)) }
    val latestDraft = rememberUpdatedState(draft)
    LaunchedEffect(draft.theme, draft.density, draft.name, draft.program, draft.export, draft.palette) {
        onPreview(draft.current())
    }
    DisposableEffect(Unit) {
        onDispose { if (latestDraft.value.dirty) onLeaveUnsaved() }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    if (showBack) {
                        ToolbarIconButton(
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            onClick = onBack,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Text(stringResource(R.string.section_school), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = draft.name, onValueChange = { draft = draft.copy(name = it) }, label = { Text(stringResource(R.string.school_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = draft.program, onValueChange = { draft = draft.copy(program = it) }, label = { Text(stringResource(R.string.program)) }, modifier = Modifier.fillMaxWidth())
            }
            SectionCard {
                Text(stringResource(R.string.section_appearance), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.appearance_theme), style = MaterialTheme.typography.labelMedium)
                ScrollChips {
                    FilterChip(selected = draft.theme == ThemeMode.SYSTEM, onClick = { draft = draft.copy(theme = ThemeMode.SYSTEM) }, label = { Text(stringResource(R.string.theme_system)) })
                    FilterChip(selected = draft.theme == ThemeMode.LIGHT, onClick = { draft = draft.copy(theme = ThemeMode.LIGHT) }, label = { Text(stringResource(R.string.theme_light)) })
                    FilterChip(selected = draft.theme == ThemeMode.DARK, onClick = { draft = draft.copy(theme = ThemeMode.DARK) }, label = { Text(stringResource(R.string.theme_dark)) })
                }
                Text(stringResource(R.string.appearance_palette), style = MaterialTheme.typography.labelMedium)
                ScrollChips(testTag = "settings-palettes") {
                    FilterChip(selected = draft.palette == ColorPalette.DYNAMIC, onClick = { draft = draft.copy(palette = ColorPalette.DYNAMIC) }, label = { Text(stringResource(R.string.palette_dynamic)) })
                    FilterChip(selected = draft.palette == ColorPalette.FRESH_GREEN, onClick = { draft = draft.copy(palette = ColorPalette.FRESH_GREEN) }, label = { Text(stringResource(R.string.palette_fresh_green)) })
                    FilterChip(selected = draft.palette == ColorPalette.OCEAN_BLUE, onClick = { draft = draft.copy(palette = ColorPalette.OCEAN_BLUE) }, label = { Text(stringResource(R.string.palette_ocean_blue)) })
                }
                PalettePreview()
                Text(stringResource(R.string.appearance_density), style = MaterialTheme.typography.labelMedium)
                ScrollChips {
                    FilterChip(selected = draft.density == TableDensity.COMPACT, onClick = { draft = draft.copy(density = TableDensity.COMPACT) }, label = { Text(stringResource(R.string.density_compact)) })
                    FilterChip(selected = draft.density == TableDensity.COMFORTABLE, onClick = { draft = draft.copy(density = TableDensity.COMFORTABLE) }, label = { Text(stringResource(R.string.density_comfortable)) })
                }
            }
            SectionCard {
                Text(stringResource(R.string.default_export), style = MaterialTheme.typography.titleMedium)
                ScrollChips {
                    FilterChip(selected = draft.export == ExportFormat.PDF, onClick = { draft = draft.copy(export = ExportFormat.PDF) }, label = { Text(stringResource(R.string.export_pdf)) })
                    FilterChip(selected = draft.export == ExportFormat.EXCEL, onClick = { draft = draft.copy(export = ExportFormat.EXCEL) }, label = { Text(stringResource(R.string.export_excel)) })
                    FilterChip(selected = draft.export == ExportFormat.BOTH, onClick = { draft = draft.copy(export = ExportFormat.BOTH) }, label = { Text(stringResource(R.string.export_both)) })
                }
            }
            PrimaryActionButton(
                label = stringResource(R.string.save_settings),
                onClick = {
                    val next = draft.current()
                    onSave(next) { ok ->
                        draft = if (ok) draft.markSaved(next) else draft.restore()
                        if (!ok) onPreview(draft.current())
                    }
                },
            )
        }
    }
}

@Composable
private fun PalettePreview() {
    val cells = LocalLedgerCells.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.surface,
            cells.input,
            cells.computed,
            cells.audit,
        ).forEach { color ->
            Box(
                Modifier
                    .weight(1f)
                    .height(16.dp)
                    .background(color, MaterialTheme.shapes.extraSmall),
            )
        }
    }
}

@Preview
@Composable
private fun SettingsPreview() {
    LedgerTheme {
        SettingsScreen(AppSettings(SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger")), {}, { _, _ -> })
    }
}
