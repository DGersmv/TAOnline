package com.example.chonline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.content.ContentValues
import android.media.MediaScannerConnection
import androidx.exifinterface.media.ExifInterface
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.calib3d.Calib3d
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Класс для склейки нескольких изображений в одну 180° панораму с простым перекрытием
 */
class PanoramaStitcher {

    private val TAG = "PanoramaStitcher"
    
    companion object {
        @Volatile
        private var isOpenCVInitialized = false

        private const val MAX_INPUT_WIDTH = 1600
        private const val MAX_INPUT_HEIGHT = 1600
        private const val OVERLAP_FRACTION = 0.35f
        private const val MAX_VERTICAL_SHIFT = 40
        private const val MIN_MATCHES_FOR_HOMOGRAPHY = 20
        private const val GAUSSIAN_KERNEL = 31.0
        private const val DEFAULT_FOV_DEGREES = 75.0
        private const val RATIO_TEST_THRESHOLD = 0.8f
        
        /**
         * Инициализировать OpenCV (вызывается при первом использовании)
         */
        fun initializeOpenCV(): Boolean {
            if (!isOpenCVInitialized) {
                synchronized(this) {
                    if (!isOpenCVInitialized) {
                        isOpenCVInitialized = OpenCVLoader.initLocal()
                        if (isOpenCVInitialized) {
                            Log.d("PanoramaStitcher", "OpenCV успешно инициализирован")
                        } else {
                            Log.e("PanoramaStitcher", "Не удалось инициализировать OpenCV")
                        }
                    }
                }
            }
            return isOpenCVInitialized
        }
    }

