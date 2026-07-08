package com.scanbase.app.data

import com.scanbase.app.image.EnhanceMode

data class ScanPage(
    val id: Long,
    val originalImageUri: String,
    val perspectiveImageUri: String? = null,
    val processedImageUri: String? = null,
    val enhanceMode: EnhanceMode = EnhanceMode.Original,
    val useOriginalForExport: Boolean = false,
    val createdAtMillis: Long
) {
    val processedPreviewImageUri: String
        get() = processedImageUri ?: perspectiveImageUri ?: originalImageUri

    val defaultPreviewImageUri: String
        get() = exportImageUri

    val exportImageUri: String
        get() = if (useOriginalForExport) {
            originalImageUri
        } else {
            processedPreviewImageUri
        }
}

data class ScanDocument(
    val id: Long,
    val title: String,
    val pages: List<ScanPage>
) {
    companion object {
        fun empty(): ScanDocument {
            return ScanDocument(
                id = System.currentTimeMillis(),
                title = "ScanBase",
                pages = emptyList()
            )
        }
    }
}
