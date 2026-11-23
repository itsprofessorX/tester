package com.professorx.mediadl

import android.content.Context
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(48) {}

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bmp: Bitmap) {
        cache.put(key, bmp)
    }

    suspend fun loadLocal(ctx: Context, uri: Uri, kind: MediaKind): Bitmap? = withContext(Dispatchers.IO) {
        val key = "local:${uri}"
        get(key)?.let { return@withContext it }
        val bmp = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ctx.contentResolver.loadThumbnail(uri, Size(360, 360), null)
            } else null
        } catch (_: Throwable) {
            null
        } ?: try {
            if (kind == MediaKind.VIDEO || kind == MediaKind.REEL) {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(ctx, uri)
                val frame = mmr.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                mmr.release()
                frame
            } else if (kind == MediaKind.PHOTO || kind == MediaKind.CAROUSEL) {
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(ins, null, opts)
                }
            } else null
        } catch (_: Throwable) {
            null
        }
        if (bmp != null) put(key, bmp)
        bmp
    }

    suspend fun loadRemote(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val key = "remote:$url"
        get(key)?.let { return@withContext it }
        val bmp = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } else null
        } catch (_: Throwable) {
            null
        }
        if (bmp != null) put(key, bmp)
        bmp
    }
}

@Composable
fun MediaThumbnail(
    uri: Uri? = null,
    remoteUrl: String? = null,
    kind: MediaKind = MediaKind.VIDEO,
    exists: Boolean = true,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val colors = LocalMediaColors.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, remoteUrl, exists) {
        value = when {
            uri != null && exists && kind != MediaKind.AUDIO -> ThumbnailCache.loadLocal(ctx, uri, kind)
            !remoteUrl.isNullOrBlank() -> ThumbnailCache.loadRemote(remoteUrl)
            else -> null
        }
    }

    Box(
        modifier = modifier.background(colors.surfaceElevated),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            val icon = when {
                !exists -> Icons.Rounded.BrokenImage
                kind == MediaKind.AUDIO -> Icons.Rounded.AudioFile
                kind == MediaKind.PHOTO || kind == MediaKind.CAROUSEL -> Icons.Rounded.Image
                else -> Icons.Rounded.Movie
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (!exists) colors.error else colors.textMuted
            )
        }
    }
}
