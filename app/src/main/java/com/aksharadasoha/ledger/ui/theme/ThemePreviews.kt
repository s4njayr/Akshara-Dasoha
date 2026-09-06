package com.aksharadasoha.ledger.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aksharadasoha.ledger.ui.components.AlternateActionButton
import com.aksharadasoha.ledger.ui.components.ConfirmDialog
import com.aksharadasoha.ledger.ui.components.DestructiveActionButton
import com.aksharadasoha.ledger.ui.components.LowEmphasisButton
import com.aksharadasoha.ledger.ui.components.PrimaryActionButton
import com.aksharadasoha.ledger.ui.components.SecondaryActionButton
import com.aksharadasoha.ledger.ui.components.SectionCard

@Preview(widthDp = 360, name = "Buttons phone")
@Preview(widthDp = 840, name = "Buttons tablet")
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Buttons dark")
@Preview(fontScale = 1.3f, name = "Buttons large font")
@Composable
private fun ButtonHierarchyPreview() {
    LedgerTheme {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Button hierarchy", style = MaterialTheme.typography.titleMedium)
            PrimaryActionButton(label = "Save settings", onClick = {})
            SecondaryActionButton(label = "Add month", onClick = {})
            AlternateActionButton(label = "Open latest", onClick = {})
            LowEmphasisButton(label = "Cancel", onClick = {})
            DestructiveActionButton(label = "Replace local data", onClick = {})
        }
    }
}

@Preview(name = "Surfaces light")
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Surfaces dark")
@Preview(name = "Fresh Green")
@Preview(name = "Ocean Blue")
@Composable
private fun SurfacePreview() {
    LedgerTheme {
        val cells = LocalLedgerCells.current
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionCard {
                Text("Elevated card")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Input", Modifier.background(cells.input).padding(8.dp))
                    Text("Calc", Modifier.background(cells.computed).padding(8.dp))
                    Text("Warn", Modifier.background(cells.warning).padding(8.dp))
                    Text("Error", Modifier.background(cells.error).padding(8.dp))
                    Text("Audit", Modifier.background(cells.audit).padding(8.dp))
                }
            }
        }
    }
}

@Preview(name = "Ocean Blue light")
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Ocean Blue dark")
@Composable
private fun OceanPalettePreview() {
    LedgerTheme(palette = com.aksharadasoha.ledger.domain.ColorPalette.OCEAN_BLUE) {
        val cells = LocalLedgerCells.current
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionCard {
                Text("Ocean Blue")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Input", Modifier.background(cells.input).padding(8.dp))
                    Text("Calc", Modifier.background(cells.computed).padding(8.dp))
                    Text("Audit", Modifier.background(cells.audit).padding(8.dp))
                }
            }
        }
    }
}

@Preview(name = "Fresh Green light")
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, name = "Fresh Green dark")
@Composable
private fun FreshGreenPalettePreview() {
    LedgerTheme(palette = com.aksharadasoha.ledger.domain.ColorPalette.FRESH_GREEN) {
        val cells = LocalLedgerCells.current
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionCard {
                Text("Fresh Green")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Input", Modifier.background(cells.input).padding(8.dp))
                    Text("Calc", Modifier.background(cells.computed).padding(8.dp))
                    Text("Audit", Modifier.background(cells.audit).padding(8.dp))
                }
            }
        }
    }
}

@Preview(name = "Destructive confirm")
@Composable
private fun DestructiveConfirmPreview() {
    LedgerTheme {
        ConfirmDialog(
            title = "Replace every local month?",
            text = "This deletes months that are not in the file.",
            confirm = "Replace local data",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
