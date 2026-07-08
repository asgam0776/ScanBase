package com.scanbase.app.image

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.scanbase.app.data.DocumentCorners
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

object DocumentDetector {
    private const val Tag = "DocumentDetector"
    private const val MaxProcessLongSide = 1200.0
    private const val MinAreaRatio = 0.05
    private const val MinWidthRatio = 0.15
    private const val MinHeightRatio = 0.15
    private const val MinAspectRatio = 0.2
    private const val MaxAspectRatio = 5.0
    private const val FullImagePenaltyThreshold = 0.92

    fun detect(bitmap: Bitmap): DocumentCorners {
        val source = Mat()
        val resized = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val closed = Mat()
        val dilated = Mat()
        val hierarchy = Mat()

        return try {
            Utils.bitmapToMat(bitmap, source)
            val processScale = resizeForProcessing(source, resized)

            Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.bilateralFilter(gray, blurred, 7, 50.0, 50.0)
            Imgproc.Canny(blurred, edges, 50.0, 150.0)

            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(9.0, 9.0)
            )
            Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.dilate(closed, dilated, kernel, Point(-1.0, -1.0), 1)
            kernel.release()

            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(
                dilated,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            Log.d(Tag, "contours=${contours.size} processScale=$processScale")

            val bestCandidate = contours
                .flatMapIndexed { index, contour ->
                    contour.toDocumentCandidates(
                        index = index,
                        imageWidth = resized.width(),
                        imageHeight = resized.height()
                    )
                }
                .maxByOrNull { candidate -> candidate.score }

            contours.forEach { it.release() }

            if (bestCandidate == null) {
                Log.d(Tag, "Fallback to full image")
                fullImageCorners(bitmap.width, bitmap.height)
            } else {
                Log.d(
                    Tag,
                    "selected type=${bestCandidate.type} areaRatio=${bestCandidate.areaRatio} " +
                        "widthRatio=${bestCandidate.widthRatio} heightRatio=${bestCandidate.heightRatio} " +
                        "aspectRatio=${bestCandidate.aspectRatio} score=${bestCandidate.score}"
                )
                val originalPoints = bestCandidate.points.map { point ->
                    Point(point.x / processScale, point.y / processScale)
                }.toTypedArray()
                addMarginAndSortCorners(
                    points = originalPoints,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height
                )
            }
        } finally {
            source.release()
            resized.release()
            gray.release()
            blurred.release()
            edges.release()
            closed.release()
            dilated.release()
            hierarchy.release()
        }
    }

    private fun resizeForProcessing(source: Mat, destination: Mat): Double {
        val longSide = max(source.width(), source.height()).toDouble()
        val scale = if (longSide > MaxProcessLongSide) MaxProcessLongSide / longSide else 1.0
        if (scale < 1.0) {
            Imgproc.resize(source, destination, Size(source.width() * scale, source.height() * scale))
        } else {
            source.copyTo(destination)
        }
        return scale
    }

