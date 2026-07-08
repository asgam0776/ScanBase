package com.scanbase.app.image

import kotlin.math.min

/** ContentScale.Fit coordinate mapping between original bitmap space and screen box space. */
data class CropCoordinateMapper(
    val bitmapWidth: Float,
    val bitmapHeight: Float,
    val containerWidth: Float,
    val containerHeight: Float
) {
    val scale: Float = min(containerWidth / bitmapWidth, containerHeight / bitmapHeight)
    val displayedWidth: Float = bitmapWidth * scale
    val displayedHeight: Float = bitmapHeight * scale
    val offsetX: Float = (containerWidth - displayedWidth) / 2f
    val offsetY: Float = (containerHeight - displayedHeight) / 2f

    fun bitmapToScreen(point: CropPoint): CropPoint {
        return CropPoint(
            x = point.x * scale + offsetX,
            y = point.y * scale + offsetY
        )
    }

    fun screenToBitmap(point: CropPoint): CropPoint {
        return CropPoint(
            x = (point.x - offsetX) / scale,
            y = (point.y - offsetY) / scale
        )
    }

    fun screenDeltaToBitmapDelta(delta: CropPoint): CropPoint {
        return CropPoint(
            x = delta.x / scale,
            y = delta.y / scale
        )
    }
    fun clampBitmapPoint(point: CropPoint): CropPoint {
        return CropPoint(
            x = point.x.coerceIn(0f, bitmapWidth - 1f),
            y = point.y.coerceIn(0f, bitmapHeight - 1f)
        )
    }
}

data class CropPoint(
    val x: Float,
    val y: Float
)

