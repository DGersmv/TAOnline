package com.example.chonline

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.chonline.network.UserService
import com.example.chonline.ui.theme.DarkGreen
import com.example.chonline.ui.theme.White1
import org.json.JSONObject

class ObjectFoldersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val objectId = intent.getStringExtra("OBJECT_ID") ?: ""
        val objectTitle = intent.getStringExtra("OBJECT_TITLE") ?: "Объект"
        val customerEmail = intent.getStringExtra("CUSTOMER_EMAIL") ?: ""
        
        if (objectId.isEmpty() || customerEmail.isEmpty()) {
            Toast.makeText(this, "Ошибка: не переданы необходимые параметры", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setContent {
            ObjectFoldersScreen(
                objectId = objectId,
                objectTitle = objectTitle,
                customerEmail = customerEmail
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectFoldersScreen(
    objectId: String,
    objectTitle: String,
    customerEmail: String
) {
    val context = LocalContext.current
    var folders by remember { mutableStateOf<List<JSONObject>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(objectId, customerEmail) {
        // Функция для загрузки папок из списка фото (fallback)
        fun loadFoldersFromPhotos() {
            // Получаем все фото и извлекаем уникальные папки
            UserService.getObjectPhotos(objectId, customerEmail, null) { photosResult ->
                isLoading = false
                if (photosResult.isSuccess) {
                    val photos = photosResult.getOrNull() ?: emptyList()
                    val foldersMap = mutableMapOf<String, Pair<String, Int>>() // folderId -> (folderName, count)
                    
                    // Подсчитываем фото в каждой папке
                    photos.forEach { photo ->
                        val folder = photo.optJSONObject("folder")
                        if (folder != null) {
                            val folderId = folder.optString("id", "")
                            val folderName = folder.optString("name", "Без названия")
                            if (folderId.isNotEmpty()) {
                                val current = foldersMap[folderId] ?: Pair(folderName, 0)
                                foldersMap[folderId] = Pair(folderName, current.second + 1)
                            }
                        }
                    }
                    
                    // Создаем список папок
                    val foldersList = mutableListOf<JSONObject>()
                    
                    // Добавляем "Все фото" если есть фото
                    if (photos.isNotEmpty()) {
                        val allPhotosFolder = JSONObject().apply {
                            put("id", JSONObject.NULL)
                            put("name", "Все фото")
                            put("photoCount", photos.size)
                            put("orderIndex", -1)
                        }
                        foldersList.add(allPhotosFolder)
                    }
                    
                    // Добавляем остальные папки
                    foldersMap.forEach { (folderId, folderData) ->
                        val folder = JSONObject().apply {
                            put("id", folderId)
                            put("name", folderData.first)
                            put("photoCount", folderData.second)
                            put("orderIndex", 0)
                        }
                        foldersList.add(folder)
                    }
                    
                    folders = foldersList
                    if (foldersList.isEmpty()) {
                        errorMessage = "Нет доступных папок"
                    }
                } else {
                    errorMessage = photosResult.exceptionOrNull()?.message ?: "Ошибка загрузки фото"
                    Log.e("ObjectFoldersActivity", "Ошибка загрузки фото: $errorMessage")
                }
            }
        }
        
        // Сначала пытаемся получить список папок через API
        UserService.getObjectFolders(objectId, customerEmail) { result ->
            if (result.isSuccess) {
                val foldersList = result.getOrNull() ?: emptyList()
                if (foldersList.isNotEmpty()) {
                    folders = foldersList
                    isLoading = false
                } else {
                    // Если папок нет через API, пробуем получить из фото
                    loadFoldersFromPhotos()
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: ""
                Log.w("ObjectFoldersActivity", "Не удалось загрузить папки через API: $error")
                // Fallback: получаем папки из списка фото
                loadFoldersFromPhotos()
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
                        Text(
                            text = "Выберите папку",
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
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                UserService.getObjectFolders(objectId, customerEmail) { result ->
                                    isLoading = false
                                    if (result.isSuccess) {
                                        folders = result.getOrNull() ?: emptyList()
                                        if (folders.isNullOrEmpty()) {
                                            errorMessage = "Нет доступных папок"
                                        }
                                    } else {
                                        errorMessage = result.exceptionOrNull()?.message ?: "Ошибка загрузки"
                                    }
                                }
                            }
                        ) {
                            Text("Повторить")
                        }
                    }
                }
                folders != null && folders!!.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(folders!!) { folder ->
                            FolderButton(
                                folder = folder,
                                objectId = objectId,
                                objectTitle = objectTitle,
                                customerEmail = customerEmail,
                                context = context
                            )
                        }
                    }
                }
                folders != null && folders!!.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нет доступных папок",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderButton(
    folder: JSONObject,
    objectId: String,
    objectTitle: String,
    customerEmail: String,
    context: Context
) {
    val folderId = folder.optString("id", null)
    val folderName = folder.optString("name", "Без названия")
    val photoCount = folder.optInt("photoCount", 0)
    
    Button(
        onClick = {
            // Перейти к экрану просмотра фото с фильтром по папке
            val intent = Intent(context, PhotoViewActivity::class.java)
            intent.putExtra("OBJECT_ID", objectId)
            intent.putExtra("OBJECT_TITLE", "$objectTitle - $folderName")
            intent.putExtra("CUSTOMER_EMAIL", customerEmail)
            // Передаем folderId как строку (null для "Все фото")
            if (folderId != null && folderId != "null") {
                intent.putExtra("FOLDER_ID", folderId)
            } else {
                intent.putExtra("FOLDER_ID", null as String?)
            }
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = DarkGreen,
            contentColor = White1
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = folderName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Фото: $photoCount",
                style = MaterialTheme.typography.bodySmall,
                color = White1.copy(alpha = 0.7f)
            )
        }
    }
}

