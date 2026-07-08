package com.scanbase.app.pdf

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.scanbase.app.data.ScanDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object PdfExporter {
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val PAGE_MARGIN = 40f

    fun save(context: Context, document: ScanDocument): File {
        require(document.pages.isNotEmpty()) {
            "Document has no pages."
        }

        val pdfDirectory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "ScanBase"
        ).apply {
            mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())
        val pdfFile = File(pdfDirectory, "scanbase_$timestamp.pdf")

        val pdfDocument = PdfDocument()
        try {
            document.pages.forEachIndexed { index, page ->
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                canvas.drawColor(Color.WHITE)
                val bitmap = decodeBitmap(context, page.exportImageUri)
                if (bitmap != null) {
                    val targetRect = fitCenterRect(
                        imageWidth = bitmap.width.toFloat(),
                        imageHeight = bitmap.height.toFloat(),
                        bounds = RectF(
                            PAGE_MARGIN,
                            PAGE_MARGIN,
                            A4_WIDTH - PAGE_MARGIN,
                            A4_HEIGHT - PAGE_MARGIN
                        )
                    )
                    canvas.drawBitmap(bitmap, null, targetRect, Paint(Paint.ANTI_ALIAS_FLAG))
                    bitmap.recycle()
                }

                pdfDocument.finishPage(pdfPage)
            }

            pdfFile.outputStream().use { output ->
                pdfDocument.writeTo(output)
            }
        } finally {
            pdfDocument.close()
        }

        return pdfFile
    }

    fun share(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "\u0050\u0044\u0046 \uACF5\uC720")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun decodeBitmap(context: Context, uriString: String): android.graphics.Bitmap? {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == "file") {
            BitmapFactory.decodeFile(uri.path)
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
    }

    private fun fitCenterRect(
        imageWidth: Float,
        imageHeight: Float,
        bounds: RectF
    ): RectF {
        val scale = minOf(bounds.width() / imageWidth, bounds.height() / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        val left = bounds.left + (bounds.width() - width) / 2f
        val top = bounds.top + (bounds.height() - height) / 2f
        return RectF(left, top, left + width, top + height)
    }
}

