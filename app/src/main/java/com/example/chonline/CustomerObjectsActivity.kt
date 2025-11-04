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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.chonline.network.AdminService
import com.example.chonline.network.AuthService
import com.example.chonline.ui.theme.Black10
import com.example.chonline.ui.theme.DarkGreen
import com.example.chonline.ui.theme.Orange1
import com.example.chonline.ui.theme.White1
import org.json.JSONObject

class CustomerObjectsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val customerId = intent.getStringExtra("CUSTOMER_ID") ?: ""
        val customerName = intent.getStringExtra("CUSTOMER_NAME") ?: ""
        val customerEmail = intent.getStringExtra("CUSTOMER_EMAIL") ?: ""
        
        if (customerId.isEmpty()) {
            Toast.makeText(this, "Ошибка: не передан ID заказчика", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setContent {
            CustomerObjectsScreen(
                customerId = customerId,
                customerName = customerName,
                customerEmail = customerEmail
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerObjectsScreen(
    customerId: String,
    customerName: String,
    customerEmail: String
) {
    val context = LocalContext.current
    var objects by remember { mutableStateOf<List<JSONObject>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(customerId) {
        val token = AuthService.getAdminToken(context)
        if (token == null) {
            errorMessage = "Ошибка авторизации. Требуется вход в систему."
            isLoading = false
            context.startActivity(Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        } else {
            AdminService.getCustomerInfo(context, customerId, token) { result ->
                isLoading = false
                if (result.isSuccess) {
                    val customerInfo = result.getOrNull()
                    if (customerInfo != null) {
                        val objectsArray = customerInfo.optJSONArray("objects")
                        if (objectsArray != null) {
                            val objectsList = mutableListOf<JSONObject>()
                            for (i in 0 until objectsArray.length()) {
                                objectsList.add(objectsArray.getJSONObject(i))
                            }
                            objects = objectsList
                            if (objectsList.isEmpty()) {
                                errorMessage = "У заказчика нет объектов"
                            }
                        } else {
                            objects = emptyList()
                            errorMessage = "У заказчика нет объектов"
                        }
                    } else {
                        errorMessage = "Не удалось получить информацию о заказчике"
                    }
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Ошибка загрузки объектов"
                    Log.e("CustomerObjectsActivity", "Ошибка: $errorMessage")
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
                            text = customerName.ifEmpty { customerEmail },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (customerEmail.isNotEmpty()) {
                            Text(
                                text = customerEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = Orange1.copy(alpha = 0.7f)
                            )
                        }
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
                },
                actions = {
                    IconButton(
                        onClick = {
                            AuthService.logout(context)
                            context.startActivity(Intent(context, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            })
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Выйти",
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
                                val token = AuthService.getAdminToken(context)
                                if (token != null) {
                                    isLoading = true
                                    errorMessage = null
                                    AdminService.getCustomerInfo(context, customerId, token) { result ->
                                        isLoading = false
                                        if (result.isSuccess) {
                                            val customerInfo = result.getOrNull()
                                            if (customerInfo != null) {
                                                val objectsArray = customerInfo.optJSONArray("objects")
                                                if (objectsArray != null) {
                                                    val objectsList = mutableListOf<JSONObject>()
                                                    for (i in 0 until objectsArray.length()) {
                                                        objectsList.add(objectsArray.getJSONObject(i))
                                                    }
                                                    objects = objectsList
                                                    if (objectsList.isEmpty()) {
                                                        errorMessage = "У заказчика нет объектов"
                                                    }
                                                } else {
                                                    objects = emptyList()
                                                    errorMessage = "У заказчика нет объектов"
                                                }
                                            } else {
                                                errorMessage = "Не удалось получить информацию о заказчике"
                                            }
                                        } else {
                                            errorMessage = result.exceptionOrNull()?.message ?: "Ошибка загрузки"
                                        }
                                    }
                                } else {
                                    context.startActivity(Intent(context, LoginActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    })
                                }
                            }
                        ) {
                            Text("Повторить")
                        }
                    }
                }
                objects != null && objects!!.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(objects!!) { obj ->
                            ObjectButton(
                                obj = obj,
                                customerId = customerId,
                                customerEmail = customerEmail,
                                context = context
                            )
                        }
                    }
                }
                objects != null && objects!!.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "У заказчика нет объектов",
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
fun ObjectButton(
    obj: JSONObject,
    customerId: String,
    customerEmail: String,
    context: Context
) {
    val objectId = obj.optString("id", "")
    val objectTitle = obj.optString("title", "Объект $objectId")
    val objectDescription = obj.optString("description", "")
    val objectStatus = obj.optString("status", "")
    
    // Получаем статистику из _count если есть
    val countObj = obj.optJSONObject("_count")
    val photosCount = countObj?.optInt("photos", 0) ?: 0
    val documentsCount = countObj?.optInt("documents", 0) ?: 0
    
    Button(
        onClick = {
            // Перейти к экрану загрузки фото для этого объекта
            val intent = Intent(context, PhotoActivity::class.java)
            intent.putExtra("OBJECT_ID", objectId)
            intent.putExtra("USER_ID", customerId)
            intent.putExtra("OBJECT_TITLE", objectTitle)
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
                text = objectTitle,
                style = MaterialTheme.typography.titleMedium
            )
            if (objectDescription.isNotEmpty()) {
                Text(
                    text = objectDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = Orange1.copy(alpha = 0.7f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (photosCount > 0) {
                    Text(
                        text = "Фото: $photosCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = Orange1.copy(alpha = 0.7f)
                    )
                }
                if (documentsCount > 0) {
                    Text(
                        text = "Документы: $documentsCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = Orange1.copy(alpha = 0.7f)
                    )
                }
                if (objectStatus.isNotEmpty()) {
                    Text(
                        text = "Статус: $objectStatus",
                        style = MaterialTheme.typography.bodySmall,
                        color = Orange1.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
