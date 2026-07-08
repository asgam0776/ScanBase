package com.scanbase.app.crop

import android.graphics.PointF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.scanbase.app.data.DocumentCorners

class CropViewModel : ViewModel() {
    var state by mutableStateOf(CropState())
        private set

    fun resetIfImageChanged(imageUri: String?) {
        if (state.imageUri != imageUri) {
            state = CropState()
        }
    }

    fun initialize(
        imageUri: String,
        bitmapWidth: Int,
        bitmapHeight: Int,
        detectedCorners: DocumentCorners?
    ) {
        if (state.isInitialized && state.imageUri == imageUri) return

        val initialCorners = detectedCorners ?: insetFallbackCorners(bitmapWidth, bitmapHeight)
        state = CropState(
            imageUri = imageUri,
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            detectedCorners = detectedCorners,
            userCorners = initialCorners,
            selectedCorner = null,
            isInitialized = true
        )
    }

    fun setSelectedCorner(corner: CropCorner?) {
        state = state.copy(selectedCorner = corner)
    }

    fun updateCorner(corner: CropCorner, point: PointF) {
        val currentCorners = state.userCorners ?: return
        state = state.copy(
            userCorners = currentCorners.withCorner(
                corner = corner,
                point = clampPoint(point, state.bitmapWidth, state.bitmapHeight)
            ),
            selectedCorner = corner
        )
    }

    fun moveSelectedCorner(deltaX: Float, deltaY: Float) {
        val corner = state.selectedCorner ?: return
        val currentCorners = state.userCorners ?: return
        val currentPoint = currentCorners.pointFor(corner)
        updateCorner(
            corner = corner,
            point = PointF(currentPoint.x + deltaX, currentPoint.y + deltaY)
        )
    }

    fun latestUserCorners(): DocumentCorners? = state.userCorners
}

data class CropState(
    val imageUri: String? = null,
    val bitmapWidth: Int = 0,
    val bitmapHeight: Int = 0,
    val detectedCorners: DocumentCorners? = null,
    val userCorners: DocumentCorners? = null,
    val selectedCorner: CropCorner? = null,
    val isInitialized: Boolean = false
)

enum class CropCorner(val logName: String) {
    TopLeft("topLeft"),
    TopRight("topRight"),
    BottomRight("bottomRight"),
    BottomLeft("bottomLeft")
}

fun DocumentCorners.pointFor(corner: CropCorner): PointF {
    return when (corner) {
        CropCorner.TopLeft -> topLeft
        CropCorner.TopRight -> topRight
        CropCorner.BottomRight -> bottomRight
        CropCorner.BottomLeft -> bottomLeft
    }
}

fun DocumentCorners.withCorner(corner: CropCorner, point: PointF): DocumentCorners {
    return when (corner) {
        CropCorner.TopLeft -> copy(topLeft = point)
        CropCorner.TopRight -> copy(topRight = point)
        CropCorner.BottomRight -> copy(bottomRight = point)
        CropCorner.BottomLeft -> copy(bottomLeft = point)
    }
}

fun insetFallbackCorners(width: Int, height: Int): DocumentCorners {
    val left = width * 0.05f
    val top = height * 0.05f
    val right = width * 0.95f
    val bottom = height * 0.95f
    return DocumentCorners(
        topLeft = PointF(left, top),
        topRight = PointF(right, top),
        bottomRight = PointF(right, bottom),
        bottomLeft = PointF(left, bottom)
    )
}

private fun clampPoint(point: PointF, bitmapWidth: Int, bitmapHeight: Int): PointF {
    val maxX = (bitmapWidth - 1).coerceAtLeast(0).toFloat()
    val maxY = (bitmapHeight - 1).coerceAtLeast(0).toFloat()
    return PointF(
        point.x.coerceIn(0f, maxX),
        point.y.coerceIn(0f, maxY)
    )
}
