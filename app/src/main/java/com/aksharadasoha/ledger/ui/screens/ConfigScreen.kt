package com.aksharadasoha.ledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aksharadasoha.ledger.R
import com.aksharadasoha.ledger.domain.ColumnDef
import com.aksharadasoha.ledger.domain.ColumnRole
import com.aksharadasoha.ledger.domain.ComputedRow
import com.aksharadasoha.ledger.domain.FormulaValidator
import com.aksharadasoha.ledger.domain.RateDef
import com.aksharadasoha.ledger.domain.ValueType
import com.aksharadasoha.ledger.ui.ConfigState
import com.aksharadasoha.ledger.ui.components.ConfirmDialog
import com.aksharadasoha.ledger.ui.components.DestructiveActionButton
import com.aksharadasoha.ledger.ui.components.DestructiveIconButton
import com.aksharadasoha.ledger.ui.components.PrimaryActionButton
import com.aksharadasoha.ledger.ui.components.ScrollChips
import com.aksharadasoha.ledger.ui.components.SecondaryActionButton
import com.aksharadasoha.ledger.ui.components.SectionCard
import com.aksharadasoha.ledger.ui.components.ToolbarIconButton
import com.aksharadasoha.ledger.ui.preview.PreviewData
import com.aksharadasoha.ledger.ui.theme.LedgerTheme
import com.aksharadasoha.ledger.ui.theme.LocalLedgerCells

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    state: ConfigState,
    onBack: () -> Unit,
    onSelectTemplate: (String) -> Unit,
    onSaveTemplate: (String, String, List<String>) -> Unit,
    onAddColumn: () -> Unit,
    onSaveColumn: (ColumnDef) -> Unit,
    onMoveColumn: (String, Int) -> Unit,
    onRequestDeleteColumn: (String?) -> Unit,
    onConfirmDeleteColumn: () -> Unit,
    onAddRate: () -> Unit,
    onSaveRate: (RateDef) -> Unit,
    onDeleteRate: (String) -> Unit,
    onAddGrade: (String) -> Unit,
    onDeleteGrade: (String) -> Unit,
    onCreateTemplate: (String, String, List<String>) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onRequestDeleteTemplate: (String?) -> Unit,
    onRequestDeleteGrade: (String?) -> Unit,
    onRequestDeleteRate: (String?) -> Unit,
    onConfirmDeleteTemplate: () -> Unit,
    onConfirmDeleteGrade: () -> Unit,
    onConfirmDeleteRate: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onDismissMessage()
        }
    }
    val selected = state.selected
    var name by remember(selected?.id) { mutableStateOf(selected?.name.orEmpty()) }
    var description by remember(selected?.id) { mutableStateOf(selected?.description.orEmpty()) }
    var gradeIds by remember(selected?.id) { mutableStateOf(selected?.gradeIds.orEmpty()) }
    var newGrade by remember { mutableStateOf("") }
    var newLedger by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var expandedRateId by remember { mutableStateOf<String?>(null) }
    var expandedColumnId by remember { mutableStateOf<String?>(null) }
    val tabs = listOf(
        stringResource(R.string.tab_classes),
        stringResource(R.string.tab_ledgers),
        stringResource(R.string.tab_rates),
        stringResource(R.string.tab_columns),
        stringResource(R.string.tab_preview),
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.configure_ledgers)) },
                navigationIcon = {
                    ToolbarIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack)
                },
                actions = {
                    ToolbarIconButton(Icons.Outlined.Add, stringResource(R.string.add_column), onAddColumn)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    0 -> SectionCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = newGrade, onValueChange = { newGrade = it }, label = { Text(stringResource(R.string.add_class)) }, modifier = Modifier.weight(1f))
                            SecondaryActionButton(label = stringResource(R.string.add), onClick = { if (newGrade.isNotBlank()) { onAddGrade(newGrade); newGrade = "" } })
                        }
                        state.grades.forEach { grade ->
                            Row {
                                Text(grade.name, Modifier.weight(1f).padding(top = 12.dp))
                                DestructiveIconButton(stringResource(R.string.delete_class), onClick = { onRequestDeleteGrade(grade.id) })
                            }
                        }
                    }
                    1 -> {
                        SectionCard {
                            ScrollChips {
                                state.templates.forEach { template ->
                                    FilterChip(
                                        selected = selected?.id == template.id,
                                        onClick = { onSelectTemplate(template.id) },
                                        label = { Text(template.name) },
                                    )
                                }
                            }
                            Row {
                                OutlinedTextField(value = newLedger, onValueChange = { newLedger = it }, label = { Text(stringResource(R.string.new_ledger_name)) }, modifier = Modifier.weight(1f))
                                SecondaryActionButton(
                                    label = stringResource(R.string.action_create),
                                    onClick = {
                                        if (newLedger.isNotBlank()) {
                                            onCreateTemplate(newLedger, "", gradeIds.ifEmpty { state.grades.map { it.id } })
                                            newLedger = ""
                                        }
                                    },
                                )
                            }
                        }
                        if (selected != null) {
                            SectionCard {
                                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.ledger_name)) }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.description)) }, modifier = Modifier.fillMaxWidth())
                                Text(stringResource(R.string.shown_for_classes))
                                ScrollChips {
                                    state.grades.forEach { grade ->
                                        FilterChip(
                                            selected = grade.id in gradeIds,
                                            onClick = {
                                                gradeIds = if (grade.id in gradeIds) gradeIds - grade.id else gradeIds + grade.id
                                            },
                                            label = { Text(grade.name) },
                                        )
                                    }
                                }
                                PrimaryActionButton(label = stringResource(R.string.save_ledger), onClick = { onSaveTemplate(name, description, gradeIds) })
                                DestructiveActionButton(label = stringResource(R.string.delete_ledger), onClick = { onRequestDeleteTemplate(selected.id) }, filled = false)
                            }
                        }
                    }
                    2 -> if (selected != null) {
                        SectionCard {
                            SecondaryActionButton(label = stringResource(R.string.add_rate), onClick = onAddRate)
                            selected.rates.forEach { rate ->
                                if (expandedRateId == rate.id) {
                                    RateEditor(rate, selected.rates.map { it.key }.toSet(), onSaveRate) { onRequestDeleteRate(rate.id) }
                                } else {
                                    Text(
                                        "${rate.label} = ${rate.value}",
                                        Modifier.fillMaxWidth().clickable { expandedRateId = rate.id }.padding(vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                    3 -> if (selected != null) {
                        SectionCard {
                            selected.columns.sortedBy { it.sortOrder }.forEach { column ->
                                if (expandedColumnId == column.id) {
                                    ColumnEditor(
                                        column = column,
                                        knownKeys = selected.columns.map { it.key }.toSet() + selected.rates.map { it.key },
                                        onSave = onSaveColumn,
                                        onMove = onMoveColumn,
                                        onDelete = { onRequestDeleteColumn(column.id) },
                                    )
                                } else {
                                    Text(
                                        "${column.label} (${column.key})",
                                        Modifier.fillMaxWidth().clickable { expandedColumnId = column.id }.padding(vertical = 8.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                            }
                        }
                    }
                    else -> state.preview?.let { preview ->
                        selected?.let {
                            SectionCard {
                                Text(stringResource(R.string.preview_first_day), style = MaterialTheme.typography.titleMedium)
                                FormulaPreview(it.columns.filter { column -> column.visible }, preview)
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.confirmDeleteColumnId != null) {
        ConfirmDialog(stringResource(R.string.delete_column_title), stringResource(R.string.delete_column_body), onConfirm = onConfirmDeleteColumn, onDismiss = { onRequestDeleteColumn(null) })
    }
    if (state.confirmDeleteTemplateId != null) {
        ConfirmDialog(stringResource(R.string.delete_ledger_title), stringResource(R.string.delete_ledger_body), onConfirm = onConfirmDeleteTemplate, onDismiss = { onRequestDeleteTemplate(null) })
    }
    if (state.confirmDeleteGradeId != null) {
        ConfirmDialog(stringResource(R.string.delete_class_title), stringResource(R.string.delete_class_body), onConfirm = onConfirmDeleteGrade, onDismiss = { onRequestDeleteGrade(null) })
    }
    if (state.confirmDeleteRateId != null) {
        ConfirmDialog(stringResource(R.string.delete_rate_title), stringResource(R.string.delete_rate_body), onConfirm = onConfirmDeleteRate, onDismiss = { onRequestDeleteRate(null) })
    }
}

@Composable
private fun FormulaPreview(columns: List<ColumnDef>, preview: ComputedRow) {
    val cells = LocalLedgerCells.current
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        columns.take(12).forEach { column ->
            val cell = preview.cells[column.key]
            Column(Modifier.width(88.dp).border(0.5.dp, MaterialTheme.colorScheme.outline).background(cells.computed).padding(6.dp)) {
                Text(column.label, style = MaterialTheme.typography.labelSmall)
                Text(cell?.number?.toString() ?: cell?.text ?: cell?.error ?: "", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RateEditor(rate: RateDef, existingKeys: Set<String>, onSave: (RateDef) -> Unit, onDelete: () -> Unit) {
    var label by remember(rate.id) { mutableStateOf(rate.label) }
    var key by remember(rate.id) { mutableStateOf(rate.key) }
    var value by remember(rate.id) { mutableStateOf(rate.value.toString()) }
    var unit by remember(rate.id) { mutableStateOf(rate.unit.orEmpty()) }
    val parsed = value.toDoubleOrNull()
    val keyError = when {
        key.isBlank() -> stringResource(R.string.blank_key)
        key != rate.key && key in existingKeys -> stringResource(R.string.duplicate_key)
        else -> null
    }
    val valueError = if (parsed == null || !parsed.isFinite()) stringResource(R.string.invalid_number) else null
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.rate_label)) }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.key_label)) }, isError = keyError != null, supportingText = keyError?.let { { Text(it) } }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(stringResource(R.string.value_label)) }, isError = valueError != null, supportingText = valueError?.let { { Text(it) } }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(stringResource(R.string.unit_label)) }, modifier = Modifier.weight(1f))
        }
        Row {
            PrimaryActionButton(
                label = stringResource(R.string.save_rate),
                enabled = keyError == null && valueError == null && parsed != null,
                onClick = { onSave(rate.copy(label = label, key = key, value = parsed!!, unit = unit.ifBlank { null })) },
            )
            DestructiveActionButton(label = stringResource(R.string.remove), onClick = onDelete, filled = false)
        }
    }
}

@Composable
private fun ColumnEditor(
    column: ColumnDef,
    knownKeys: Set<String>,
    onSave: (ColumnDef) -> Unit,
    onMove: (String, Int) -> Unit,
    onDelete: () -> Unit,
) {
    var label by remember(column.id) { mutableStateOf(column.label) }
    var key by remember(column.id) { mutableStateOf(column.key) }
    var group by remember(column.id) { mutableStateOf(column.groupLabel.orEmpty()) }
    var unit by remember(column.id) { mutableStateOf(column.unit.orEmpty()) }
    var formula by remember(column.id) { mutableStateOf(column.formula.orEmpty()) }
    var role by remember(column.id) { mutableStateOf(column.role) }
    var valueType by remember(column.id) { mutableStateOf(column.valueType) }
    var visible by remember(column.id) { mutableStateOf(column.visible) }
    var warn by remember(column.id) { mutableStateOf(column.warnNegative) }
    val keyError = when {
        key.isBlank() -> stringResource(R.string.blank_key)
        key != column.key && key in knownKeys -> stringResource(R.string.duplicate_key)
        else -> null
    }
    val formulaErrors = if (formula.isBlank()) emptyList() else FormulaValidator.validate(formula, knownKeys - column.key + key)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${column.label} (${column.key})", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.label_label)) }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.key_label)) }, isError = keyError != null, supportingText = keyError?.let { { Text(it) } }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text(stringResource(R.string.group_label)) }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(stringResource(R.string.unit_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = formula,
            onValueChange = { formula = it },
            label = { Text(stringResource(R.string.formula_label)) },
            isError = formulaErrors.isNotEmpty(),
            supportingText = {
                if (formulaErrors.isNotEmpty()) Text(formulaErrors.joinToString("\n"))
                else if (formula.isNotBlank()) Text(FormulaValidator.referencedColumns(formula).joinToString(", "))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        ScrollChips {
            ColumnRole.entries.forEach { item ->
                FilterChip(selected = role == item, onClick = { role = item }, label = { Text(item.name) })
            }
        }
        ScrollChips {
            ValueType.entries.forEach { item ->
                FilterChip(selected = valueType == item, onClick = { valueType = item }, label = { Text(item.name) })
            }
        }
        Row {
            Text(stringResource(R.string.visible_label), Modifier.padding(end = 8.dp, top = 12.dp))
            Switch(checked = visible, onCheckedChange = { visible = it })
            Text(stringResource(R.string.warn_negative), Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp))
            Switch(checked = warn, onCheckedChange = { warn = it })
        }
        Row {
            ToolbarIconButton(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.move_up), onClick = { onMove(column.id, -1) })
            ToolbarIconButton(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.move_down), onClick = { onMove(column.id, 1) })
            PrimaryActionButton(
                label = stringResource(R.string.save_column),
                enabled = keyError == null && formulaErrors.isEmpty(),
                onClick = {
                    onSave(
                        column.copy(
                            label = label,
                            key = key,
                            groupLabel = group.ifBlank { null },
                            unit = unit.ifBlank { null },
                            formula = formula.ifBlank { null },
                            role = role,
                            valueType = valueType,
                            visible = visible,
                            warnNegative = warn,
                        ),
                    )
                },
            )
            DestructiveIconButton(stringResource(R.string.delete_column), onDelete)
        }
    }
}

@Preview
@Composable
private fun ConfigPreview() {
    LedgerTheme {
        ConfigScreen(
            state = PreviewData.config,
            onBack = {},
            onSelectTemplate = {},
            onSaveTemplate = { _, _, _ -> },
            onAddColumn = {},
            onSaveColumn = {},
            onMoveColumn = { _, _ -> },
            onRequestDeleteColumn = {},
            onConfirmDeleteColumn = {},
            onAddRate = {},
            onSaveRate = {},
            onDeleteRate = {},
            onAddGrade = {},
            onDeleteGrade = {},
            onCreateTemplate = { _, _, _ -> },
            onDeleteTemplate = {},
            onDismissMessage = {},
            onRequestDeleteTemplate = {},
            onRequestDeleteGrade = {},
            onRequestDeleteRate = {},
            onConfirmDeleteTemplate = {},
            onConfirmDeleteGrade = {},
            onConfirmDeleteRate = {},
        )
    }
}
