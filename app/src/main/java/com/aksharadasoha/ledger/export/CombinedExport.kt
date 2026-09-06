package com.aksharadasoha.ledger.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CombinedExport {
    fun writeZip(
        pdfName: String,
        pdf: ByteArray,
        xlsxName: String,
        xlsx: ByteArray,
        out: OutputStream,
    ) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(pdfName))
            zip.write(pdf)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(xlsxName))
            zip.write(xlsx)
            zip.closeEntry()
        }
    }
}
