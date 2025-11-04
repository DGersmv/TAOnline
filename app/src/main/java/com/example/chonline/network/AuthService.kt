package com.example.chonline.network

import android.content.Context
import android.util.Log
import com.example.chonline.network.ApiConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException

/**
 * Сервис для аутентификации админа
 */
object AuthService {
    private const val TAG = "AuthService"
    
    /**
     * Вход админа в систему
     * @param email Email админа
     * @param password Пароль админа
     * @param callback Callback с результатом (успех содержит JWT токен)
     */
    fun login(
        context: Context,
        email: String,
        password: String,
        callback: (Result<String>) -> Unit
    ) {
        if (email.isEmpty() || password.isEmpty()) {
            callback(Result.failure(IOException("Email и пароль не могут быть пустыми")))
            return
        }
        
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("email", email)
            put("password", password)
        }
        
        val requestBody = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            json.toString()
        )
        
        val request = Request.Builder()
            .url(ApiConfig.getLoginUrl())
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Ошибка сети при входе: ${e.message}")
                callback(Result.failure(e))
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val responseBody = response.body?.string() ?: "{}"
                        Log.d(TAG, "Ответ сервера: код=${response.code}, тело=$responseBody")
                        
                        // Пытаемся прочитать JSON ответ, даже если статус не успешный
                        val jsonResponse = try {
                            JSONObject(responseBody)
                        } catch (e: org.json.JSONException) {
                            // Если не удалось распарсить JSON, создаем пустой объект
                            JSONObject()
                        }
                        
                        if (response.isSuccessful && jsonResponse.optBoolean("success", false)) {
                            // Успешный вход
                            val token = jsonResponse.optString("token", "")
                            val user = jsonResponse.optJSONObject("user")
                            val role = user?.optString("role", "") ?: ""
                            val userId = user?.optString("id", "") ?: ""
                            val userName = user?.optString("name", "") ?: ""
                            
                            if (token.isNotEmpty()) {
                                // Сохранить токен и информацию о пользователе в SharedPreferences
                                val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                                prefs.edit().apply {
                                    putString("user_token", token)
                                    putString("user_email", email)
                                    putString("user_role", role)
                                    putString("user_id", userId)
                                    putString("user_name", userName)
                                    
                                    // Для обратной совместимости с админом
                                    if (role == "MASTER") {
                                        putString("admin_token", token)
                                        putString("admin_email", email)
                                    }
                                    apply()
                                }
                                Log.d(TAG, "Успешный вход, токен сохранен. Роль: $role, Email: $email")
                                callback(Result.success(token))
                            } else {
                                Log.e(TAG, "Токен не найден в ответе. Ответ: $responseBody")
                                callback(Result.failure(IOException("Токен не получен от сервера")))
                            }
                        } else {
                            // Ошибка - пытаемся извлечь сообщение из ответа
                            val errorMessage = when {
                                jsonResponse.has("message") -> jsonResponse.optString("message", "")
                                response.code == 401 -> "Неверный email или пароль"
                                response.code == 400 -> "Неверный формат запроса"
                                response.code == 500 -> "Ошибка сервера"
                                else -> "Ошибка авторизации (код: ${response.code})"
                            }
                            
                            Log.e(TAG, "Ошибка авторизации: $errorMessage (код: ${response.code})")
                            callback(Result.failure(IOException(errorMessage)))
                        }
                    } catch (e: org.json.JSONException) {
                        Log.e(TAG, "Ошибка парсинга JSON: ${e.message}")
                        val errorMsg = if (response.code == 401) {
                            "Неверный email или пароль"
                        } else {
                            "Неверный формат ответа от сервера: ${e.message}"
                        }
                        callback(Result.failure(IOException(errorMsg)))
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка обработки ответа: ${e.message}", e)
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }
    
    /**
     * Получить сохраненный токен пользователя
     */
    fun getUserToken(context: Context): String? {
        val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        return prefs.getString("user_token", null)
    }
    
    /**
     * Получить сохраненный токен админа (для обратной совместимости)
     */
    fun getAdminToken(context: Context): String? {
        val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        return prefs.getString("admin_token", null) ?: getUserToken(context)
    }
    
    /**
     * Получить роль пользователя
     */
    fun getUserRole(context: Context): String? {
        val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        return prefs.getString("user_role", null)
    }
    
    /**
     * Получить email пользователя
     */
    fun getUserEmail(context: Context): String? {
        val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        return prefs.getString("user_email", null)
    }
    
    /**
     * Проверить, авторизован ли админ
     */
    fun isAdminLoggedIn(context: Context): Boolean {
        return getUserRole(context) == "MASTER"
    }
    
    /**
     * Проверить, авторизован ли заказчик
     */
    fun isCustomerLoggedIn(context: Context): Boolean {
        return getUserRole(context) == "USER"
    }
    
    /**
     * Выйти из системы (очистить токен)
     */
    fun logout(context: Context) {
        val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("user_token")
            remove("user_email")
            remove("user_role")
            remove("user_id")
            remove("user_name")
            remove("admin_token")
            remove("admin_email")
            apply()
        }
    }
}

