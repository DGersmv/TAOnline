package com.example.chonline

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.chonline.ui.theme.DarkGreen
import com.example.chonline.ui.theme.White1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

private const val ESTIMATED_STEP_DEGREES = 32f
private const val FULL_ROTATION_DEGREES = 360f

/**
 * Activity для съемки 360 панорам с визуальным совмещением.
 */
class Panorama360Activity : ComponentActivity() {

    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val objectId = intent.getStringExtra("OBJECT_ID") ?: "0"
        val objectTitle = intent.getStringExtra("OBJECT_TITLE") ?: "Панорама 360"

        setContent {
            Panorama360Screen(
                objectId = objectId,
                objectTitle = objectTitle,
                onPanoramaScheduled = { workId ->
                    val resultIntent = Intent().apply {
                        putExtra("PANORAMA_WORK_ID", workId.toString())
                        putExtra("OBJECT_ID", objectId)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                },
                onCancel = { finish() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Panorama360Screen(
    objectId: String,
    objectTitle: String,
    onPanoramaScheduled: (UUID) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContext = context.applicationContext
    val workManager = remember { WorkManager.getInstance(appContext) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Съёмка
    var shotCount by remember { mutableStateOf(0) }
    var capturedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var previousImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Геометрия видимой части превью камеры (после FIT_CENTER)
    var cameraContentWidthPx by remember { mutableStateOf(0) }
    var cameraContentHeightPx by remember { mutableStateOf(0) }
    var cameraOffsetTopPx by remember { mutableStateOf(0) }

    // Подгружаем последний снимок
    LaunchedEffect(capturedImages.size) {
        if (capturedImages.isNotEmpty() && shotCount > 0) {
            val previousUri = capturedImages.last()
            previousImageBitmap = withContext(Dispatchers.IO) {
                val bitmap = loadBitmapFromUri(context, previousUri)
                bitmap?.let {
                    if (it.width > it.height) {
                        val matrix = Matrix().apply { postRotate(90f) }
                        val rotated = Bitmap.createBitmap(
                            it, 0, 0, it.width, it.height, matrix, true
                        )
                        it.recycle()
                        rotated
                    } else {
                        it
                    }
                }
            }
        }
    }

    // Инициализация камеры
    val initializeCamera: (PreviewView) -> Unit = { view ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageCaptureInstance = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val previewInstance = Preview.Builder().build().apply {
                setSurfaceProvider(view.surfaceProvider)
            }

            imageCapture = imageCaptureInstance

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewInstance,
                    imageCaptureInstance
                )
            } catch (e: Exception) {
                Log.e("Panorama360", "Ошибка привязки камеры: ${e.message}")
                context.showTopToast("Ошибка инициализации камеры")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Съемка 360° панорамы") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkGreen,
                    titleContentColor = White1
                ),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = White1
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Верхняя зона: previous (1/3) + camera (2/3)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 16.dp)
            ) {
                // Область камеры с наложением предыдущего снимка
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    CameraPreviewFrame(
                        onPreviewViewCreated = initializeCamera,
                        onLayoutCalculated = { viewW, viewH, contentW, contentH, offsetTop ->
                            cameraContentWidthPx = contentW
                            cameraContentHeightPx = contentH
                            cameraOffsetTopPx = offsetTop

                            Log.d(
                                "Panorama360",
                                "PreviewLayout: view=${viewW}x$viewH, content=${contentW}x$contentH, top=$offsetTop"
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (
                        shotCount > 0 &&
                        previousImageBitmap != null &&
                        cameraContentHeightPx > 0 &&
                        cameraContentWidthPx > 0
                    ) {
                        PreviousShotOverlay(
                            image = previousImageBitmap!!.asImageBitmap(),
                            cameraContentWidthPx = cameraContentWidthPx,
                            cameraContentHeightPx = cameraContentHeightPx,
                            cameraOffsetTopPx = cameraOffsetTopPx,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Информационная панель
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                )
            ) {
                val approxAngle = ((shotCount - 1).coerceAtLeast(0)) * ESTIMATED_STEP_DEGREES
                val progressAngle = min(approxAngle, FULL_ROTATION_DEGREES)
                val progress = (progressAngle / FULL_ROTATION_DEGREES).coerceIn(0f, 1f)

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = if (shotCount == 0)
                            "Наведите камеру на начальную точку и нажмите 'Начать'"
                        else
                            "Снимков: $shotCount. Продолжайте поворот, пока не замкнёте круг, затем нажмите 'Завершить'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (shotCount == 0)
                            MaterialTheme.colorScheme.onSurface
                        else
                            DarkGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ориентировочный охват: ~${progressAngle.roundToInt()}° из 360°",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Кнопка съёмки / индикатор
            if (!isProcessing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val capture = imageCapture
                            if (capture != null) {
                                isProcessing = true
                                capturePanoramaShot(
                                    context = context,
                                    imageCapture = capture,
                                    shotNumber = shotCount
                                ) { uri ->
                                    if (uri != null) {
                                        capturedImages = capturedImages + uri
                                        shotCount++
                                        context.showTopToast("Снимок $shotCount готов")
                                        isProcessing = false
                                    } else {
                                        context.showTopToast("Ошибка при съемке")
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = !isProcessing && imageCapture != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkGreen,
                            contentColor = White1
                        )
                    ) {
                        Text(
                            text = if (shotCount == 0) "Начать" else "Снять",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Button(
                        onClick = {
                            if (shotCount < 2) {
                                context.showTopToast("Нужно минимум два кадра")
                                return@Button
                            }

                            isProcessing = true

                            val workId = UUID.randomUUID()
                            val request = OneTimeWorkRequestBuilder<PanoramaStitchWorker>()
                                .setId(workId)
                                .setInputData(
                                    PanoramaStitchWorker.createInputData(
                                        imageUris = capturedImages,
                                        objectId = objectId
                                    )
                                )
                                .build()

                            workManager.enqueue(request)
                            context.showTopToast("Панорама обрабатывается в фоне")
                            onPanoramaScheduled(workId)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        enabled = shotCount >= 2,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = DarkGreen
                        )
                    ) {
                        Text(
                            text = "Завершить",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            } else {
                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(56.dp),
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGreen,
                        contentColor = White1
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = White1
                        )
                        Text("Обработка...")
                    }
                }
            }
        }
    }
}

/**
 * Полупрозрачный оверлей: правый фрагмент предыдущего кадра поверх превью.
 */
@Composable
private fun PreviousShotOverlay(
    image: ImageBitmap,
    cameraContentWidthPx: Int,
    cameraContentHeightPx: Int,
    cameraOffsetTopPx: Int,
    overlayFraction: Float = 0.25f,
    overlayAlpha: Float = 0.6f,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    if (cameraContentWidthPx <= 0 || cameraContentHeightPx <= 0) return

    val overlayWidthPx = (cameraContentWidthPx * overlayFraction)
        .roundToInt()
        .coerceIn(1, cameraContentWidthPx)

    val scale = cameraContentHeightPx.toFloat() / image.height.toFloat()
    val srcVisibleWidth = (overlayWidthPx / scale)
        .roundToInt()
        .coerceAtMost(image.width)
        .coerceAtLeast(1)
    val srcX = (image.width - srcVisibleWidth).coerceAtLeast(0)

    val overlayWidthDp = with(density) { overlayWidthPx.toDp() }
    val overlayHeightDp = with(density) { cameraContentHeightPx.toDp() }
    val verticalOffsetPx = cameraOffsetTopPx.coerceAtLeast(0)

    LaunchedEffect(
        cameraContentWidthPx,
        cameraContentHeightPx,
        overlayWidthPx,
        srcVisibleWidth,
        srcX,
        verticalOffsetPx
    ) {
        Log.d(
            "Panorama360",
            "Overlay debug: image=${image.width}x${image.height}, content=${cameraContentWidthPx}x$cameraContentHeightPx, overlay=${overlayWidthPx}x${cameraContentHeightPx}, srcX=$srcX, srcWidth=$srcVisibleWidth, offsetY=$verticalOffsetPx"
        )
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, verticalOffsetPx) }
                .width(overlayWidthDp)
                .height(overlayHeightDp)
        ) {
            drawImage(
                image = image,
                srcOffset = IntOffset(srcX, 0),
                srcSize = IntSize(srcVisibleWidth, image.height),
                dstOffset = IntOffset(0, 0),
                dstSize = IntSize(overlayWidthPx, cameraContentHeightPx),
                alpha = overlayAlpha
            )
        }
    }
}

/**
 * Превью камеры. Считает реальную видимую область для FIT_CENTER и отдаёт наружу.
 */
@Composable
private fun CameraPreviewFrame(
    onPreviewViewCreated: (PreviewView) -> Unit,
    onLayoutCalculated: (
        viewWidth: Int,
        viewHeight: Int,
        contentWidth: Int,
        contentHeight: Int,
        offsetTop: Int
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                    Log.d(
                        "Panorama360",
                        "PreviewView layout bounds=(${right - left}x${bottom - top}), pos=($left,$top)"
                    )
                }
                onPreviewViewCreated(this)
            }
        },
        modifier = modifier.onSizeChanged { size ->
            val viewW = size.width
            val viewH = size.height
            if (viewW == 0 || viewH == 0) return@onSizeChanged

            // Портрет: содержимое 3:4 (после поворота).
            val isPortrait = viewH >= viewW
            val contentRatio = if (isPortrait) 3f / 4f else 4f / 3f

            val heightByWidth = viewW / contentRatio
            val widthByHeight = viewH * contentRatio

            val contentW: Int
            val contentH: Int
            val offsetTop: Int

            if (heightByWidth <= viewH) {
                // Ограничиваем по ширине, сверху/снизу появляются поля
                contentW = viewW
                contentH = heightByWidth.toInt()
                offsetTop = ((viewH - contentH) / 2f).toInt()
            } else {
                // Ограничиваем по высоте
                contentH = viewH
                contentW = widthByHeight.toInt()
                offsetTop = 0
            }

            onLayoutCalculated(viewW, viewH, contentW, contentH, offsetTop)
        }
    )
}

/**
 * Загрузка Bitmap из Uri.
 */
private suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e("Panorama360", "Ошибка загрузки изображения: ${e.message}")
            null
        }
    }

/**
 * Сохранение одного кадра.
 */
private fun capturePanoramaShot(
    context: Context,
    imageCapture: ImageCapture,
    shotNumber: Int,
    callback: (Uri?) -> Unit
) {
    val timeStamp =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir =
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
    val imageFile =
        File.createTempFile("PANO360_SHOT${shotNumber}_${timeStamp}_", ".jpg", storageDir)

    val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    imageFile
                )
                Log.d("Panorama360", "Снимок $shotNumber сохранен: ${imageFile.absolutePath}")
                callback(savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("Panorama360", "Ошибка при съемке $shotNumber: ${exception.message}")
                callback(null)
            }
        }
    )
}
