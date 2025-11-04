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
import com.example.chonline.network.AuthService
import com.example.chonline.network.UserService
import com.example.chonline.ui.theme.DarkGreen
import com.example.chonline.ui.theme.White1
import org.json.JSONObject

class CustomerObjectsListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val customerEmail = intent.getStringExtra("CUSTOMER_EMAIL")
            ?: AuthService.getUserEmail(this)
            ?: ""
        
        if (customerEmail.isEmpty()) {
            Toast.makeText(this, "Ошибка: не передан email заказчика", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setContent {
            CustomerObjectsListScreen(customerEmail = customerEmail)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerObjectsListScreen(customerEmail: String) {
    val context = LocalContext.current
    var objects by remember { mutableStateOf<List<JSONObject>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val customerName = remember { 
        val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        prefs.getString("user_name", "") ?: customerEmail.substringBefore("@")
    }

    LaunchedEffect(customerEmail) {
        UserService.getObjects(customerEmail) { result ->
            isLoading = false
            if (result.isSuccess) {
                objects = result.getOrNull() ?: emptyList()
                if (objects.isNullOrEmpty()) {
                    errorMessage = "У вас нет объектов"
                }
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Ошибка загрузки объектов"
                Log.e("CustomerObjectsListActivity", "Ошибка: $errorMessage")
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
                        Text(
                            text = "Мои объекты",
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
                                isLoading = true
                                errorMessage = null
                                UserService.getObjects(customerEmail) { result ->
                                    isLoading = false
                                    if (result.isSuccess) {
                                        objects = result.getOrNull() ?: emptyList()
                                        if (objects.isNullOrEmpty()) {
                                            errorMessage = "У вас нет объектов"
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
                objects != null && objects!!.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(objects!!) { obj ->
                            CustomerObjectButton(
                                obj = obj,
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
                            text = "У вас пока нет объектов",
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
fun CustomerObjectButton(
    obj: JSONObject,
    customerEmail: String,
    context: Context
) {
    val objectId = obj.optString("id", "")
    val objectTitle = obj.optString("title", "Объект $objectId")
    val objectDescription = obj.optString("description", "")
    val objectAddress = obj.optString("address", "")
    val unreadMessages = obj.optInt("unreadMessagesCount", 0)
    val unreadComments = obj.optInt("unreadCommentsCount", 0)
    
    Button(
        onClick = {
            // Перейти к экрану выбора папок объекта
            val intent = Intent(context, ObjectFoldersActivity::class.java)
            intent.putExtra("OBJECT_ID", objectId)
            intent.putExtra("CUSTOMER_EMAIL", customerEmail)
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
                    color = White1.copy(alpha = 0.7f)
                )
            }
            if (objectAddress.isNotEmpty()) {
                Text(
                    text = objectAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = White1.copy(alpha = 0.7f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (unreadMessages > 0) {
                    Text(
                        text = "Непрочитанных сообщений: $unreadMessages",
                        style = MaterialTheme.typography.bodySmall,
                        color = White1.copy(alpha = 0.7f)
                    )
                }
                if (unreadComments > 0) {
                    Text(
                        text = "Непрочитанных комментариев: $unreadComments",
                        style = MaterialTheme.typography.bodySmall,
                        color = White1.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
