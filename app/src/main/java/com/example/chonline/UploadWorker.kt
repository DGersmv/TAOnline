package com.example.chonline

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.chonline.network.AdminService
import com.example.chonline.network.AuthService
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Result as KotlinResult

class UploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        
        // Получить JWT токен админа
        val token = AuthService.getAdminToken(context)
        if (token == null) {
            Log.e("UploadWorker", "Ошибка авторизации: токен не найден")
            showToast(context, "Ошибка: требуется авторизация")
            return Result.failure()
        }
        
        val objectId = inputData.getString("OBJECT_ID") ?: return Result.failure()
        val imageUris = inputData.getStringArray("IMAGE_URIS") ?: return Result.failure()
        val mediaTypes = inputData.getStringArray("MEDIA_TYPES")
        val isVisibleToCustomer = inputData.getBoolean("IS_VISIBLE_TO_CUSTOMER", false)

        if (imageUris.isEmpty()) {
            Log.e("UploadWorker", "Нет файлов для отправки!")
            return Result.failure()
        }

        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val latch = CountDownLatch(imageUris.size)

        imageUris.forEachIndexed { index, uriString ->
            val type = mediaTypes
                ?.getOrNull(index)
                ?.let { runCatching { UploadMediaType.valueOf(it) }.getOrNull() }
                ?: UploadMediaType.PHOTO

            val file = uriToFile(context, uriString, type)

            if (file != null && file.exists()) {
                val uploadCallback: (KotlinResult<JSONObject>) -> Unit = { result ->
                    if (result.isSuccess) {
                        successCount.incrementAndGet()
                        Log.d("UploadWorker", "Файл успешно загружен: ${file.name}")
                    } else {
                        failureCount.incrementAndGet()
                        Log.e(
                            "UploadWorker",
                            "Ошибка загрузки файла ${file.name}: ${result.exceptionOrNull()?.message}"
                        )
                    }
                    file.delete()
                    latch.countDown()
                }

                when (type) {
                    UploadMediaType.PHOTO -> {
                        AdminService.uploadPhoto(
                            context = context,
                            objectId = objectId,
                            file = file,
                            isVisibleToCustomer = isVisibleToCustomer,
                            token = token,
                            callback = uploadCallback
                        )
                    }
                    UploadMediaType.PANORAMA -> {
                        AdminService.uploadPanorama(
                            context = context,
                            objectId = objectId,
                            file = file,
                            isVisibleToCustomer = isVisibleToCustomer,
                            token = token,
                            callback = uploadCallback
                        )
                    }
                }
            } else {
                Log.e("UploadWorker", "Файл не найден или не может быть создан: $uriString")
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

    private fun uriToFile(context: Context, uriString: String, type: UploadMediaType): File? {
        return try {
            val uri = Uri.parse(uriString)
            
            // Для всех файлов пытаемся использовать оригинальный файл без обработки
            // Это важно для файлов из галереи (и фото, и панорамы)
            val filePath = getRealPathFromURI(context, uri)
            if (filePath != null) {
                val directFile = File(filePath)
                if (directFile.exists() && directFile.canRead()) {
                    Log.d("UploadWorker", "Используется оригинальный файл без сжатия: $filePath (тип: ${type.name})")
                    return directFile
                }
            }
            
            // Если прямой путь недоступен, копируем без сжатия
            // Используем буферизованное копирование для сохранения качества
            val fileName = buildFileName(context, uri, type)
            val file = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri).use { input ->
                FileOutputStream(file).use { output ->
                    input?.copyTo(output, bufferSize = 8192)
                }
            }
            Log.d("UploadWorker", "Файл скопирован без сжатия: ${file.absolutePath} (тип: ${type.name})")
            file
        } catch (e: IOException) {
            Log.e("UploadWorker", "Ошибка преобразования URI в файл: ${e.message}")
            null
        }
    }
    
    /**
     * Получить реальный путь к файлу из URI
     * Для файлов из галереи важно использовать оригинальный файл без обработки
     */
    private fun getRealPathFromURI(context: Context, uri: Uri): String? {
        return try {
            when {
                // Для file:// URI возвращаем путь напрямую
                uri.scheme == "file" -> uri.path
                
                // Для content:// URI пытаемся получить путь через MediaStore
                uri.scheme == "content" -> {
                    val projection = arrayOf(android.provider.MediaStore.Images.Media.DATA)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        val columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
                        if (cursor.moveToFirst()) {
                            cursor.getString(columnIndex)
                        } else null
                    }
                }
                
                else -> null
            }
        } catch (e: Exception) {
            Log.e("UploadWorker", "Ошибка получения пути к файлу: ${e.message}")
            null
        }
    }

    private fun buildFileName(context: Context, uri: Uri, type: UploadMediaType): String {
        val baseName = queryDisplayName(context, uri)
            ?: "upload_${System.currentTimeMillis()}"
        val extension = resolveExtension(context, uri, baseName, type)
        val sanitizedBase = baseName.substringBeforeLast('.')
        return "${sanitizedBase}_${System.currentTimeMillis()}.$extension"
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index != -1 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return null
    }

    private fun resolveExtension(
        context: Context,
        uri: Uri,
        baseName: String,
        type: UploadMediaType
    ): String {
        val existingExt = baseName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        if (existingExt.isNotBlank()) {
            return existingExt
        }
        val mimeType = context.contentResolver.getType(uri)
        return when {
            mimeType.equals("image/png", ignoreCase = true) -> "png"
            mimeType.equals("image/webp", ignoreCase = true) -> "webp"
            mimeType.equals("image/gif", ignoreCase = true) -> "gif"
            mimeType.equals("image/jpeg", ignoreCase = true) -> "jpg"
            type == UploadMediaType.PANORAMA -> "jpg"
            else -> "jpg"
        }
    }

    private fun showToast(context: Context, message: String) {
        context.postTopToast(message)
    }
}
