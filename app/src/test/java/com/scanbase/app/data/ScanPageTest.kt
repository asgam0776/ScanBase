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

    @Test
    fun replacingPageKeepsIdAndCreatedAtButUpdatesImagesAndMode() {
        val page = ScanPage(
            id = 7L,
            originalImageUri = "file://old-original.jpg",
            perspectiveImageUri = "file://old-perspective.jpg",
            processedImageUri = "file://old-processed.jpg",
            enhanceMode = EnhanceMode.Document,
            createdAtMillis = 100L
        )

        val replaced = page.replaceImages(
            originalImageUri = "file://new-original.jpg",
            perspectiveImageUri = "file://new-perspective.jpg",
            processedImageUri = "file://new-processed.jpg",
            enhanceMode = EnhanceMode.BlackWhite,
            updatedAtMillis = 200L
        )

        assertEquals(7L, replaced.id)
        assertEquals(100L, replaced.createdAtMillis)
        assertEquals(200L, replaced.updatedAtMillis)
        assertEquals("file://new-original.jpg", replaced.originalImageUri)
        assertEquals("file://new-perspective.jpg", replaced.perspectiveImageUri)
        assertEquals("file://new-processed.jpg", replaced.processedImageUri)
        assertEquals(EnhanceMode.BlackWhite, replaced.enhanceMode)
        assertEquals("file://new-processed.jpg", replaced.exportImageUri)
    }
}
