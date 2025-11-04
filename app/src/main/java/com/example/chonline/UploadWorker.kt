package com.example.chonline

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.chonline.network.AdminService
import com.example.chonline.network.AuthService
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class UploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val sharedPreferences = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        
        // Получить JWT токен админа
        val token = AuthService.getAdminToken(context)
        if (token == null) {
            Log.e("UploadWorker", "Ошибка авторизации: токен не найден")
            showToast(context, "Ошибка: требуется авторизация")
            return Result.failure()
        }
        
        val objectId = inputData.getString("OBJECT_ID") ?: return Result.failure()
        val imageUris = inputData.getStringArray("IMAGE_URIS") ?: return Result.failure()
        val isVisibleToCustomer = inputData.getBoolean("IS_VISIBLE_TO_CUSTOMER", false)

        if (imageUris.isEmpty()) {
            Log.e("UploadWorker", "Нет файлов для отправки!")
            return Result.failure()
        }

        // Загрузить каждый файл отдельно
        var successCount = AtomicInteger(0)
        var failureCount = AtomicInteger(0)
        val latch = CountDownLatch(imageUris.size)

        imageUris.forEach { uriString ->
            val uri = Uri.parse(uriString)
            val file = uriToFile(context, uri)

            if (file != null && file.exists()) {
                // Загрузить файл через AdminService
                AdminService.uploadPhoto(
                    context = context,
                    objectId = objectId,
                    file = file,
                    isVisibleToCustomer = isVisibleToCustomer,
                    token = token,
                    callback = { result ->
                        if (result.isSuccess) {
                            successCount.incrementAndGet()
                            Log.d("UploadWorker", "Файл успешно загружен: ${file.name}")
                        } else {
                            failureCount.incrementAndGet()
                            Log.e("UploadWorker", "Ошибка загрузки файла ${file.name}: ${result.exceptionOrNull()?.message}")
                        }
                        latch.countDown()
                    }
                )
            } else {
                Log.e("UploadWorker", "Файл не найден: $uriString")
                failureCount.incrementAndGet()
                latch.countDown()
            }
        }

        // Дождаться завершения всех загрузок
        try {
            latch.await()
        } catch (e: InterruptedException) {
            Log.e("UploadWorker", "Ожидание прервано: ${e.message}")
            return Result.retry()
        }

        // Проверить результаты
        if (successCount.get() > 0) {
            val message = if (failureCount.get() > 0) {
                "Загружено ${successCount.get()} из ${imageUris.size} файлов"
            } else {
                "Все файлы успешно загружены!"
            }
            showToast(context, message)
            Log.d("UploadWorker", message)
            return Result.success()
        } else {
            showToast(context, "Ошибка загрузки всех файлов")
            Log.e("UploadWorker", "Не удалось загрузить ни одного файла")
            return Result.retry()
        }
    }

    private fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri).use { input ->
                FileOutputStream(file).use { output ->
                    input?.copyTo(output)
                }
            }
            file
        } catch (e: IOException) {
            Log.e("UploadWorker", "Ошибка преобразования URI в файл: ${e.message}")
            null
        }
    }

    private fun showToast(context: Context, message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
