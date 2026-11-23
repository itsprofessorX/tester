package com.professorx.mediadl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
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
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val colors = LocalMediaColors.current
    val site = remember(vm.url) { if (vm.url.isNotBlank()) LinkPreviewer.platformOf(vm.url) else null }
    val duplicateMatch = remember(vm.url, vm.history.toList()) {
        val clean = LinkPreviewer.extractFirstUrl(vm.url)
        if (clean != null) vm.history.firstOrNull { it.sourceUrl == clean } else null
    }
    var deleteCandidate by remember { mutableStateOf<SavedMedia?>(null) }
    var playerStartIndex by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = vm.currentPreview != null) {
        vm.clearPreview()
    }

    fun triggerHaptic(type: HapticFeedbackType) {
        if (vm.prefs.hapticsEnabled) haptic.performHapticFeedback(type)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top minimal header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("MediaDown", color = colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Your media space.", color = colors.textMuted, fontSize = 13.sp)
            }
            Text(
                text = if (vm.ready) "Ready" else "Starting…",
                color = if (vm.ready) colors.good else colors.warning,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 6.dp)
            )
            IconButton(onClick = onOpenNotifications) {
                Icon(Icons.Rounded.Notifications, contentDescription = "Notifications", tint = colors.textPrimary)
            }
        }

        // Upper scrollable feed: Recent Downloads (cardless ultra-minimal) + Preview
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (!vm.previewError.isNullOrBlank()) {
                item {
                    Text(vm.previewError.orEmpty(), color = colors.error, fontSize = 13.sp)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent downloads",
                        color = colors.textMuted,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (vm.history.isNotEmpty()) {
                        TextButton(onClick = { vm.activeTab = NavTab.LIBRARY }) {
                            Text("See all →", color = colors.accent, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (vm.history.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Download,
                        title = "Nothing saved yet",
                        subtitle = "Paste a link below or share from Instagram."
                    )
                }
            } else {
                val recent = vm.history.take(6)
                itemsIndexed(recent, key = { _, item -> item.id }) { idx, item ->
                    MinimalRowItem(onClick = { playerStartIndex = idx }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MediaThumbnail(
                                uri = item.uri,
                                kind = item.kind,
                                exists = item.existsInGallery,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
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
                            IconButton(onClick = { shareSavedMedia(androidx.compose.ui.platform.LocalContext.current, item) }) {
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

        // Compact One-Handed Bottom Dock: Clipboard + Duplicate Alert + Link Input + Preview/Download
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = elasticSpring())
                .background(colors.background)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Spacer(Modifier.height(10.dp))

            AnimatedVisibility(
                visible = vm.clipboardSuggestion != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        vm.clipboardSuggestion.orEmpty(),
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            triggerHaptic(HapticFeedbackType.TextHandleMove)
                            vm.applyClipboardSuggestion()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Preview →", color = colors.accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                    IconButton(onClick = { vm.dismissClipboardSuggestion() }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = colors.textMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (duplicateMatch != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceElevated)
                        .elasticClickable {
                            val idx = vm.history.indexOfFirst { it.id == duplicateMatch.id }
                            if (idx >= 0) playerStartIndex = idx
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Already saved ${formatRelativeTime(duplicateMatch.timestamp)} — tap to open",
                        color = colors.textPrimary,
                        fontSize = 12.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Download anything", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (site != null && site != "Web") {
                    Text(site, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = vm.url,
                onValueChange = { vm.url = it },
                placeholder = { Text("Paste link here…", color = colors.textMuted) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = RoundedCornerShape(16.dp),
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
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.border),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paste", color = colors.textPrimary, fontSize = 13.sp)
                }

                Button(
                    enabled = vm.ready && vm.url.isNotBlank() && !vm.isPreviewLoading,
                    onClick = {
                        triggerHaptic(HapticFeedbackType.LongPress)
                        vm.previewLink()
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                ) {
                    if (vm.isPreviewLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.onAccent)
                        Spacer(Modifier.width(8.dp))
                        Text("Inspecting…", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Preview →", fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedButton(
                    enabled = vm.ready && vm.url.isNotBlank(),
                    onClick = {
                        triggerHaptic(HapticFeedbackType.LongPress)
                        vm.enqueueUrl(vm.url)
                        vm.activeTab = NavTab.DOWNLOADS
                    },
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = "Quick download", tint = colors.textPrimary)
                }
            }

            if (site == "Instagram" && !vm.igLoggedIn) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Public posts & reels work without login · Connect Instagram only for private posts & stories ›",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onOpenInstagramLogin() }.padding(top = 4.dp)
                )
            }
        }
    }

    playerStartIndex?.let { idx ->
        InbuiltMediaPlayerDialog(
            items = vm.history.take(6),
            initialIndex = idx,
            onDismiss = { playerStartIndex = null }
        )
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
