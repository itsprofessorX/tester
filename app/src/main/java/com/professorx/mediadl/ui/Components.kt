package com.professorx.mediadl.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.professorx.mediadl.LinkPreview
import com.professorx.mediadl.LocalMediaColors
import com.professorx.mediadl.MediaKind
import com.professorx.mediadl.MediaThumbnail
import com.professorx.mediadl.PreviewFormatOption
import com.professorx.mediadl.SavedMedia
import com.professorx.mediadl.ToolboxAction
import com.professorx.mediadl.formatBytes
import com.professorx.mediadl.formatDuration

fun <T> elasticSpring() = spring<T>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

fun Modifier.elasticClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = elasticSpring(),
        label = "elasticPress"
    )
    this
        .scale(scale)
        .clickable(
            interactionSource = src,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalMediaColors.current
    val base = modifier
        .fillMaxWidth()
        .animateContentSize(animationSpec = elasticSpring())
        .clip(RoundedCornerShape(16.dp))
        .background(colors.surface)
        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
        .let { if (onClick != null) it.elasticClickable(onClick = onClick) else it }
        .padding(16.dp)

    Column(modifier = base, content = content)
}

@Composable
fun MinimalRowItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalMediaColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = elasticSpring())
            .let { if (onClick != null) it.elasticClickable(onClick = onClick) else it }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            content = content
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMediaColors.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = elasticSpring(),
        label = "pillScale"
    )
    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .elasticClickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.accent else colors.surfaceElevated,
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.border)
    ) {
        Text(
            text = text,
            color = if (selected) colors.onAccent else colors.textMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
fun SmoothProgressBar(
    progress: Float,
    indeterminate: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalMediaColors.current
    val p by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) tween(0) else elasticSpring(),
        label = "progress"
    )
    val inf = rememberInfiniteTransition(label = "indeterminate")
    val t by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reducedMotion) 2400 else 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t"
    )

    Canvas(modifier = modifier.fillMaxWidth().height(5.dp)) {
        val h = size.height
        val r = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(colors.border, cornerRadius = r)
        if (indeterminate) {
            val seg = size.width * 0.3f
            val x = (size.width + seg) * t - seg
            val left = x.coerceAtLeast(0f)
            val right = (x + seg).coerceAtMost(size.width)
            if (right > left) {
                drawRoundRect(colors.accent, Offset(left, 0f), Size(right - left, h), r)
            }
        } else {
            val w = size.width * p
            if (w > 0f) {
                drawRoundRect(colors.accent, Offset(0f, 0f), Size(w, h), r)
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = LocalMediaColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.textMuted)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = colors.textMuted, fontSize = 13.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Text(actionLabel, color = colors.textPrimary)
            }
        }
    }
}

