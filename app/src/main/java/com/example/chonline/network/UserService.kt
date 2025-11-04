package com.example.chonline.network

import android.util.Log
import com.example.chonline.network.ApiConfig
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Сервис для работы заказчика с просмотром объектов и фото
 */
object UserService {
    private const val TAG = "UserService"
    
    /**
     * Получить список объектов заказчика
     * @param email Email заказчика
     * @param callback Callback с результатом (список объектов)
     */
    fun getObjects(
        email: String,
        callback: (Result<List<JSONObject>>) -> Unit
    ) {
        if (email.isEmpty()) {
            callback(Result.failure(IOException("Email не может быть пустым")))
            return
        }
        
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(ApiConfig.getUserObjectsUrl(email))
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Ошибка сети при получении объектов: ${e.message}")
                callback(Result.failure(e))
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val responseBody = response.body?.string() ?: "{}"
                        val jsonResponse = JSONObject(responseBody)
                        
                        if (response.isSuccessful && jsonResponse.optBoolean("success", false)) {
                            val objectsArray = jsonResponse.optJSONArray("objects") ?: JSONArray()
                            val objectsList = mutableListOf<JSONObject>()
                            
                            for (i in 0 until objectsArray.length()) {
                                val obj = objectsArray.optJSONObject(i)
                                if (obj != null) {
                                    objectsList.add(obj)
                                }
                            }
                            
                            Log.d(TAG, "Получено объектов: ${objectsList.size}")
                            callback(Result.success(objectsList))
                        } else {
                            val errorMessage = jsonResponse.optString("message", "Ошибка получения объектов")
                            Log.e(TAG, "Ошибка получения объектов: $errorMessage")
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
    
    /**
     * Получить информацию об объекте
     * @param objectId ID объекта
     * @param email Email заказчика
     * @param callback Callback с результатом (информация об объекте)
     */
    fun getObject(
        objectId: String,
        email: String,
        callback: (Result<JSONObject>) -> Unit
    ) {
        if (email.isEmpty() || objectId.isEmpty()) {
            callback(Result.failure(IOException("Email и ID объекта не могут быть пустыми")))
            return
        }
        
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(ApiConfig.getUserObjectUrl(objectId, email))
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Ошибка сети при получении объекта: ${e.message}")
                callback(Result.failure(e))
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val responseBody = response.body?.string() ?: "{}"
                        val jsonResponse = JSONObject(responseBody)
                        
                        if (response.isSuccessful && jsonResponse.optBoolean("success", false)) {
                            val objectData = jsonResponse.optJSONObject("object")
                            if (objectData != null) {
                                Log.d(TAG, "Получена информация об объекте: $objectId")
                                callback(Result.success(objectData))
                            } else {
                                Log.e(TAG, "Данные объекта не найдены в ответе")
                                callback(Result.failure(IOException("Данные объекта не получены")))
                            }
                        } else {
                            val errorMessage = jsonResponse.optString("message", "Ошибка получения объекта")
                            Log.e(TAG, "Ошибка получения объекта: $errorMessage")
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
    
    /**
     * Получить список папок объекта
     * @param objectId ID объекта
     * @param email Email заказчика
     * @param callback Callback с результатом (список папок)
     */
    fun getObjectFolders(
        objectId: String,
        email: String,
        callback: (Result<List<JSONObject>>) -> Unit
    ) {
        if (email.isEmpty() || objectId.isEmpty()) {
            callback(Result.failure(IOException("Email и ID объекта не могут быть пустыми")))
            return
        }
        
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(ApiConfig.getUserObjectFoldersUrl(objectId, email))
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Ошибка сети при получении папок: ${e.message}")
                callback(Result.failure(e))
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = response.body?.string() ?: "{}"
                    val contentType = response.header("Content-Type", "")
                    val url = call.request().url.toString()
                    
                    try {
                        Log.d(TAG, "URL: $url")
                        Log.d(TAG, "Статус код: ${response.code}")
                        Log.d(TAG, "Content-Type: $contentType")
                        Log.d(TAG, "Ответ сервера (первые 500 символов): ${responseBody.take(500)}")
                        
                        // Проверяем, что ответ не HTML
                        if (responseBody.trimStart().startsWith("<!DOCTYPE") || 
                            responseBody.trimStart().startsWith("<html") ||
                            responseBody.trimStart().startsWith("<HTML")) {
                            Log.e(TAG, "Сервер вернул HTML вместо JSON. URL: $url")
                            callback(Result.failure(IOException("Сервер вернул HTML страницу вместо JSON. Возможно, эндпоинт не существует или произошла ошибка на сервере.")))
                            return
                        }
                        
                        // Проверяем, что ответ начинается с { или [
                        if (!responseBody.trimStart().startsWith("{") && !responseBody.trimStart().startsWith("[")) {
                            Log.e(TAG, "Ответ не является валидным JSON. Начало ответа: ${responseBody.take(100)}")
                            callback(Result.failure(IOException("Сервер вернул невалидный JSON. Ответ: ${responseBody.take(200)}")))
                            return
                        }
                        
                        val jsonResponse = JSONObject(responseBody)
                        
                        if (response.isSuccessful && jsonResponse.optBoolean("success", false)) {
                            val foldersArray = jsonResponse.optJSONArray("folders") ?: JSONArray()
                            val foldersList = mutableListOf<JSONObject>()
                            
                            for (i in 0 until foldersArray.length()) {
                                val folder = foldersArray.optJSONObject(i)
                                if (folder != null) {
                                    foldersList.add(folder)
                                }
                            }
                            
                            Log.d(TAG, "Получено папок: ${foldersList.size}")
                            callback(Result.success(foldersList))
                        } else {
                            val errorMessage = jsonResponse.optString("message", "Ошибка получения папок")
                            Log.e(TAG, "Ошибка получения папок: $errorMessage")
                            callback(Result.failure(IOException(errorMessage)))
                        }
                    } catch (e: org.json.JSONException) {
                        Log.e(TAG, "Ошибка парсинга JSON: ${e.message}")
                        Log.e(TAG, "Ответ сервера: ${responseBody.take(500)}")
                        callback(Result.failure(IOException("Ошибка парсинга JSON: ${e.message}. Ответ сервера: ${responseBody.take(200)}")))
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка обработки ответа: ${e.message}", e)
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    /**
     * Получить список фото объекта
     * @param objectId ID объекта
     * @param email Email заказчика
     * @param folderId ID папки (опционально, null для всех фото)
     * @param callback Callback с результатом (список фото)
     */
    fun getObjectPhotos(
        objectId: String,
        email: String,
        folderId: String? = null,
        callback: (Result<List<JSONObject>>) -> Unit
    ) {
        if (email.isEmpty() || objectId.isEmpty()) {
            callback(Result.failure(IOException("Email и ID объекта не могут быть пустыми")))
            return
        }
        
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(ApiConfig.getUserObjectPhotosUrl(objectId, email, folderId))
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Ошибка сети при получении фото: ${e.message}")
                callback(Result.failure(e))
            }
            
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val responseBody = response.body?.string() ?: "{}"
                        val jsonResponse = JSONObject(responseBody)
                        
                        if (response.isSuccessful && jsonResponse.optBoolean("success", false)) {
                            val photosArray = jsonResponse.optJSONArray("photos") ?: JSONArray()
                            val photosList = mutableListOf<JSONObject>()
                            
                            for (i in 0 until photosArray.length()) {
                                val photo = photosArray.optJSONObject(i)
                                if (photo != null) {
                                    photosList.add(photo)
                                }
                            }
                            
                            Log.d(TAG, "Получено фото: ${photosList.size}")
                            callback(Result.success(photosList))
                        } else {
                            val errorMessage = jsonResponse.optString("message", "Ошибка получения фото")
                            Log.e(TAG, "Ошибка получения фото: $errorMessage")
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
    
    /**
     * Получить URL для загрузки файла фото
     */
    fun getPhotoFileUrl(objectId: String, filename: String, email: String): String {
        return ApiConfig.getPhotoFileUrl(objectId, filename, email)
    }
}