    private fun MatOfPoint.toDocumentCandidates(
        index: Int,
        imageWidth: Int,
        imageHeight: Int
    ): List<DocumentCandidate> {
        val contour2f = MatOfPoint2f(*toArray())
        val approx = MatOfPoint2f()
        val candidates = mutableListOf<DocumentCandidate>()

        return try {
            val perimeter = Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approx, 0.025 * perimeter, true)
            val approxPoints = approx.toArray()
            Log.d(Tag, "contour[$index] approxPoints=${approxPoints.size}")

            if (approxPoints.size == 4) {
                buildCandidate(index, "approx4", approxPoints, imageWidth, imageHeight, true)?.let { candidates += it }
            } else {
                Log.d(Tag, "contour[$index] approx4 reject reason=point_count_${approxPoints.size}")
            }

            val contourAreaRatio = abs(Imgproc.contourArea(this)) / (imageWidth.toDouble() * imageHeight.toDouble())
            if (contourAreaRatio >= MinAreaRatio) {
                val minRectPoints = minAreaRectPoints(contour2f)
                buildCandidate(index, "minAreaRect", minRectPoints, imageWidth, imageHeight, true)?.let { candidates += it }

                val boundingRect = Imgproc.boundingRect(this)
                val boundingPoints = arrayOf(
                    Point(boundingRect.x.toDouble(), boundingRect.y.toDouble()),
                    Point((boundingRect.x + boundingRect.width).toDouble(), boundingRect.y.toDouble()),
                    Point((boundingRect.x + boundingRect.width).toDouble(), (boundingRect.y + boundingRect.height).toDouble()),
                    Point(boundingRect.x.toDouble(), (boundingRect.y + boundingRect.height).toDouble())
                )
                buildCandidate(index, "boundingRect", boundingPoints, imageWidth, imageHeight, false)?.let { candidates += it }
            } else {
                Log.d(Tag, "contour[$index] fallback shapes reject reason=contour_area_too_small areaRatio=$contourAreaRatio")
            }

            candidates
        } finally {
            contour2f.release()
            approx.release()
        }
    }

    private fun minAreaRectPoints(contour2f: MatOfPoint2f): Array<Point> {
        val rect: RotatedRect = Imgproc.minAreaRect(contour2f)
        val points = Array(4) { Point() }
        rect.points(points)
        return points
    }

    private fun buildCandidate(
        index: Int,
        type: String,
        points: Array<Point>,
        imageWidth: Int,
        imageHeight: Int,
        requireConvex: Boolean
    ): DocumentCandidate? {
        val pointMat = MatOfPoint(*points)
        return try {
            if (requireConvex && !Imgproc.isContourConvex(pointMat)) {
                logReject(index, type, "not_convex")
                return null
            }

            val area = abs(Imgproc.contourArea(pointMat))
            val boundingRect = Imgproc.boundingRect(pointMat)
            val width = boundingRect.width.toDouble()
            val height = boundingRect.height.toDouble()
            val aspectRatio = if (height == 0.0) Double.POSITIVE_INFINITY else width / height
            val imageArea = imageWidth.toDouble() * imageHeight.toDouble()
            val areaRatio = area / imageArea
            val widthRatio = width / imageWidth.toDouble()
            val heightRatio = height / imageHeight.toDouble()

            val rejectReason = rejectReason(areaRatio, widthRatio, heightRatio, aspectRatio)
            if (rejectReason != null) {
                logCandidate(index, type, areaRatio, widthRatio, heightRatio, aspectRatio, 0.0, rejectReason)
                return null
            }

            val score = calculateScore(
                areaRatio = areaRatio,
                widthRatio = widthRatio,
                heightRatio = heightRatio,
                aspectRatio = aspectRatio,
                centerX = boundingRect.x + width / 2.0,
                centerY = boundingRect.y + height / 2.0,
                imageWidth = imageWidth,
                imageHeight = imageHeight
            )

            logCandidate(index, type, areaRatio, widthRatio, heightRatio, aspectRatio, score, "accepted")
            DocumentCandidate(points, areaRatio, widthRatio, heightRatio, aspectRatio, score, type)
        } finally {
            pointMat.release()
        }
    }

    private fun rejectReason(
        areaRatio: Double,
        widthRatio: Double,
        heightRatio: Double,
        aspectRatio: Double
    ): String? {
        if (areaRatio < MinAreaRatio) return "area_too_small"
        if (widthRatio < MinWidthRatio) return "width_too_small"
        if (heightRatio < MinHeightRatio) return "height_too_small"
        if (aspectRatio < MinAspectRatio || aspectRatio > MaxAspectRatio) return "aspect_extreme"
        return null
    }

    private fun calculateScore(
        areaRatio: Double,
        widthRatio: Double,
        heightRatio: Double,
        aspectRatio: Double,
        centerX: Double,
        centerY: Double,
        imageWidth: Int,
        imageHeight: Int
    ): Double {
        val imageCenterX = imageWidth / 2.0
        val imageCenterY = imageHeight / 2.0
        val maxDistance = hypot(imageCenterX, imageCenterY)
        val centerDistance = hypot(centerX - imageCenterX, centerY - imageCenterY)
        val centerScore = (1.0 - centerDistance / maxDistance).coerceIn(0.0, 1.0)
        val normalizedRatio = if (aspectRatio >= 1.0) aspectRatio else 1.0 / aspectRatio
        val ratioScore = (1.0 / normalizedRatio).coerceIn(0.0, 1.0)
        val fullImagePenalty = if (widthRatio > FullImagePenaltyThreshold && heightRatio > FullImagePenaltyThreshold) 0.35 else 0.0

        return areaRatio * 0.55 + centerScore * 0.30 + ratioScore * 0.15 - fullImagePenalty
    }

    private fun logCandidate(
        index: Int,
        type: String,
        areaRatio: Double,
        widthRatio: Double,
        heightRatio: Double,
        aspectRatio: Double,
        score: Double,
        reason: String
    ) {
        Log.d(
            Tag,
            "candidate contour=$index type=$type areaRatio=$areaRatio " +
                "widthRatio=$widthRatio heightRatio=$heightRatio aspectRatio=$aspectRatio " +
                "score=$score reason=$reason"
        )
    }

    private fun logReject(index: Int, type: String, reason: String) {
        Log.d(Tag, "candidate contour=$index type=$type reject reason=$reason")
    }

    private fun addMarginAndSortCorners(
        points: Array<Point>,
        imageWidth: Int,
        imageHeight: Int
    ): DocumentCorners {
        val topLeft = points.minBy { it.x + it.y }
        val bottomRight = points.maxBy { it.x + it.y }
        val topRight = points.maxBy { it.x - it.y }
        val bottomLeft = points.minBy { it.x - it.y }
        val margin = minOf(imageWidth, imageHeight) * 0.025

        return DocumentCorners(
            topLeft = topLeft.withMargin(-margin, -margin, imageWidth, imageHeight),
            topRight = topRight.withMargin(margin, -margin, imageWidth, imageHeight),
            bottomRight = bottomRight.withMargin(margin, margin, imageWidth, imageHeight),
            bottomLeft = bottomLeft.withMargin(-margin, margin, imageWidth, imageHeight)
        )
    }

    private fun fullImageCorners(width: Int, height: Int): DocumentCorners {
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

    private fun Point.withMargin(
        marginX: Double,
        marginY: Double,
        imageWidth: Int,
        imageHeight: Int
    ): PointF {
        val maxX = (imageWidth - 1).coerceAtLeast(0).toFloat()
        val maxY = (imageHeight - 1).coerceAtLeast(0).toFloat()
        return PointF(
            (x + marginX).toFloat().coerceIn(0f, maxX),
            (y + marginY).toFloat().coerceIn(0f, maxY)
        )
    }

    private data class DocumentCandidate(
        val points: Array<Point>,
        val areaRatio: Double,
        val widthRatio: Double,
        val heightRatio: Double,
        val aspectRatio: Double,
        val score: Double,
        val type: String
    )
}

