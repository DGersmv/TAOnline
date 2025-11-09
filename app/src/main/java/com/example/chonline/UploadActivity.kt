package com.example.chonline

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

import com.example.chonline.UploadMediaType

private const val TAG = "UploadActivity"

class UploadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageUris = intent.getStringArrayListExtra("IMAGE_URIS") ?: arrayListOf()
        val mediaTypesRaw = intent.getStringArrayListExtra("MEDIA_TYPES") ?: arrayListOf()
        val objectId = intent.getStringExtra("OBJECT_ID") ?: "0"
        val isVisibleToCustomer = intent.getBooleanExtra("IS_VISIBLE_TO_CUSTOMER", false)

        val resolvedTypes = resolveMediaTypes(imageUris, mediaTypesRaw)

        Log.d(TAG, "Получены файлы: $imageUris")
        Log.d(TAG, "Типы файлов: $resolvedTypes")
        Log.d(TAG, "Объект ID: $objectId")
        Log.d(TAG, "Видимо для заказчика: $isVisibleToCustomer")

        if (imageUris.isEmpty()) {
            showTopToast("Ошибка: файлы не переданы!")
            Log.e(TAG, "Ошибка: imageUris пустой!")
            finish()
            return
        }

        setContent {
            UploadScreen(imageUris, resolvedTypes, objectId, isVisibleToCustomer) { finish() }
        }
    }
}

@Composable
fun UploadScreen(
    imageUris: List<String>,
    mediaTypes: List<String>,
    objectId: String,
    isVisibleToCustomer: Boolean,
    onUploadComplete: () -> Unit
) {
    val context = LocalContext.current
    val uploadStatus = remember { mutableStateOf("Подготовка к загрузке...") }

    LaunchedEffect(Unit) {
        startUploadWork(context, imageUris, mediaTypes, objectId, isVisibleToCustomer)
        uploadStatus.value = "Файлы поставлены в очередь загрузки..."
        delay(2000)
        onUploadComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(uploadStatus.value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun resolveMediaTypes(imageUris: List<String>, mediaTypesRaw: List<String>): List<String> {
    if (imageUris.isEmpty()) return emptyList()
    if (mediaTypesRaw.size != imageUris.size) {
        return List(imageUris.size) { UploadMediaType.PHOTO.name }
    }
    return mediaTypesRaw
}

fun startUploadWork(
    context: Context,
    imageUris: List<String>,
    mediaTypes: List<String>,
    objectId: String,
    isVisibleToCustomer: Boolean = false
) {
    val workManager = WorkManager.getInstance(context)

    val uploadWork = OneTimeWorkRequestBuilder<UploadWorker>()
        .setInputData(workDataOf(
            "IMAGE_URIS" to imageUris.toTypedArray(),
            "MEDIA_TYPES" to mediaTypes.toTypedArray(),
            "OBJECT_ID" to objectId,
            "IS_VISIBLE_TO_CUSTOMER" to isVisibleToCustomer
        ))
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            1,
            TimeUnit.MINUTES
        )
        .build()

    workManager.enqueue(uploadWork)
}


