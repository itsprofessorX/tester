package com.professorx.mediadl.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Build
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.professorx.mediadl.LocalMediaColors
import com.professorx.mediadl.MediaKind
import com.professorx.mediadl.SavedMedia
import com.professorx.mediadl.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun InbuiltMediaPlayerDialog(
    items: List<SavedMedia>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = onDismiss)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val isCurrent = pagerState.currentPage == page
                ReelMediaPage(
                    item = items[page],
                    isActivePage = isCurrent,
                    pageNumber = page + 1,
                    totalPages = items.size,
                    onDismiss = onDismiss
                )
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

@Composable
private fun ReelMediaPage(
    item: SavedMedia,
    isActivePage: Boolean,
    pageNumber: Int,
    totalPages: Int,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val colors = LocalMediaColors.current
    val isVideo = item.kind == MediaKind.VIDEO || item.kind == MediaKind.REEL
    val isAudio = item.kind == MediaKind.AUDIO
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
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
                val sub = if (totalPages > 1) "${item.platform} · $pageNumber / $totalPages (swipe)" else item.platform
                Text(sub, color = colors.textMuted, fontSize = 12.sp)
            }
            if (isVideo) {
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

        when {
            isVideo -> {
                var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
                var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
                var isPlaying by remember { mutableStateOf(false) }
                var durationMs by remember { mutableIntStateOf(0) }
                var positionMs by remember { mutableIntStateOf(0) }

                LaunchedEffect(isActivePage, videoViewRef) {
                    val vv = videoViewRef ?: return@LaunchedEffect
                    if (isActivePage) {
                        vv.start()
                        isPlaying = true
                    } else if (vv.isPlaying) {
                        vv.pause()
                        isPlaying = false
                    }
                }

                LaunchedEffect(playbackSpeed, mediaPlayerRef) {
                    val mp = mediaPlayerRef ?: return@LaunchedEffect
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        runCatching {
                            mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
                        }
                    }
                }

                LaunchedEffect(isPlaying, videoViewRef, isActivePage) {
                    while (isActivePage) {
                        val vv = videoViewRef
                        if (vv != null && vv.isPlaying) {
                            positionMs = vv.currentPosition
                            if (durationMs <= 0 && vv.duration > 0) durationMs = vv.duration
                        }
                        delay(250)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0A0A0A)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { context ->
                            VideoView(context).apply {
                                setVideoURI(item.uri)
                                setOnPreparedListener { mp ->
                                    mediaPlayerRef = mp
                                    mp.isLooping = true
                                    durationMs = mp.duration.coerceAtLeast(0)
                                    if (isActivePage) {
                                        start()
                                        isPlaying = true
                                    }
                                }
                                setOnCompletionListener { isPlaying = false }
                                videoViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(12.dp))
                PlayerControlsBar(
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    accent = colors.accent,
                    onSeek = { target ->
                        positionMs = target
                        videoViewRef?.seekTo(target)
                    },
                    onTogglePlay = {
                        val vv = videoViewRef ?: return@PlayerControlsBar
                        if (vv.isPlaying) {
                            vv.pause()
                            isPlaying = false
                        } else {
                            vv.start()
                            isPlaying = true
                        }
                    }
                )
            }

            isAudio -> {
                var isPlaying by remember { mutableStateOf(false) }
                var durationMs by remember { mutableIntStateOf(0) }
                var positionMs by remember { mutableIntStateOf(0) }
                val player = remember(item.uriString) {
                    MediaPlayer().apply {
                        runCatching {
                            setDataSource(ctx, item.uri)
                            prepare()
                            durationMs = duration.coerceAtLeast(0)
                        }
                    }
                }

                LaunchedEffect(isActivePage, player) {
                    runCatching {
                        if (isActivePage) {
                            player.start()
                            isPlaying = true
                        } else if (player.isPlaying) {
                            player.pause()
                            isPlaying = false
                        }
                    }
                }

                DisposableEffect(player) {
                    onDispose {
                        runCatching {
                            if (player.isPlaying) player.stop()
                            player.release()
                        }
                    }
                }

                LaunchedEffect(player, isActivePage) {
                    while (isActivePage) {
                        runCatching {
                            if (player.isPlaying) positionMs = player.currentPosition
                            isPlaying = player.isPlaying
                        }
                        delay(250)
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141414)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = null, tint = colors.accent, modifier = Modifier.size(64.dp))
                    }
                }

                PlayerControlsBar(
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    accent = colors.accent,
                    onSeek = { target ->
                        positionMs = target
                        runCatching { player.seekTo(target) }
                    },
                    onTogglePlay = {
                        runCatching {
                            if (player.isPlaying) {
                                player.pause()
                                isPlaying = false
                            } else {
                                player.start()
                                isPlaying = true
                            }
                        }
                    }
                )
            }

            else -> {
                val bmp by produceState<Bitmap?>(initialValue = null, item.uriString) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            ctx.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it) }
                        }.getOrNull()
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp!!.asImageBitmap(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        Text("Unable to load image preview.", color = colors.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlsBar(
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    accent: Color,
    onSeek: (Int) -> Unit,
    onTogglePlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val maxVal = durationMs.coerceAtLeast(1).toFloat()
        Slider(
            value = positionMs.coerceIn(0, durationMs.coerceAtLeast(1)).toFloat(),
            onValueChange = { onSeek(it.toInt()) },
            valueRange = 0f..maxVal,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatDuration((positionMs / 1000L)), color = Color.White, fontSize = 12.sp)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .elasticClickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }
            Text(formatDuration((durationMs / 1000L)), color = Color(0xFF8E8E93), fontSize = 12.sp)
        }
    }
}