    /**
     * Склеить несколько изображений в одну панораму (простое наложение)
     */
    suspend fun stitchPanorama(
        imageUris: List<Uri>,
        context: Context
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            if (imageUris.isEmpty()) {
                Log.e(TAG, "Список изображений пуст")
                return@withContext null
            }

            Log.d(TAG, "Начало склейки ${imageUris.size} изображений с использованием OpenCV")

            if (!initializeOpenCV()) {
                Log.e(TAG, "Не удалось инициализировать OpenCV")
                return@withContext null
            }

            val bitmaps = imageUris.mapIndexedNotNull { index, uri ->
                val bmp = loadBitmapForStitching(context, uri)
                bmp?.also {
                    Log.d(TAG, "Загружено изображение #$index: ${it.width}x${it.height}")
                }
            }

            if (bitmaps.isEmpty()) {
                Log.e(TAG, "Не удалось загрузить изображения")
                return@withContext null
            }

            if (bitmaps.size == 1) {
                val singleBitmap = bitmaps[0]
                val mat = Mat()
                Utils.bitmapToMat(singleBitmap, mat)
                val uri = saveMatAsUri(context, mat)
                mat.release()
                singleBitmap.recycle()
                return@withContext uri
            }

            val targetWidth = bitmaps.minOf { it.width }
            val targetHeight = bitmaps.minOf { it.height }

            val resized = bitmaps.map { bmp ->
                if (bmp.width != targetWidth || bmp.height != targetHeight) {
                    Bitmap.createScaledBitmap(bmp, targetWidth, targetHeight, true).also {
                        bmp.recycle()
                    }
                } else {
                    bmp
                }
            }

            val panoramaMat = stitchWithHomography(resized) ?: run {
                Log.w(TAG, "Гомография не сработала, возвращаемся к простому наложению")
                stitchWithLinearOverlap(resized)
            }

            val alignedPanorama = alignLoopSeam(panoramaMat)
            panoramaMat.release()

            resized.forEach { it.recycle() }

            Log.d(TAG, "Панорама успешно склеена. Размер: ${alignedPanorama.width()}x${alignedPanorama.height()}")

            val outputUri = saveMatAsUri(context, alignedPanorama)
            alignedPanorama.release()

            Log.d(TAG, "Панорама успешно сохранена: $outputUri")
            outputUri
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при склейке панорамы: ${e.message}", e)
            null
        }
    }

    private fun stitchWithHomography(bitmaps: List<Bitmap>): Mat? {
        if (bitmaps.size < 2) return null

        val projectedMats = ArrayList<Mat>(bitmaps.size)
        try {
            bitmaps.forEachIndexed { index, bitmap ->
                val rgba = Mat()
                Utils.bitmapToMat(bitmap, rgba)
                val bgr = Mat()
                Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
                rgba.release()

                val cylindrical = applyCylindricalProjection(bgr)
                bgr.release()

                projectedMats.add(cylindrical)
                Log.d(TAG, "Cylindrical projection for frame #$index -> ${cylindrical.cols()}x${cylindrical.rows()}")
            }

            val homographies = mutableListOf(Mat.eye(3, 3, CvType.CV_64F))
            val orb = ORB.create(4000)

            try {
                for (i in 0 until projectedMats.size - 1) {
                    val homography = computeHomographyBetween(projectedMats[i], projectedMats[i + 1], orb)
                    if (homography == null || homography.empty()) {
                        Log.w(TAG, "Не удалось вычислить гомографию для пары $i-${i + 1}")
                        homography?.release()
                        homographies.forEach { it.release() }
                        projectedMats.forEach { it.release() }
                        return null
                    }

                    val cumulative = Mat()
                    Core.gemm(homographies[i], homography, 1.0, Mat(), 0.0, cumulative)
                    homographies.add(cumulative)
                    homography.release()
                }

                var minX = Double.POSITIVE_INFINITY
                var minY = Double.POSITIVE_INFINITY
                var maxX = Double.NEGATIVE_INFINITY
                var maxY = Double.NEGATIVE_INFINITY

                val corners = arrayOf(
                    Point(0.0, 0.0),
                    Point(projectedMats[0].cols().toDouble(), 0.0),
                    Point(projectedMats[0].cols().toDouble(), projectedMats[0].rows().toDouble()),
                    Point(0.0, projectedMats[0].rows().toDouble())
                )

                for (i in projectedMats.indices) {
                    val srcCorners = MatOfPoint2f(*corners)
                    val dstCorners = MatOfPoint2f()
                    Core.perspectiveTransform(srcCorners, dstCorners, homographies[i])

                    dstCorners.toArray().forEach { pt ->
                        minX = min(minX, pt.x)
                        minY = min(minY, pt.y)
                        maxX = max(maxX, pt.x)
                        maxY = max(maxY, pt.y)
                    }

                    srcCorners.release()
                    dstCorners.release()
                }

                val shiftX = if (minX < 0) -minX else 0.0
                val shiftY = if (minY < 0) -minY else 0.0

                val translation = Mat.eye(3, 3, CvType.CV_64F).apply {
                    put(0, 2, shiftX)
                    put(1, 2, shiftY)
                }

                val adjustedHomographies = homographies.map { h ->
                    val adjusted = Mat()
                    Core.gemm(translation, h, 1.0, Mat(), 0.0, adjusted)
                    adjusted
                }
                translation.release()

                val outputWidth = ceil(maxX + shiftX).coerceAtLeast(1.0).roundToInt()
                val outputHeight = ceil(maxY + shiftY).coerceAtLeast(1.0).roundToInt()

                val accumulator = Mat.zeros(outputHeight, outputWidth, CvType.CV_32FC3)
                val weightSum = Mat.zeros(outputHeight, outputWidth, CvType.CV_32FC1)

                for (i in projectedMats.indices) {
                    val warped = Mat()
                    Imgproc.warpPerspective(
                        projectedMats[i],
                        warped,
                        adjustedHomographies[i],
                        Size(outputWidth.toDouble(), outputHeight.toDouble()),
                        Imgproc.INTER_LINEAR,
                        Core.BORDER_CONSTANT,
                        Scalar(0.0, 0.0, 0.0)
                    )

                    val warpedFloat = Mat()
                    warped.convertTo(warpedFloat, CvType.CV_32FC3, 1.0 / 255.0)

                    val maskGray = Mat()
                    Imgproc.cvtColor(warped, maskGray, Imgproc.COLOR_BGR2GRAY)
                    Imgproc.threshold(maskGray, maskGray, 0.0, 255.0, Imgproc.THRESH_BINARY)
                    maskGray.convertTo(maskGray, CvType.CV_32FC1, 1.0 / 255.0)
                    Imgproc.GaussianBlur(maskGray, maskGray, Size(GAUSSIAN_KERNEL, GAUSSIAN_KERNEL), 0.0)
                    Core.min(maskGray, Scalar(1.0), maskGray)

                    val maskChannels = mutableListOf(maskGray, maskGray, maskGray)
                    val maskColor = Mat()
                    Core.merge(maskChannels, maskColor)

                    Core.multiply(warpedFloat, maskColor, warpedFloat)
                    Core.add(accumulator, warpedFloat, accumulator)
                    Core.add(weightSum, maskGray, weightSum)

                    maskColor.release()
                    warped.release()
                    warpedFloat.release()
                    maskGray.release()
                }

                homographies.forEach { it.release() }
                adjustedHomographies.forEach { it.release() }
                projectedMats.forEach { it.release() }

                val accumChannels = ArrayList<Mat>()
                Core.split(accumulator, accumChannels)

                val weightSafe = Mat()
                Core.max(weightSum, Scalar(1e-6), weightSafe)

                for (channel in accumChannels) {
                    Core.divide(channel, weightSafe, channel)
                }

                val resultFloat = Mat()
                Core.merge(accumChannels, resultFloat)

                val result = Mat()
                resultFloat.convertTo(result, CvType.CV_8UC3, 255.0)

                accumulator.release()
                weightSum.release()
                weightSafe.release()
                resultFloat.release()
                accumChannels.forEach { it.release() }

                val aligned = alignLoopSeam(result)
                result.release()
                return aligned
            } finally {
                orb.clear()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при склейке через гомографию: ${e.message}")
            projectedMats.forEach { it.release() }
            return null
        }
    }

    private fun applyCylindricalProjection(src: Mat, fovDegrees: Double = DEFAULT_FOV_DEGREES): Mat {
        val width = src.cols()
        val height = src.rows()
        val fovRadians = Math.toRadians(fovDegrees)
        val focalLength = width / (2.0 * tan(fovRadians / 2.0))
        val centerX = width / 2.0
        val centerY = height / 2.0

        val mapX = Mat(height, width, CvType.CV_32FC1)
        val mapY = Mat(height, width, CvType.CV_32FC1)

        val thetaRow = DoubleArray(width)
        for (x in 0 until width) {
            thetaRow[x] = (x - centerX) / focalLength
        }

        for (y in 0 until height) {
            val mapXRow = FloatArray(width)
            val mapYRow = FloatArray(width)
            val yDiff = y - centerY
            for (x in 0 until width) {
                val theta = thetaRow[x]
                val cosTheta = cos(theta).takeIf { abs(it) > 1e-6 } ?: 1e-6
                val sourceX = focalLength * tan(theta) + centerX
                val sourceY = yDiff / cosTheta + centerY
                mapXRow[x] = sourceX.toFloat()
                mapYRow[x] = sourceY.toFloat()
            }
            mapX.put(y, 0, mapXRow)
            mapY.put(y, 0, mapYRow)
        }

        val dst = Mat(height, width, src.type())
        Imgproc.remap(src, dst, mapX, mapY, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0, 0.0, 0.0))
        mapX.release()
        mapY.release()
        return dst
    }

    private fun stitchWithLinearOverlap(bitmaps: List<Bitmap>): Mat {
        require(bitmaps.isNotEmpty()) { "Список изображений пуст" }

        val targetWidth = bitmaps[0].width
        val targetHeight = bitmaps[0].height

        val overlapWidth = (targetWidth * OVERLAP_FRACTION).roundToInt().coerceIn(1, targetWidth - 1)
        val stepWidth = targetWidth - overlapWidth
        val panoramaWidth = targetWidth + stepWidth * (bitmaps.size - 1)

        val outputPixels = IntArray(panoramaWidth * targetHeight) { Color.BLACK }
        val tempRow = IntArray(targetWidth)

        for (y in 0 until targetHeight) {
            bitmaps[0].getPixels(tempRow, 0, targetWidth, 0, y, targetWidth, 1)
            tempRow.copyInto(outputPixels, y * panoramaWidth)
        }

        val offsets = computeOffset(bitmaps[0], bitmaps[1], overlapWidth)
        val shiftX = offsets.first
        val shiftY = offsets.second
        Log.d(TAG, "Базовый сдвиг: dx=$shiftX dy=$shiftY")

        var currentX = targetWidth
        var accumulatedY = shiftY

        for (i in 1 until bitmaps.size) {
            val bmp = bitmaps[i]

            val nonOverlapStartX = currentX + shiftX
            val overlapStartX = nonOverlapStartX - overlapWidth

            for (y in 0 until targetHeight) {
                val destY = (y + accumulatedY).coerceIn(0, targetHeight - 1)
                bmp.getPixels(tempRow, 0, targetWidth, 0, y, targetWidth, 1)

                val overlapIndex = destY * panoramaWidth + overlapStartX
                blendOverlap(
                    basePixels = outputPixels,
                    baseIndex = overlapIndex,
                    newRow = tempRow,
                    overlapWidth = overlapWidth
                )

                val nonOverlapIndex = destY * panoramaWidth + nonOverlapStartX
                val length = targetWidth - overlapWidth
                if (length > 0) {
                    tempRow.copyInto(
                        destination = outputPixels,
                        destinationOffset = nonOverlapIndex,
                        startIndex = overlapWidth,
                        endIndex = targetWidth
                    )
                }
            }

            currentX += stepWidth + shiftX
            accumulatedY += shiftY
        }

        val outputBitmap = Bitmap.createBitmap(panoramaWidth, targetHeight, Bitmap.Config.ARGB_8888)
        outputBitmap.setPixels(outputPixels, 0, panoramaWidth, 0, 0, panoramaWidth, targetHeight)

        val outputMat = Mat()
        Utils.bitmapToMat(outputBitmap, outputMat)
        outputBitmap.recycle()

        return outputMat
    }

    private fun computeOffset(first: Bitmap, second: Bitmap, overlapWidth: Int): Pair<Int, Int> {
        val height = first.height
        val roiWidth = overlapWidth
        val roiHeight = min(height, 128)
        val startY = (height - roiHeight) / 2

        val firstOverlap = Bitmap.createBitmap(first, first.width - roiWidth, startY, roiWidth, roiHeight)
        val secondOverlap = Bitmap.createBitmap(second, 0, startY, roiWidth, roiHeight)

        val matFirst = Mat()
        val matSecond = Mat()
        Utils.bitmapToMat(firstOverlap, matFirst)
        Utils.bitmapToMat(secondOverlap, matSecond)
        firstOverlap.recycle()
        secondOverlap.recycle()

        Imgproc.cvtColor(matFirst, matFirst, Imgproc.COLOR_BGR2GRAY)
        Imgproc.cvtColor(matSecond, matSecond, Imgproc.COLOR_BGR2GRAY)

        val resultCols = matFirst.cols() - matSecond.cols() + 1
        val resultRows = matFirst.rows() - matSecond.rows() + 1
        val result = Mat(resultRows, resultCols, CvType.CV_32FC1)

        Imgproc.matchTemplate(matFirst, matSecond, result, Imgproc.TM_CCOEFF_NORMED)
        val mmr = Core.minMaxLoc(result)
        val bestX = mmr.maxLoc.x.roundToInt()
        val bestY = mmr.maxLoc.y.roundToInt()

        matFirst.release()
        matSecond.release()
        result.release()

        val dx = bestX - (matFirst.cols() - matSecond.cols())
        val dy = (bestY - resultRows / 2).coerceIn(-MAX_VERTICAL_SHIFT, MAX_VERTICAL_SHIFT)
        return Pair(dx, dy)
    }

    private fun computeHomographyBetween(
        reference: Mat,
        candidate: Mat,
        orb: ORB
    ): Mat? {
        val grayReference = Mat()
        val grayCandidate = Mat()
        Imgproc.cvtColor(reference, grayReference, Imgproc.COLOR_BGR2GRAY)
        Imgproc.cvtColor(candidate, grayCandidate, Imgproc.COLOR_BGR2GRAY)

        val keypointsRef = MatOfKeyPoint()
        val keypointsCand = MatOfKeyPoint()
        val descriptorsRef = Mat()
        val descriptorsCand = Mat()

        orb.detectAndCompute(grayReference, Mat(), keypointsRef, descriptorsRef)
        orb.detectAndCompute(grayCandidate, Mat(), keypointsCand, descriptorsCand)

        grayReference.release()
        grayCandidate.release()

        if (descriptorsRef.empty() || descriptorsCand.empty()) {
            keypointsRef.release()
            keypointsCand.release()
            descriptorsRef.release()
            descriptorsCand.release()
            Log.w(TAG, "ORB не нашёл ключевых точек")
            return null
        }

        val matcher = BFMatcher.create(Core.NORM_HAMMING, false)
        val knnMatches = ArrayList<MatOfDMatch>()
        matcher.knnMatch(descriptorsRef, descriptorsCand, knnMatches, 2)

        val goodMatches = ArrayList<DMatch>()
        for (mat in knnMatches) {
            val matches = mat.toArray()
            if (matches.size >= 2) {
                val m1 = matches[0]
                val m2 = matches[1]
                if (m1.distance < RATIO_TEST_THRESHOLD * m2.distance) {
                    goodMatches.add(m1)
                }
            }
            mat.release()
        }
        matcher.clear()

        val refKeypoints = keypointsRef.toArray()
        val candKeypoints = keypointsCand.toArray()

        keypointsRef.release()
        keypointsCand.release()
        descriptorsRef.release()
        descriptorsCand.release()

        if (goodMatches.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
            Log.w(TAG, "Недостаточно хороших совпадений: ${goodMatches.size}")
            return null
        }

        val refPoints = ArrayList<Point>(goodMatches.size)
        val candPoints = ArrayList<Point>(goodMatches.size)

        for (match in goodMatches) {
            refPoints.add(refKeypoints[match.queryIdx].pt)
            candPoints.add(candKeypoints[match.trainIdx].pt)
        }

        val refMat = MatOfPoint2f(*refPoints.toTypedArray())
        val candMat = MatOfPoint2f(*candPoints.toTypedArray())

        val mask = Mat()
        val homography = Calib3d.findHomography(candMat, refMat, Calib3d.RANSAC, 4.0, mask)
        val inliers = Core.countNonZero(mask)
        Log.d(TAG, "Гомография: найдено совпадений=${goodMatches.size}, инлайеров=$inliers")

        refMat.release()
        candMat.release()
        mask.release()

        return homography
    }

    private fun blendOverlap(
        basePixels: IntArray,
        baseIndex: Int,
        newRow: IntArray,
        overlapWidth: Int
    ) {
        if (overlapWidth <= 0) return
        val denom = (overlapWidth - 1).coerceAtLeast(1)
        for (x in 0 until overlapWidth) {
            val alpha = x.toFloat() / denom
            val invAlpha = 1f - alpha
            val srcIndex = baseIndex + x
            if (srcIndex !in basePixels.indices) continue

            val existingColor = basePixels[srcIndex]
            val newColor = newRow.getOrNull(x) ?: continue

            val r = (Color.red(existingColor) * invAlpha + Color.red(newColor) * alpha).roundToInt().coerceIn(0, 255)
            val g = (Color.green(existingColor) * invAlpha + Color.green(newColor) * alpha).roundToInt().coerceIn(0, 255)
            val b = (Color.blue(existingColor) * invAlpha + Color.blue(newColor) * alpha).roundToInt().coerceIn(0, 255)
            basePixels[srcIndex] = Color.argb(255, r, g, b)
        }
    }

    private fun cropBlackBorders(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        val thresh = Mat()
        Imgproc.threshold(gray, thresh, 1.0, 255.0, Imgproc.THRESH_BINARY)

        val points = Mat()
        Core.findNonZero(thresh, points)

        gray.release()
        thresh.release()

        if (points.empty()) {
            points.release()
            return src.clone()
        }

        val pointList = ArrayList<Point>(points.rows())
        for (i in 0 until points.rows()) {
            val data = points.get(i, 0)
            if (data != null && data.size >= 2) {
                pointList.add(Point(data[0], data[1]))
            }
        }
        points.release()

        val matOfPoint = MatOfPoint()
        matOfPoint.fromList(pointList)
        val rect = Imgproc.boundingRect(matOfPoint)
        matOfPoint.release()

        return Mat(src, rect).clone()
    }

    private fun loadBitmapForStitching(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { rawStream ->
                val exifRotation = context.contentResolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f

                val original = BitmapFactory.decodeStream(rawStream) ?: return null

                val rotated = if (exifRotation != 0f) {
                    val matrix = android.graphics.Matrix().apply { postRotate(exifRotation) }
                    Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true).also {
                        original.recycle()
                    }
                } else {
                    original
                }

                val resized = resizeWithinBounds(rotated, MAX_INPUT_WIDTH, MAX_INPUT_HEIGHT)
                if (resized != rotated) {
                    rotated.recycle()
                }
                resized
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки изображения: ${e.message}")
            null
        }
    }

    private fun resizeWithinBounds(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val scale = min(1.0, min(maxWidth.toDouble() / width, maxHeight.toDouble() / height))
        return if (scale < 0.999) {
            val scaled = Bitmap.createScaledBitmap(bitmap, (width * scale).roundToInt(), (height * scale).roundToInt(), true)
            scaled
        } else {
            bitmap
        }
    }

    private fun alignLoopSeam(panorama: Mat, seamFraction: Double = 0.2): Mat {
        val width = panorama.cols()
        val height = panorama.rows()
        if (width <= 0 || height <= 0) return panorama.clone()

        val seamWidth = (width * seamFraction).roundToInt().coerceIn(32, width / 2)
        if (seamWidth <= 0 || seamWidth * 2 > width) {
            Log.d(TAG, "Seam alignment skipped: seamWidth=$seamWidth width=$width")
            return panorama.clone()
        }

        val leftStrip = panorama.colRange(0, seamWidth)
        val rightStrip = panorama.colRange(width - seamWidth, width)

        val leftGray = Mat()
        val rightGray = Mat()
        Imgproc.cvtColor(leftStrip, leftGray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.cvtColor(rightStrip, rightGray, Imgproc.COLOR_BGR2GRAY)
        leftStrip.release()
        rightStrip.release()

        val leftExtended = Mat()
        Core.hconcat(listOf(leftGray, leftGray), leftExtended)

        val matchResult = Mat()
        Imgproc.matchTemplate(leftExtended, rightGray, matchResult, Imgproc.TM_CCOEFF_NORMED)
        val mmr = Core.minMaxLoc(matchResult)
        val offsetX = mmr.maxLoc.x.roundToInt()
        val dx = (seamWidth - offsetX).coerceIn(0, seamWidth)

        leftGray.release()
        rightGray.release()
        leftExtended.release()
        matchResult.release()

        if (dx <= 0) {
            Log.d(TAG, "Seam alignment: dx<=0 (offsetX=$offsetX), оставляем оригинал")
            return panorama.clone()
        }

        val cropWidth = width - dx
        val cropped = Mat(panorama, Rect(0, 0, cropWidth, height)).clone()
        Log.d(TAG, "Seam alignment: cropped $dx px to align edges (offsetX=$offsetX)")
        return cropped
    }

    private fun saveMatAsUri(context: Context, mat: Mat): Uri? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "PANO360_STITCHED_${timestamp}.jpg"

            val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, bitmap)

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Panorama360")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val itemUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)) {
                            throw IllegalStateException("Не удалось сохранить панораму в MediaStore")
                        }
                    } ?: run {
                        resolver.delete(itemUri, null, null)
                        throw IllegalStateException("Не удалось открыть OutputStream для MediaStore")
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)

                    itemUri
                } else {
                    throw IllegalStateException("Не удалось создать запись в MediaStore")
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val panoDir = File(picturesDir, "Panorama360").apply {
                    if (!exists()) mkdirs()
                }
                val imageFile = File(panoDir, fileName)

                FileOutputStream(imageFile).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)) {
                        throw IllegalStateException("Не удалось сохранить панораму в файл")
                    }
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(imageFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )

                Uri.fromFile(imageFile)
            }

            bitmap.recycle()
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения панорамы: ${e.message}", e)
            null
        }
    }
}
