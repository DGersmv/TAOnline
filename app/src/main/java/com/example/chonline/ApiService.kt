package com.example.chonline.network

import android.content.Context
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Устаревший метод для получения групп
 * @deprecated Используйте UserService.getObjects() для заказчика или создайте соответствующий эндпоинт для админа
 */
@Deprecated("Используйте новый API через UserService или AdminService")
fun fetchGroups(context: Context, callback: (Result<List<Pair<String, String>>>) -> Unit) {
    // Этот метод больше не используется с новым API
    // Если нужен список объектов, используйте UserService.getObjects(email)
    callback(Result.failure(IOException("Этот метод устарел. Используйте новый API.")))
}

