package com.professorx.mediadl.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.professorx.mediadl.LocalMediaColors
import com.professorx.mediadl.MediaKind
import com.professorx.mediadl.SavedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Composable
fun InbuiltMediaPlayerDialog(
    items: List<SavedMedia>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    val ctx = LocalContext.current
    val colors = LocalMediaColors.current
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, items.lastIndex)) }
    val item = items[currentIndex]
    val isPlayableMedia = item.kind == MediaKind.VIDEO || item.kind == MediaKind.REEL || item.kind == MediaKind.AUDIO
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    val exoPlayer = remember {
        ExoPlayer.Builder(ctx).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { exoPlayer.release() }
        }
    }

    LaunchedEffect(item.uriString, isPlayableMedia) {
        if (isPlayableMedia) {
            runCatching {
                exoPlayer.setMediaItem(MediaItem.fromUri(item.uri))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        } else {
            runCatching { exoPlayer.pause() }
        }
    }

    LaunchedEffect(playbackSpeed) {
        runCatching { exoPlayer.setPlaybackSpeed(playbackSpeed) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = onDismiss)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            var dragAccumulator = 0f
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .pointerInput(items.size, currentIndex) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragAccumulator < -120f && currentIndex < items.lastIndex) {
                                    currentIndex++
                                } else if (dragAccumulator > 120f && currentIndex > 0) {
                                    currentIndex--
                                }
                                dragAccumulator = 0f
                            },
                            onVerticalDrag = { _, dragAmount ->
                                dragAccumulator += dragAmount
                            }
                        )
                    }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close player", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = if (items.size > 1) "${item.platform} · ${currentIndex + 1} / ${items.size}" else item.platform
                        Text(sub, color = colors.textMuted, fontSize = 12.sp)
                    }
                    if (isPlayableMedia) {
                        FilterPill(
                            text = "${if (playbackSpeed == 1f) "1" else playbackSpeed}x",
                            selected = playbackSpeed != 1f,
                            onClick = {
                                playbackSpeed = when (playbackSpeed) {
                                    1f -> 1.5f
                                    1.5f -> 2f
                                    else -> 1f
                                }
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(onClick = { shareSavedMedia(ctx, item) }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share", tint = Color.White)
                    }
                    IconButton(onClick = { openSavedMedia(ctx, item) }) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = "Open externally", tint = colors.accent)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0A0A0A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlayableMedia) {
                        AndroidView(
                            factory = { context ->
                                PlayerView(context).apply {
                                    player = exoPlayer
                                    useController = true
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                }
                            },
                            update = { view ->
                                if (view.player !== exoPlayer) view.player = exoPlayer
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val bmp by produceState<Bitmap?>(initialValue = null, item.uriString) {
                            value = withContext(Dispatchers.IO) {
                                runCatching {
                                    ctx.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it) }
                                }.getOrNull()
                            }
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp!!.asImageBitmap(),
                                contentDescription = item.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("Unable to load image preview.", color = colors.textMuted)
                        }
                    }
                }

                if (items.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            enabled = currentIndex > 0,
                            onClick = { currentIndex-- }
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Previous",
                                tint = if (currentIndex > 0) Color.White else colors.textMuted
                            )
                        }
                        Text(
                            "Swipe or tap arrows (${currentIndex + 1}/${items.size})",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        IconButton(
                            enabled = currentIndex < items.lastIndex,
                            onClick = { currentIndex++ }
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Next",
                                tint = if (currentIndex < items.lastIndex) Color.White else colors.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InbuiltMediaPlayerDialog(
    item: SavedMedia,
    onDismiss: () -> Unit
) {
    InbuiltMediaPlayerDialog(
        items = listOf(item),
        initialIndex = 0,
        onDismiss = onDismiss
    )
}
