package com.aksharadasoha.ledger.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.aksharadasoha.ledger.domain.LedgerZoom

data class LedgerDimens(
    val cellWidth: Dp,
    val dateWidth: Dp,
    val actionWidth: Dp,
    val cellPadding: Dp,
    val screenPadding: Dp,
    val rowHeight: Dp,
    val headerHeight: Dp,
    val minTouch: Dp,
) {
    fun scaled(zoom: Float, fontScale: Float = 1f): LedgerDimens {
        val scale = LedgerZoom.clamp(zoom)
        val textScale = scale * fontScale.coerceAtLeast(1f)
        return copy(
            cellWidth = cellWidth * scale,
            dateWidth = dateWidth * scale,
            actionWidth = minTouch,
            rowHeight = max(rowHeight * textScale, minTouch),
            headerHeight = max(headerHeight * textScale, 24.dp),
        )
    }
}

val CompactDimens = LedgerDimens(
    cellWidth = 76.dp,
    dateWidth = 64.dp,
    actionWidth = 48.dp,
    cellPadding = 4.dp,
    screenPadding = 16.dp,
    rowHeight = 48.dp,
    headerHeight = 28.dp,
    minTouch = 48.dp,
)

val ComfortableDimens = LedgerDimens(
    cellWidth = 96.dp,
    dateWidth = 76.dp,
    actionWidth = 48.dp,
    cellPadding = 8.dp,
    screenPadding = 20.dp,
    rowHeight = 56.dp,
    headerHeight = 32.dp,
    minTouch = 48.dp,
)
