package com.aksharadasoha.ledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aksharadasoha.ledger.R
import com.aksharadasoha.ledger.domain.LedgerTemplate
import com.aksharadasoha.ledger.domain.MonthlyReport
import com.aksharadasoha.ledger.domain.ReportFormat
import com.aksharadasoha.ledger.domain.ReportItem
import com.aksharadasoha.ledger.ui.ReportsState
import com.aksharadasoha.ledger.ui.components.EmptyState
import com.aksharadasoha.ledger.ui.components.ScrollChips
import com.aksharadasoha.ledger.ui.components.SecondaryActionButton
import com.aksharadasoha.ledger.ui.components.SectionCard
import com.aksharadasoha.ledger.ui.components.ToolbarIconButton
import com.aksharadasoha.ledger.ui.preview.PreviewData
import com.aksharadasoha.ledger.ui.theme.LedgerTheme
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: ReportsState,
    templates: List<LedgerTemplate>,
    onBack: () -> Unit,
    onFilter: (String?, Int?, Int?) -> Unit,
    onDownloadPdf: () -> Unit,
    onDownloadExcel: () -> Unit,
    onDownloadCsv: () -> Unit,
    onOpenInstance: (String) -> Unit = {},
    showBack: Boolean = false,
) {
    val years = state.reports.map { it.year }.distinct().sortedDescending()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_reports), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    ToolbarIconButton(
                        icon = Icons.Outlined.PictureAsPdf,
                        contentDescription = stringResource(R.string.action_download_pdf),
                        onClick = onDownloadPdf,
                    )
                    ToolbarIconButton(
                        icon = Icons.Outlined.TableChart,
                        contentDescription = stringResource(R.string.action_download_excel),
                        onClick = onDownloadExcel,
                    )
                    ToolbarIconButton(
                        icon = Icons.Outlined.Description,
                        contentDescription = stringResource(R.string.action_download_csv),
                        onClick = onDownloadCsv,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .testTag("reports-list")
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ReportFilters(state, templates, years, onFilter)
            }
            if (state.reports.isEmpty()) {
                item {
                    EmptyState(stringResource(R.string.no_reports_title), stringResource(R.string.empty_reports))
                }
            }
            items(state.reports, key = { it.instanceId }) { report ->
                ReportCard(report = report, onOpenInstance = onOpenInstance)
            }
        }
    }
}

@Composable
private fun ReportFilters(
    state: ReportsState,
    templates: List<LedgerTemplate>,
    years: List<Int>,
    onFilter: (String?, Int?, Int?) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .testTag("report-filters"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScrollChips(testTag = "report-filter-ledgers") {
            FilterChip(selected = state.selectedTemplateId == null, onClick = { onFilter(null, state.selectedYear, state.selectedMonth) }, label = { Text(stringResource(R.string.all_classes)) })
            templates.forEach { template ->
                FilterChip(
                    selected = state.selectedTemplateId == template.id,
                    onClick = { onFilter(template.id, state.selectedYear, state.selectedMonth) },
                    label = { Text(template.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        ScrollChips(testTag = "report-filter-years") {
            FilterChip(selected = state.selectedYear == null, onClick = { onFilter(state.selectedTemplateId, null, state.selectedMonth) }, label = { Text(stringResource(R.string.filter_all_years)) })
            years.forEach { year ->
                FilterChip(
                    selected = state.selectedYear == year,
                    onClick = { onFilter(state.selectedTemplateId, year, state.selectedMonth) },
                    label = { Text(year.toString()) },
                )
            }
        }
        ScrollChips(testTag = "report-filter-months") {
            FilterChip(selected = state.selectedMonth == null, onClick = { onFilter(state.selectedTemplateId, state.selectedYear, null) }, label = { Text(stringResource(R.string.filter_all_months)) })
            Month.entries.forEach { month ->
                FilterChip(
                    selected = state.selectedMonth == month.value,
                    onClick = { onFilter(state.selectedTemplateId, state.selectedYear, month.value) },
                    label = { Text(month.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportCard(report: MonthlyReport, onOpenInstance: (String) -> Unit) {
    SectionCard(Modifier.testTag("report-card-${report.instanceId}")) {
        Text(report.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(report.templateName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SummaryBadge(stringResource(R.string.report_children, ReportFormat.number(report.childrenServed)))
            SummaryBadge(stringResource(R.string.report_days, report.days))
            SummaryBadge(
                if (report.auditDifferences > 0) {
                    stringResource(R.string.audit_differences, report.auditDifferences)
                } else {
                    stringResource(R.string.no_audit_issues)
                },
                highlight = report.auditDifferences > 0,
            )
        }
        if (report.auditDifferences > 0) {
            SecondaryActionButton(
                label = stringResource(R.string.action_open),
                onClick = { onOpenInstance(report.instanceId) },
            )
        }
        report.items.forEach { item ->
            CommodityRow(item)
        }
    }
}

@Composable
private fun CommodityRow(item: ReportItem) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag("report-item-${item.key}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(item.label, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCell(stringResource(R.string.report_opening), ReportFormat.quantity(item.opening, item.unit), Modifier.weight(1f))
            MetricCell(stringResource(R.string.report_supply), ReportFormat.quantity(item.supply, item.unit), Modifier.weight(1f))
            MetricCell(stringResource(R.string.report_spent), ReportFormat.quantity(item.expenditure, item.unit), Modifier.weight(1f))
            MetricCell(stringResource(R.string.report_closing), ReportFormat.quantity(item.closing, item.unit), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryBadge(text: String, highlight: Boolean = false) {
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (highlight) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Preview(widthDp = 360, name = "Reports phone")
@Preview(widthDp = 840, name = "Reports tablet")
@Preview(fontScale = 1.3f, name = "Reports large font")
@Composable
private fun ReportsPreview() {
    LedgerTheme {
        ReportsScreen(PreviewData.reports, listOf(PreviewData.template), {}, { _, _, _ -> }, {}, {}, {})
    }
}
