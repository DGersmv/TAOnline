package com.example.chonline.network

import android.content.Context
import java.net.URLEncoder

/**
 * Конфигурация API для подключения к серверу TAOnline
 */
object ApiConfig {
    // Базовый URL сервера
    private const val BASE_URL = "https://tashi-ani.ru"
    
    /**
     * Получить базовый URL сервера
     */
    fun getBaseUrl(): String {
        return BASE_URL
    }
    
    /**
     * Получить URL для входа админа
     */
    fun getLoginUrl(): String {
        return "$BASE_URL/api/auth/login"
    }
    
    /**
     * Получить URL для получения списка заказчиков (админ)
     * Возвращает всех пользователей, нужно фильтровать по role === "USER"
     */
    fun getAdminCustomersUrl(): String {
        return "$BASE_URL/api/admin/users"
    }
    
    /**
     * Получить URL для получения информации о заказчике с объектами (админ)
     */
    fun getAdminCustomerInfoUrl(userId: String): String {
        return "$BASE_URL/api/admin/users/$userId"
    }
    
    /**
     * Получить URL для получения списка объектов (для админа - все объекты всех заказчиков)
     * Если вызывается с токеном админа и без email, возвращает объекты всех заказчиков
     */
    fun getAdminObjectsUrl(): String {
        return "$BASE_URL/api/user/objects"
    }
    
    /**
     * Получить URL для загрузки фото админом
     */
    fun getAdminUploadPhotosUrl(objectId: String): String {
        return "$BASE_URL/api/admin/objects/$objectId/photos"
    }

    /**
     * Получить URL для загрузки панорам админом
     */
    fun getAdminUploadPanoramasUrl(objectId: String): String {
        return "$BASE_URL/api/admin/objects/$objectId/panoramas"
    }
    
    /**
     * Получить URL для получения списка объектов заказчика
     */
    fun getUserObjectsUrl(email: String): String {
        val encodedEmail = URLEncoder.encode(email, "UTF-8")
        return "$BASE_URL/api/user/objects?email=$encodedEmail"
    }
    
    /**
     * Получить URL для получения информации об объекте заказчика
     */
    fun getUserObjectUrl(objectId: String, email: String): String {
        val encodedEmail = URLEncoder.encode(email, "UTF-8")
        return "$BASE_URL/api/user/objects/$objectId?email=$encodedEmail"
    }
    
    /**
     * Получить URL для получения списка фото объекта заказчика
     */
        fun getUserObjectPhotosUrl(objectId: String, email: String, folderId: String? = null): String {
            val encodedEmail = URLEncoder.encode(email, "UTF-8")
            val url = "$BASE_URL/api/user/objects/$objectId/photos?email=$encodedEmail"
            return if (folderId != null) {
                "$url&folderId=$folderId"
            } else {
                url
            }
        }

        fun getUserObjectFoldersUrl(objectId: String, email: String): String {
            val encodedEmail = URLEncoder.encode(email, "UTF-8")
            return "$BASE_URL/api/user/objects/$objectId/folders?email=$encodedEmail"
        }
    
    /**
     * Получить URL для получения файла фото
     */
    fun getPhotoFileUrl(objectId: String, filename: String, email: String): String {
        val encodedEmail = URLEncoder.encode(email, "UTF-8")
        return "$BASE_URL/api/uploads/objects/$objectId/$filename?email=$encodedEmail"
    }
    
    /**
     * Таймаут запросов (в секундах)
     */
    const val REQUEST_TIMEOUT = 30L
}

