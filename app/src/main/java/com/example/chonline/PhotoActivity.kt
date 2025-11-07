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
import android.widget.Toast
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
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var pendingPickerType by remember { mutableStateOf(UploadMediaType.PHOTO) }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Камера не доступна: разрешение не предоставлено", Toast.LENGTH_SHORT).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                uploadItems.addAll(uris.map { UploadMediaItem(it, pendingPickerType) })
                Log.d("PhotoActivity", "Добавлены файлы (${pendingPickerType.name}) из галереи: $uris")
            } else {
                Log.e("PhotoActivity", "Ошибка: Список uri пуст при выборе из галереи")
            }
        }
    )

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val targetUri = tempPhotoUri
        val type = pendingCaptureType
        if (success && targetUri != null && type != null) {
            uploadItems.add(UploadMediaItem(targetUri, type))
            Log.d("PhotoActivity", "Добавлен файл (${type.name}) с камеры: $targetUri")
        } else if (!success && targetUri != null) {
            context.contentResolver.delete(targetUri, null, null)
            Log.e("PhotoActivity", "Съёмка отменена или завершилась с ошибкой для $targetUri")
            Toast.makeText(context, "Ошибка при съёмке файла", Toast.LENGTH_SHORT).show()
        }
        tempPhotoUri = null
        pendingCaptureType = null
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
                                        Toast.makeText(context, "Файл удалён", Toast.LENGTH_SHORT).show()
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
                        pickImageLauncher.launch("image/*")
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
                        val uri = createTempImageUri(context, UploadMediaType.PHOTO)
                        if (uri != null) {
                            tempPhotoUri = uri
                            pendingCaptureType = UploadMediaType.PHOTO
                            takePictureLauncher.launch(uri)
                        } else {
                            Toast.makeText(context, "Не удалось создать файл для фото", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text("Снять фото на камеру")
                }

                Button(
                    onClick = {
                        pendingPickerType = UploadMediaType.PANORAMA
                        pickImageLauncher.launch("image/*")
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
                        val uri = createTempImageUri(context, UploadMediaType.PANORAMA)
                        if (uri != null) {
                            tempPhotoUri = uri
                            pendingCaptureType = UploadMediaType.PANORAMA
                            takePictureLauncher.launch(uri)
                        } else {
                            Toast.makeText(context, "Не удалось создать файл для панорамы", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text("Снять панораму")
                }

                Button(
                    onClick = {
                        if (uploadItems.isNotEmpty()) {
                            val intent = Intent(context, UploadActivity::class.java)
                            intent.putStringArrayListExtra(
                                "IMAGE_URIS",
                                ArrayList(uploadItems.map { it.uri.toString() })
                            )
                            intent.putStringArrayListExtra(
                                "MEDIA_TYPES",
                                ArrayList(uploadItems.map { it.type.name })
                            )
                            intent.putExtra("OBJECT_ID", objectId)
                            intent.putExtra("IS_VISIBLE_TO_CUSTOMER", false)
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "Выберите файлы перед отправкой", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGreen,
                        contentColor = White1
                    )
                ) {
                    Text("Отправить файлы")
                }

                Button(
                    onClick = { uploadItems.clear() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = uploadItems.isNotEmpty()
                ) {
                    Text("Удалить все файлы")
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
