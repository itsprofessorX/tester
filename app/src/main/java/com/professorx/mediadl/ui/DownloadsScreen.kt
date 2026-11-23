package com.professorx.mediadl.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.professorx.mediadl.DlViewModel
import com.professorx.mediadl.DownloadStatus
import com.professorx.mediadl.DownloadTask
import com.professorx.mediadl.LocalMediaColors
import com.professorx.mediadl.MediaThumbnail
import com.professorx.mediadl.NavTab
import com.professorx.mediadl.formatBytes
import com.professorx.mediadl.formatDuration

private enum class DownloadFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    QUEUED("Queued"),
    COMPLETED("Completed"),
    FAILED("Failed")
}

@Composable
fun DownloadsScreen(vm: DlViewModel) {
    val colors = LocalMediaColors.current
    val ctx = LocalContext.current
    var filter by remember { mutableStateOf(DownloadFilter.ALL) }

    val filtered = remember(vm.tasks.toList(), filter) {
        when (filter) {
            DownloadFilter.ALL -> vm.tasks.toList()
            DownloadFilter.ACTIVE -> vm.tasks.filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONVERTING || it.status == DownloadStatus.PAUSED
            }
            DownloadFilter.QUEUED -> vm.tasks.filter { it.status == DownloadStatus.QUEUED }
            DownloadFilter.COMPLETED -> vm.tasks.filter { it.status == DownloadStatus.COMPLETED }
            DownloadFilter.FAILED -> vm.tasks.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Downloads", color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    val activeCount = vm.tasks.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
                    Text("$activeCount active or queued", color = colors.textMuted, fontSize = 13.sp)
                }
                if (vm.tasks.any {
                        it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED
                    }
                ) {
                    TextButton(onClick = { vm.clearFinishedTasks() }) {
                        Text("Clear finished", color = colors.accent, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DownloadFilter.entries.forEach { f ->
                    FilterPill(
                        text = f.label,
                        selected = filter == f,
                        onClick = { filter = f }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Download,
                    title = "No downloads in ${filter.label.lowercase()}",
                    subtitle = "Paste a link on Home or share from Instagram to queue downloads.",
                    actionLabel = "Go to Home",
                    onAction = { vm.activeTab = NavTab.HOME }
                )
            }
        } else {
            items(filtered, key = { it.id }) { task ->
                DownloadTaskCard(
                    task = task,
                    reducedMotion = vm.prefs.reducedMotion,
                    onPause = { vm.pauseTask(task.id) },
                    onResume = { vm.resumeTask(task.id) },
                    onCancel = { vm.cancelTask(task.id) },
                    onRetry = { vm.retryTask(task.id) },
                    onDelete = { vm.deleteTask(task.id) },
                    onOpen = {
                        if (!task.savedUriString.isNullOrBlank() && !task.savedMime.isNullOrBlank()) {
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(Uri.parse(task.savedUriString), task.savedMime)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                )
                            } catch (_: Exception) {}
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    reducedMotion: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    val colors = LocalMediaColors.current
    val statusColor = when (task.status) {
        DownloadStatus.COMPLETED -> colors.good
        DownloadStatus.FAILED, DownloadStatus.CANCELLED -> colors.error
        DownloadStatus.PAUSED -> colors.warning
        DownloadStatus.DOWNLOADING, DownloadStatus.CONVERTING -> colors.accent
        DownloadStatus.QUEUED -> colors.textMuted
    }

    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaThumbnail(
                remoteUrl = task.thumbnailUrl,
                kind = task.mediaKind,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${task.platform} · ${task.qualityLabel} ${task.format.label}",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    task.status.label,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Individual controls
            when (task.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.CONVERTING, DownloadStatus.QUEUED -> {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Rounded.Pause, contentDescription = "Pause", tint = colors.textPrimary)
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Cancel, contentDescription = "Cancel", tint = colors.textMuted)
                    }
                }
                DownloadStatus.PAUSED -> {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume", tint = colors.accent)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = colors.error)
                    }
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Retry", tint = colors.accent)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = colors.error)
                    }
                }
                DownloadStatus.COMPLETED -> {
                    if (!task.savedUriString.isNullOrBlank()) {
                        IconButton(onClick = onOpen) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = "Open", tint = colors.accent)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = colors.textMuted)
                    }
                }
            }
        }

        if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.CONVERTING || task.status == DownloadStatus.PAUSED) {
            Spacer(Modifier.height(10.dp))
            SmoothProgressBar(
                progress = task.progress,
                indeterminate = task.indeterminate && task.status != DownloadStatus.PAUSED,
                reducedMotion = reducedMotion
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val pctText = if (task.indeterminate) task.stage else "${(task.progress * 100).toInt()}% · ${task.stage}"
                Text(pctText, color = colors.textMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                val metrics = buildList {
                    if (task.speedBytesPerSec > 0L) add("${formatBytes(task.speedBytesPerSec)}/s")
                    task.etaSeconds?.let { add("ETA ${formatDuration(it)}") }
                }.joinToString(" · ")
                if (metrics.isNotBlank()) {
                    Text(metrics, color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (!task.errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(task.errorMessage, color = colors.error, fontSize = 12.sp)
        }
    }
}
