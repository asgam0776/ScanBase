package com.scanbase.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.util.Log
import com.scanbase.app.data.DocumentCorners
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

object PerspectiveTransformer {
    private const val Tag = "PerspectiveTransformer"
    private const val MinOutputSide = 100
    private const val MinAreaRatio = 0.02

    fun transform(sourceBitmap: Bitmap, corners: DocumentCorners): Bitmap? {
        if (!isValidSelection(sourceBitmap, corners)) {
            Log.d(Tag, "invalid selection corners=$corners")
            return null
        }

        val outputSize = calculateOutputSize(corners)
        if (outputSize.width < MinOutputSide || outputSize.height < MinOutputSide) {
            Log.d(Tag, "invalid output size width=${outputSize.width} height=${outputSize.height}")
            return null
        }

        val sourceMat = Mat()
        val destinationMat = Mat()
        var perspectiveMatrix: Mat? = null
        val sourcePoints = MatOfPoint2f()
        val destinationPoints = MatOfPoint2f()

        return try {
            Utils.bitmapToMat(sourceBitmap, sourceMat)

            sourcePoints.fromArray(
                corners.topLeft.toOpenCvPoint(),
                corners.topRight.toOpenCvPoint(),
                corners.bottomRight.toOpenCvPoint(),
                corners.bottomLeft.toOpenCvPoint()
            )
            destinationPoints.fromArray(
                Point(0.0, 0.0),
                Point(outputSize.width.toDouble(), 0.0),
                Point(outputSize.width.toDouble(), outputSize.height.toDouble()),
                Point(0.0, outputSize.height.toDouble())
            )

            perspectiveMatrix = Imgproc.getPerspectiveTransform(sourcePoints, destinationPoints)
            Imgproc.warpPerspective(
                sourceMat,
                destinationMat,
                perspectiveMatrix,
                Size(outputSize.width.toDouble(), outputSize.height.toDouble())
            )

            if (destinationMat.empty()) {
                Log.d(Tag, "warpPerspective returned empty mat")
                null
            } else {
                Bitmap.createBitmap(
                    destinationMat.cols(),
                    destinationMat.rows(),
                    Bitmap.Config.ARGB_8888
                ).also { resultBitmap ->
                    Utils.matToBitmap(destinationMat, resultBitmap)
                    Log.d(Tag, "result bitmap width=${resultBitmap.width} height=${resultBitmap.height}")
                }
            }
        } catch (exception: Exception) {
            Log.d(Tag, "transform failed", exception)
            null
        } finally {
            sourceMat.release()
            destinationMat.release()
            perspectiveMatrix?.release()
            sourcePoints.release()
            destinationPoints.release()
        }
    }

    fun transformToCache(
        context: Context,
        sourceBitmap: Bitmap,
        corners: DocumentCorners
    ): Uri? {
        val resultBitmap = transform(sourceBitmap, corners) ?: return null
        return try {
            saveBitmapToCache(context, resultBitmap)
        } finally {
            resultBitmap.recycle()
        }
    }

    private fun isValidSelection(bitmap: Bitmap, corners: DocumentCorners): Boolean {
        val points = listOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
        val insideBitmap = points.all { point ->
            point.x in 0f..(bitmap.width - 1).toFloat() && point.y in 0f..(bitmap.height - 1).toFloat()
        }
        if (!insideBitmap) return false

        val area = polygonArea(points)
        val imageArea = bitmap.width.toFloat() * bitmap.height.toFloat()
        return area >= imageArea * MinAreaRatio
    }

    private fun calculateOutputSize(corners: DocumentCorners): TransformOutputSize {
        val topWidth = corners.topLeft.distanceTo(corners.topRight)
        val bottomWidth = corners.bottomLeft.distanceTo(corners.bottomRight)
        val leftHeight = corners.topLeft.distanceTo(corners.bottomLeft)
        val rightHeight = corners.topRight.distanceTo(corners.bottomRight)
        return TransformOutputSize(
            width = max(topWidth, bottomWidth).toInt().coerceAtLeast(1),
            height = max(leftHeight, rightHeight).toInt().coerceAtLeast(1)
        )
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri? {
        return runCatching {
            val directory = File(context.cacheDir, "processed").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(System.currentTimeMillis())
            val file = File(directory, "scan_processed_$timestamp.jpg")
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            }
            Log.d(Tag, "saved processed image path=${file.absolutePath} width=${bitmap.width} height=${bitmap.height}")
            Uri.fromFile(file)
        }.getOrNull()
    }

    private fun polygonArea(points: List<PointF>): Float {
        var sum = 0f
        points.forEachIndexed { index, point ->
            val next = points[(index + 1) % points.size]
            sum += point.x * next.y - next.x * point.y
        }
        return abs(sum) / 2f
    }

    private fun PointF.distanceTo(other: PointF): Float {
        return hypot((x - other.x).toDouble(), (y - other.y).toDouble()).toFloat()
    }

    private fun PointF.toOpenCvPoint(): Point {
        return Point(x.toDouble(), y.toDouble())
    }

    private data class TransformOutputSize(
        val width: Int,
        val height: Int
    )
}
