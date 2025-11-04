package com.example.chonline.network

import android.content.Context
import android.util.Log
import com.example.chonline.network.ApiConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Сервис для работы админа с загрузкой фото и управлением заказчиками
 */
object AdminService {
    private const val TAG = "AdminService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.REQUEST_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.REQUEST_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.REQUEST_TIMEOUT, TimeUnit.SECONDS)
        .build()
    
    /**
     * Получить список объектов всех заказчиков (для админа)
     * Использует эндпоинт /api/user/objects без параметра email, но с токеном админа
     * @param context Контекст приложения
     * @param token JWT токен админа
     * @param callback Callback с результатом (список объектов с информацией о заказчиках)
     */
    fun getCustomers(
        context: Context,
        token: String,
        callback: (Result<List<JSONObject>>) -> Unit
    ) {
        // Сначала пробуем эндпоинт для получения списка заказчиков
        val request = Request.Builder()
            .url(ApiConfig.getAdminCustomersUrl())
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Ошибка сети при получении списка объектов: ${e.message}")
                callback(Result.failure(e))
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val responseBody = response.body?.string() ?: "{}"
                        Log.d(TAG, "Ответ сервера: код=${response.code}, тело=$responseBody")
                        
                        if (!response.isSuccessful) {
                            // Пытаемся извлечь сообщение об ошибке из ответа
                            val serverMessage = try {
                                val errorJson = JSONObject(responseBody)
                                errorJson.optString("message", "")
                            } catch (e: Exception) {
                                ""
                            }
                            
                            val errorMsg = when (response.code) {
                                400 -> serverMessage.ifEmpty { 
                                    "Неверный запрос. Возможно, требуется параметр email. Ответ сервера: $responseBody"
                                }
                                404 -> "Эндпоинт не найден. Проверьте URL: ${call.request().url}"
                                401 -> "Не авторизован. Требуется повторный вход"
                                403 -> "Доступ запрещен. Недостаточно прав"
                                500 -> "Ошибка сервера"
                                else -> serverMessage.ifEmpty { "Ошибка сервера: ${response.code}" }
                            }
                            Log.e(TAG, "$errorMsg (код: ${response.code})")
                            Log.e(TAG, "URL: ${call.request().url}")
                            Log.e(TAG, "Ответ сервера: $responseBody")
                            callback(Result.failure(IOException(errorMsg)))
                            return
                        }
                        
                        val jsonResponse = JSONObject(responseBody)
                        
                        if (jsonResponse.optBoolean("success", false)) {
                            // Получаем список пользователей
                            val usersArray = jsonResponse.optJSONArray("users")
                            if (usersArray != null) {
                                // Фильтруем только заказчиков (role === "USER")
                                val customersList = mutableListOf<JSONObject>()
                                
                                for (i in 0 until usersArray.length()) {
                                    val user = usersArray.getJSONObject(i)
                                    val role = user.optString("role", "")
                                    
                                    // Добавляем только пользователей с ролью "USER" (заказчики)
                                    if (role == "USER") {
                                        customersList.add(user)
                                    }
                                }
                                
                                Log.d(TAG, "Получено заказчиков: ${customersList.size}")
                                callback(Result.success(customersList))
                            } else {
                                Log.e(TAG, "Поле 'users' не найдено в ответе")
                                callback(Result.failure(IOException("Неверный формат ответа: отсутствует поле 'users'")))
                            }
                        } else {
                            val errorMessage = jsonResponse.optString("message", "Ошибка получения списка")
                            Log.e(TAG, "Ошибка: $errorMessage")
                            callback(Result.failure(IOException(errorMessage)))
                        }
                    } catch (e: org.json.JSONException) {
                        Log.e(TAG, "Ошибка парсинга JSON: ${e.message}")
                        callback(Result.failure(IOException("Неверный формат ответа от сервера: ${e.message}")))
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка обработки ответа: ${e.message}", e)
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }
    
