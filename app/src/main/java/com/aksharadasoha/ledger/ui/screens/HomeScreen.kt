package com.aksharadasoha.ledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aksharadasoha.ledger.R
import com.aksharadasoha.ledger.domain.LedgerInstance
import com.aksharadasoha.ledger.domain.LedgerTemplate
import com.aksharadasoha.ledger.ui.HomeState
import com.aksharadasoha.ledger.domain.ThemeMode
import com.aksharadasoha.ledger.ui.components.AlternateActionButton
import com.aksharadasoha.ledger.ui.components.ConfirmDialog
import com.aksharadasoha.ledger.ui.components.DestructiveIconButton
import com.aksharadasoha.ledger.ui.components.EmptyState
import com.aksharadasoha.ledger.ui.components.LoadingState
import com.aksharadasoha.ledger.ui.components.LowEmphasisButton
import com.aksharadasoha.ledger.ui.components.MonthPickerDialog
import com.aksharadasoha.ledger.ui.components.ResponsiveActionRow
import com.aksharadasoha.ledger.ui.components.ScrollChips
import com.aksharadasoha.ledger.ui.components.SecondaryActionButton
import com.aksharadasoha.ledger.ui.components.ToolbarIconButton
import com.aksharadasoha.ledger.ui.preview.PreviewData
import com.aksharadasoha.ledger.ui.theme.LedgerTheme
import com.aksharadasoha.ledger.ui.theme.LocalLedgerCells
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onSelectGrade: (String?) -> Unit,
    onSelectTemplate: (String?) -> Unit,
    onOpenInstance: (String) -> Unit,
    onCreateMonth: (String) -> Unit,
    onOpenConfig: () -> Unit,
    onDismissMessage: () -> Unit,
    onBeginCreateMonth: (String) -> Unit = onCreateMonth,
    onUpdateMonthPicker: (Int?, Int?) -> Unit = { _, _ -> },
    onConfirmMonth: () -> Unit = {},
    onDismissMonthPicker: () -> Unit = {},
    onOpenLatest: (String) -> Unit = {},
    onRequestDeleteInstance: (String?) -> Unit = {},
    onConfirmDeleteInstance: () -> Unit = {},
    wide: Boolean = false,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onDismissMessage()
        }
    }
    val visibleTemplates = state.templates.filter { template ->
        state.selectedGradeId == null || state.selectedGradeId in template.gradeIds
    }
    val visibleInstances = state.instances.filter { instance ->
        (state.selectedTemplateId == null || instance.templateId == state.selectedTemplateId) &&
            visibleTemplates.any { it.id == instance.templateId }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.school.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.school.program, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    ToolbarIconButton(
                        icon = Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.action_configure),
                        onClick = onOpenConfig,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.ready) {
            LoadingState()
            return@Scaffold
        }
        if (wide) {
            Row(Modifier.padding(padding).fillMaxSize()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ClassFilters(state, onSelectGrade)
                    Text(stringResource(R.string.ledgers), style = MaterialTheme.typography.titleMedium)
                    if (visibleTemplates.isEmpty()) {
                        EmptyState(stringResource(R.string.no_ledgers_title), stringResource(R.string.empty_ledgers))
                    }
                    visibleTemplates.forEach { template ->
                        LedgerCard(
                            template = template,
                            instances = emptyList(),
                            selected = state.selectedTemplateId == template.id,
                            compact = true,
                            onSelect = { onSelectTemplate(if (state.selectedTemplateId == template.id) null else template.id) },
                            onOpen = onOpenInstance,
                            onCreate = { onBeginCreateMonth(template.id) },
                            onOpenLatest = { onOpenLatest(template.id) },
                            onDelete = onRequestDeleteInstance,
                        )
                    }
                }
                val selected = visibleTemplates.firstOrNull { it.id == state.selectedTemplateId } ?: visibleTemplates.firstOrNull()
                Column(
                    Modifier.weight(1.2f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (selected == null) {
                        EmptyState(stringResource(R.string.no_ledgers_title), stringResource(R.string.empty_ledgers))
                    } else {
                        LedgerCard(
                            template = selected,
                            instances = visibleInstances.filter { it.templateId == selected.id },
                            selected = true,
                            compact = false,
                            onSelect = {},
                            onOpen = onOpenInstance,
                            onCreate = { onBeginCreateMonth(selected.id) },
                            onOpenLatest = { onOpenLatest(selected.id) },
                            onDelete = onRequestDeleteInstance,
                        )
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClassFilters(state, onSelectGrade)
                Text(stringResource(R.string.ledgers), style = MaterialTheme.typography.titleMedium)
                if (visibleTemplates.isEmpty()) {
                    EmptyState(stringResource(R.string.no_ledgers_title), stringResource(R.string.empty_ledgers))
                }
                visibleTemplates.forEach { template ->
                    LedgerCard(
                        template = template,
                        instances = visibleInstances.filter { it.templateId == template.id },
                        selected = state.selectedTemplateId == template.id,
                        compact = false,
                        onSelect = { onSelectTemplate(if (state.selectedTemplateId == template.id) null else template.id) },
                        onOpen = onOpenInstance,
                        onCreate = { onBeginCreateMonth(template.id) },
                        onOpenLatest = { onOpenLatest(template.id) },
                        onDelete = onRequestDeleteInstance,
                    )
                }
            }
        }
    }
    state.monthPicker?.let { picker ->
        val carry = picker.carryValues.entries.take(4).joinToString("  ") { "${it.key}=${it.value ?: 0}" }
            .takeIf { it.isNotBlank() }?.let { stringResource(R.string.opening_from_previous, it) }
        MonthPickerDialog(
            title = stringResource(R.string.add_month_title),
            year = picker.year,
            month = picker.month,
            exists = picker.exists,
            carrySummary = carry,
            onYear = { onUpdateMonthPicker(it, null) },
            onMonth = { onUpdateMonthPicker(null, it) },
            onConfirm = onConfirmMonth,
            onDismiss = onDismissMonthPicker,
        )
    }
    if (state.confirmDeleteInstanceId != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_month_title),
            text = stringResource(R.string.delete_month_body),
            onConfirm = onConfirmDeleteInstance,
            onDismiss = { onRequestDeleteInstance(null) },
        )
    }
}

