package com.example.chonline

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Обработчик Deep Links для приложения
 * Поддерживает два типа deep links:
 * 1. tashi-ani://upload?userId={userId}&objectId={objectId} - для админа (загрузка фото)
 * 2. tashi-ani://view?email={email}&objectId={objectId} - для заказчика (просмотр фото)
 */
object DeepLinkHandler {
    private const val TAG = "DeepLinkHandler"
    
    /**
     * Обработать deep link из Intent
     */
    fun handleDeepLink(intent: Intent, context: Context) {
        val uri = intent.data ?: return
        
        Log.d(TAG, "Получен deep link: $uri")
        
        when {
            uri.scheme == "tashi-ani" && uri.host == "upload" -> {
                handleUploadDeepLink(uri, context)
            }
            uri.scheme == "tashi-ani" && uri.host == "view" -> {
                handleViewDeepLink(uri, context)
            }
            else -> {
                Log.w(TAG, "Неизвестный deep link: $uri")
            }
        }
    }
    
    /**
     * Обработать deep link для загрузки фото (админ)
     */
    private fun handleUploadDeepLink(uri: Uri, context: Context) {
        val userId = uri.getQueryParameter("userId")
        val objectId = uri.getQueryParameter("objectId")
        
        Log.d(TAG, "Deep link для загрузки: userId=$userId, objectId=$objectId")
        
        if (userId.isNullOrEmpty() || objectId.isNullOrEmpty()) {
            Log.e(TAG, "Отсутствуют обязательные параметры в deep link для загрузки")
            return
        }
        
        // Сохранить параметры в SharedPreferences
        val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("deep_link_user_id", userId)
            putString("deep_link_object_id", objectId)
            putString("deep_link_type", "upload")
            apply()
        }
        
        // Проверить, авторизован ли пользователь (админ)
        val adminToken = prefs.getString("admin_token", "")
        if (adminToken.isNullOrEmpty()) {
            // Пользователь не авторизован, перейти к экрану входа
            val intent = Intent(context, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            context.startActivity(intent)
        } else {
            // Пользователь авторизован, перейти к экрану загрузки фото
            val intent = Intent(context, PhotoActivity::class.java)
            intent.putExtra("USER_ID", userId)
            intent.putExtra("OBJECT_ID", objectId)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            context.startActivity(intent)
        }
    }
    
    /**
     * Обработать deep link для просмотра фото (заказчик)
     */
    private fun handleViewDeepLink(uri: Uri, context: Context) {
        val email = uri.getQueryParameter("email")
        val objectId = uri.getQueryParameter("objectId")
        
        Log.d(TAG, "Deep link для просмотра: email=$email, objectId=$objectId")
        
        if (email.isNullOrEmpty() || objectId.isNullOrEmpty()) {
            Log.e(TAG, "Отсутствуют обязательные параметры в deep link для просмотра")
            return
        }
        
        // Сохранить параметры в SharedPreferences
        val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("deep_link_email", email)
            putString("deep_link_object_id", objectId)
            putString("deep_link_type", "view")
            apply()
        }
        
        // Перейти к экрану просмотра фото
        val intent = Intent(context, PhotoViewActivity::class.java)
        intent.putExtra("EMAIL", email)
        intent.putExtra("OBJECT_ID", objectId)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        context.startActivity(intent)
    }
    
    /**
     * Очистить сохраненные данные deep link
     */
    fun clearDeepLinkData(context: Context) {
        val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("deep_link_user_id")
            remove("deep_link_object_id")
            remove("deep_link_email")
            remove("deep_link_type")
            apply()
        }
    }
}


