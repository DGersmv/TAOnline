package com.example.chonline

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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

class CustomersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CustomersScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen() {
    val context = LocalContext.current
    var customers by remember { mutableStateOf<List<JSONObject>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val token = AuthService.getAdminToken(context)
        if (token == null) {
            errorMessage = "Ошибка авторизации. Требуется вход в систему."
            isLoading = false
            // Перейти к экрану входа
            context.startActivity(Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        } else {
            AdminService.getCustomers(context, token) { result ->
                isLoading = false
                if (result.isSuccess) {
                    customers = result.getOrNull() ?: emptyList()
                    if (customers.isNullOrEmpty()) {
                        errorMessage = "Нет заказчиков"
                    }
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Ошибка загрузки заказчиков"
                    Log.e("CustomersActivity", "Ошибка: $errorMessage")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выберите заказчика") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkGreen,
                    titleContentColor = White1
                ),
                actions = {
                    IconButton(
                        onClick = {
                            // Выйти из системы
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
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val token = AuthService.getAdminToken(context)
                                    if (token != null) {
                                        isLoading = true
                                        errorMessage = null
                                        AdminService.getCustomers(context, token) { result ->
                                            isLoading = false
                                            if (result.isSuccess) {
                                                customers = result.getOrNull() ?: emptyList()
                                                if (customers.isNullOrEmpty()) {
                                                    errorMessage = "Нет заказчиков"
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
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Повторить")
                            }
                            
                            Button(
                                onClick = {
                                    // Выйти из системы
                                    AuthService.logout(context)
                                    context.startActivity(Intent(context, LoginActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    })
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Выйти")
                            }
                        }
                    }
                }
                customers != null && customers!!.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(customers!!) { customer ->
                            CustomerButton(customer = customer, context = context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerButton(customer: JSONObject, context: Context) {
    val customerId = customer.optString("id", "")
    val customerName = customer.optString("name", "")
        .takeIf { it.isNotEmpty() }
        ?: customer.optString("email", "Заказчик $customerId")
    val customerEmail = customer.optString("email", "")

    Button(
        onClick = {
            // Перейти к экрану объектов заказчика
            val intent = Intent(context, CustomerObjectsActivity::class.java)
            intent.putExtra("CUSTOMER_ID", customerId)
            intent.putExtra("CUSTOMER_NAME", customerName)
            intent.putExtra("CUSTOMER_EMAIL", customerEmail)
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = DarkGreen,
            contentColor = White1
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = customerName,
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
    }
}
