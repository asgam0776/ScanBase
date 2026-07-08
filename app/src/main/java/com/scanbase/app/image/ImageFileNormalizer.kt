package com.scanbase.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object ImageFileNormalizer {
    private const val Tag = "ImageFileNormalizer"
    private const val MaxDecodeSize = 2800

    fun normalizeToCache(context: Context, sourceUri: Uri): Uri? {
        return normalizeToCache(
            context = context,
            sourceUri = sourceUri,
            forcePortraitIfExifNormal = false
        )
    }

    fun normalizeToCache(
        context: Context,
        sourceUri: Uri,
        forcePortraitIfExifNormal: Boolean
    ): Uri? {
        return runCatching {
            val orientation = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: readFileExifOrientation(sourceUri)

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            options.inJustDecodeBounds = false
            options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight)
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: decodeFileUri(sourceUri, options) ?: return null

            val normalizedFile = saveNormalizedBitmap(
                context = context,
                bitmap = bitmap,
                orientation = orientation,
                forcePortraitIfExifNormal = forcePortraitIfExifNormal
            )
            Uri.fromFile(normalizedFile)
        }.getOrNull()
    }

    private fun readFileExifOrientation(uri: Uri): Int {
        val path = uri.path ?: return ExifInterface.ORIENTATION_NORMAL
        return runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun decodeFileUri(uri: Uri, options: BitmapFactory.Options): Bitmap? {
        val path = uri.path ?: return null
        return BitmapFactory.decodeFile(path, options)
    }

    private fun saveNormalizedBitmap(
        context: Context,
        bitmap: Bitmap,
        orientation: Int,
        forcePortraitIfExifNormal: Boolean
    ): File {
        val rotationDegrees = rotationDegreesFor(
            orientation = orientation,
            bitmap = bitmap,
            forcePortraitIfExifNormal = forcePortraitIfExifNormal
        )
        Log.d(
            Tag,
            "normalize orientation=$orientation rotationDegrees=$rotationDegrees input=${bitmap.width}x${bitmap.height}"
        )

        val normalizedBitmap = applyOrientation(
            bitmap = bitmap,
            orientation = orientation,
            forcePortraitIfExifNormal = forcePortraitIfExifNormal
        )
        val outputFile = createNormalizedImageFile(context)

        outputFile.outputStream().use { output ->
            normalizedBitmap.compress(Bitmap.CompressFormat.JPEG, 96, output)
        }
        ExifInterface(outputFile.absolutePath).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            saveAttributes()
        }
        Log.d(
            Tag,
            "normalized file=${Uri.fromFile(outputFile)} output=${normalizedBitmap.width}x${normalizedBitmap.height}"
        )

        if (normalizedBitmap !== bitmap) {
            normalizedBitmap.recycle()
        }
        bitmap.recycle()

        return outputFile
    }

    private fun applyOrientation(
        bitmap: Bitmap,
        orientation: Int,
        forcePortraitIfExifNormal: Boolean
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> {
                if (forcePortraitIfExifNormal && bitmap.width > bitmap.height) {
                    matrix.postRotate(90f)
                } else {
                    return bitmap
                }
            }
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun rotationDegreesFor(
        orientation: Int,
        bitmap: Bitmap,
        forcePortraitIfExifNormal: Boolean
    ): Int {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> if (forcePortraitIfExifNormal && bitmap.width > bitmap.height) 90 else 0
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height

        while (sampledWidth / 2 >= MaxDecodeSize || sampledHeight / 2 >= MaxDecodeSize) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }

        return sampleSize.coerceAtLeast(1)
    }

    private fun createNormalizedImageFile(context: Context): File {
        val directory = File(context.cacheDir, "normalized").apply {
            mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(System.currentTimeMillis())
        return File(directory, "normalized_$timestamp.jpg")
    }
}
