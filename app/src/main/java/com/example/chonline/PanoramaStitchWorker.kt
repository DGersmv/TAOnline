package com.example.chonline

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class PanoramaStitchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uriStrings = inputData.getStringArray(KEY_INPUT_URIS) ?: return Result.failure()
        if (uriStrings.isEmpty()) {
            Log.e(TAG, "Нет входных файлов для склейки")
            return Result.failure()
        }

        val uris = uriStrings.map { Uri.parse(it) }
        Log.d(TAG, "Старт фоновой склейки панорамы (${uris.size} кадров)")

        return try {
            val stitcher = PanoramaStitcher()
            val resultUri = stitcher.stitchPanorama(uris, applicationContext)

            if (resultUri != null) {
                cleanupSourceShots(uris)
                Log.d(TAG, "Фоновая склейка завершена: $resultUri")
                Result.success(workDataOf(KEY_OUTPUT_URI to resultUri.toString()))
            } else {
                Log.e(TAG, "Склейка завершилась без результата")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка фоновой склейки: ${e.message}", e)
            Result.retry()
        }
    }

    private fun cleanupSourceShots(uris: List<Uri>) {
        uris.forEach { uri ->
            runCatching {
                val rows = applicationContext.contentResolver.delete(uri, null, null)
                Log.d(TAG, "Удаление временного кадра $uri -> $rows")
            }.onFailure { error ->
                Log.w(TAG, "Не удалось удалить кадр $uri: ${error.message}")
            }
        }
    }

    companion object {
        const val KEY_INPUT_URIS = "PANORAMA_INPUT_URIS"
        const val KEY_OUTPUT_URI = "PANORAMA_OUTPUT_URI"
        const val KEY_OBJECT_ID = "PANORAMA_OBJECT_ID"
        private const val TAG = "PanoramaStitchWorker"

        fun createInputData(imageUris: List<Uri>, objectId: String?): Data {
            val builder = Data.Builder()
                .putStringArray(KEY_INPUT_URIS, imageUris.map { it.toString() }.toTypedArray())
            if (!objectId.isNullOrEmpty()) {
                builder.putString(KEY_OBJECT_ID, objectId)
            }
            return builder.build()
        }
    }
}
