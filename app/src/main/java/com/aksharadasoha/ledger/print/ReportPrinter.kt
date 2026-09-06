package com.aksharadasoha.ledger.print

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import com.aksharadasoha.ledger.domain.MonthlyReport
import com.aksharadasoha.ledger.domain.SchoolProfile
import java.io.OutputStream
import kotlin.math.max

object ReportPrinter {
    fun writePdf(context: Context, school: SchoolProfile, reports: List<MonthlyReport>, target: OutputStream) {
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .build()
        val document = PrintedPdfDocument(context, attributes)
        val pageWidth = 842
        val pageHeight = 595
        val margin = 32f
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B5E20")
            textSize = 16f
            isFakeBoldText = true
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222222")
            textSize = 10f
        }
        val headerFill = Paint().apply { color = Color.parseColor("#1B5E20") }
        val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9f
            isFakeBoldText = true
        }
        val line = Paint().apply {
            color = Color.parseColor("#9E9E9E")
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#555555")
            textSize = 9f
        }
        val headers = listOf("Ledger", "Period", "Children", "Item", "Opening", "Supply", "Spend", "Closing")
        val widths = listOf(130f, 70f, 70f, 110f, 80f, 80f, 80f, 80f)
        val rows = reports.flatMap { report ->
            report.items.map { item ->
                listOf(
                    report.templateName,
                    "${report.year}-${report.month.toString().padStart(2, '0')}",
                    format(report.childrenServed),
                    item.label,
                    format(item.opening),
                    format(item.supply),
                    format(item.expenditure),
                    format(item.closing),
                )
            }
        }
        val rowHeight = 16f
        val headerTop = 64f
        val usableBottom = pageHeight - 48f
        val rowsPerPage = max(1, ((usableBottom - 86f) / rowHeight).toInt())
        val pages = max(1, (rows.size + rowsPerPage - 1) / rowsPerPage)
        for (pageIndex in 0 until pages) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
            val canvas = page.canvas
            canvas.drawText(school.name, margin, 28f, title)
            canvas.drawText("${school.program}  |  Monthly commodity summary  |  Page ${pageIndex + 1} of $pages", margin, 46f, body)
            var x = margin
            headers.forEachIndexed { index, label ->
                canvas.drawRect(x, headerTop, x + widths[index], 86f, headerFill)
                canvas.drawText(label, x + 4f, 80f, headerText)
                x += widths[index]
            }
            var y = 86f
            rows.drop(pageIndex * rowsPerPage).take(rowsPerPage).forEach { values ->
                x = margin
                values.forEachIndexed { index, value ->
                    canvas.drawRect(x, y, x + widths[index], y + rowHeight, line)
                    canvas.drawText(value.take(18), x + 3f, y + 12f, body)
                    x += widths[index]
                }
                y += rowHeight
            }
            canvas.drawText("Head Master ________________    Cook ________________    Date ________", margin, pageHeight - 24f, footer)
            document.finishPage(page)
        }
        document.writeTo(target)
        document.close()
    }

    private fun format(value: Double?): String {
        if (value == null) return "-"
        return if (value == value.toLong().toDouble()) value.toLong().toString() else "%.3f".format(value).trimEnd('0').trimEnd('.')
    }
}
