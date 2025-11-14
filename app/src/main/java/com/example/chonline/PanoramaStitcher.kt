package com.example.chonline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

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
        private const val OVERLAP_FRACTION = 0.25f
        private const val MAX_VERTICAL_SHIFT = 40
        
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
        context: Context,
        alignmentOffsetX: Float = 0f,
        alignmentOffsetY: Float = 0f
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

            val preparedBitmaps = applyAlignmentOffsets(
                bitmaps = resized,
                offsetXFraction = alignmentOffsetX,
                offsetYFraction = alignmentOffsetY
            )

            val panoramaMat = stitchWithStitcher(preparedBitmaps) ?: run {
                Log.w(TAG, "OpenCV Stitcher не справился, возвращаемся к простому наложению")
                stitchWithLinearOverlap(preparedBitmaps)
            }

            preparedBitmaps.forEach { it.recycle() }

            val croppedPanorama = cropBlackBorders(panoramaMat)
            panoramaMat.release()

            Log.d(TAG, "Панорама успешно склеена. Размер: ${croppedPanorama.width()}x${croppedPanorama.height()}")

            val outputUri = saveMatAsUri(context, croppedPanorama)
            croppedPanorama.release()

            Log.d(TAG, "Панорама успешно сохранена: $outputUri")
            outputUri
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при склейке панорамы: ${e.message}", e)
            null
        }
    }

    private fun stitchWithStitcher(bitmaps: List<Bitmap>): Mat? {
        if (bitmaps.size < 2) return null

        val stitcherClass = try {
            Class.forName("org.opencv.stitching.Stitcher")
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "OpenCV Stitcher недоступен в сборке: ${e.message}")
            return null
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "OpenCV Stitcher недоступен: ${e.message}")
            return null
        }

        val sourceMats = ArrayList<Mat>(bitmaps.size)
        val panorama = Mat()

        return try {
            bitmaps.forEachIndexed { index, bitmap ->
                val rgba = Mat()
                Utils.bitmapToMat(bitmap, rgba)
                val bgr = Mat()
                Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
                rgba.release()
                sourceMats.add(bgr)
                Log.d(TAG, "Stitcher input frame #$index -> ${bgr.cols()}x${bgr.rows()}")
            }

            val panoramaMode = stitcherClass.getField("PANORAMA").getInt(null)
            val createMethod = stitcherClass.getMethod("create", Int::class.javaPrimitiveType)
            val stitcherInstance = createMethod.invoke(null, panoramaMode)

            runCatching {
                stitcherClass.getMethod("setWaveCorrection", Boolean::class.javaPrimitiveType)
                    .invoke(stitcherInstance, true)
            }
            runCatching {
                stitcherClass.getMethod("setPanoConfidenceThresh", Double::class.javaPrimitiveType)
                    .invoke(stitcherInstance, 0.7)
            }

            val stitchMethod = stitcherClass.getMethod("stitch", java.util.List::class.java, Mat::class.java)
            val status = stitchMethod.invoke(stitcherInstance, sourceMats, panorama) as? Int ?: -1

            val okStatus = stitcherClass.getField("OK").getInt(null)

            if (status == okStatus) {
                Log.d(TAG, "OpenCV Stitcher вернул OK: ${panorama.cols()}x${panorama.rows()}")
                panorama
            } else {
                Log.w(TAG, "OpenCV Stitcher завершился со статусом $status")
                panorama.release()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка OpenCV Stitcher: ${e.message}", e)
            panorama.release()
            null
        } finally {
            sourceMats.forEach { it.release() }
        }
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

    private fun applyAlignmentOffsets(
        bitmaps: List<Bitmap>,
        offsetXFraction: Float,
        offsetYFraction: Float
    ): List<Bitmap> {
        if (bitmaps.size < 2) return bitmaps
        val clampedX = offsetXFraction.coerceIn(-1f, 1f)
        val clampedY = offsetYFraction.coerceIn(-1f, 1f)
        if (clampedX == 0f && clampedY == 0f) return bitmaps

        val lastIndex = bitmaps.lastIndex
        if (lastIndex < 1) return bitmaps

        val lastBitmap = bitmaps[lastIndex]
        val width = lastBitmap.width
        val height = lastBitmap.height
        if (width <= 0 || height <= 0) return bitmaps

        val dx = (clampedX * width / 2f).roundToInt()
        val dy = (clampedY * height / 2f).roundToInt()
        if (dx == 0 && dy == 0) return bitmaps

        val config = lastBitmap.config ?: Bitmap.Config.ARGB_8888
        val shifted = Bitmap.createBitmap(width, height, config)
        val canvas = Canvas(shifted)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

        canvas.drawBitmap(lastBitmap, dx.toFloat(), dy.toFloat(), paint)

        if (dx != 0) {
            val fillWidth = abs(dx).coerceAtMost(width)
            val srcX = if (dx > 0) lastBitmap.width - 1 else 0
            val srcRect = android.graphics.Rect(srcX, 0, srcX + 1, lastBitmap.height)
            val dstRect = if (dx > 0) {
                android.graphics.Rect(0, 0, fillWidth, height)
            } else {
                android.graphics.Rect(width - fillWidth, 0, width, height)
            }
            canvas.drawBitmap(lastBitmap, srcRect, dstRect, paint)
        }

        if (dy != 0) {
            val fillHeight = abs(dy).coerceAtMost(height)
            val srcY = if (dy > 0) lastBitmap.height - 1 else 0
            val srcRect = android.graphics.Rect(0, srcY, lastBitmap.width, srcY + 1)
            val dstRect = if (dy > 0) {
                android.graphics.Rect(0, 0, width, fillHeight)
            } else {
                android.graphics.Rect(0, height - fillHeight, width, height)
            }
            canvas.drawBitmap(lastBitmap, srcRect, dstRect, paint)
        }

        lastBitmap.recycle()

        val mutable = bitmaps.toMutableList()
        mutable[lastIndex] = shifted
        return mutable
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
        Imgproc.threshold(gray, gray, 1.0, 255.0, Imgproc.THRESH_BINARY)

        val height = gray.rows()
        val width = gray.cols()

        var top = 0
        while (top < height) {
            val row = gray.row(top)
            val nonZero = Core.countNonZero(row)
            row.release()
            if (nonZero > 0) break
            top++
        }

        var bottom = height - 1
        while (bottom >= top) {
            val row = gray.row(bottom)
            val nonZero = Core.countNonZero(row)
            row.release()
            if (nonZero > 0) break
            bottom--
        }

        var left = 0
        while (left < width) {
            val col = gray.col(left)
            val nonZero = Core.countNonZero(col)
            col.release()
            if (nonZero > 0) break
            left++
        }

        var right = width - 1
        while (right >= left) {
            val col = gray.col(right)
            val nonZero = Core.countNonZero(col)
            col.release()
            if (nonZero > 0) break
            right--
        }

        gray.release()

        if (top >= bottom || left >= right) {
            return src.clone()
        }

        val rect = Rect(left, top, right - left + 1, bottom - top + 1)
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

    private fun saveMatAsUri(context: Context, mat: Mat): Uri? {
        return try {
            val timestamp = System.currentTimeMillis()
            val formattedTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))
            val fileName = "PANO360_STITCHED_${formattedTimestamp}.jpg"
            val albumName = "Camera"
            val dcimCameraPath = "${Environment.DIRECTORY_DCIM}/$albumName/"

            val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, bitmap)

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, dcimCameraPath)
                    put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, albumName)
                    put(
                        MediaStore.Images.Media.BUCKET_ID,
                        dcimCameraPath.lowercase(Locale.getDefault()).hashCode()
                    )
                    put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
                    put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                    put(MediaStore.Images.Media.WIDTH, bitmap.width)
                    put(MediaStore.Images.Media.HEIGHT, bitmap.height)
                    put(MediaStore.Images.Media.ORIENTATION, 0)
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
                val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                val cameraDir = File(dcimDir, albumName).apply {
                    if (!exists()) mkdirs()
                }
                val imageFile = File(cameraDir, fileName)

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
