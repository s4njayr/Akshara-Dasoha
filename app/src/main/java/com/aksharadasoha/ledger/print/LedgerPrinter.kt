package com.aksharadasoha.ledger.print

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import com.aksharadasoha.ledger.domain.ComputedRow
import com.aksharadasoha.ledger.domain.LedgerInstance
import com.aksharadasoha.ledger.domain.LedgerTemplate
import com.aksharadasoha.ledger.domain.SchoolProfile
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

object LedgerPrinter {
    fun print(
        context: Context,
        school: SchoolProfile,
        template: LedgerTemplate,
        instance: LedgerInstance,
        rows: List<ComputedRow>,
    ) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(
            instance.title,
            adapter(context, school, template, instance, rows),
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                .build(),
        )
    }

    fun writePdf(
        context: Context,
        school: SchoolProfile,
        template: LedgerTemplate,
        instance: LedgerInstance,
        rows: List<ComputedRow>,
        target: File,
    ): File {
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .build()
        val document = PrintedPdfDocument(context, attributes)
        render(document, school, template, instance, rows)
        FileOutputStream(target).use { writeTo(document, it) }
        return target
    }

    fun writePdf(
        context: Context,
        school: SchoolProfile,
        template: LedgerTemplate,
        instance: LedgerInstance,
        rows: List<ComputedRow>,
        target: OutputStream,
    ) {
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .build()
        val document = PrintedPdfDocument(context, attributes)
        render(document, school, template, instance, rows)
        writeTo(document, target)
    }

    private fun writeTo(document: PrintedPdfDocument, target: OutputStream) {
        document.writeTo(target)
        document.close()
    }

    fun adapter(
        context: Context,
        school: SchoolProfile,
        template: LedgerTemplate,
        instance: LedgerInstance,
        rows: List<ComputedRow>,
    ): PrintDocumentAdapter {
        return object : android.print.PrintDocumentAdapter() {
            private var pdf: PrintedPdfDocument? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?,
            ) {
                val landscape = PrintAttributes.Builder()
                    .setMediaSize(newAttributes.mediaSize?.asLandscape() ?: PrintAttributes.MediaSize.ISO_A4.asLandscape())
                    .setMinMargins(newAttributes.minMargins ?: PrintAttributes.Margins.NO_MARGINS)
                    .setResolution(newAttributes.resolution ?: PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                    .setColorMode(newAttributes.colorMode)
                    .build()
                pdf = PrintedPdfDocument(context, landscape)
                val pageCount = render(pdf!!, school, template, instance, rows)
                callback.onLayoutFinished(
                    android.print.PrintDocumentInfo.Builder("${instance.title}.pdf")
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(pageCount)
                        .build(),
                    true,
                )
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>,
                destination: android.os.ParcelFileDescriptor,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                FileOutputStream(destination.fileDescriptor).use { pdf?.writeTo(it) }
                callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            }
        }
    }

    internal fun render(
        document: PdfDocument,
        school: SchoolProfile,
        template: LedgerTemplate,
        instance: LedgerInstance,
        rows: List<ComputedRow>,
    ): Int {
        val columns = template.columns.filter { it.visible }
        val pageWidth = 842
        val pageHeight = 595
        val margin = 28f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B5E20")
            textSize = 14f
            isFakeBoldText = true
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333")
            textSize = 9f
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8f
            isFakeBoldText = true
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222222")
            textSize = 8f
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#9E9E9E")
            strokeWidth = 0.6f
            style = Paint.Style.STROKE
        }
        val headerFill = Paint().apply { color = Color.parseColor("#2E7D32") }
        val warnFill = Paint().apply { color = Color.parseColor("#FFEBEE") }

        val usable = pageWidth - margin * 2
        val colWidth = max(42f, usable / max(columns.size, 1))
        val rowHeight = 16f
        val headerHeight = 36f
        val top = 70f
        val rowsPerPage = ((pageHeight - top - 70f) / rowHeight).toInt().coerceAtLeast(8)
        val pages = max(1, (rows.size + rowsPerPage - 1) / rowsPerPage)
        val month = YearMonth.of(instance.year, instance.month)
        val range = "${month.atDay(1)} to ${month.atEndOfMonth()}"
        val grades = template.gradeIds.joinToString(", ") { it.removePrefix("grade_").replaceFirstChar { ch -> ch.titlecase() } }

        for (pageIndex in 0 until pages) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
            val canvas = page.canvas
            canvas.drawText(school.name, margin, 24f, titlePaint)
            canvas.drawText("${school.program}  |  ${instance.title}", margin, 40f, metaPaint)
            canvas.drawText("Class: $grades    Period: $range    Page ${pageIndex + 1} of $pages", margin, 54f, metaPaint)
            drawHeader(canvas, columns, margin, top, colWidth, headerHeight, headerFill, headerPaint, linePaint)
            val slice = rows.drop(pageIndex * rowsPerPage).take(rowsPerPage)
            slice.forEachIndexed { index, row ->
                val y = top + headerHeight + index * rowHeight
                columns.forEachIndexed { colIndex, column ->
                    val cell = row.cells[column.key]
                    val x = margin + colIndex * colWidth
                    if (cell?.negativeWarning == true) {
                        canvas.drawRect(x, y, x + colWidth, y + rowHeight, warnFill)
                    }
                    canvas.drawRect(x, y, x + colWidth, y + rowHeight, linePaint)
                    val text = formatCell(cell)
                    canvas.drawText(text.take(12), x + 2f, y + 11f, cellPaint)
                }
            }
            val totalsY = top + headerHeight + slice.size * rowHeight + 18f
            canvas.drawText("Totals / closing balances", margin, totalsY, metaPaint)
            val last = rows.lastOrNull()
            val totals = columns.filter { it.key.contains("balance") || it.key.contains("total") || it.key.contains("count") }
                .take(6)
                .joinToString("   ") { column ->
                    val value = last?.cells?.get(column.key)?.number
                    "${column.label}: ${value?.let { formatNumber(it) } ?: "-"}"
                }
            canvas.drawText(totals, margin, totalsY + 14f, cellPaint)
            canvas.drawText("Head Master ________________    Cook ________________    Date ________", margin, pageHeight - 28f, metaPaint)
            document.finishPage(page)
        }
        return pages
    }

    private fun drawHeader(
        canvas: Canvas,
        columns: List<com.aksharadasoha.ledger.domain.ColumnDef>,
        margin: Float,
        top: Float,
        colWidth: Float,
        headerHeight: Float,
        fill: Paint,
        text: Paint,
        line: Paint,
    ) {
        columns.forEachIndexed { index, column ->
            val x = margin + index * colWidth
            canvas.drawRect(x, top, x + colWidth, top + headerHeight, fill)
            canvas.drawRect(x, top, x + colWidth, top + headerHeight, line)
            val showGroup = column.groupLabel != null && (index == 0 || columns[index - 1].groupLabel != column.groupLabel)
            if (showGroup) canvas.drawText(column.groupLabel.orEmpty().take(14), x + 2f, top + 12f, text)
            canvas.drawText(column.label.take(14), x + 2f, top + 24f, text)
        }
    }

    private fun formatCell(cell: com.aksharadasoha.ledger.domain.ComputedCell?): String {
        if (cell == null) return ""
        if (cell.error != null) return "ERR"
        if (cell.text != null) return cell.text
        return cell.number?.let { formatNumber(it) } ?: ""
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) value.toLong().toString() else "%.3f".format(value).trimEnd('0').trimEnd('.')
    }
}
