package com.example.chonline

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.chonline.network.UserService
import com.example.chonline.ui.theme.DarkGreen
import com.example.chonline.ui.theme.White1
import org.json.JSONObject

class PhotoViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val email = intent.getStringExtra("EMAIL") 
            ?: intent.getStringExtra("CUSTOMER_EMAIL") 
            ?: ""
        val objectId = intent.getStringExtra("OBJECT_ID") ?: "0"
        val objectTitle = intent.getStringExtra("OBJECT_TITLE") ?: "Объект $objectId"
        val folderId = intent.getStringExtra("FOLDER_ID") // Может быть null для "Все фото"
        
        Log.d("PhotoViewActivity", "Email: $email, Object ID: $objectId, Folder ID: $folderId")
        
        if (email.isEmpty() || objectId == "0") {
            Toast.makeText(this, "Ошибка: не переданы необходимые параметры", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setContent {
            PhotoViewScreen(email, objectId, objectTitle, folderId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewScreen(email: String, objectId: String, objectTitle: String = "Объект", folderId: String? = null) {
    val context = LocalContext.current
    
    var photos by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    
    LaunchedEffect(email, objectId, folderId) {
        UserService.getObjectPhotos(objectId, email, folderId) { result ->
            isLoading = false
            if (result.isSuccess) {
                photos = result.getOrNull() ?: emptyList()
                Log.d("PhotoViewActivity", "Загружено фото: ${photos.size}")
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Ошибка загрузки фото"
                Log.e("PhotoViewActivity", "Ошибка: $errorMessage")
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
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
                                text = "Фото объекта",
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
            verticalArrangement = Arrangement.Top
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                photos.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Нет доступных фото")
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(photos) { index, photo ->
                            PhotoThumbnail(
                                photo = photo,
                                objectId = objectId,
                                email = email,
                                onClick = { selectedPhotoIndex = index }
                            )
                        }
                    }
                }
            }
        }
        }
        
        // Полноэкранный просмотр фото поверх Scaffold
        selectedPhotoIndex?.let { index ->
            FullScreenPhotoViewer(
                photos = photos,
                initialIndex = index,
                objectId = objectId,
                email = email,
                onDismiss = { selectedPhotoIndex = null }
            )
        }
    }
}

@Composable
fun PhotoThumbnail(
    photo: JSONObject,
    objectId: String,
    email: String,
    onClick: () -> Unit
) {
    val filename = photo.optString("filename", "")
    val photoUrl = if (filename.isNotEmpty()) {
        UserService.getPhotoFileUrl(objectId, filename, email)
    } else {
        null
    }
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        if (photoUrl != null) {
            Image(
                painter = rememberAsyncImagePainter(photoUrl),
                contentDescription = "Фото",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun FullScreenPhotoViewer(
    photos: List<JSONObject>,
    initialIndex: Int,
    objectId: String,
    email: String,
    onDismiss: () -> Unit
) {
    var currentPage by remember { mutableStateOf(initialIndex) }
    var dragOffset by remember { mutableStateOf(0f) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        // Определяем направление свайпа
                        if (dragOffset > 100f && currentPage < photos.size - 1) {
                            currentPage++
                        } else if (dragOffset < -100f && currentPage > 0) {
                            currentPage--
                        }
                        dragOffset = 0f
                    }
                ) { change, dragAmount ->
                    dragOffset += dragAmount.x
                }
            }
    ) {
        // Отображаем текущее фото с анимацией перехода
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dragOffset
                    alpha = 1f - kotlin.math.abs(dragOffset) / 1000f
                }
        ) {
            val photo = photos[currentPage]
            val filename = photo.optString("filename", "")
            val photoUrl = if (filename.isNotEmpty()) {
                UserService.getPhotoFileUrl(objectId, filename, email)
            } else {
                null
            }
            
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }
            
            val transformableState = rememberTransformableState { zoomChange, panChange, rotationChange ->
                scale = (scale * zoomChange).coerceIn(1f, 5f)
                // Ограничиваем смещение при масштабировании
                if (scale > 1f) {
                    offsetX = (offsetX + panChange.x).coerceIn(-500f, 500f)
                    offsetY = (offsetY + panChange.y).coerceIn(-500f, 500f)
                } else {
                    // Если масштаб вернулся к 1, сбрасываем смещение
                    offsetX = 0f
                    offsetY = 0f
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = transformableState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl != null) {
                    Image(
                        painter = rememberAsyncImagePainter(photoUrl),
                        contentDescription = "Фото",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
        
        // Кнопка закрытия
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        
        // Индикатор текущего фото
        if (photos.size > 1) {
            Text(
                text = "${currentPage + 1} / ${photos.size}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

