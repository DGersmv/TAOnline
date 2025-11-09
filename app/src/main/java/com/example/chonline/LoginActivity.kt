package com.example.chonline

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import com.example.chonline.ui.theme.Orange1
import com.example.chonline.ui.theme.Black10
import com.example.chonline.ui.theme.DarkGreen
import com.example.chonline.ui.theme.White1
import androidx.core.content.edit
import com.example.chonline.network.fetchGroups
import com.example.chonline.network.AuthService
import com.example.chonline.DeepLinkHandler

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Обработать deep link, если приложение открыто через него
        handleDeepLink()
        
        // Проверить, не авторизован ли уже админ
        checkIfAlreadyLoggedIn()
        
        setContent {
            LoginScreen()
        }
    }
    
    private fun handleDeepLink() {
        val intent = intent
        if (intent.action == android.content.Intent.ACTION_VIEW && intent.data != null) {
            DeepLinkHandler.handleDeepLink(intent, this)
        }
    }
    
    private fun checkIfAlreadyLoggedIn() {
        val token = AuthService.getUserToken(this)
        val role = AuthService.getUserRole(this)
        
        // Если пользователь уже авторизован и нет deep link, переходим на нужный экран
        if (token != null && role != null && intent.action != android.content.Intent.ACTION_VIEW) {
            val intent = when (role) {
                "MASTER" -> Intent(this, CustomersActivity::class.java)
                "USER" -> {
                    val customerEmail = AuthService.getUserEmail(this) ?: ""
                    Intent(this, CustomerObjectsListActivity::class.java).apply {
                        putExtra("CUSTOMER_EMAIL", customerEmail)
                    }
                }
                else -> null
            }
            intent?.let {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(it)
                finish() // Закрыть экран входа
            }
        }
    }
}

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }

    // Проверить, есть ли уже авторизованный админ
    val adminToken = remember { AuthService.getAdminToken(context) }
    val savedEmail = remember { 
        sharedPreferences.getString("admin_email", "") ?: ""
    }

    var username by remember { mutableStateOf(savedEmail) }
    var password by remember { mutableStateOf("") }
    
    // Обновить email в поле, если админ уже авторизован
    LaunchedEffect(adminToken) {
        if (adminToken != null && savedEmail.isNotEmpty()) {
            username = savedEmail
            password = "" // Очистить пароль для безопасности
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Логотип приложения
        Box(
            modifier = Modifier
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            // Попробуем загрузить логотип из разных мест
            val logoResId = try {
                context.resources.getIdentifier("logo", "drawable", context.packageName)
            } catch (e: Exception) {
                0
            }
            
            if (logoResId != 0) {
                Image(
                    painter = painterResource(id = logoResId),
                    contentDescription = "Логотип ТАШИАНи",
                    modifier = Modifier
                        .size(240.dp)
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Если логотип не найден, показываем название приложения
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ТАШИАНи",
                        style = MaterialTheme.typography.headlineLarge,
                        color = DarkGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Вход в систему",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Логин") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (username.isNotEmpty() && password.isNotEmpty()) {
                    // Попытка входа как админ
                    loginAsAdmin(context, username, password, sharedPreferences)
                } else {
                    context.showTopToast("Введите логин и пароль")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkGreen,
                contentColor = White1
            )
        ) {
            Text("Войти")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Кнопка "Очистить данные и выйти"
        Button(
            onClick = {
                // Очистить все данные, включая токен авторизации
                clearUserData(sharedPreferences)
                AuthService.logout(context) // Очистить токен админа
                username = ""
                password = ""
                context.showTopToast("Данные удалены, вы вышли из системы")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Очистить данные и выйти")
        }
    }
}

/**
 * Функция для сохранения логина и пароля в SharedPreferences
 */
fun saveUserData(sharedPreferences: SharedPreferences, username: String, password: String) {
    sharedPreferences.edit {
        putString("username", username)
        putString("password", password)
    }
}

/**
 * Функция для загрузки сохранённых данных
 */
fun loadSavedData(sharedPreferences: SharedPreferences, key: String): String {
    return sharedPreferences.getString(key, "") ?: ""
}

/**
 * Функция для очистки сохранённых данных
 * Очищает все данные, включая токен авторизации
 */
fun clearUserData(sharedPreferences: SharedPreferences) {
    sharedPreferences.edit {
        remove("username")
        remove("password")
        remove("admin_token")
        remove("admin_email")
        remove("deep_link_type")
        remove("deep_link_user_id")
        remove("deep_link_object_id")
        remove("deep_link_email")
    }
}
/**
 * Вход админа в систему
 */
fun loginAsAdmin(
    context: Context,
    email: String,
    password: String,
    sharedPreferences: SharedPreferences
) {
    AuthService.login(context, email, password) { result ->
        // Выполнить в главном потоке, так как callback приходит из фонового потока
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (result.isSuccess) {
                val token = result.getOrNull()
                context.showTopToast("Успешный вход!")
                
                // Очистить старые данные из SharedPreferences (удалить username и password)
                sharedPreferences.edit().apply {
                    remove("username")
                    remove("password")
                    apply()
                }
                
                // Проверить, есть ли deep link для загрузки
                val deepLinkType = sharedPreferences.getString("deep_link_type", null)
                if (deepLinkType == "upload") {
                    // Перейти к экрану загрузки фото
                    val objectId = sharedPreferences.getString("deep_link_object_id", null)
                    val userId = sharedPreferences.getString("deep_link_user_id", null)
                    if (objectId != null) {
                        val intent = Intent(context, PhotoActivity::class.java)
                        intent.putExtra("OBJECT_ID", objectId)
                        if (userId != null) {
                            intent.putExtra("USER_ID", userId)
                        }
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        context.startActivity(intent)
                        // Очистить deep link данные
                        DeepLinkHandler.clearDeepLinkData(context)
                    }
                } else {
                    // Если нет deep link, переходим на нужный экран в зависимости от роли
                    // Получаем роль из ответа сервера
                    val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                    val role = prefs.getString("user_role", null)
                    val customerEmail = prefs.getString("user_email", null) ?: email
                    
                    val intent = when (role) {
                        "MASTER" -> Intent(context, CustomersActivity::class.java)
                        "USER" -> Intent(context, CustomerObjectsListActivity::class.java).apply {
                            putExtra("CUSTOMER_EMAIL", customerEmail)
                        }
                        else -> {
                            // Если роль не определена, пробуем определить по токену
                            if (AuthService.isAdminLoggedIn(context)) {
                                Intent(context, CustomersActivity::class.java)
                            } else {
                                Intent(context, CustomerObjectsListActivity::class.java).apply {
                                    putExtra("CUSTOMER_EMAIL", customerEmail)
                                }
                            }
                        }
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    context.startActivity(intent)
                }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Ошибка входа"
                context.showTopToast(error)
            }
        }
    }
}

fun loginAndFetchGroups(
    context: Context,
    sharedPreferences: SharedPreferences,
    username: String,
    password: String
) {
    if (username.isEmpty() || password.isEmpty()) {
        context.showTopToast("Введите логин и пароль")
        return
    }

    // Сохраняем логин и пароль
    saveUserData(sharedPreferences, username, password)

    // Запрашиваем группы с API
    fetchGroups(context) { result ->
        if (result.isSuccess) {
            val groups = result.getOrNull()?.joinToString("\n") ?: "Нет данных"
            context.showTopToast("Группы:\n$groups", Toast.LENGTH_LONG)
        } else {
            context.showTopToast("Ошибка загрузки групп: ${result.exceptionOrNull()?.message}")
        }
    }
}
