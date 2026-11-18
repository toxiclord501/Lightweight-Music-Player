package com.txc.mp.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small in-memory cache so scrolling a list (or the same song reappearing in
 * the mini player / now playing / queue) doesn't repeatedly re-decode the
 * same thumbnail from disk. Capped at a fraction of the app's heap so it
 * can't itself become a memory problem.
 */
private object AlbumArtCache {
    private val cache = object : LruCache<String, Bitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun cacheSizeBytes(): Int {
        val maxHeapKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (maxHeapKb / 16) * 1024 // ~1/16th of the app's heap
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

private fun loadEmbeddedArt(context: android.content.Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val bytes = retriever.embeddedPicture ?: return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    } finally {
        try { retriever.release() } catch (e: Exception) { }
    }
}

/**
 * Loads (with caching) the raw [Bitmap] for a song's album art. Shared by
 * [AlbumArt] itself and by anything else that needs the actual pixels (e.g.
 * palette extraction for the Now Playing background), so the image is only
 * decoded once per song rather than once per consumer.
 */
@Composable
fun rememberAlbumArtBitmap(uri: Uri): Bitmap? {
    val context = LocalContext.current
    val cacheKey = remember(uri) { uri.toString() }
    var bitmap by remember(uri) { mutableStateOf(AlbumArtCache.get(cacheKey)) }

    LaunchedEffect(uri) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            // MediaStore's thumbnail cache is the fast path, but it's not
            // reliable for every track (unindexed files, missing/stale cache
            // entries) - some songs silently come back null. When that
            // happens, fall back to reading embedded art straight out of the
            // audio file's own tags, which works regardless of MediaStore.
            val fromMediaStore = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // 512px is enough to stay sharp as the large cover art AND
                    // as a blurred full-screen background - the previous
                    // 160px was fine for a small thumbnail but produced a
                    // blotchy, shapeless blur once stretched to fill the
                    // whole screen and blurred on top of that.
                    context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                } else null
            } catch (e: Exception) {
                null
            }
            fromMediaStore ?: loadEmbeddedArt(context, uri)
        }
        if (loaded != null) AlbumArtCache.put(cacheKey, loaded)
        bitmap = loaded
    }

    return bitmap
}

@Composable
fun AlbumArt(uri: Uri, modifier: Modifier = Modifier) {
    val bitmap = rememberAlbumArtBitmap(uri)

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
