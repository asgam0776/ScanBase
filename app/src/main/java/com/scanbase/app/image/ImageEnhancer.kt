package com.scanbase.app.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object ImageEnhancer {
    private const val Tag = "ImageEnhancer"

    fun enhance(sourceBitmap: Bitmap, mode: EnhanceMode): Bitmap? {
        return when (mode) {
            EnhanceMode.Original -> sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
            EnhanceMode.Document -> enhanceDocument(sourceBitmap)
            EnhanceMode.BlackWhite -> enhanceBlackWhite(sourceBitmap)
        }
    }

    fun enhanceToCache(
        context: Context,
        sourceBitmap: Bitmap,
        mode: EnhanceMode
    ): Uri? {
        val enhancedBitmap = enhance(sourceBitmap, mode) ?: return null
        return try {
            saveBitmapToCache(context, enhancedBitmap, mode)
        } finally {
            enhancedBitmap.recycle()
        }
    }

    private fun enhanceDocument(sourceBitmap: Bitmap): Bitmap? {
        val sourceMat = Mat()
        val rgbMat = Mat()
        val labMat = Mat()
        val enhancedLabMat = Mat()
        val enhancedRgbMat = Mat()
        val blurredMat = Mat()
        val sharpenedMat = Mat()
        val resultMat = Mat()
        val channels = mutableListOf<Mat>()
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))

        return try {
            Utils.bitmapToMat(sourceBitmap, sourceMat)
            Imgproc.cvtColor(sourceMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgbMat, labMat, Imgproc.COLOR_RGB2Lab)

            Core.split(labMat, channels)
            if (channels.size < 3) return null

            clahe.apply(channels[0], channels[0])
            Core.merge(channels, enhancedLabMat)
            Imgproc.cvtColor(enhancedLabMat, enhancedRgbMat, Imgproc.COLOR_Lab2RGB)

            Imgproc.GaussianBlur(enhancedRgbMat, blurredMat, Size(0.0, 0.0), 1.1)
            Core.addWeighted(enhancedRgbMat, 1.18, blurredMat, -0.18, 4.0, sharpenedMat)
            Imgproc.cvtColor(sharpenedMat, resultMat, Imgproc.COLOR_RGB2RGBA)

            resultMat.toBitmapOrNull()
        } catch (exception: Exception) {
            Log.d(Tag, "document enhance failed", exception)
            null
        } finally {
            sourceMat.release()
            rgbMat.release()
            labMat.release()
            enhancedLabMat.release()
            enhancedRgbMat.release()
            blurredMat.release()
            sharpenedMat.release()
            resultMat.release()
            channels.forEach { it.release() }
            clahe.collectGarbage()
        }
    }

    private fun enhanceBlackWhite(sourceBitmap: Bitmap): Bitmap? {
        val sourceMat = Mat()
        val grayMat = Mat()
        val denoisedMat = Mat()
        val thresholdMat = Mat()
        val resultMat = Mat()

        return try {
            Utils.bitmapToMat(sourceBitmap, sourceMat)
            Imgproc.cvtColor(sourceMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(grayMat, denoisedMat, Size(3.0, 3.0), 0.0)
            Imgproc.adaptiveThreshold(
                denoisedMat,
                thresholdMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                31,
                8.0
            )
            Imgproc.cvtColor(thresholdMat, resultMat, Imgproc.COLOR_GRAY2RGBA)

            resultMat.toBitmapOrNull()
        } catch (exception: Exception) {
            Log.d(Tag, "black white enhance failed", exception)
            null
        } finally {
            sourceMat.release()
            grayMat.release()
            denoisedMat.release()
            thresholdMat.release()
            resultMat.release()
        }
    }

    private fun Mat.toBitmapOrNull(): Bitmap? {
        if (empty() || cols() <= 0 || rows() <= 0) return null
        return Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888).also { bitmap ->
            Utils.matToBitmap(this, bitmap)
            Log.d(Tag, "enhanced bitmap width=${bitmap.width} height=${bitmap.height}")
        }
    }

    private fun saveBitmapToCache(
        context: Context,
        bitmap: Bitmap,
        mode: EnhanceMode
    ): Uri? {
        return runCatching {
            val directory = File(context.cacheDir, "enhanced").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(System.currentTimeMillis())
            val file = File(directory, "scan_${mode.name.lowercase(Locale.US)}_$timestamp.jpg")
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            }
            Log.d(Tag, "saved enhanced image mode=$mode path=${file.absolutePath} width=${bitmap.width} height=${bitmap.height}")
            Uri.fromFile(file)
        }.onFailure { exception ->
            Log.d(Tag, "save enhanced image failed mode=$mode", exception)
        }.getOrNull()
    }
}
