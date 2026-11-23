package com.professorx.mediadl.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.professorx.mediadl.DlViewModel
import com.professorx.mediadl.LinkPreviewer
import com.professorx.mediadl.LocalMediaColors
import com.professorx.mediadl.MediaThumbnail
import com.professorx.mediadl.NavTab
import com.professorx.mediadl.SavedMedia
import com.professorx.mediadl.formatBytes
import com.professorx.mediadl.formatRelativeTime

@Composable
fun HomeScreen(
    vm: DlViewModel,
    onOpenInstagramLogin: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val colors = LocalMediaColors.current
    val site = remember(vm.url) { if (vm.url.isNotBlank()) LinkPreviewer.platformOf(vm.url) else null }
    var deleteCandidate by remember { mutableStateOf<SavedMedia?>(null) }
    var playingMedia by remember { mutableStateOf<SavedMedia?>(null) }

    fun triggerHaptic(type: HapticFeedbackType) {
        if (vm.prefs.hapticsEnabled) haptic.performHapticFeedback(type)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("MediaDown", color = colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Your media space.", color = colors.textMuted, fontSize = 13.sp)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = if (vm.ready) "Ready" else "Starting…",
                        color = if (vm.ready) colors.good else colors.warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Rounded.Notifications, contentDescription = "Notifications", tint = colors.textPrimary)
                }
            }
        }

        // Clipboard Intelligence Banner
        item {
            AnimatedVisibility(visible = vm.clipboardSuggestion != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceElevated,
                    border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Link detected in clipboard", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                vm.clipboardSuggestion.orEmpty(),
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = {
                            triggerHaptic(HapticFeedbackType.TextHandleMove)
                            vm.applyClipboardSuggestion()
                        }) {
                            Text("Preview →", color = colors.accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        IconButton(onClick = { vm.dismissClipboardSuggestion() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Main Link Input & Smart Download Assistant Card
        item {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Download anything", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (site != null && site != "Web") {
                        Text(site, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vm.url,
                    onValueChange = { vm.url = it },
                    placeholder = { Text("Paste your media link here…", color = colors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accent
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            triggerHaptic(HapticFeedbackType.TextHandleMove)
                            clipboard.getText()?.text?.let {
                                vm.url = it
                                vm.previewLink(it)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.border),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paste from clipboard", color = colors.textPrimary, fontSize = 12.sp)
                    }
                    if (vm.url.isNotBlank()) {
                        TextButton(onClick = { vm.url = ""; vm.clearPreview() }) {
                            Text("Clear", color = colors.textMuted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("IG · YT · More", color = colors.textMuted, fontSize = 11.sp)
                }

                if (site == "Instagram" && !vm.igLoggedIn) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Public posts & reels work without login · Connect Instagram only for private posts & stories ›",
                        color = colors.accent,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onOpenInstagramLogin() }.padding(vertical = 4.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = vm.ready && vm.url.isNotBlank() && !vm.isPreviewLoading,
                        onClick = {
                            triggerHaptic(HapticFeedbackType.LongPress)
                            vm.previewLink()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                    ) {
                        if (vm.isPreviewLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.onAccent)
                            Spacer(Modifier.width(8.dp))
                            Text("Inspecting…", fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Preview link →", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OutlinedButton(
                        enabled = vm.ready && vm.url.isNotBlank(),
                        onClick = {
                            triggerHaptic(HapticFeedbackType.LongPress)
                            vm.enqueueUrl(vm.url)
                            vm.activeTab = NavTab.DOWNLOADS
                        },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = "Quick download", tint = colors.textPrimary)
                    }
                }
            }
        }

        // Preview Error
        if (!vm.previewError.isNullOrBlank()) {
            item {
                SurfaceCard {
                    Text(vm.previewError.orEmpty(), color = colors.error, fontSize = 13.sp)
                }
            }
        }

        // Smart Link Preview & Quality Selector
        if (vm.currentPreview != null) {
            item {
                SmartPreviewCard(
                    preview = vm.currentPreview!!,
                    selectedOption = vm.selectedOption,
                    onSelectOption = { vm.selectedOption = it },
                    onDownload = { selectedSlides, audioMp3 ->
                        triggerHaptic(HapticFeedbackType.LongPress)
                        vm.downloadFromPreview(selectedSlides, audioMp3)
                    },
                    onOpenLogin = onOpenInstagramLogin,
                    onDismiss = { vm.clearPreview() }
                )
            }
        }


        // Recent Downloads Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent downloads", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.activeTab = NavTab.LIBRARY }) {
                    Text("See all →", color = colors.accent, fontSize = 13.sp)
                }
            }
        }

        if (vm.history.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Download,
                    title = "No downloads yet",
                    subtitle = "Paste a link or share from Instagram to start saving media."
                )
            }
        } else {
            items(vm.history.take(5), key = { it.id }) { item ->
                SurfaceCard(onClick = { playingMedia = item }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MediaThumbnail(
                            uri = item.uri,
                            kind = item.kind,
                            exists = item.existsInGallery,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.name,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${item.platform} · ${formatBytes(item.sizeBytes)} · ${formatRelativeTime(item.timestamp)}",
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { shareSavedMedia(ctx, item) }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { deleteCandidate = item }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = colors.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    playingMedia?.let { item ->
        InbuiltMediaPlayerDialog(item = item, onDismiss = { playingMedia = null })
    }

    deleteCandidate?.let { item ->
        DeleteMediaDialog(
            item = item,
            onDismiss = { deleteCandidate = null },
            onDeleteHistoryOnly = {
                vm.deleteSavedMedia(item, deleteFromDevice = false)
                deleteCandidate = null
            },
            onDeleteFromDevice = {
                vm.deleteSavedMedia(item, deleteFromDevice = true)
                deleteCandidate = null
            }
        )
    }
}

