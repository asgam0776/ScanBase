package com.scanbase.app.data

import com.scanbase.app.image.EnhanceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanPageTest {
    @Test
    fun exportImageUriUsesProcessedImageByDefault() {
        val page = ScanPage(
            id = 1L,
            originalImageUri = "file://original.jpg",
            perspectiveImageUri = "file://perspective.jpg",
            processedImageUri = "file://processed.jpg",
            enhanceMode = EnhanceMode.Document,
            createdAtMillis = 10L
        )

        assertEquals("file://processed.jpg", page.exportImageUri)
        assertEquals("file://processed.jpg", page.defaultPreviewImageUri)
    }

    @Test
    fun exportImageUriFallsBackToPerspectiveThenOriginal() {
        val perspectiveOnly = ScanPage(
            id = 1L,
            originalImageUri = "file://original.jpg",
            perspectiveImageUri = "file://perspective.jpg",
            processedImageUri = null,
            enhanceMode = EnhanceMode.Original,
            createdAtMillis = 10L
        )
        val originalOnly = perspectiveOnly.copy(perspectiveImageUri = null)

        assertEquals("file://perspective.jpg", perspectiveOnly.exportImageUri)
        assertEquals("file://original.jpg", originalOnly.exportImageUri)
    }

    @Test
    fun exportImageUriUsesOriginalWhenPageIsRevertedToOriginal() {
        val page = ScanPage(
            id = 1L,
            originalImageUri = "file://original.jpg",
            perspectiveImageUri = "file://perspective.jpg",
            processedImageUri = "file://processed.jpg",
            enhanceMode = EnhanceMode.BlackWhite,
            useOriginalForExport = true,
            createdAtMillis = 10L
        )

        assertEquals("file://original.jpg", page.exportImageUri)
        assertEquals("file://processed.jpg", page.processedPreviewImageUri)
    }
}
