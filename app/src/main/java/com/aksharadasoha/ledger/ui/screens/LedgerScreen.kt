package com.aksharadasoha.ledger.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.aksharadasoha.ledger.R
import com.aksharadasoha.ledger.domain.ColumnDef
import com.aksharadasoha.ledger.domain.ColumnRole
import com.aksharadasoha.ledger.domain.ComputedCell
import com.aksharadasoha.ledger.domain.ComputedRow
import com.aksharadasoha.ledger.domain.LedgerZoom
import com.aksharadasoha.ledger.domain.ValueType
import com.aksharadasoha.ledger.ui.LedgerUiState
import com.aksharadasoha.ledger.ui.components.CellLegend
import com.aksharadasoha.ledger.ui.components.DestructiveIconButton
import com.aksharadasoha.ledger.ui.components.EmptyState
import com.aksharadasoha.ledger.ui.components.LoadingState
import com.aksharadasoha.ledger.ui.components.ToolbarIconButton
import com.aksharadasoha.ledger.ui.preview.PreviewData
import com.aksharadasoha.ledger.ui.theme.LedgerTheme
import com.aksharadasoha.ledger.ui.theme.LocalLedgerCells
import com.aksharadasoha.ledger.ui.theme.LocalLedgerDimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    state: LedgerUiState,
    onBack: () -> Unit,
    onUpdate: (String, String, String) -> Unit,
    onAddDay: () -> Unit,
    onRequestDelete: (String) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onPrint: () -> Unit,
    onSharePdf: () -> Unit,
    onConfigure: () -> Unit,
    onDismissMessage: () -> Unit,
    onShowError: (String?) -> Unit = {},
    onToggleAudit: () -> Unit = {},
    onDownloadPdf: () -> Unit = {},
    onDownloadExcel: () -> Unit = {},
    onDownloadCsv: () -> Unit = {},
    onExport: () -> Unit = onDownloadPdf,
    zoom: Float = LedgerZoom.DEFAULT,
    onZoomChange: (Float) -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    var overflow by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<LedgerCellEdit?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val addDayDescription = stringResource(R.string.action_add_day)
    val exportDescription = stringResource(R.string.action_export)
    val moreDescription = stringResource(R.string.action_more)
    fun saveEditor() {
        val current = editor ?: return
        if (current.invalid) return
        onUpdate(current.rowId, current.columnKey, current.draft)
        editor = null
        focusManager.clearFocus()
        keyboard?.hide()
    }
    fun cancelEditor() {
        editor = null
        focusManager.clearFocus()
        keyboard?.hide()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onDismissMessage()
        }
    }
    val template = state.template
    val instance = state.instance
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(instance?.title ?: stringResource(R.string.ledgers), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    ToolbarIconButton(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        onClick = onBack,
                    )
                },
                actions = {
                    ToolbarIconButton(Icons.Outlined.Add, addDayDescription, onAddDay)
                    ToolbarIconButton(Icons.Outlined.FileDownload, exportDescription, onExport)
                    Box {
                        ToolbarIconButton(Icons.Outlined.MoreVert, moreDescription, onClick = { overflow = true })
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_print)) }, onClick = { overflow = false; onPrint() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_share_pdf)) }, onClick = { overflow = false; onSharePdf() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_download_pdf)) }, onClick = { overflow = false; onDownloadPdf() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_download_excel)) }, onClick = { overflow = false; onDownloadExcel() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_download_csv)) }, onClick = { overflow = false; onDownloadCsv() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_configure)) }, onClick = { overflow = false; onConfigure() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_audit)) }, onClick = { overflow = false; onToggleAudit() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_jump_date)) }, onClick = { overflow = false; showJump = true })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (template == null) {
            LoadingState()
            return@Scaffold
        }
        val scrollCols = template.columns.filter { it.visible && it.role != ColumnRole.DATE && it.valueType != ValueType.WEEKDAY }
        val scroll = rememberScrollState()
        val dimens = LocalLedgerDimens.current.scaled(zoom, LocalDensity.current.fontScale)
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
        val showActions = maxWidth >= 600.dp
        Column(
            Modifier
                .fillMaxSize()
                .ledgerPinchZoom(zoom, onZoomChange),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = showLegend,
                    onClick = { showLegend = !showLegend },
                    label = { Text(stringResource(if (showLegend) R.string.hide_legend else R.string.show_legend)) },
                )
                FilterChip(selected = state.showAudit, onClick = onToggleAudit, label = { Text(stringResource(R.string.action_audit)) })
                ZoomControls(zoom = zoom, onZoomChange = onZoomChange)
                if (state.pendingSave) Text(stringResource(R.string.pending_save), style = MaterialTheme.typography.labelSmall)
            }
            if (editor != null) {
                Row(
                    Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { saveEditor() }, modifier = Modifier.testTag("cell-save").defaultMinSize(minHeight = LocalLedgerDimens.current.minTouch)) {
                        Text(stringResource(R.string.action_save))
                    }
                    TextButton(onClick = { cancelEditor() }, modifier = Modifier.testTag("cell-cancel").defaultMinSize(minHeight = LocalLedgerDimens.current.minTouch)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
            if (showLegend) {
                Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { CellLegend() }
            }
            if (state.rows.isEmpty()) {
                EmptyState(stringResource(R.string.no_days_title), stringResource(R.string.empty_month), stringResource(R.string.action_add_day), onAddDay)
                return@Column
            }
            val actionWidth = if (showActions) dimens.actionWidth else 0.dp
            TwoLevelHeader(scrollCols, scroll, dimens.dateWidth, dimens.cellWidth, dimens.headerHeight, actionWidth)
            LazyColumn(Modifier.weight(1f), state = listState) {
                itemsIndexed(state.rows, key = { _, row -> row.row.id }) { _, row ->
                    LedgerDataRow(
                        row = row,
                        columns = scrollCols,
                        scroll = scroll,
                        dateWidth = dimens.dateWidth,
                        cellWidth = dimens.cellWidth,
                        rowHeight = dimens.rowHeight,
                        showDelete = showActions,
                        showAudit = state.showAudit,
                        editor = editor,
                        onEditorChange = { editor = it },
                        onSaveEditor = { saveEditor() },
                        onShowError = onShowError,
                        onDelete = { onRequestDelete(row.row.id) },
                    )
                }
            }
            TotalsRow(scrollCols, state.rows, dimens.dateWidth, dimens.cellWidth, dimens.headerHeight, scroll, actionWidth)
        }
        }
        if (showJump) {
            AlertDialog(
                onDismissRequest = { showJump = false },
                title = { Text(stringResource(R.string.action_jump_date)) },
                text = {
                    Column {
                        state.rows.forEachIndexed { index, row ->
                            TextButton(onClick = {
                                showJump = false
                                scope.launch { listState.animateScrollToItem(index) }
                            }) { Text(row.row.date) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showJump = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
    if (state.confirmDeleteRowId != null) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.delete_day_title)) },
            text = { Text(stringResource(R.string.delete_day_body)) },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    state.cellError?.let { (_, message) ->
        AlertDialog(
            onDismissRequest = { onShowError(null) },
            title = { Text(stringResource(R.string.cell_error)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { onShowError(null) }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

internal data class LedgerCellEdit(
    val rowId: String,
    val columnKey: String,
    val draft: String,
    val original: String,
    val numeric: Boolean,
    val invalid: Boolean,
)

internal data class ColumnGroup(
    val label: String?,
    val columns: List<ColumnDef>,
    val band: Int,
)

internal fun groupedColumns(columns: List<ColumnDef>): List<ColumnGroup> {
    val groups = mutableListOf<ColumnGroup>()
    var band = -1
    columns.forEach { column ->
        val last = groups.lastOrNull()
        if (last != null && last.label == column.groupLabel && !column.groupLabel.isNullOrBlank()) {
            groups[groups.lastIndex] = last.copy(columns = last.columns + column)
        } else {
            if (!column.groupLabel.isNullOrBlank()) band += 1
            groups += ColumnGroup(column.groupLabel, listOf(column), band.coerceAtLeast(0))
        }
    }
    return groups
}

@Composable
private fun TwoLevelHeader(
    columns: List<ColumnDef>,
    scroll: androidx.compose.foundation.ScrollState,
    dateWidth: Dp,
    cellWidth: Dp,
    headerHeight: Dp,
    actionWidth: Dp,
) {
    val colors = LocalLedgerCells.current
    val groups = groupedColumns(columns)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        HeaderCell(
            text = stringResource(R.string.date_column),
            width = dateWidth,
            height = headerHeight * 2,
            background = MaterialTheme.colorScheme.primary,
            onColor = colors.headerText,
            testTag = "header-date",
        )
        if (actionWidth > 0.dp) {
            HeaderCell(
                text = "",
                width = actionWidth,
                height = headerHeight * 2,
                background = MaterialTheme.colorScheme.primary,
                onColor = colors.headerText,
                testTag = "header-actions",
            )
        }
        Row(Modifier.horizontalScroll(scroll)) {
            groups.forEach { group ->
                if (group.label.isNullOrBlank()) {
                    val column = group.columns.single()
                    HeaderCell(
                        text = column.label,
                        width = cellWidth,
                        height = headerHeight * 2,
                        background = MaterialTheme.colorScheme.primary,
                        onColor = colors.headerText,
                        testTag = "header-col-${column.key}",
                    )
                } else {
                    val band = colors.groupBands[group.band % colors.groupBands.size]
                    Column {
                        HeaderCell(
                            text = group.label,
                            width = cellWidth * group.columns.size,
                            height = headerHeight,
                            background = band,
                            onColor = colors.groupOn,
                            testTag = "group-${group.label}",
                        )
                        Row {
                            group.columns.forEach { column ->
                                HeaderCell(
                                    text = column.label,
                                    width = cellWidth,
                                    height = headerHeight,
                                    background = band,
                                    onColor = colors.groupOn,
                                    testTag = "header-col-${column.key}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerDataRow(
    row: ComputedRow,
    columns: List<ColumnDef>,
    scroll: androidx.compose.foundation.ScrollState,
    dateWidth: Dp,
    cellWidth: Dp,
    rowHeight: Dp,
    showDelete: Boolean,
    showAudit: Boolean,
    editor: LedgerCellEdit?,
    onEditorChange: (LedgerCellEdit?) -> Unit,
    onSaveEditor: () -> Unit,
    onShowError: (String?) -> Unit,
    onDelete: () -> Unit,
) {
    val cells = LocalLedgerCells.current
    var menu by remember { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(row.row.date) }.getOrNull()
    val primary = date?.format(DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())) ?: row.row.date
    val weekday = date?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale.getDefault()).orEmpty()
    val dateSemantics = listOfNotNull(row.row.date, weekday).joinToString(", ")
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (row.cells.values.any { it.negativeWarning }) cells.warning.copy(alpha = 0.25f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Column(
                Modifier
                    .width(dateWidth)
                    .heightIn(min = max(rowHeight, LocalLedgerDimens.current.minTouch))
                    .height(max(rowHeight, LocalLedgerDimens.current.minTouch))
                    .background(cells.computed)
                    .border(0.5.dp, cells.border)
                    .combinedClickable(onClick = { menu = true }, onLongClick = { menu = true })
                    .padding(4.dp)
                    .semantics { contentDescription = dateSemantics },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(primary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(weekday, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
        if (showDelete) {
            DestructiveIconButton(
                contentDescription = stringResource(R.string.row_actions),
                onClick = onDelete,
                modifier = Modifier
                    .width(LocalLedgerDimens.current.actionWidth)
                    .semantics { contentDescription = "Row actions" },
            )
        }
        val groups = groupedColumns(columns)
        Row(Modifier.horizontalScroll(scroll)) {
            groups.forEach { group ->
                group.columns.forEachIndexed { index, column ->
                    LedgerCell(
                        row = row,
                        column = column,
                        cell = row.cells[column.key],
                        width = cellWidth,
                        height = rowHeight,
                        editor = editor,
                        onEditorChange = onEditorChange,
                        onSaveEditor = onSaveEditor,
                        onShowError = onShowError,
                        showAudit = showAudit,
                        groupStart = index == 0 && !group.label.isNullOrBlank(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalsRow(
    columns: List<ColumnDef>,
    rows: List<ComputedRow>,
    dateWidth: Dp,
    cellWidth: Dp,
    headerHeight: Dp,
    scroll: androidx.compose.foundation.ScrollState,
    actionWidth: Dp,
) {
    val last = rows.lastOrNull() ?: return
    val colors = LocalLedgerCells.current
    val groups = groupedColumns(columns)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        HeaderCell(stringResource(R.string.closing), dateWidth, headerHeight, MaterialTheme.colorScheme.primary, colors.headerText)
        if (actionWidth > 0.dp) {
            HeaderCell("", actionWidth, headerHeight, MaterialTheme.colorScheme.primary, colors.headerText, testTag = "totals-actions")
        }
        Row(Modifier.horizontalScroll(scroll)) {
            groups.forEach { group ->
                val band = colors.groupBands[group.band % colors.groupBands.size]
                group.columns.forEachIndexed { index, column ->
                    val value = last.cells[column.key]
                    HeaderCell(
                        text = if (column.key.contains("balance") || column.key.contains("total") || column.key == "count") display(value) else "",
                        width = cellWidth,
                        height = headerHeight,
                        background = if (group.label.isNullOrBlank()) MaterialTheme.colorScheme.primary else band,
                        onColor = if (group.label.isNullOrBlank()) colors.headerText else colors.groupOn,
                        groupStart = index == 0 && !group.label.isNullOrBlank(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(
    text: String,
    width: Dp,
    height: Dp,
    background: androidx.compose.ui.graphics.Color,
    onColor: androidx.compose.ui.graphics.Color,
    groupStart: Boolean = false,
    testTag: String? = null,
) {
    val colors = LocalLedgerCells.current
    Box(
        Modifier
            .width(width)
            .heightIn(min = height)
            .height(height)
            .background(background)
            .border(0.5.dp, colors.border)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics { if (text.isNotBlank()) contentDescription = text }
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = onColor, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerCell(
    row: ComputedRow,
    column: ColumnDef,
    cell: ComputedCell?,
    width: Dp,
    height: Dp,
    editor: LedgerCellEdit?,
    onEditorChange: (LedgerCellEdit?) -> Unit,
    onSaveEditor: () -> Unit,
    onShowError: (String?) -> Unit,
    showAudit: Boolean,
    groupStart: Boolean = false,
) {
    val cells = LocalLedgerCells.current
    val editable = column.role == ColumnRole.INPUT || (column.role == ColumnRole.OPENING && row.row.sortOrder == 1)
    val warning = cell?.negativeWarning == true
    val background = when {
        cell?.error != null -> cells.error
        showAudit && cell?.auditDiffers == true -> cells.audit
        warning -> cells.warning
        editable -> cells.input
        else -> cells.computed
    }
    val stored = display(cell)
    val active = editor?.rowId == row.row.id && editor.columnKey == column.key
    val draft = if (active) editor.draft else stored
    val invalid = active && editor.invalid
    val numeric = column.valueType != ValueType.TEXT
    Box(
        Modifier
            .width(width)
            .heightIn(min = max(height, LocalLedgerDimens.current.minTouch))
            .height(max(height, LocalLedgerDimens.current.minTouch))
            .background(background)
            .border(0.5.dp, if (invalid) MaterialTheme.colorScheme.error else cells.border)
            .semantics {
                contentDescription = listOfNotNull(
                    column.groupLabel,
                    column.label,
                    row.row.date,
                    if (editable) "editable" else "calculated",
                    if (warning) "negative warning" else null,
                    if (invalid) "invalid input" else null,
                    cell?.error ?: draft,
                    if (cell?.auditDiffers == true) "differs from imported Excel" else null,
                ).joinToString(", ")
            }
            .testTag(if (groupStart) "group-start-${column.key}" else "cell-target-${column.key}")
            .padding(horizontal = 6.dp)
            .combinedClickable(enabled = cell?.error != null, onClick = { onShowError(cell?.error) }),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (editable) {
            BasicTextField(
                value = draft,
                onValueChange = { incoming ->
                    if (!numeric || incoming.isEmpty() || incoming.matches(Regex("""-?\d*\.?\d*"""))) {
                        val nextInvalid = numeric && incoming.isNotEmpty() && incoming.toDoubleOrNull() == null
                        onEditorChange(
                            LedgerCellEdit(row.row.id, column.key, incoming, stored, numeric, nextInvalid),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSaveEditor() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .testTag("cell-${column.key}")
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onEditorChange(
                                editor?.takeIf { it.rowId == row.row.id && it.columnKey == column.key }
                                    ?: LedgerCellEdit(row.row.id, column.key, stored, stored, numeric, false),
                            )
                        }
                    },
                singleLine = true,
            )
        } else {
            val shown = if (showAudit && cell?.auditDiffers == true) {
                "${display(cell)} / ${cell.sourceNumber}"
            } else {
                display(cell)
            }
            Text(shown, style = MaterialTheme.typography.bodySmall, color = if (warning) cells.warningText else Color.Unspecified)
        }
    }
}

private fun display(cell: ComputedCell?): String {
    if (cell == null) return ""
    if (cell.error != null) return "!"
    if (cell.text != null) return cell.text
    val number = cell.number ?: return ""
    return if (number == number.toLong().toDouble()) number.toLong().toString() else "%.3f".format(number).trimEnd('0').trimEnd('.')
}

@Composable
private fun ZoomControls(zoom: Float, onZoomChange: (Float) -> Unit) {
    Row(
        Modifier.testTag("ledger-zoom"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToolbarIconButton(
            icon = Icons.Outlined.Remove,
            contentDescription = stringResource(R.string.action_zoom_out),
            onClick = { onZoomChange(LedgerZoom.clamp(zoom - LedgerZoom.STEP)) },
            modifier = Modifier.testTag("zoom-out"),
        )
        Text(
            stringResource(R.string.zoom_percent, (zoom * 100).toInt()),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag("zoom-value"),
        )
        ToolbarIconButton(
            icon = Icons.Outlined.RestartAlt,
            contentDescription = stringResource(R.string.action_zoom_reset),
            onClick = { onZoomChange(LedgerZoom.DEFAULT) },
            modifier = Modifier.testTag("zoom-reset"),
        )
        ToolbarIconButton(
            icon = Icons.Outlined.Add,
            contentDescription = stringResource(R.string.action_zoom_in),
            onClick = { onZoomChange(LedgerZoom.clamp(zoom + LedgerZoom.STEP)) },
            modifier = Modifier.testTag("zoom-in"),
        )
    }
}

private fun Modifier.ledgerPinchZoom(zoom: Float, onZoomChange: (Float) -> Unit): Modifier {
    return pointerInput(zoom) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val zoomChange = event.calculateZoom()
                    if (zoomChange != 1f) {
                        onZoomChange(LedgerZoom.clamp(zoom * zoomChange))
                        pressed.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

@Preview(widthDp = 360, name = "Ledger phone")
@Preview(widthDp = 840, name = "Ledger tablet")
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Ledger dark")
@Preview(fontScale = 1.3f, name = "Ledger large font")
@Composable
private fun LedgerPreview() {
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
        )
    }
}
