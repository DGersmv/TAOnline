package com.example.chonline

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.example.chonline.ui.theme.DarkGreen
import com.example.chonline.ui.theme.White1
import com.example.chonline.MediaPickerFilter
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PhotoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val objectId = intent.getStringExtra("OBJECT_ID")
            ?: intent.getStringExtra("GROUP_ID")
            ?: getDeepLinkObjectId()
            ?: "0"

        val userId = intent.getStringExtra("USER_ID") ?: getDeepLinkUserId()
        val objectTitle = intent.getStringExtra("OBJECT_TITLE") ?: "Объект $objectId"

        Log.d("PhotoActivity", "Object ID: $objectId, User ID: $userId, Title: $objectTitle")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }

        setContent {
            PhotoScreen(objectId, userId, objectTitle)
        }
    }

    private fun getDeepLinkObjectId(): String? {
        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        return prefs.getString("deep_link_object_id", null)
    }

    private fun getDeepLinkUserId(): String? {
        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        return prefs.getString("deep_link_user_id", null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScreen(objectId: String, userId: String? = null, objectTitle: String = "Объект") {
    val context = LocalContext.current
    val uploadItems = remember { mutableStateListOf<UploadMediaItem>() }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCaptureType by remember { mutableStateOf<UploadMediaType?>(null) }
    var pendingPickerType by remember { mutableStateOf<UploadMediaType?>(null) }
    val appContext = context.applicationContext
    val workManager = remember { WorkManager.getInstance(appContext) }
    val queuePrefs = remember { context.getSharedPreferences(PANORAMA_QUEUE_PREFS, Context.MODE_PRIVATE) }
    val pendingPanoramaWorkIds = remember { mutableStateListOf<String>() }

    fun persistUploadItems() {
        val jsonArray = org.json.JSONArray().apply {
            uploadItems.forEach { item ->
                put(
                    org.json.JSONObject().apply {
                        put("uri", item.uri.toString())
                        put("type", item.type.name)
                    }
                )
            }
        }
        queuePrefs.edit().putString(KEY_STORED_UPLOAD_ITEMS, jsonArray.toString()).apply()
    }

    fun persistPendingWorkIds() {
        queuePrefs.edit()
            .putStringSet(KEY_PENDING_WORK_IDS, pendingPanoramaWorkIds.toSet())
            .apply()
    }

    fun addUploadItem(
        uri: Uri,
        type: UploadMediaType,
        dedupe: Boolean = false
    ): Boolean {
        if (dedupe && uploadItems.any { it.uri == uri }) {
            Log.d(
                "PhotoActivity",
                "Пропускаем дублирование файла в очереди: $uri (${type.name})"
            )
            return false
        }
        uploadItems.add(UploadMediaItem(uri, type))
        persistUploadItems()
        return true
    }

    LaunchedEffect(Unit) {
        // Восстановить заголовок загрузок
        val stored = queuePrefs.getString(KEY_STORED_UPLOAD_ITEMS, null)
        if (!stored.isNullOrBlank()) {
            runCatching {
                val array = org.json.JSONArray(stored)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val uriString = obj.optString("uri", null)
                    val typeName = obj.optString("type", UploadMediaType.PHOTO.name)
                    if (!uriString.isNullOrBlank()) {
                        val uri = Uri.parse(uriString)
                        val type = runCatching { UploadMediaType.valueOf(typeName) }.getOrDefault(UploadMediaType.PHOTO)
                        if (uploadItems.none { it.uri == uri }) {
                            uploadItems.add(UploadMediaItem(uri, type))
                        }
                    }
                }
            }
        }

        // Восстановить отложенные работы
        val storedIds = queuePrefs.getStringSet(KEY_PENDING_WORK_IDS, emptySet()) ?: emptySet()
        pendingPanoramaWorkIds.addAll(storedIds)

        // Очистить завершённые/просроченные задачи
        withContext(Dispatchers.IO) {
            pendingPanoramaWorkIds.toList().forEach { idString ->
                val workId = runCatching { UUID.fromString(idString) }.getOrNull()
                if (workId == null) {
                    pendingPanoramaWorkIds.remove(idString)
                    return@forEach
                }
                val info = runCatching { workManager.getWorkInfoById(workId).get() }.getOrNull()
                if (info == null || info.state.isFinished) {
                    pendingPanoramaWorkIds.remove(idString)
                }
            }
        }
        persistPendingWorkIds()
    }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                context.showTopToast("Камера не доступна: разрешение не предоставлено")
            }
        }
    )

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resolvedType: UploadMediaType? = pendingPickerType
            ?: result.data?.getStringExtra(MediaPickerActivity.RESULT_FILTER)?.let { filterName ->
                when (runCatching { MediaPickerFilter.valueOf(filterName) }.getOrNull()) {
                    MediaPickerFilter.PHOTO -> UploadMediaType.PHOTO
                    MediaPickerFilter.PANORAMA -> UploadMediaType.PANORAMA
                    null -> null
                }
            }

        if (result.resultCode == ComponentActivity.RESULT_OK && resolvedType != null) {
            val uriList: List<Uri> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableArrayListExtra(MediaPickerActivity.RESULT_URIS, Uri::class.java).orEmpty()
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableArrayListExtra<Uri>(MediaPickerActivity.RESULT_URIS).orEmpty()
            }

            if (uriList.isNotEmpty()) {
                var addedCount = 0
                uriList.forEach { uri ->
                    val added = addUploadItem(uri, resolvedType, dedupe = true)
                    if (added) {
                        addedCount++
                    }
                }
                if (addedCount > 0) {
                    val label = if (resolvedType == UploadMediaType.PANORAMA) "панорам" else "фото"
                    context.showTopToast("Добавлено $addedCount $label")
                }
                Log.d("PhotoActivity", "Добавлены файлы (${resolvedType.name}) из медиапикера: $uriList")
            } else {
                context.showTopToast("Файлы не выбраны")
                Log.w("PhotoActivity", "Медиапикер вернул пустой список")
            }
        } else {
            Log.w(
                "PhotoActivity",
                "Медиапикер завершился без результата или тип не определён (type=$resolvedType, code=${result.resultCode})"
            )
        }

        pendingPickerType = null
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val targetUri = tempPhotoUri
        val type = pendingCaptureType
        if (success && targetUri != null && type != null) {
            uploadItems.add(UploadMediaItem(targetUri, type))
            persistUploadItems()
            Log.d("PhotoActivity", "Добавлен файл (${type.name}) с камеры: $targetUri")
        } else if (!success && targetUri != null) {
            context.contentResolver.delete(targetUri, null, null)
            context.showTopToast("Ошибка при съёмке файла")
            Log.e("PhotoActivity", "Съёмка отменена или завершилась с ошибкой для $targetUri")
        }
        tempPhotoUri = null
        pendingCaptureType = null
    }

    // Launcher для получения результата от Panorama360Activity
    val panorama360Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val workIdString = result.data?.getStringExtra("PANORAMA_WORK_ID")
            if (!workIdString.isNullOrBlank()) {
                if (!pendingPanoramaWorkIds.contains(workIdString)) {
                    pendingPanoramaWorkIds.add(workIdString)
                    persistPendingWorkIds()
                    Log.d("PhotoActivity", "Фоновая обработка панорамы запущена: $workIdString")
                }
                context.showTopToast("Панорама обрабатывается в фоне")
            }
            val panoramaUri = result.data?.getStringExtra("PANORAMA_URI")
            if (panoramaUri != null) {
                val uri = Uri.parse(panoramaUri)
                val added = addUploadItem(uri, UploadMediaType.PANORAMA, dedupe = true)
                if (added) {
                    Log.d("PhotoActivity", "Добавлена 360 панорама: $uri")
                    context.showTopToast("360° панорама добавлена")
                } else {
                    Log.d("PhotoActivity", "Панорама уже присутствует в очереди: $uri")
                }
            }
        }
    }

    pendingPanoramaWorkIds.toList().forEach { idString ->
        LaunchedEffect(idString) {
            val workId = runCatching { UUID.fromString(idString) }.getOrNull()
            if (workId == null) {
                pendingPanoramaWorkIds.remove(idString)
                return@LaunchedEffect
            }

            workManager.getWorkInfoByIdFlow(workId).collectLatest { info ->
                if (info == null) {
                    Log.w("PhotoActivity", "WorkManager больше не знает о работе $idString — очищаем запись")
                    pendingPanoramaWorkIds.remove(idString)
                    persistPendingWorkIds()
                    cancel()
                    return@collectLatest
                }
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val outputUri = info.outputData.getString(PanoramaStitchWorker.KEY_OUTPUT_URI)
                        if (!outputUri.isNullOrBlank()) {
                            val uri = Uri.parse(outputUri)
                            val added = addUploadItem(uri, UploadMediaType.PANORAMA, dedupe = true)
                            if (added) {
                                context.showTopToast("Панорама готова")
                                Log.d("PhotoActivity", "Панорама готова: $uri")
                            } else {
                                Log.d("PhotoActivity", "Панорама $uri уже была добавлена ранее")
                            }
                        } else {
                            Log.w("PhotoActivity", "Работа $idString завершилась без URI")
                            context.showTopToast("Панорама обработана, но файл не найден")
                        }
                        pendingPanoramaWorkIds.remove(idString)
                        persistPendingWorkIds()
                        cancel()
                    }
                    WorkInfo.State.FAILED -> {
                        pendingPanoramaWorkIds.remove(idString)
                        persistPendingWorkIds()
                        context.showTopToast("Ошибка обработки панорамы")
                        Log.e("PhotoActivity", "Фоновая работа $idString завершилась ошибкой")
                        cancel()
                    }
                    WorkInfo.State.CANCELLED -> {
                        pendingPanoramaWorkIds.remove(idString)
                        persistPendingWorkIds()
                        Log.w("PhotoActivity", "Фоновая работа $idString отменена")
                        cancel()
                    }
                    else -> {
                        // ENQUEUED / RUNNING / BLOCKED
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = objectTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val photoCount = uploadItems.count { it.type == UploadMediaType.PHOTO }
                        val panoramaCount = uploadItems.count { it.type == UploadMediaType.PANORAMA }
                        val summaryText = when {
                            uploadItems.isEmpty() -> "Выбор файлов для загрузки"
                            panoramaCount == 0 -> "Фото: $photoCount"
                            photoCount == 0 -> "Панорамы: $panoramaCount"
                            else -> "Фото: $photoCount • Панорамы: $panoramaCount"
                        }
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkGreen,
                    titleContentColor = White1
                ),
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (pendingPanoramaWorkIds.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Обработка панорам: ${pendingPanoramaWorkIds.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkGreen
                    )
                    TextButton(onClick = {
                        pendingPanoramaWorkIds.clear()
                        persistPendingWorkIds()
                    }) {
                        Text("Очистить")
                    }
                }
            }

            if (uploadItems.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uploadItems) { index, item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (uploadItems.remove(item)) {
                                        context.showTopToast("Файл удалён")
                                        persistUploadItems()
                                    }
                                }
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(item.uri),
                                contentDescription = "Файл $index",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Удалить",
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp),
                                tint = Color.White
                            )
                            Text(
                                text = when (item.type) {
                                    UploadMediaType.PHOTO -> "Фото"
                                    UploadMediaType.PANORAMA -> "Панорама"
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Выберите файлы для загрузки",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        pendingPickerType = UploadMediaType.PHOTO
                        mediaPickerLauncher.launch(
                            MediaPickerActivity.createIntent(context, MediaPickerFilter.PHOTO)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGreen,
                        contentColor = White1
                    )
                ) {
                    Text("Выбрать фото из галереи")
                }

                Button(
                    onClick = {
                        pendingPickerType = UploadMediaType.PANORAMA
                        mediaPickerLauncher.launch(
                            MediaPickerActivity.createIntent(context, MediaPickerFilter.PANORAMA)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGreen,
                        contentColor = White1
                    )
                ) {
                    Text("Выбрать панорамы из галереи")
                }

                Button(
                    onClick = {
                        // Запускаем Activity для съемки 360 панорамы
                        val intent = Intent(context, Panorama360Activity::class.java).apply {
                            putExtra("OBJECT_ID", objectId)
                            putExtra("OBJECT_TITLE", objectTitle)
                        }
                        panorama360Launcher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGreen,
                        contentColor = White1
                    )
                ) {
                    Text("Снять панораму 360°")
                }

                Button(
                    onClick = {
                        if (uploadItems.isNotEmpty()) {
                            val itemsToUpload = uploadItems.toList()
                            val intent = Intent(context, UploadActivity::class.java).apply {
                                putStringArrayListExtra(
                                    "IMAGE_URIS",
                                    ArrayList(itemsToUpload.map { it.uri.toString() })
                                )
                                putStringArrayListExtra(
                                    "MEDIA_TYPES",
                                    ArrayList(itemsToUpload.map { it.type.name })
                                )
                                putExtra("OBJECT_ID", objectId)
                                putExtra("IS_VISIBLE_TO_CUSTOMER", false)
                            }

                            context.startActivity(intent)

                            // После постановки на загрузку очищаем очередь локально,
                            // чтобы не оставались "призрачные" элементы с уже удалёнными файлами.
                            uploadItems.clear()
                            persistUploadItems()
                        } else {
                            context.showTopToast("Выберите файлы перед отправкой")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp), // На 10% выше стандартной кнопки (стандарт ~50dp)
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGreen,
                        contentColor = White1
                    )
                ) {
                    Text("ОТПРАВИТЬ", style = MaterialTheme.typography.titleMedium)
                }

                Button(
                    onClick = {
                        uploadItems.clear()
                        persistUploadItems()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = uploadItems.isNotEmpty()
                ) {
                    Text("Удалить из списка")
                }
            }
        }
    }
}

private data class UploadMediaItem(
    val uri: Uri,
    val type: UploadMediaType
)

private fun createTempImageUri(context: Context, type: UploadMediaType): Uri? {
    return try {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val prefix = when (type) {
            UploadMediaType.PHOTO -> "IMG_${'$'}timeStamp_"
            UploadMediaType.PANORAMA -> "PANO_${'$'}timeStamp_"
        }
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFile = File.createTempFile(prefix, ".jpg", storageDir)
        FileProvider.getUriForFile(context, "${'$'}{context.packageName}.provider", imageFile)
    } catch (e: IOException) {
        Log.e("PhotoActivity", "Ошибка создания временного файла: ${'$'}{e.message}")
        null
    }
}

private const val PANORAMA_QUEUE_PREFS = "panorama_queue_prefs"
private const val KEY_PENDING_WORK_IDS = "pending_work_ids"
private const val KEY_STORED_UPLOAD_ITEMS = "stored_upload_items"

