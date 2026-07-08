package com.scanbase.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DocumentRecordTest {
    @Test
    fun createsRecordFromPdfFile() {
        val file = File("build/tmp/test_scanbase.pdf")
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(2048))

        val record = DocumentRecord.fromPdfFile(
            file = file,
            pageCount = 3,
            now = 1000L
        )

        assertEquals("doc_1000", record.id)
        assertEquals("test_scanbase.pdf", record.title)
        assertEquals(file.absolutePath, record.pdfPath)
        assertEquals(1000L, record.createdAt)
        assertEquals(1000L, record.updatedAt)
        assertEquals(3, record.pageCount)
        assertEquals(2048L, record.fileSizeBytes)
        assertEquals("2.0 KB", record.fileSizeText)

        file.delete()
    }
}
