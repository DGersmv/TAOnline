package com.example.chonline

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import com.example.chonline.ui.theme.DarkGreen
import com.example.chonline.ui.theme.White1
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.io.IOException
import android.os.Build
import android.provider.Settings



class PhotoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Получить параметры из Intent или из deep link (SharedPreferences)
        val objectId = intent.getStringExtra("OBJECT_ID") 
            ?: intent.getStringExtra("GROUP_ID") // Для обратной совместимости
            ?: getDeepLinkObjectId()
            ?: "0"
        
        val userId = intent.getStringExtra("USER_ID") ?: getDeepLinkUserId()
        val objectTitle = intent.getStringExtra("OBJECT_TITLE") ?: "Объект $objectId"
        
        Log.d("PhotoActivity", "Object ID: $objectId, User ID: $userId, Title: $objectTitle")

        // 🔹 Проверяем и запрашиваем доступ к файлам (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent) // 📌 Открывает настройки для разрешения доступа
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
    val imageList = remember { mutableStateListOf<Uri>() }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // 🔹 Запрос разрешений на камеру
    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Камера не доступна: разрешение не предоставлено", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // ✅ Проверяем и запрашиваем разрешение перед использованием камеры
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 📸 Выбор нескольких фото из галереи
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(), // ✅ Позволяет выбрать несколько файлов
        onResult = { uris: List<Uri> -> // ✅ Обрабатываем список выбранных файлов
            if (uris.isNotEmpty()) {
                imageList.addAll(uris) // ✅ Добавляем все фото в список
                Log.d("PhotoActivity", "Добавлены фото из галереи: $uris")
            } else {
                Log.e("PhotoActivity", "Ошибка: Список uri пуст при выборе из галереи")
            }
        }
    )





    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = objectTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (imageList.isNotEmpty()) {
                                "Выбрано фото: ${imageList.size}"
                            } else {
                                "Выбор фото для загрузки"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkGreen,
                    titleContentColor = White1
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { (context as? ComponentActivity)?.finish() }
                    ) {
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
            if (imageList.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(imageList) { index, uri ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    // Удалить фото из списка при клике (используем URI для безопасного удаления)
                                    if (imageList.remove(uri)) {
                                        Toast.makeText(context, "Фото удалено", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(uri),
                                contentDescription = "Фото $index",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Полупрозрачный фон для иконки удаления
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                            )
                            // Иконка удаления в правом верхнем углу
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Удалить",
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            } else {
                // Показываем сообщение, если нет фото
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Выберите фото для загрузки",
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
                    onClick = { pickImageLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGreen,
                        contentColor = White1
                    )
                ) {
                    Text("Выбрать фото из галереи")
                }

                val takePhotoLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture(),
                    onResult = { success: Boolean ->
                        if (success && tempPhotoUri != null) {
                            imageList.add(tempPhotoUri!!)
                            Log.d("PhotoActivity", "Добавлено фото с камеры: $tempPhotoUri")
                        } else {
                            Log.e("PhotoActivity", "Ошибка: Фото не сохранено")
                            Toast.makeText(context, "Ошибка при съёмке фото", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                Button(
                    onClick = {
                        if (imageList.isNotEmpty()) {
                            val intent = Intent(context, UploadActivity::class.java)
                            intent.putStringArrayListExtra("IMAGE_URIS", ArrayList(imageList.map { it.toString() }))
                            intent.putExtra("OBJECT_ID", objectId)
                            intent.putExtra("IS_VISIBLE_TO_CUSTOMER", false) // По умолчанию скрыто для заказчика
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "Выберите фото перед отправкой", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGreen,
                        contentColor = White1
                    )
                ) {
                    Text("Отправить фото")
                }

                Button(
                    onClick = { imageList.clear() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = imageList.isNotEmpty()
                ) {
                    Text("Удалить все фото")
                }
            }
        }
    }
}


