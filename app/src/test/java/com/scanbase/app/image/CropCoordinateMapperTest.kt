package com.scanbase.app.image

import org.junit.Assert.assertEquals
import org.junit.Test

class CropCoordinateMapperTest {
    @Test
    fun bitmapToScreenUsesContentScaleFitOffsets() {
        val mapper = CropCoordinateMapper(
            bitmapWidth = 1000f,
            bitmapHeight = 2000f,
            containerWidth = 300f,
            containerHeight = 300f
        )

        val screenPoint = mapper.bitmapToScreen(CropPoint(500f, 1000f))

        assertEquals(150f, screenPoint.x, 0.01f)
        assertEquals(150f, screenPoint.y, 0.01f)
        assertEquals(75f, mapper.offsetX, 0.01f)
        assertEquals(0f, mapper.offsetY, 0.01f)
    }

    @Test
    fun screenToBitmapReversesBitmapToScreen() {
        val mapper = CropCoordinateMapper(
            bitmapWidth = 3059f,
            bitmapHeight = 4079f,
            containerWidth = 300f,
            containerHeight = 300f
        )
        val bitmapPoint = CropPoint(1529.5f, 2039.5f)

        val screenPoint = mapper.bitmapToScreen(bitmapPoint)
        val restoredPoint = mapper.screenToBitmap(screenPoint)

        assertEquals(bitmapPoint.x, restoredPoint.x, 0.05f)
        assertEquals(bitmapPoint.y, restoredPoint.y, 0.05f)
    }

    @Test
    fun clampBitmapPointKeepsPointInsideBitmap() {
        val mapper = CropCoordinateMapper(
            bitmapWidth = 100f,
            bitmapHeight = 80f,
            containerWidth = 200f,
            containerHeight = 200f
        )

        val clampedPoint = mapper.clampBitmapPoint(CropPoint(130f, -20f))

        assertEquals(99f, clampedPoint.x, 0.01f)
        assertEquals(0f, clampedPoint.y, 0.01f)
    }

    @Test
    fun screenDeltaToBitmapDeltaKeepsDragRelativeToSelectedCorner() {
        val mapper = CropCoordinateMapper(
            bitmapWidth = 1000f,
            bitmapHeight = 2000f,
            containerWidth = 300f,
            containerHeight = 300f
        )

        val bitmapDelta = mapper.screenDeltaToBitmapDelta(CropPoint(3f, 6f))

        assertEquals(20f, bitmapDelta.x, 0.01f)
        assertEquals(40f, bitmapDelta.y, 0.01f)
    }
}