@Composable
private fun ClassFilters(state: HomeState, onSelectGrade: (String?) -> Unit) {
    Text(stringResource(R.string.classes), style = MaterialTheme.typography.titleMedium)
    ScrollChips(testTag = "home-class-filters") {
        FilterChip(selected = state.selectedGradeId == null, onClick = { onSelectGrade(null) }, label = { Text(stringResource(R.string.all_classes)) })
        state.grades.forEach { grade ->
            FilterChip(
                selected = state.selectedGradeId == grade.id,
                onClick = { onSelectGrade(grade.id) },
                label = { Text(grade.name) },
            )
        }
    }
}

@Composable
private fun LedgerCard(
    template: LedgerTemplate,
    instances: List<LedgerInstance>,
    selected: Boolean,
    compact: Boolean,
    onSelect: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenLatest: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val current = YearMonth.now()
    val latest = instances.maxWithOrNull(compareBy({ it.year }, { it.month }))
    val cells = LocalLedgerCells.current
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) cells.selected else cells.elevated,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleMedium)
            Text(template.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(
                    R.string.classes_line,
                    template.gradeIds.joinToString { it.removePrefix("grade_").replaceFirstChar { ch -> ch.titlecase() } },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            latest?.let {
                val month = Month.of(it.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
                Text(stringResource(R.string.latest_month, "$month ${it.year}"), style = MaterialTheme.typography.bodySmall)
            }
            if (!compact) {
                Spacer(Modifier.height(8.dp))
                if (instances.isEmpty()) {
                    Text(stringResource(R.string.no_months_hint), style = MaterialTheme.typography.bodySmall)
                }
                instances.forEach { instance ->
                    val month = Month.of(instance.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
                    val currentBadge = if (instance.year == current.year && instance.month == current.monthValue) {
                        "  • ${stringResource(R.string.current_month)}"
                    } else {
                        ""
                    }
                    Row(
                        Modifier.fillMaxWidth().testTag("home-month-row"),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        LowEmphasisButton(
                            label = "$month ${instance.year}  (${stringResource(R.string.days_count, instance.rows.size)})$currentBadge",
                            onClick = { onOpen(instance.id) },
                            modifier = Modifier.weight(1f).testTag("home-month-open"),
                        )
                        DestructiveIconButton(
                            contentDescription = stringResource(R.string.delete_month),
                            onClick = { onDelete(instance.id) },
                            modifier = Modifier.testTag("home-month-delete"),
                        )
                    }
                }
            }
            ResponsiveActionRow(
                primary = { modifier ->
                    SecondaryActionButton(
                        label = stringResource(R.string.add_month),
                        onClick = onCreate,
                        modifier = modifier.testTag("home-add-month"),
                    )
                },
                secondary = { modifier ->
                    AlternateActionButton(
                        label = stringResource(R.string.open_latest),
                        onClick = onOpenLatest,
                        modifier = modifier.testTag("home-open-latest"),
                    )
                },
            )
        }
    }
}

@Preview(widthDp = 360, name = "Home phone")
@Preview(widthDp = 840, name = "Home tablet")
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Home dark")
@Preview(fontScale = 1.3f, name = "Home large font")
@Composable
private fun HomePreview() {
    LedgerTheme(themeMode = ThemeMode.LIGHT) {
        HomeScreen(
            state = PreviewData.home,
            onSelectGrade = {},
            onSelectTemplate = {},
            onOpenInstance = {},
            onCreateMonth = {},
            onOpenConfig = {},
            onDismissMessage = {},
        )
    }
}
