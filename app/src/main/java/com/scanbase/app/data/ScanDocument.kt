package com.scanbase.app.data

import com.scanbase.app.image.EnhanceMode

data class ScanPage(
    val id: Long,
    val originalImageUri: String,
    val perspectiveImageUri: String? = null,
    val processedImageUri: String? = null,
    val enhanceMode: EnhanceMode = EnhanceMode.Original,
    val useOriginalForExport: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long? = null
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

    fun replaceImages(
        originalImageUri: String,
        perspectiveImageUri: String,
        processedImageUri: String,
        enhanceMode: EnhanceMode,
        updatedAtMillis: Long
    ): ScanPage {
        return copy(
            originalImageUri = originalImageUri,
            perspectiveImageUri = perspectiveImageUri,
            processedImageUri = processedImageUri,
            enhanceMode = enhanceMode,
            useOriginalForExport = false,
            updatedAtMillis = updatedAtMillis
        )
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
