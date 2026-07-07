package com.scanbase.app.image

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat

object BitmapMatConverter {
    fun bitmapToMat(bitmap: Bitmap): Mat {
        val sourceBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        return Mat().also { mat ->
            Utils.bitmapToMat(sourceBitmap, mat)
            if (sourceBitmap !== bitmap) {
                sourceBitmap.recycle()
            }
        }
    }

    fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }

    fun roundTrip(bitmap: Bitmap): Bitmap {
        val mat = bitmapToMat(bitmap)
        return try {
            matToBitmap(mat)
        } finally {
            mat.release()
        }
    }
}
