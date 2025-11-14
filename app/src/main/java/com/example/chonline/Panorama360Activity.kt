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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

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
    val coroutineScope = rememberCoroutineScope()
    val alignmentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val rotationSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Съёмка
    var shotCount by remember { mutableStateOf(0) }
    var capturedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var previousImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showAlignmentSheet by remember { mutableStateOf(false) }
    var alignmentFirstBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var alignmentLastBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var alignmentOffsetX by remember { mutableStateOf(0f) }
    var alignmentOffsetY by remember { mutableStateOf(0f) }
    var alignmentLoading by remember { mutableStateOf(false) }
    var alignmentError by remember { mutableStateOf<String?>(null) }

    fun clearAlignmentPreview() {
        alignmentFirstBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        alignmentLastBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        alignmentFirstBitmap = null
        alignmentLastBitmap = null
        alignmentLoading = false
        alignmentError = null
        alignmentOffsetX = 0f
        alignmentOffsetY = 0f
    }

    // Геометрия видимой части превью камеры (после FIT_CENTER)
    var cameraContentWidthPx by remember { mutableStateOf(0) }
    var cameraContentHeightPx by remember { mutableStateOf(0) }
    var cameraOffsetTopPx by remember { mutableStateOf(0) }
    var pitchDegrees by remember { mutableFloatStateOf(0f) }

    DisposableEffect(rotationSensor) {
        if (rotationSensor == null) {
            return@DisposableEffect onDispose { }
        }

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                // pitch = rotation around X axis, convert to degrees
                val pitch = orientationAngles[1] * (180f / Math.PI.toFloat())
                pitchDegrees = pitch
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // no-op
            }
        }

        sensorManager.registerListener(
            listener,
            rotationSensor,
            SensorManager.SENSOR_DELAY_UI
        )

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Подгружаем последний снимок
    LaunchedEffect(capturedImages.size) {
        if (capturedImages.isNotEmpty() && shotCount > 0) {
            val previousUri = capturedImages.last()
            previousImageBitmap = withContext(Dispatchers.IO) {
                val bitmap = loadBitmapFromUri(context, previousUri)
                bitmap?.let { ensurePortraitBitmap(it) }
            }
        }
    }

    if (showAlignmentSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showAlignmentSheet = false
                clearAlignmentPreview()
            },
            sheetState = alignmentSheetState
        ) {
            PanoramaAlignmentSheetContent(
                alignmentFirstBitmap = alignmentFirstBitmap,
                alignmentLastBitmap = alignmentLastBitmap,
                alignmentHorizontalOffset = alignmentOffsetX,
                alignmentVerticalOffset = alignmentOffsetY,
                alignmentLoading = alignmentLoading,
                alignmentError = alignmentError,
                onHorizontalOffsetChange = { alignmentOffsetX = it },
                onVerticalOffsetChange = { alignmentOffsetY = it },
                onStartStitch = {
                    val workId = UUID.randomUUID()
                    val request = OneTimeWorkRequestBuilder<PanoramaStitchWorker>()
                        .setId(workId)
                        .setInputData(
                            PanoramaStitchWorker.createInputData(
                                imageUris = capturedImages,
                                objectId = objectId,
                                alignmentOffsetX = alignmentOffsetX,
                                alignmentOffsetY = alignmentOffsetY
                            )
                        )
                        .build()

                    workManager.enqueue(request)
                    context.showTopToast("Панорама обрабатывается в фоне")
                    clearAlignmentPreview()
                    showAlignmentSheet = false
                    onPanoramaScheduled(workId)
                },
                onKeepShots = {
                    showAlignmentSheet = false
                    clearAlignmentPreview()
                    context.showTopToast("Оставляем кадры без склейки")
                },
                onCancelAll = {
                    val imagesToDelete = capturedImages
                    clearAlignmentPreview()
                    coroutineScope.launch {
                        deleteCapturedImages(context, imagesToDelete)
                    }
                    capturedImages = emptyList()
                    shotCount = 0
                    previousImageBitmap?.let { bitmap ->
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    }
                    previousImageBitmap = null
                    showAlignmentSheet = false
                    isProcessing = false
                    context.showTopToast("Съёмка панорамы отменена")
                }
            )
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

                    if (cameraContentHeightPx > 0 && cameraContentWidthPx > 0) {
                        HorizonLevelOverlay(
                            cameraContentHeightPx = cameraContentHeightPx,
                            cameraOffsetTopPx = cameraOffsetTopPx,
                            pitchDegrees = pitchDegrees,
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
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = {
                            if (shotCount < 2) {
                                context.showTopToast("Нужно минимум два кадра")
                                return@Button
                            }

                            val firstUri = capturedImages.firstOrNull()
                            val lastUri = capturedImages.lastOrNull()
                            if (firstUri == null || lastUri == null) {
                                context.showTopToast("Не удалось подготовить предпросмотр")
                                return@Button
                            }

                            alignmentOffsetX = 0f
                            alignmentOffsetY = 0f
                            alignmentError = null
                            alignmentLoading = true
                            showAlignmentSheet = true

                            coroutineScope.launch {
                                val first = withContext(Dispatchers.IO) {
                                    loadBitmapFromUri(context, firstUri)?.let { ensurePortraitBitmap(it) }
                                }
                                val last = withContext(Dispatchers.IO) {
                                    loadBitmapFromUri(context, lastUri)?.let { ensurePortraitBitmap(it) }
                                }
                                alignmentFirstBitmap = first
                                alignmentLastBitmap = last
                                alignmentLoading = false
                                if (first == null || last == null) {
                                    alignmentError = "Не удалось загрузить кадры для сверки"
                                }
                            }
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
 * Лазерный уровень: статичная линия по центру и динамическая линия по наклону устройства.
 */
@Composable
private fun HorizonLevelOverlay(
    cameraContentHeightPx: Int,
    cameraOffsetTopPx: Int,
    pitchDegrees: Float,
    modifier: Modifier = Modifier,
    maxTiltDegrees: Float = 8f
) {
    if (cameraContentHeightPx <= 0) return
    val density = LocalDensity.current
    val centerLineY = cameraOffsetTopPx + cameraContentHeightPx / 2f
    val normalizedTilt = (pitchDegrees / maxTiltDegrees).coerceIn(-1f, 1f)
    val tiltOffsetPx = normalizedTilt * (cameraContentHeightPx / 2f)

    Canvas(modifier = modifier) {
        val widthPx = size.width
        val centerYPx = centerLineY.coerceIn(0f, size.height)
        drawLine(
            color = Color.White.copy(alpha = 0.55f),
            start = Offset(0f, centerYPx),
            end = Offset(widthPx, centerYPx),
            strokeWidth = 2.dp.toPx()
        )

        val dynamicY = (centerLineY - tiltOffsetPx).coerceIn(0f, size.height)
        drawLine(
            color = Color.Red.copy(alpha = 0.8f),
            start = Offset(0f, dynamicY),
            end = Offset(widthPx, dynamicY),
            strokeWidth = 2.dp.toPx()
        )
    }
}

/**
 * Предпросмотр совмещения первого и последнего кадров перед склейкой.
 */
@Composable
private fun PanoramaAlignmentSheetContent(
    alignmentFirstBitmap: Bitmap?,
    alignmentLastBitmap: Bitmap?,
    alignmentHorizontalOffset: Float,
    alignmentVerticalOffset: Float,
    alignmentLoading: Boolean,
    alignmentError: String?,
    onHorizontalOffsetChange: (Float) -> Unit,
    onVerticalOffsetChange: (Float) -> Unit,
    onStartStitch: () -> Unit,
    onKeepShots: () -> Unit,
    onCancelAll: () -> Unit
) {
    val canPreview = alignmentFirstBitmap != null && alignmentLastBitmap != null && alignmentError == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Совместите первый и последний кадры",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Сместите последний кадр по горизонтали и вертикали, чтобы совпали линии горизонта и главный объект, затем выберите действие.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        when {
            alignmentLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Подготавливаем предпросмотр…")
                }
            }
            alignmentError != null -> {
                Text(
                    text = alignmentError,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            alignmentFirstBitmap != null && alignmentLastBitmap != null -> {
                PanoramaAlignmentPreview(
                    firstBitmap = alignmentFirstBitmap,
                    lastBitmap = alignmentLastBitmap,
                    horizontalOffset = alignmentHorizontalOffset,
                    verticalOffset = alignmentVerticalOffset
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Slider(
                        value = alignmentHorizontalOffset,
                        onValueChange = onHorizontalOffsetChange,
                        valueRange = -1f..1f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = DarkGreen,
                            thumbColor = DarkGreen
                        )
                    )
                    Text(
                        text = "Горизонталь: ${String.format(Locale.getDefault(), "%.2f", alignmentHorizontalOffset)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = alignmentVerticalOffset,
                        onValueChange = onVerticalOffsetChange,
                        valueRange = -1f..1f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = DarkGreen,
                            thumbColor = DarkGreen
                        )
                    )
                    Text(
                        text = "Вертикаль: ${String.format(Locale.getDefault(), "%.2f", alignmentVerticalOffset)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStartStitch,
                enabled = canPreview && !alignmentLoading,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkGreen,
                    contentColor = White1
                )
            ) {
                Text("Склеить панораму")
            }

            OutlinedButton(
                onClick = onKeepShots,
                enabled = !alignmentLoading,
                modifier = Modifier.weight(1f)
            ) {
                Text("Оставить как есть")
            }
        }

        TextButton(
            onClick = onCancelAll,
            enabled = !alignmentLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Отменить съёмку")
        }
    }
}

@Composable
private fun PanoramaAlignmentPreview(
    firstBitmap: Bitmap,
    lastBitmap: Bitmap,
    horizontalOffset: Float,
    verticalOffset: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 320.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 320.dp)
        ) {
            val firstImage = remember(firstBitmap) { firstBitmap.asImageBitmap() }
            val lastImage = remember(lastBitmap) { lastBitmap.asImageBitmap() }
            val maxWidthPx = constraints.maxWidth.takeIf { it > 0 }?.toFloat() ?: 0f
            val maxHeightPx = constraints.maxHeight.takeIf { it > 0 }?.toFloat() ?: 0f
            val translationX = horizontalOffset.coerceIn(-1f, 1f) * (maxWidthPx / 2f)
            val translationY = verticalOffset.coerceIn(-1f, 1f) * (maxHeightPx / 2f)

            Image(
                bitmap = firstImage,
                contentDescription = "Первый кадр",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Image(
                bitmap = lastImage,
                contentDescription = "Последний кадр",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.translationX = translationX
                        this.translationY = translationY
                        alpha = 0.65f
                    }
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                val centerY = size.height / 2f
                drawLine(
                    color = Color.Red.copy(alpha = 0.7f),
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = strokeWidth
                )
                val centerX = size.width / 2f
                drawLine(
                    color = Color.Red.copy(alpha = 0.4f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = strokeWidth
                )
            }
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

private fun ensurePortraitBitmap(bitmap: Bitmap): Bitmap {
    return if (bitmap.width > bitmap.height) {
        val matrix = Matrix().apply { postRotate(90f) }
        Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        ).also {
            bitmap.recycle()
        }
    } else {
        bitmap
    }
}

private suspend fun deleteCapturedImages(context: Context, uris: List<Uri>) {
    withContext(Dispatchers.IO) {
        uris.forEach { uri ->
            runCatching {
                val rows = context.contentResolver.delete(uri, null, null)
                Log.d("Panorama360", "Удаление кадра $uri -> $rows")
            }.onFailure { error ->
                Log.w("Panorama360", "Не удалось удалить временный кадр $uri: ${error.message}")
            }
        }
    }
}
