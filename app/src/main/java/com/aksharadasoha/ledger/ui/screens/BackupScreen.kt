package com.aksharadasoha.ledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aksharadasoha.ledger.R
import com.aksharadasoha.ledger.ui.BackupState
import com.aksharadasoha.ledger.domain.ThemeMode
import com.aksharadasoha.ledger.ui.components.AlternateActionButton
import com.aksharadasoha.ledger.ui.components.ConfirmDialog
import com.aksharadasoha.ledger.ui.components.DestructiveActionButton
import com.aksharadasoha.ledger.ui.components.LowEmphasisButton
import com.aksharadasoha.ledger.ui.components.PrimaryActionButton
import com.aksharadasoha.ledger.ui.components.SecondaryActionButton
import com.aksharadasoha.ledger.ui.components.SectionCard
import com.aksharadasoha.ledger.ui.theme.LedgerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    state: BackupState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onChooseRestore: () -> Unit,
    onRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onChooseExcel: () -> Unit = {},
    onImportExcel: () -> Unit = {},
    onReplaceExcel: () -> Unit = {},
    onCancelReplace: () -> Unit = {},
    onApplySeedUpgrade: () -> Unit = {},
    onDismissMessage: () -> Unit = {},
    showBack: Boolean = false,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message, state.error) {
        (state.message ?: state.error)?.let {
            snackbar.showSnackbar(it)
            onDismissMessage()
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.backup_and_restore)) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.backup_blurb))
            PrimaryActionButton(label = stringResource(R.string.export_backup), onClick = onExport)
            AlternateActionButton(label = stringResource(R.string.choose_backup), onClick = onChooseRestore)
            AlternateActionButton(label = stringResource(R.string.import_excel), onClick = onChooseExcel)
            if (state.busy) CircularProgressIndicator()
            state.preview?.let { item ->
                SectionCard {
                    Text(stringResource(R.string.restore_this), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.school_line, item.school))
                    Text(stringResource(R.string.classes_count, item.grades))
                    Text(stringResource(R.string.ledgers_count, item.templates))
                    Text(stringResource(R.string.months_line, item.instances))
                    Text(stringResource(R.string.days_line, item.rows))
                    item.exportedAt?.let { Text(stringResource(R.string.exported_on, it)) }
                    Text(stringResource(R.string.this_replaces))
                    DestructiveActionButton(label = stringResource(R.string.replace_local), onClick = onRestore)
                    LowEmphasisButton(label = stringResource(R.string.action_cancel), onClick = onCancelRestore)
                }
            }
            state.excelPreview?.let { item ->
                SectionCard {
                    Text(stringResource(R.string.import_this_excel), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.school_line, item.school))
                    Text(stringResource(R.string.sheets_line, item.sheets))
                    Text(stringResource(R.string.months_line, item.instances))
                    Text(stringResource(R.string.days_line, item.rows))
                    Text(stringResource(R.string.added_updated, item.addedInstances, item.updatedInstances))
                    Text(stringResource(R.string.preserved_months, item.preservedInstances))
                    Text(stringResource(R.string.unchanged_months, item.unchangedInstances))
                    Text(stringResource(R.string.would_remove_months, item.wouldRemoveInstances))
                    if (!item.recognized) {
                        Text(stringResource(R.string.unrecognized_excel), color = MaterialTheme.colorScheme.error)
                    }
                    PrimaryActionButton(label = stringResource(R.string.merge_import), onClick = onImportExcel)
                    AlternateActionButton(label = stringResource(R.string.replace_local), onClick = onReplaceExcel)
                }
            }
            state.seedUpgrade?.takeIf { it.available }?.let { upgrade ->
                SectionCard {
                    Text(stringResource(R.string.updated_workbook), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.seed_upgrade_body, upgrade.bundledVersion, upgrade.newInstances, upgrade.newRows))
                    SecondaryActionButton(label = stringResource(R.string.merge_updated), onClick = onApplySeedUpgrade)
                }
            }
            Text(stringResource(R.string.workbook_corrections), style = MaterialTheme.typography.titleMedium)
            state.corrections.forEach { note ->
                SectionCard {
                    Text(note.item, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.workbook_used, note.workbook.toString(), note.app.toString()))
                    Text(note.reason, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (state.confirmReplace) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_replace_title),
            text = stringResource(R.string.confirm_replace_body),
            confirm = stringResource(R.string.replace_local),
            onConfirm = onReplaceExcel,
            onDismiss = onCancelReplace,
        )
    }
    if (showBack) {
        // Tab root: back is unused; parameter kept for call-site compatibility.
    }
}

@Preview(name = "Backup")
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Backup dark")
@Composable
private fun BackupPreview() {
    LedgerTheme(themeMode = ThemeMode.LIGHT) {
        BackupScreen(
            state = BackupState(),
            onBack = {},
            onExport = {},
            onChooseRestore = {},
            onRestore = {},
            onCancelRestore = {},
        )
    }
}
