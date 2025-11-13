package com.example.chonline

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage

class MediaPickerActivity : ComponentActivity() {

    private lateinit var filter: MediaPickerFilter
    private var permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filter = runCatching {
            MediaPickerFilter.valueOf(
                intent.getStringExtra(EXTRA_FILTER) ?: MediaPickerFilter.PHOTO.name
            )
        }.getOrElse { MediaPickerFilter.PHOTO }

        val permission = requiredPermission()
        if (permission == null || ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            setupContent()
        } else {
            permissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    setupContent()
                } else {
                    showTopToast("Доступ к файлам не предоставлен")
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
            permissionLauncher?.launch(permission)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        permissionLauncher = null
    }

    private fun setupContent() {
        setContent {
            MediaPickerScreen(
                filter = filter,
                onCancel = {
                    setResult(RESULT_CANCELED)
                    finish()
                },
                onConfirm = { uris ->
                    if (uris.isEmpty()) {
                        setResult(RESULT_CANCELED)
                    } else {
                        val data = Intent().apply {
                            putParcelableArrayListExtra(RESULT_URIS, ArrayList(uris))
                            putExtra(RESULT_FILTER, filter.name)
                        }
                        setResult(RESULT_OK, data)
                    }
                    finish()
                }
            )
        }
    }

    private fun requiredPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    companion object {
        const val EXTRA_FILTER = "media_picker_filter"
        const val RESULT_URIS = "media_picker_result_uris"
        const val RESULT_FILTER = "media_picker_result_filter"

        fun createIntent(context: android.content.Context, filter: MediaPickerFilter): Intent {
            return Intent(context, MediaPickerActivity::class.java).apply {
                putExtra(EXTRA_FILTER, filter.name)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MediaPickerScreen(
    filter: MediaPickerFilter,
    onCancel: () -> Unit,
    onConfirm: (List<android.net.Uri>) -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var buckets by remember { mutableStateOf<List<MediaBucket>>(emptyList()) }
    var selectedBucket by remember { mutableStateOf<MediaBucket?>(null) }
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val selectedUris = remember { mutableStateListOf<android.net.Uri>() }

    val loadBuckets: suspend () -> Unit = {
        val loadedBuckets = MediaStoreRepository.loadBuckets(context)
        buckets = buildList {
            add(MediaBucket(null, "Все изображения", null))
            addAll(loadedBuckets)
        }
        selectedBucket = MediaStoreRepository.guessDefaultCameraBucket(loadedBuckets)
        if (selectedBucket == null && buckets.isNotEmpty()) {
            selectedBucket = buckets.first()
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        loadBuckets()
        val bucketId = selectedBucket?.id
        mediaItems = MediaStoreRepository.loadMediaItems(context, bucketId, filter)
        isLoading = false
    }

    LaunchedEffect(selectedBucket, filter) {
        val bucketId = selectedBucket?.id
        isLoading = true
        mediaItems = MediaStoreRepository.loadMediaItems(context, bucketId, filter)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (filter) {
                                MediaPickerFilter.PHOTO -> "Выбор фото"
                                MediaPickerFilter.PANORAMA -> "Выбор панорам"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        val bucketName = selectedBucket?.displayName ?: "Все изображения"
                        Text(
                            text = bucketName,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Выбрано: ${selectedUris.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onCancel) {
                            Text("Отмена")
                        }
                        Button(
                            onClick = { onConfirm(selectedUris.toList()) },
                            enabled = selectedUris.isNotEmpty()
                        ) {
                            Text("Готово")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (buckets.size > 1) {
                LazyRowBuckets(
                    buckets = buckets,
                    selected = selectedBucket,
                    onSelect = { bucket ->
                        if (bucket != selectedBucket) {
                            selectedBucket = if (bucket.id == null) null else bucket
                        }
                    }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Нет файлов в этой папке")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mediaItems, key = { it.uri }) { item ->
                        val isSelected = selectedUris.contains(item.uri)
                        MediaGridItem(
                            item = item,
                            selected = isSelected,
                            onToggle = {
                                if (isSelected) {
                                    selectedUris.remove(item.uri)
                                } else {
                                    selectedUris.add(item.uri)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyRowBuckets(
    buckets: List<MediaBucket>,
    selected: MediaBucket?,
    onSelect: (MediaBucket) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        buckets.forEach { bucket ->
            val isSelected = selected?.id == bucket.id
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(bucket) },
                label = { Text(bucket.displayName) }
            )
        }
    }
}

@Composable
private fun MediaGridItem(
    item: MediaItem,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black.copy(alpha = 0.05f))
            .clickable { onToggle() }
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            modifier = Modifier.fillMaxSize()
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Выбрано",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
            )
        }
    }
}

