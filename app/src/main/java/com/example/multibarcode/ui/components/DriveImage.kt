package com.example.multibarcode.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.multibarcode.data.DriveService

/** Small in-memory cache so scrolling/recomposition doesn't re-download Drive images. */
private object DriveImageCache {
    private val cache = LruCache<String, ImageBitmap>(80)
    fun get(id: String): ImageBitmap? = cache.get(id)
    fun put(id: String, bmp: ImageBitmap) = cache.put(id, bmp)
}

/** Loads and shows a product image stored on Drive by [fileId]; shows a placeholder while loading. */
@Composable
fun DriveImage(fileId: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(
        initialValue = fileId?.let { DriveImageCache.get(it) },
        key1 = fileId,
    ) {
        if (value == null && !fileId.isNullOrBlank()) {
            val bytes = DriveService.downloadPublicThumb(fileId)
            val decoded = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            decoded?.asImageBitmap()?.let {
                DriveImageCache.put(fileId, it)
                value = it
            }
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Default.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