@Composable
fun CelebrationBanner(
    title: String?,
    reducedMotion: Boolean,
    onDismiss: () -> Unit
) {
    val colors = LocalMediaColors.current
    AnimatedVisibility(
        visible = title != null,
        enter = if (reducedMotion) fadeIn(tween(0)) else slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        ) { -it } + fadeIn(),
        exit = if (reducedMotion) fadeOut(tween(0)) else slideOutVertically { -it } + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.good.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.good)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Download complete!", color = colors.good, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        title.orEmpty(),
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun SmartPreviewCard(
    preview: LinkPreview,
    selectedOption: PreviewFormatOption?,
    onSelectOption: (PreviewFormatOption) -> Unit,
    onDownload: (selectedSlides: Set<Int>?, audioMp3: Boolean) -> Unit,
    onOpenLogin: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalMediaColors.current
    val clipboard = LocalClipboardManager.current
    var selectedSlides by remember(preview.url) {
        mutableStateOf(preview.slides.map { it.index }.toSet())
    }
    var captionCopied by remember(preview.url) { mutableStateOf(false) }

    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Smart Link Preview", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close preview", tint = colors.textMuted, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaThumbnail(
                remoteUrl = preview.thumbnailUrl,
                kind = preview.mediaKind,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    preview.title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val metaParts = buildList {
                    add(preview.platform)
                    add(if (preview.itemCount > 1) "${preview.mediaKind.label} (${preview.itemCount})" else preview.mediaKind.label)
                    if (preview.creator.isNotBlank() && preview.creator != preview.platform) add(preview.creator)
                    preview.durationSeconds?.let { add(formatDuration(it)) }
                }
                Text(
                    metaParts.joinToString(" · "),
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (preview.requiresLoginHint) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceElevated)
                    .elasticClickable { onOpenLogin() }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Instagram blocked anonymous carousel/post access — tap to connect Instagram & unlock all slides.",
                    color = colors.textPrimary,
                    fontSize = 12.sp
                )
            }
        }

        if (preview.caption.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceElevated)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    preview.caption,
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(preview.caption))
                        captionCopied = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (captionCopied) "Copied" else "Copy", color = colors.accent, fontSize = 11.sp)
                }
            }
        }

        if (preview.slides.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Carousel Slides (${selectedSlides.size}/${preview.slides.size})",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        selectedSlides = if (selectedSlides.size == preview.slides.size) emptySet()
                        else preview.slides.map { it.index }.toSet()
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (selectedSlides.size == preview.slides.size) "Clear all" else "Select all",
                        color = colors.accent,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                preview.slides.forEach { slide ->
                    val isSelected = slide.index in selectedSlides
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) colors.accent else colors.border,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .elasticClickable {
                                selectedSlides = if (isSelected) selectedSlides - slide.index else selectedSlides + slide.index
                            }
                    ) {
                        MediaThumbnail(
                            remoteUrl = slide.thumbnailUrl ?: slide.mediaUrl,
                            kind = if (slide.isVideo) MediaKind.VIDEO else MediaKind.PHOTO,
                            modifier = Modifier.matchParentSize()
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Quality & Format", color = colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            preview.options.forEach { opt ->
                val sizeStr = opt.estimatedSizeBytes?.let { " · ${formatBytes(it)}" } ?: ""
                FilterPill(
                    text = "${opt.qualityLabel} (${opt.format.label})$sizeStr",
                    selected = selectedOption?.id == opt.id,
                    onClick = { onSelectOption(opt) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = preview.slides.size <= 1 || selectedSlides.isNotEmpty(),
                onClick = { onDownload(if (preview.slides.size > 1) selectedSlides else null, false) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val label = when {
                    preview.slides.size > 1 -> "Download ${selectedSlides.size} Slide(s)"
                    selectedOption != null -> "Download ${selectedOption.qualityLabel} ${selectedOption.format.label}"
                    else -> "Download"
                }
                Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (preview.mediaKind == MediaKind.VIDEO || preview.mediaKind == MediaKind.REEL) {
                OutlinedButton(
                    onClick = { onDownload(null, true) },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Icon(Icons.Rounded.AudioFile, contentDescription = "Save Audio MP3", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun DeleteMediaDialog(
    item: SavedMedia,
    onDismiss: () -> Unit,
    onDeleteHistoryOnly: () -> Unit,
    onDeleteFromDevice: () -> Unit
) {
    val colors = LocalMediaColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textMuted,
        title = { Text("Delete media?", fontWeight = FontWeight.SemiBold) },
        text = {
            Text("Choose whether to remove \"${item.name}\" from MediaDown history only, or permanently delete the file from your device storage.")
        },
        confirmButton = {
            Button(
                onClick = onDeleteFromDevice,
                colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = Color.White)
            ) {
                Text("Delete file")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textMuted) }
                TextButton(onClick = onDeleteHistoryOnly) { Text("History only", color = colors.textPrimary) }
            }
        }
    )
}

@Composable
fun MediaToolboxDialog(
    item: SavedMedia,
    busy: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onRun: (ToolboxAction, String, Int, Int, Int) -> Unit
) {
    val colors = LocalMediaColors.current
    var selectedAction by remember { mutableStateOf(ToolboxAction.EXTRACT_AUDIO) }
    var audioExt by remember { mutableStateOf("mp3") }
    var startSecText by remember { mutableStateOf("0") }
    var endSecText by remember { mutableStateOf("15") }
    var crf by remember { mutableIntStateOf(28) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textMuted,
        title = { Text("Media Toolbox", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.name, color = colors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ToolboxAction.entries.forEach { act ->
                        FilterPill(
                            text = act.label,
                            selected = selectedAction == act,
                            onClick = { selectedAction = act }
                        )
                    }
                }
                Text(selectedAction.subtitle, fontSize = 12.sp, color = colors.textMuted)

                when (selectedAction) {
                    ToolboxAction.EXTRACT_AUDIO, ToolboxAction.CONVERT_AUDIO -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("mp3", "m4a", "opus", "wav").forEach { ext ->
                                FilterPill(ext.uppercase(), audioExt == ext, { audioExt = ext })
                            }
                        }
                    }
                    ToolboxAction.TRIM_MEDIA -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startSecText,
                                onValueChange = { startSecText = it.filter(Char::isDigit) },
                                label = { Text("Start (s)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = endSecText,
                                onValueChange = { endSecText = it.filter(Char::isDigit) },
                                label = { Text("End (s)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                    ToolboxAction.COMPRESS_VIDEO -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterPill("Balanced (26)", crf == 26, { crf = 26 })
                            FilterPill("Smaller (30)", crf == 30, { crf = 30 })
                            FilterPill("Compact (34)", crf == 34, { crf = 34 })
                        }
                    }
                    ToolboxAction.EXTRACT_THUMBNAIL -> {
                        OutlinedTextField(
                            value = startSecText,
                            onValueChange = { startSecText = it.filter(Char::isDigit) },
                            label = { Text("Frame time (seconds)") },
                            singleLine = true
                        )
                    }
                }

                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(message ?: "Processing…", color = colors.textPrimary, fontSize = 12.sp)
                    }
                } else if (!message.isNullOrBlank()) {
                    Text(message, color = if (message.startsWith("✅")) colors.good else colors.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    onRun(
                        selectedAction,
                        audioExt,
                        startSecText.toIntOrNull() ?: 0,
                        endSecText.toIntOrNull() ?: 15,
                        crf
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
            ) {
                Text("Run")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text("Close", color = colors.textMuted)
            }
        }
    )
}

fun openSavedMedia(ctx: Context, item: SavedMedia) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(item.uri, item.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    } catch (_: Exception) {}
}

fun shareSavedMedia(ctx: Context, item: SavedMedia) {
    try {
        val i = Intent(Intent.ACTION_SEND)
            .setType(item.mime)
            .putExtra(Intent.EXTRA_STREAM, item.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Share"))
    } catch (_: Exception) {}
}
