package com.scanbase.app.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DocumentRecord(
    val id: String,
    val title: String,
    val pdfPath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val fileSizeBytes: Long
) {
    val fileSizeText: String
        get() = when {
            fileSizeBytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", fileSizeBytes / 1024.0 / 1024.0)
            fileSizeBytes >= 1024L -> String.format(Locale.US, "%.1f KB", fileSizeBytes / 1024.0)
            else -> "$fileSizeBytes B"
        }

    val createdAtText: String
        get() = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.US).format(Date(createdAt))

    companion object {
        fun fromPdfFile(
            file: File,
            pageCount: Int,
            now: Long = System.currentTimeMillis()
        ): DocumentRecord {
            return DocumentRecord(
                id = "doc_$now",
                title = file.name,
                pdfPath = file.absolutePath,
                createdAt = now,
                updatedAt = now,
                pageCount = pageCount,
                fileSizeBytes = file.length()
            )
        }
    }
}
