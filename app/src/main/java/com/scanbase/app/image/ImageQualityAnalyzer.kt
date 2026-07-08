package com.scanbase.app.image

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc
import kotlin.math.max

object ImageQualityAnalyzer {
    private const val Tag = "ImageQualityAnalyzer"
    private const val MaxAnalysisSide = 1200

    fun analyze(bitmap: Bitmap): QualityResult? {
        val analysisBitmap = bitmap.scaledForAnalysis()
        val sourceMat = Mat()
        val grayMat = Mat()
        val laplacianMat = Mat()
        val mean = MatOfDouble()
        val stdDev = MatOfDouble()

        return try {
            Utils.bitmapToMat(analysisBitmap, sourceMat)
            Imgproc.cvtColor(sourceMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Laplacian(grayMat, laplacianMat, CvType.CV_64F)
            Core.meanStdDev(laplacianMat, mean, stdDev)

            val laplacianStdDev = stdDev.toArray().firstOrNull() ?: 0.0
            val blurScore = laplacianStdDev * laplacianStdDev
            val brightness = Core.mean(grayMat).`val`.firstOrNull() ?: 0.0

            QualityResult.fromScores(
                blurScore = blurScore,
                brightness = brightness
            ).also { result ->
                Log.d(
                    Tag,
                    "quality blurScore=${result.blurScore} brightness=${result.brightness} warnings=${result.warnings.size}"
                )
            }
        } catch (exception: Exception) {
            Log.d(Tag, "quality analysis failed", exception)
            null
        } finally {
            sourceMat.release()
            grayMat.release()
            laplacianMat.release()
            mean.release()
            stdDev.release()
            if (analysisBitmap !== bitmap) {
                analysisBitmap.recycle()
            }
        }
    }

    private fun Bitmap.scaledForAnalysis(): Bitmap {
        val longestSide = max(width, height)
        if (longestSide <= MaxAnalysisSide) return this

        val scale = MaxAnalysisSide.toFloat() / longestSide.toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }
}
