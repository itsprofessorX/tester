package com.professorx.mediadl.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.professorx.mediadl.AccentPreset
import com.professorx.mediadl.AppThemeMode
import com.professorx.mediadl.DlViewModel
import com.professorx.mediadl.LocalMediaColors
import com.professorx.mediadl.OutputFormat
import com.professorx.mediadl.SavedMedia
import com.professorx.mediadl.formatBytes
import com.professorx.mediadl.formatRelativeTime

enum class SettingsSheet {
    NONE, PERSONALIZATION, DOWNLOAD_PREFS, STORAGE, PRIVACY, NOTIFICATIONS, WHATS_NEW, TOOLBOX_PICKER
}

@Composable
fun SettingsScreen(
    vm: DlViewModel,
    initialSheet: SettingsSheet = SettingsSheet.NONE,
    onOpenInstagramLogin: () -> Unit,
    onPickCookiesFile: () -> Unit
) {
    val ctx = LocalContext.current
    val colors = LocalMediaColors.current
    var sheet by remember(initialSheet) { mutableStateOf(initialSheet) }
    var toolboxMedia by remember { mutableStateOf<SavedMedia?>(null) }

    BackHandler(enabled = sheet != SettingsSheet.NONE) { sheet = SettingsSheet.NONE }

    LaunchedEffect(sheet) {
        if (sheet == SettingsSheet.STORAGE) {
            vm.refreshTempCacheSize()
            vm.verifyGalleryFiles()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings", color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("MediaDown 2.0 · ${vm.engine}", color = colors.textMuted, fontSize = 13.sp)
        }

        item {
            SettingsRowCard(
                icon = Icons.Rounded.Palette,
                title = "Personalization",
                subtitle = "${vm.prefs.themeMode.label} · ${vm.prefs.accentPreset.label}",
                onClick = { sheet = SettingsSheet.PERSONALIZATION }
            )
        }
        item {
            SettingsRowCard(
                icon = Icons.Rounded.Tune,
                title = "Download Preferences",
                subtitle = "Default ${vm.prefs.defaultQuality} ${vm.prefs.defaultFormat.label} · Smart naming ${if (vm.prefs.smartFileNaming) "ON" else "OFF"}",
                onClick = { sheet = SettingsSheet.DOWNLOAD_PREFS }
            )
        }
        item {
            val summary = vm.storageSummary()
            SettingsRowCard(
                icon = Icons.Rounded.Storage,
                title = "Storage Dashboard",
                subtitle = "${summary.totalFiles} files · ${formatBytes(summary.totalBytes)} used",
                onClick = { sheet = SettingsSheet.STORAGE }
            )
        }
        item {
            SettingsRowCard(
                icon = Icons.Rounded.Build,
                title = "Media Toolbox",
                subtitle = "Extract audio, convert, trim, compress & thumbnails",
                onClick = { sheet = SettingsSheet.TOOLBOX_PICKER }
            )
        }
        item {
            SettingsRowCard(
                icon = Icons.Rounded.Notifications,
                title = "Notification Center",
                subtitle = "${vm.notifications.size} recent alerts & preferences",
                onClick = { sheet = SettingsSheet.NOTIFICATIONS }
            )
        }
        item {
            SettingsRowCard(
                icon = Icons.Rounded.Security,
                title = "Privacy Center",
                subtitle = if (vm.igLoggedIn) "Instagram connected (private & stories)" else "Optional cookies (private posts & stories only)",
                onClick = { sheet = SettingsSheet.PRIVACY }
            )
        }
        item {
            SettingsRowCard(
                icon = Icons.Rounded.NewReleases,
                title = "What's New & Engine",
                subtitle = "v2.0 · Engine ${vm.engineVersion}",
                onClick = { sheet = SettingsSheet.WHATS_NEW }
            )
        }
    }

    when (sheet) {
        SettingsSheet.PERSONALIZATION -> {
            AlertDialog(
                onDismissRequest = { sheet = SettingsSheet.NONE },
                containerColor = colors.surface,
                title = { Text("Personalization", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Theme", color = colors.textMuted, fontSize = 12.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppThemeMode.entries.forEach { mode ->
                                FilterPill(mode.label, vm.prefs.themeMode == mode, {
                                    vm.updatePreferences { it.copy(themeMode = mode) }
                                })
                            }
                        }
                        Text("Accent Color", color = colors.textMuted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AccentPreset.entries.forEach { preset ->
                                val selected = vm.prefs.accentPreset == preset && !vm.prefs.dynamicColor
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(preset.color)
                                        .border(if (selected) 3.dp else 0.dp, colors.textPrimary, CircleShape)
                                        .clickable {
                                            vm.updatePreferences { it.copy(accentPreset = preset, dynamicColor = false) }
                                        }
                                )
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ToggleRow("Dynamic Android colors", vm.prefs.dynamicColor) { v ->
                                vm.updatePreferences { it.copy(dynamicColor = v) }
                            }
                        }
                        ToggleRow("Haptic feedback", vm.prefs.hapticsEnabled) { v ->
                            vm.updatePreferences { it.copy(hapticsEnabled = v) }
                        }
                        ToggleRow("Reduced motion", vm.prefs.reducedMotion) { v ->
                            vm.updatePreferences { it.copy(reducedMotion = v) }
                        }
                        ToggleRow("Larger text size", vm.prefs.largeTextScale) { v ->
                            vm.updatePreferences { it.copy(largeTextScale = v) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { sheet = SettingsSheet.NONE }) { Text("Done", color = colors.accent) } }
            )
        }

        SettingsSheet.DOWNLOAD_PREFS -> {
            AlertDialog(
                onDismissRequest = { sheet = SettingsSheet.NONE },
                containerColor = colors.surface,
                title = { Text("Download Preferences", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Default Quality", color = colors.textMuted, fontSize = 12.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Original", "1080p", "720p", "480p", "360p").forEach { q ->
                                FilterPill(q, vm.prefs.defaultQuality == q, {
                                    vm.updatePreferences { it.copy(defaultQuality = q) }
                                })
                            }
                        }
                        Text("Default Format", color = colors.textMuted, fontSize = 12.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(OutputFormat.MP4, OutputFormat.WEBM, OutputFormat.MP3, OutputFormat.M4A).forEach { fmt ->
                                FilterPill(fmt.label, vm.prefs.defaultFormat == fmt, {
                                    vm.updatePreferences { it.copy(defaultFormat = fmt) }
                                })
                            }
                        }
                        ToggleRow("Smart file naming", vm.prefs.smartFileNaming) { v ->
                            vm.updatePreferences { it.copy(smartFileNaming = v) }
                        }
                        ToggleRow("Auto-download on Share", vm.prefs.autoShare) { v ->
                            vm.updatePreferences { it.copy(autoShare = v) }
                        }
                        ToggleRow("Clipboard link suggestion", vm.prefs.clipboardDetectEnabled) { v ->
                            vm.updatePreferences { it.copy(clipboardDetectEnabled = v) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { sheet = SettingsSheet.NONE }) { Text("Done", color = colors.accent) } }
            )
        }

        SettingsSheet.STORAGE -> {
            val s = vm.storageSummary()
            AlertDialog(
                onDismissRequest = { sheet = SettingsSheet.NONE },
                containerColor = colors.surface,
                title = { Text("Storage Dashboard", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Total: ${s.totalFiles} files · ${formatBytes(s.totalBytes)}", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Photos: ${s.photoCount} (${formatBytes(s.photoBytes)})", color = colors.textMuted, fontSize = 13.sp)
                        Text("Videos: ${s.videoCount} (${formatBytes(s.videoBytes)})", color = colors.textMuted, fontSize = 13.sp)
                        Text("Audio: ${s.audioCount} (${formatBytes(s.audioBytes)})", color = colors.textMuted, fontSize = 13.sp)
                        Text("Temporary cache: ${formatBytes(s.tempCacheBytes)}", color = colors.textMuted, fontSize = 13.sp)
                        if (s.missingCount > 0) {
                            Text("Suggestion: ${s.missingCount} missing gallery entries can be cleaned up in Library.", color = colors.warning, fontSize = 12.sp)
                        }
                        if (s.tempCacheBytes > 0L) {
                            OutlinedButton(onClick = { vm.clearTempCache() }, shape = RoundedCornerShape(12.dp)) {
                                Text("Clear temporary files (${formatBytes(s.tempCacheBytes)})", color = colors.accent)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { sheet = SettingsSheet.NONE }) { Text("Close", color = colors.accent) } }
            )
        }

        SettingsSheet.TOOLBOX_PICKER -> {
            AlertDialog(
                onDismissRequest = { sheet = SettingsSheet.NONE },
                containerColor = colors.surface,
                title = { Text("Select Media for Toolbox", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
                text = {
                    if (vm.history.isEmpty()) {
                        Text("Download a video or audio file first to use Media Toolbox.", color = colors.textMuted, fontSize = 13.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            vm.history.take(8).forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceElevated)
                                        .clickable {
                                            toolboxMedia = item
                                            sheet = SettingsSheet.NONE
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.name, color = colors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(item.kind.label, color = colors.accent, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { sheet = SettingsSheet.NONE }) { Text("Close", color = colors.textMuted) } }
            )
        }

        SettingsSheet.NOTIFICATIONS -> {
            AlertDialog(
                onDismissRequest = { sheet = SettingsSheet.NONE },
                containerColor = colors.surface,
                title = { Text("Notification Center", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToggleRow("Download completion", vm.prefs.notifyComplete) { v ->
                            vm.updatePreferences { it.copy(notifyComplete = v) }
                        }
                        ToggleRow("Failure & retry alerts", vm.prefs.notifyFailure) { v ->
                            vm.updatePreferences { it.copy(notifyFailure = v) }
                        }
                        ToggleRow("Progress notifications", vm.prefs.notifyProgress) { v ->
                            vm.updatePreferences { it.copy(notifyProgress = v) }
                        }
                        if (vm.notifications.isEmpty()) {
                            Text("No recent notifications.", color = colors.textMuted, fontSize = 12.sp)
                        } else {
                            vm.notifications.take(5).forEach { n ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceElevated)
                                        .padding(8.dp)
                                ) {
                                    Text(n.title, color = if (n.isError) colors.error else colors.good, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(n.message, color = colors.textPrimary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(formatRelativeTime(n.timestamp), color = colors.textMuted, fontSize = 10.sp)
                                }
                            }
                            TextButton(onClick = { vm.clearNotifications() }) {
                                Text("Clear notification log", color = colors.textMuted, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { sheet = SettingsSheet.NONE }) { Text("Done", color = colors.accent) } }
            )
        }

        SettingsSheet.PRIVACY -> {
            AlertDialog(
                onDismissRequest = { sheet = SettingsSheet.NONE },
                containerColor = colors.surface,
                title = { Text("Privacy Center", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToggleRow("Save local download history", vm.prefs.saveHistoryEnabled) { v ->
                            vm.updatePreferences { it.copy(saveHistoryEnabled = v) }
                        }
                        Text("Permissions: Internet (downloads), Notifications (progress), Foreground Service (background downloads).", color = colors.textMuted, fontSize = 11.sp)
                        OutlinedButton(onClick = onOpenInstagramLogin, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text(if (vm.igLoggedIn) "Reconnect Instagram" else "Connect Instagram", color = colors.textPrimary)
                        }
                        OutlinedButton(onClick = onPickCookiesFile, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text("Import cookies.txt", color = colors.textPrimary)
                        }
                        if (vm.hasCookies) {
                            OutlinedButton(onClick = { vm.removeCookies() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Text("Clear login session & cookies", color = colors.error)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val json = vm.exportHistoryJson()
                                    val send = Intent(Intent.ACTION_SEND).setType("application/json")
                                        .putExtra(Intent.EXTRA_TEXT, json)
                                    ctx.startActivity(Intent.createChooser(send, "Export MediaDown History"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Export", color = colors.textPrimary, fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = { vm.clearHistory() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Clear history", color = colors.error, fontSize = 12.sp) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { sheet = SettingsSheet.NONE }) { Text("Done", color = colors.accent) } }
            )
        }

        SettingsSheet.WHATS_NEW -> {
            AlertDialog(
                onDismissRequest = { sheet = SettingsSheet.NONE },
                containerColor = colors.surface,
                title = { Text("What's New in v2.0", color = colors.textPrimary, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Engine: ${vm.engineVersion} (${vm.engine})", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("• Floating Pill Bottom Bar & Cardless AMOLED Ultra-Minimal UI", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Compact One-Handed Home with Paste, Clear & Duplicate Link Detection", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Smart Link Preview, Quality Selector (144p–4K), Carousel Slide Picker & Copy Caption", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Multi-Download Queue with Pause, Resume, Cancel, Retry, Delete, Speed & ETA", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Media Library with Grid/List, Search, Favorites, Toolbox & Reels-Style Swipe Player (1x/1.5x/2x)", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Predictive Back Gesture support (Android 13–17), Privacy Center & Personalization", color = colors.textPrimary, fontSize = 12.sp)
                        Text("• Single-Task Instagram Share fix (no duplicate recent tasks)", color = colors.textPrimary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { vm.forceUpdate() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)
                        ) { Text("Check for engine update") }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/itsprofessorX/MediaDown/issues")))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Report a problem", color = colors.textPrimary)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { sheet = SettingsSheet.NONE }) { Text("Close", color = colors.accent) } }
            )
        }

        SettingsSheet.NONE -> {}
    }

    toolboxMedia?.let { item ->
        MediaToolboxDialog(
            item = item,
            busy = vm.toolboxBusy,
            message = vm.toolboxMessage,
            onDismiss = {
                vm.toolboxMessage = null
                toolboxMedia = null
            },
            onRun = { action, ext, start, end, crf ->
                vm.runToolbox(item, action, ext, start, end, crf)
            }
        )
    }
}

@Composable
private fun SettingsRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = LocalMediaColors.current
    MinimalRowItem(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, color = colors.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textMuted)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = LocalMediaColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.accent)
        )
    }
}
