package com.professorx.mediadl

import android.net.Uri
import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class NavTab(val label: String) {
    HOME("Home"),
    DOWNLOADS("Downloads"),
    LIBRARY("Library"),
    SETTINGS("Settings")
}

enum class AppThemeMode(val label: String) {
    AMOLED("AMOLED Black"),
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System")
}

enum class AccentPreset(val label: String, val color: Color) {
    CORAL("Coral Red", Color(0xFFFF3B4A)),
    BLUE("Electric Blue", Color(0xFF3B82F6)),
    EMERALD("Emerald", Color(0xFF10B981)),
    VIOLET("Violet", Color(0xFF8B5CF6)),
    AMBER("Amber", Color(0xFFF59E0B))
}

enum class MediaKind(val label: String) {
    VIDEO("Video"),
    PHOTO("Photo"),
    REEL("Reel"),
    CAROUSEL("Carousel"),
    AUDIO("Audio")
}

enum class OutputFormat(val label: String, val ext: String, val isAudio: Boolean) {
    ORIGINAL("Original", "mp4", false),
    MP4("MP4", "mp4", false),
    WEBM("WebM", "webm", false),
    MP3("MP3", "mp3", true),
    M4A("M4A", "m4a", true),
    OPUS("OPUS", "opus", true)
}

enum class DownloadStatus(val label: String) {
    QUEUED("Queued"),
    DOWNLOADING("Downloading"),
    PAUSED("Paused"),
    CONVERTING("Converting"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

enum class LibraryCategory(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    FAVORITES("Favorites")
}

enum class LibrarySort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    NAME("Name A–Z"),
    SIZE("Largest")
}

data class PreviewFormatOption(
    val id: String,
    val qualityLabel: String,
    val format: OutputFormat,
    val resolutionHeight: Int?,
    val estimatedSizeBytes: Long?,
    val formatSelector: String,
    val note: String = ""
)

data class PreviewSlide(
    val index: Int,
    val mediaUrl: String,
    val thumbnailUrl: String?,
    val isVideo: Boolean
)

data class LinkPreview(
    val url: String,
    val title: String,
    val creator: String,
    val platform: String,
    val thumbnailUrl: String?,
    val mediaKind: MediaKind,
    val itemCount: Int = 1,
    val durationSeconds: Long? = null,
    val caption: String = "",
    val slides: List<PreviewSlide> = emptyList(),
    val requiresLoginHint: Boolean = false,
    val options: List<PreviewFormatOption>
)

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val platform: String,
    val creator: String = "",
    val thumbnailUrl: String? = null,
    val mediaKind: MediaKind = MediaKind.VIDEO,
    val qualityLabel: String = "Original",
    val format: OutputFormat = OutputFormat.MP4,
    val formatSelector: String = "best[ext=mp4]/best",
    val directMediaUrls: List<String> = emptyList(),
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val indeterminate: Boolean = true,
    val stage: String = "Waiting in queue",
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val savedUriString: String? = null,
    val savedMime: String? = null
)

data class SavedMedia(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uriString: String,
    val mime: String,
    val platform: String = "Unknown",
    val kind: MediaKind = MediaKind.VIDEO,
    val sizeBytes: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val existsInGallery: Boolean = true,
    val sourceUrl: String = ""
) {
    val uri: Uri get() = Uri.parse(uriString)
}

data class NotificationLogItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val uriString: String? = null,
    val mime: String? = null,
    val retryUrl: String? = null
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val target: Int,
    val current: Int
) {
    val unlocked: Boolean get() = current >= target
    val fraction: Float get() = (current.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
}

data class DownloadStats(
    val totalDownloads: Int = 0,
    val photosSaved: Int = 0,
    val videosSaved: Int = 0,
    val audioSaved: Int = 0,
    val totalBytesSaved: Long = 0L,
    val toolboxRuns: Int = 0
)

data class StorageSummary(
    val totalFiles: Int = 0,
    val totalBytes: Long = 0L,
    val photoCount: Int = 0,
    val photoBytes: Long = 0L,
    val videoCount: Int = 0,
    val videoBytes: Long = 0L,
    val audioCount: Int = 0,
    val audioBytes: Long = 0L,
    val tempCacheBytes: Long = 0L,
    val missingCount: Int = 0
)

data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.AMOLED,
    val accentPreset: AccentPreset = AccentPreset.CORAL,
    val dynamicColor: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val largeTextScale: Boolean = false,
    val autoShare: Boolean = false,
    val smartFileNaming: Boolean = true,
    val defaultQuality: String = "1080p",
    val defaultFormat: OutputFormat = OutputFormat.MP4,
    val localOnlyHistory: Boolean = true,
    val saveHistoryEnabled: Boolean = true,
    val clipboardDetectEnabled: Boolean = true,
    val notifyComplete: Boolean = true,
    val notifyFailure: Boolean = true,
    val notifyProgress: Boolean = true,
    val notifySound: Boolean = false
)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(java.util.Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(java.util.Locale.US, "%.2f GB", gb)
}

fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val rem = s % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, rem)
    else String.format(java.util.Locale.US, "%02d:%02d", m, rem)
}

fun formatRelativeTime(timestamp: Long): String {
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val mins = diff / 60_000L
    val hours = mins / 60L
    val days = hours / 24L
    return when {
        mins < 1L -> "Just now"
        mins < 60L -> "${mins}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.util.Date(timestamp))
    }
}
