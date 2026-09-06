package com.aksharadasoha.ledger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aksharadasoha.ledger.domain.OperationState
import com.aksharadasoha.ledger.print.LedgerPrinter
import com.aksharadasoha.ledger.ui.AppViewModel
import com.aksharadasoha.ledger.ui.DownloadKind
import com.aksharadasoha.ledger.ui.navigation.Route
import com.aksharadasoha.ledger.ui.screens.BackupScreen
import com.aksharadasoha.ledger.ui.screens.ConfigScreen
import com.aksharadasoha.ledger.ui.screens.HomeScreen
import com.aksharadasoha.ledger.ui.screens.LedgerScreen
import com.aksharadasoha.ledger.ui.screens.ReportsScreen
import com.aksharadasoha.ledger.ui.screens.SettingsScreen
import com.aksharadasoha.ledger.ui.theme.LedgerTheme
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val home by viewModel.home.collectAsStateWithLifecycle()
            val dark = when (home.settings.themeMode) {
                com.aksharadasoha.ledger.domain.ThemeMode.DARK -> true
                com.aksharadasoha.ledger.domain.ThemeMode.LIGHT -> false
                com.aksharadasoha.ledger.domain.ThemeMode.SYSTEM ->
                    (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            LedgerTheme(
                themeMode = home.settings.themeMode,
                palette = home.settings.colorPalette,
                density = home.settings.tableDensity,
            ) {
                val nav = rememberNavController()
                val ledger by viewModel.ledger.collectAsStateWithLifecycle()
                val config by viewModel.config.collectAsStateWithLifecycle()
                val backup by viewModel.backup.collectAsStateWithLifecycle()
                val reports by viewModel.reports.collectAsStateWithLifecycle()
                val operation by viewModel.operation.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                val snackbar = remember { SnackbarHostState() }
                var pendingDownload by remember { mutableStateOf(DownloadKind.PDF) }
                var pendingExcel by remember { mutableStateOf<ByteArray?>(null) }
                val route = nav.currentBackStackEntryAsState().value?.destination?.route
                val wide = LocalConfiguration.current.screenWidthDp >= 840

                LaunchedEffect(operation) {
                    when (val current = operation) {
                        is OperationState.Success -> {
                            snackbar.showSnackbar(current.message)
                            viewModel.consumeOperation()
                        }
                        is OperationState.Failure -> {
                            snackbar.showSnackbar(current.message)
                            viewModel.consumeOperation()
                        }
                        else -> Unit
                    }
                }

                val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        viewModel.beginOperation()
                        runCatching {
                            val json = viewModel.exportJson()
                            val stream = contentResolver.openOutputStream(uri) ?: error(getString(R.string.download_failed))
                            stream.use { it.write(json.toByteArray()) }
                        }.onSuccess {
                            viewModel.reportSuccess(getString(R.string.backup_exported))
                            viewModel.loadBackupExtras()
                        }.onFailure { viewModel.reportFailure(it.message ?: getString(R.string.download_failed)) }
                    }
                }
                val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        viewModel.beginOperation()
                        runCatching {
                            val stream = contentResolver.openInputStream(uri) ?: error(getString(R.string.file_unreadable))
                            stream.bufferedReader().use { it.readText() }
                        }.onSuccess { json ->
                            viewModel.setPendingRestore(json)
                            viewModel.consumeOperation()
                            if (route != Route.Backup.path) nav.navigate(Route.Backup.path)
                        }.onFailure { viewModel.reportFailure(it.message ?: getString(R.string.file_unreadable)) }
                    }
                }
                val openExcel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        viewModel.beginOperation()
                        runCatching {
                            val stream = contentResolver.openInputStream(uri) ?: error(getString(R.string.file_unreadable))
                            stream.use { it.readBytes() }
                        }.onSuccess { bytes ->
                            pendingExcel = bytes
                            viewModel.previewExcel(bytes.inputStream())
                            viewModel.consumeOperation()
                            if (route != Route.Backup.path) nav.navigate(Route.Backup.path)
                        }.onFailure { viewModel.reportFailure(it.message ?: getString(R.string.file_unreadable)) }
                    }
                }
                val createDownload = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        viewModel.beginOperation()
                        runCatching {
                            val stream = contentResolver.openOutputStream(uri) ?: error(getString(R.string.download_failed))
                            stream.use { viewModel.writeDownload(pendingDownload, it) }
                        }.onSuccess { viewModel.reportSuccess(getString(R.string.download_ok)) }
                            .onFailure { viewModel.reportFailure(it.message ?: getString(R.string.download_failed)) }
                    }
                }

                fun download(kind: DownloadKind, name: String) {
                    pendingDownload = kind
                    createDownload.launch(name)
                }

                val tabs = listOf(Route.Home, Route.Reports, Route.Backup, Route.Settings)
                val showTabs = route in tabs.map { it.path }

                fun navigateTab(path: String) {
                    if (route == Route.Settings.path && path != Route.Settings.path) {
                        viewModel.discardSettingsPreview()
                    }
                    nav.navigate(path) {
                        popUpTo(Route.Home.path) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    if (path == Route.Backup.path) viewModel.loadBackupExtras()
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (showTabs && !wide) {
                            NavigationBar {
                                NavigationBarItem(selected = route == Route.Home.path, onClick = { navigateTab(Route.Home.path) }, icon = { Icon(Icons.Outlined.Home, contentDescription = stringResource(R.string.nav_home)) }, label = { Text(stringResource(R.string.nav_home)) })
                                NavigationBarItem(selected = route == Route.Reports.path, onClick = { navigateTab(Route.Reports.path) }, icon = { Icon(Icons.Outlined.Assessment, contentDescription = stringResource(R.string.nav_reports)) }, label = { Text(stringResource(R.string.nav_reports)) })
                                NavigationBarItem(selected = route == Route.Backup.path, onClick = { navigateTab(Route.Backup.path) }, icon = { Icon(Icons.Outlined.Backup, contentDescription = stringResource(R.string.nav_backup)) }, label = { Text(stringResource(R.string.nav_backup)) })
                                NavigationBarItem(selected = route == Route.Settings.path, onClick = { navigateTab(Route.Settings.path) }, icon = { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.nav_settings)) }, label = { Text(stringResource(R.string.nav_settings)) })
                            }
                        }
                    },
                ) { padding ->
                    Row(Modifier.padding(padding).fillMaxSize()) {
                        if (wide && showTabs) {
                            NavigationRail {
                                NavigationRailItem(selected = route == Route.Home.path, onClick = { navigateTab(Route.Home.path) }, icon = { Icon(Icons.Outlined.Home, contentDescription = stringResource(R.string.nav_home)) }, label = { Text(stringResource(R.string.nav_home)) })
                                NavigationRailItem(selected = route == Route.Reports.path, onClick = { navigateTab(Route.Reports.path) }, icon = { Icon(Icons.Outlined.Assessment, contentDescription = stringResource(R.string.nav_reports)) }, label = { Text(stringResource(R.string.nav_reports)) })
                                NavigationRailItem(selected = route == Route.Backup.path, onClick = { navigateTab(Route.Backup.path) }, icon = { Icon(Icons.Outlined.Backup, contentDescription = stringResource(R.string.nav_backup)) }, label = { Text(stringResource(R.string.nav_backup)) })
                                NavigationRailItem(selected = route == Route.Settings.path, onClick = { navigateTab(Route.Settings.path) }, icon = { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.nav_settings)) }, label = { Text(stringResource(R.string.nav_settings)) })
                            }
                        }
                        NavHost(
                            navController = nav,
                            startDestination = Route.Home.path,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
                            exitTransition = { fadeOut() + slideOutHorizontally { -it / 12 } },
                        ) {
                            composable(Route.Home.path) {
                                HomeScreen(
                                    state = home,
                                    onSelectGrade = viewModel::selectGrade,
                                    onSelectTemplate = viewModel::selectTemplate,
                                    onOpenInstance = { id ->
                                        viewModel.openLedger(id)
                                        nav.navigate(Route.Ledger.path)
                                    },
                                    onCreateMonth = viewModel::beginCreateMonth,
                                    onOpenConfig = {
                                        viewModel.loadConfig()
                                        nav.navigate(Route.Config.path)
                                    },
                                    onDismissMessage = viewModel::consumeMessage,
                                    onBeginCreateMonth = viewModel::beginCreateMonth,
                                    onUpdateMonthPicker = { year, month -> viewModel.updateMonthPicker(year, month) },
                                    onConfirmMonth = {
                                        viewModel.confirmCreateMonth()
                                        nav.navigate(Route.Ledger.path)
                                    },
                                    onDismissMonthPicker = viewModel::dismissMonthPicker,
                                    onOpenLatest = { id ->
                                        viewModel.openLatest(id)
                                        nav.navigate(Route.Ledger.path)
                                    },
                                    onRequestDeleteInstance = viewModel::requestDeleteInstance,
                                    onConfirmDeleteInstance = viewModel::confirmDeleteInstance,
                                    wide = wide,
                                )
                            }
                            composable(Route.Ledger.path) {
                                LedgerScreen(
                                    state = ledger,
                                    onBack = { nav.popBackStack() },
                                    onUpdate = viewModel::updateCell,
                                    onAddDay = viewModel::addDay,
                                    onRequestDelete = viewModel::requestDeleteRow,
                                    onConfirmDelete = viewModel::confirmDeleteRow,
                                    onDismissDelete = { viewModel.requestDeleteRow(null) },
                                    onPrint = {
                                        val template = ledger.template ?: return@LedgerScreen
                                        val instance = ledger.instance ?: return@LedgerScreen
                                        LedgerPrinter.print(this@MainActivity, home.school, template, instance, ledger.rows)
                                    },
                                    onSharePdf = {
                                        val template = ledger.template ?: return@LedgerScreen
                                        val instance = ledger.instance ?: return@LedgerScreen
                                        viewModel.beginOperation()
                                        runCatching {
                                            val file = File(cacheDir, "${instance.id}.pdf")
                                            LedgerPrinter.writePdf(this@MainActivity, home.school, template, instance, ledger.rows, file)
                                            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                                            startActivity(
                                                Intent.createChooser(
                                                    Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/pdf"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    },
                                                    getString(R.string.share_ledger),
                                                ),
                                            )
                                        }.onSuccess {
                                            viewModel.reportSuccess(getString(R.string.share_ok))
                                        }.onFailure {
                                            viewModel.reportFailure(it.message ?: getString(R.string.share_failed))
                                        }
                                    },
                                    onConfigure = {
                                        ledger.template?.id?.let { viewModel.loadConfig(it) }
                                        nav.navigate(Route.Config.path)
                                    },
                                    onDismissMessage = viewModel::consumeLedgerMessage,
                                    onShowError = viewModel::showCellError,
                                    onToggleAudit = viewModel::toggleAudit,
                                    onDownloadPdf = {
                                        val instance = ledger.instance ?: return@LedgerScreen
                                        download(DownloadKind.PDF, "${instance.id}.pdf")
                                    },
                                    onDownloadExcel = {
                                        val instance = ledger.instance ?: return@LedgerScreen
                                        download(DownloadKind.EXCEL, "${instance.id}.xlsx")
                                    },
                                    onDownloadCsv = {
                                        val instance = ledger.instance ?: return@LedgerScreen
                                        download(DownloadKind.CSV, "${instance.id}.csv")
                                    },
                                    onExport = {
                                        val instance = ledger.instance ?: return@LedgerScreen
                                        when (viewModel.defaultDownloadKind()) {
                                            DownloadKind.EXCEL -> download(DownloadKind.EXCEL, "${instance.id}.xlsx")
                                            DownloadKind.BOTH_ZIP -> download(DownloadKind.BOTH_ZIP, "${instance.id}.zip")
                                            else -> download(DownloadKind.PDF, "${instance.id}.pdf")
                                        }
                                    },
                                    zoom = home.settings.ledgerZoom,
                                    onZoomChange = viewModel::updateLedgerZoom,
                                )
                            }
                            composable(Route.Config.path) {
                                ConfigScreen(
                                    state = config,
                                    onBack = { nav.popBackStack() },
                                    onSelectTemplate = viewModel::selectConfigTemplate,
                                    onSaveTemplate = viewModel::saveTemplateName,
                                    onAddColumn = viewModel::addColumn,
                                    onSaveColumn = viewModel::saveColumn,
                                    onMoveColumn = viewModel::moveColumn,
                                    onRequestDeleteColumn = viewModel::requestDeleteColumn,
                                    onConfirmDeleteColumn = viewModel::confirmDeleteColumn,
                                    onAddRate = viewModel::addRate,
                                    onSaveRate = viewModel::saveRate,
                                    onDeleteRate = viewModel::deleteRate,
                                    onAddGrade = viewModel::addGrade,
                                    onDeleteGrade = viewModel::deleteGrade,
                                    onCreateTemplate = viewModel::createTemplate,
                                    onDeleteTemplate = viewModel::deleteTemplate,
                                    onDismissMessage = viewModel::consumeMessage,
                                    onRequestDeleteTemplate = viewModel::requestDeleteTemplate,
                                    onRequestDeleteGrade = viewModel::requestDeleteGrade,
                                    onRequestDeleteRate = viewModel::requestDeleteRate,
                                    onConfirmDeleteTemplate = { config.confirmDeleteTemplateId?.let { viewModel.deleteTemplate(it) } },
                                    onConfirmDeleteGrade = { config.confirmDeleteGradeId?.let { viewModel.deleteGrade(it) } },
                                    onConfirmDeleteRate = { config.confirmDeleteRateId?.let { viewModel.deleteRate(it) } },
                                )
                            }
                            composable(Route.Backup.path) {
                                BackupScreen(
                                    state = backup,
                                    onBack = {},
                                    onExport = { createBackup.launch("akshara-dasoha-backup.json") },
                                    onChooseRestore = { openBackup.launch(arrayOf("application/json", "*/*")) },
                                    onRestore = {
                                        val json = backup.pendingJson ?: return@BackupScreen
                                        scope.launch {
                                            runCatching { viewModel.restore(json) }
                                                .onSuccess { viewModel.setPendingRestore(null) }
                                        }
                                    },
                                    onCancelRestore = { viewModel.setPendingRestore(null) },
                                    onChooseExcel = { openExcel.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*")) },
                                    onImportExcel = { pendingExcel?.let { bytes -> viewModel.importExcel(bytes.inputStream(), replace = false) } },
                                    onReplaceExcel = { pendingExcel?.let { bytes -> viewModel.importExcel(bytes.inputStream(), replace = true) } },
                                    onCancelReplace = viewModel::cancelReplaceConfirm,
                                    onApplySeedUpgrade = viewModel::applySeedUpgrade,
                                    onDismissMessage = viewModel::consumeBackupMessage,
                                )
                            }
                            composable(Route.Settings.path) {
                                SettingsScreen(
                                    settings = home.settings,
                                    onBack = {},
                                    onSave = { next, onResult -> viewModel.saveSettings(next, onResult) },
                                    onPreview = viewModel::previewSettings,
                                    onLeaveUnsaved = viewModel::discardSettingsPreview,
                                )
                            }
                            composable(Route.Reports.path) {
                                ReportsScreen(
                                    state = reports,
                                    templates = home.templates,
                                    onBack = {},
                                    onFilter = viewModel::filterReports,
                                    onDownloadPdf = { download(DownloadKind.REPORT_PDF, "akshara-reports.pdf") },
                                    onDownloadExcel = { download(DownloadKind.REPORT_EXCEL, "akshara-reports.xlsx") },
                                    onDownloadCsv = { download(DownloadKind.REPORT_CSV, "akshara-reports.csv") },
                                    onOpenInstance = { id ->
                                        viewModel.openLedger(id)
                                        nav.navigate(Route.Ledger.path)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