    /**
     * Получить информацию о заказчике с его объектами
     * @param context Контекст приложения
     * @param userId ID заказчика
     * @param token JWT токен админа
     * @param callback Callback с результатом (информация о заказчике с объектами)
     */
    fun getCustomerInfo(
        context: Context,
        userId: String,
        token: String,
        callback: (Result<JSONObject>) -> Unit
    ) {
        val request = Request.Builder()
            .url(ApiConfig.getAdminCustomerInfoUrl(userId))
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Ошибка сети при получении информации о заказчике: ${e.message}")
                callback(Result.failure(e))
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val responseBody = response.body?.string() ?: "{}"
                        Log.d(TAG, "Ответ сервера: код=${response.code}, тело=$responseBody")
                        
                        if (!response.isSuccessful) {
                            val serverMessage = try {
                                val errorJson = JSONObject(responseBody)
                                errorJson.optString("message", "")
                            } catch (e: Exception) {
                                ""
                            }
                            
                            val errorMsg = when (response.code) {
                                400 -> serverMessage.ifEmpty { "Неверный запрос" }
                                401 -> "Не авторизован. Требуется повторный вход"
                                403 -> "Доступ запрещен. Недостаточно прав"
                                404 -> "Заказчик не найден"
                                500 -> "Ошибка сервера"
                                else -> serverMessage.ifEmpty { "Ошибка сервера: ${response.code}" }
                            }
                            Log.e(TAG, "$errorMsg (код: ${response.code})")
                            Log.e(TAG, "URL: ${call.request().url}")
                            Log.e(TAG, "Ответ сервера: $responseBody")
                            callback(Result.failure(IOException(errorMsg)))
                            return
                        }
                        
                        val jsonResponse = JSONObject(responseBody)
                        
                        if (jsonResponse.optBoolean("success", false)) {
                            val user = jsonResponse.optJSONObject("user")
                            if (user != null) {
                                Log.d(TAG, "Получена информация о заказчике: ${user.optString("email")}")
                                callback(Result.success(user))
                            } else {
                                Log.e(TAG, "Поле 'user' не найдено в ответе")
                                callback(Result.failure(IOException("Неверный формат ответа: отсутствует поле 'user'")))
                            }
                        } else {
                            val errorMessage = jsonResponse.optString("message", "Ошибка получения информации")
                            Log.e(TAG, "Ошибка: $errorMessage")
                            callback(Result.failure(IOException(errorMessage)))
                        }
                    } catch (e: org.json.JSONException) {
                        Log.e(TAG, "Ошибка парсинга JSON: ${e.message}")
                        callback(Result.failure(IOException("Неверный формат ответа от сервера: ${e.message}")))
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка обработки ответа: ${e.message}", e)
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }
    
    /**
     * Загрузить фото в объект
     * @param context Контекст приложения
     * @param objectId ID объекта
     * @param file Файл для загрузки
     * @param isVisibleToCustomer Видимость фото для заказчика (по умолчанию false)
     * @param token JWT токен админа
     * @param callback Callback с результатом
     */
    fun uploadPhoto(
        context: Context,
        objectId: String,
        file: File,
        isVisibleToCustomer: Boolean = false,
        token: String,
        callback: (Result<JSONObject>) -> Unit
    ) {
        if (!file.exists()) {
            callback(Result.failure(IOException("Файл не найден: ${file.path}")))
            return
        }
        
        // Определить MIME тип файла
        val mimeType = when {
            file.name.endsWith(".jpg", ignoreCase = true) || 
            file.name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            file.name.endsWith(".png", ignoreCase = true) -> "image/png"
            file.name.endsWith(".gif", ignoreCase = true) -> "image/gif"
            file.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
            file.name.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            file.name.endsWith(".avi", ignoreCase = true) -> "video/x-msvideo"
            file.name.endsWith(".mov", ignoreCase = true) -> "video/quicktime"
            else -> "application/octet-stream"
        }
        
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                RequestBody.create(mimeType.toMediaTypeOrNull(), file)
            )
            .addFormDataPart("isVisibleToCustomer", isVisibleToCustomer.toString())
            .build()
        
        val request = Request.Builder()
            .url(ApiConfig.getAdminUploadPhotosUrl(objectId))
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Ошибка сети при загрузке фото: ${e.message}")
                callback(Result.failure(e))
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val responseBody = response.body?.string() ?: "{}"
                        val jsonResponse = JSONObject(responseBody)
                        
                        if (response.isSuccessful && jsonResponse.optBoolean("success", false)) {
                            val photo = jsonResponse.optJSONObject("photo")
                            if (photo != null) {
                                Log.d(TAG, "Фото успешно загружено: ${photo.optString("id")}")
                                callback(Result.success(jsonResponse))
                            } else {
                                Log.e(TAG, "Данные фото не найдены в ответе")
                                callback(Result.failure(IOException("Данные фото не получены")))
                            }
                        } else {
                            val errorMessage = jsonResponse.optString("message", "Ошибка загрузки фото")
                            Log.e(TAG, "Ошибка загрузки: $errorMessage")
                            callback(Result.failure(IOException(errorMessage)))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка парсинга ответа: ${e.message}")
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }
}

