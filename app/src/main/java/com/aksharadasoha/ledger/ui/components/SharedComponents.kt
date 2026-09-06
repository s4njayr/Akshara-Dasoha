package com.aksharadasoha.ledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aksharadasoha.ledger.ui.theme.LocalLedgerDimens
import com.aksharadasoha.ledger.R
import com.aksharadasoha.ledger.ui.theme.LocalLedgerCells
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
private fun Modifier.actionTarget(): Modifier {
    val min = LocalLedgerDimens.current.minTouch
    return defaultMinSize(minWidth = min, minHeight = min)
}

@Composable
fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.actionTarget()) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp)) }
        Text(label)
    }
}

@Composable
fun SecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.actionTarget()) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp)) }
        Text(label)
    }
}

@Composable
fun AlternateActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.actionTarget()) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp)) }
        Text(label)
    }
}

@Composable
fun LowEmphasisButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 2,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.actionTarget()) {
        Text(label, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val min = LocalLedgerDimens.current.minTouch
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(min)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (tint == Color.Unspecified) LocalContentColor.current else tint,
        )
    }
}

@Composable
fun DestructiveIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolbarIconButton(
        icon = Icons.Outlined.Delete,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.error,
    )
}

@Composable
fun ResponsiveActionRow(
    primary: @Composable (Modifier) -> Unit,
    secondary: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.3f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                primary(Modifier.fillMaxWidth())
                secondary(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                primary(Modifier.weight(1f))
                secondary(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DestructiveActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    val colors = if (filled) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    }
    if (filled) {
        Button(onClick = onClick, colors = colors, modifier = modifier.actionTarget()) { Text(label) }
    } else {
        TextButton(onClick = onClick, colors = colors, modifier = modifier.actionTarget()) { Text(label) }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cells = LocalLedgerCells.current
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cells.elevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String = stringResource(R.string.action_delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { DestructiveActionButton(label = confirm, onClick = onConfirm) },
        dismissButton = { LowEmphasisButton(label = stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}

@Composable
fun LoadingState(message: String = stringResource(R.string.loading)) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        if (action != null && onAction != null) {
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun ScrollChips(
    modifier: Modifier = Modifier,
    testTag: String = "scroll-affordance",
    content: @Composable RowScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val fade = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxWidth().testTag(testTag)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (scroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(28.dp)
                    .height(40.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, fade))),
            )
        }
        if (scroll.canScrollBackward) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(20.dp)
                    .height(40.dp)
                    .background(Brush.horizontalGradient(listOf(fade, Color.Transparent))),
            )
        }
    }
}

@Composable
fun CellLegend() {
    val cells = LocalLedgerCells.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LegendSwatch(cells.input, stringResource(R.string.legend_input))
        LegendSwatch(cells.computed, stringResource(R.string.legend_computed))
        LegendSwatch(cells.warning, stringResource(R.string.legend_warning))
        LegendSwatch(cells.error, stringResource(R.string.legend_error))
        LegendSwatch(cells.audit, stringResource(R.string.legend_audit))
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(12.dp).background(color, MaterialTheme.shapes.extraSmall))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun MonthPickerDialog(
    title: String,
    year: Int,
    month: Int,
    exists: Boolean,
    carrySummary: String?,
    onYear: (Int) -> Unit,
    onMonth: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.add_month_body))
                ScrollChips {
                    (YearMonth.now().year - 2..YearMonth.now().year + 1).forEach { item ->
                        FilterChip(selected = year == item, onClick = { onYear(item) }, label = { Text(item.toString()) })
                    }
                }
                ScrollChips {
                    Month.entries.forEach { item ->
                        FilterChip(
                            selected = month == item.value,
                            onClick = { onMonth(item.value) },
                            label = { Text(item.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                if (exists) Text(stringResource(R.string.month_exists))
                carrySummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            PrimaryActionButton(
                label = stringResource(if (exists) R.string.action_open else R.string.action_create),
                onClick = onConfirm,
            )
        },
        dismissButton = { LowEmphasisButton(label = stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}
