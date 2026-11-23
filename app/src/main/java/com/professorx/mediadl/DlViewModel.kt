package com.professorx.mediadl

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class DlViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = PrefsStore(app)

    var prefs by mutableStateOf(store.loadPreferences())
        private set
    var stats by mutableStateOf(store.loadStats())
        private set

    var activeTab by mutableStateOf(NavTab.HOME)
    var url by mutableStateOf("")
    var status by mutableStateOf("Starting…")
    var engine by mutableStateOf("Initializing…")
    var engineVersion by mutableStateOf("Checking…")
    var ready by mutableStateOf(false)
    var hasCookies by mutableStateOf(false)
    var igLoggedIn by mutableStateOf(false)

    // Smart Link Preview & Clipboard Intelligence
    var currentPreview by mutableStateOf<LinkPreview?>(null)
    var selectedOption by mutableStateOf<PreviewFormatOption?>(null)
    var isPreviewLoading by mutableStateOf(false)
    var previewError by mutableStateOf<String?>(null)
    var clipboardSuggestion by mutableStateOf<String?>(null)
    private var lastDismissedClipboardUrl: String? = null

    // Queue, History, Notifications, Celebrations
    val tasks = mutableStateListOf<DownloadTask>()
    val history = mutableStateListOf<SavedMedia>()
    val notifications = mutableStateListOf<NotificationLogItem>()
    var celebrationTitle by mutableStateOf<String?>(null)
    var toolboxBusy by mutableStateOf(false)
    var toolboxMessage by mutableStateOf<String?>(null)
    var tempCacheBytes by mutableStateOf(0L)

    private val cookies get() = File(app.filesDir, "cookies.txt")
    private val engineLock = Mutex()
    private val queueLock = Mutex()
    private var queueJob: Job? = null
    private var pendingAutoUrls = mutableListOf<String>()

    @Volatile private var lastUpdate = 0L
    @Volatile private var activeTaskId: String? = null
    @Volatile private var activeActionCancel = false
    @Volatile private var activeActionPause = false

    private val speedRegex = Regex("at\\s+([\\d.]+)\\s*([KMG]?i?B)/s", RegexOption.IGNORE_CASE)
    private val etaRegex = Regex("ETA\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)")

    init {
        history.addAll(store.loadHistory())
        notifications.addAll(store.loadNotifications())
        refreshTempCacheSize()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                refreshCookies()
                engineVersion = runCatching { YoutubeDL.getInstance().version(app) ?: "Installed" }.getOrDefault("Installed")
                ready = true
                status = "Ready"
                verifyGalleryFiles()
                if (pendingAutoUrls.isNotEmpty()) {
                    val copy = pendingAutoUrls.toList()
                    pendingAutoUrls.clear()
                    copy.forEach { enqueueUrl(it) }
                }
                updateEngine()
            } catch (e: Throwable) {
                status = "Engine failed to start: ${e.message}"
                engine = "Engine error"
            }
        }
    }

    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        prefs = transform(prefs)
        store.savePreferences(prefs)
    }

    private suspend fun updateEngine(): Boolean = engineLock.withLock {
        engine = "Checking engine updates…"
        try {
            YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
            lastUpdate = System.currentTimeMillis()
            engineVersion = runCatching { YoutubeDL.getInstance().version(app) ?: engineVersion }.getOrDefault(engineVersion)
            engine = "Engine up to date"
            true
        } catch (_: Throwable) {
            engine = "Engine ready"
            false
        }
    }

    fun forceUpdate() {
        val hasActive = tasks.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONVERTING }
        if (!ready || hasActive) return
        viewModelScope.launch(Dispatchers.IO) { updateEngine() }
    }

    /** Link arrived from Instagram or another app's Share menu (singleTask reuses existing task). */
    fun onShared(text: String) {
        val extracted = LinkPreviewer.extractFirstUrl(text) ?: text.trim()
        if (extracted.isBlank()) return
        url = extracted
        activeTab = NavTab.HOME
        if (prefs.autoShare) {
            if (ready) {
                enqueueUrl(extracted)
                activeTab = NavTab.DOWNLOADS
            } else {
                pendingAutoUrls.add(extracted)
            }
        } else {
            previewLink(extracted)
        }
    }

    fun checkClipboard(clipText: String?) {
        if (!prefs.clipboardDetectEnabled || clipText.isNullOrBlank()) {
            clipboardSuggestion = null
            return
        }
        val candidate = LinkPreviewer.extractFirstUrl(clipText) ?: return
        val isSupported = LinkPreviewer.platformOf(candidate) != "Web" ||
            candidate.lowercase().let { it.endsWith(".mp4") || it.endsWith(".jpg") || it.endsWith(".png") }
        if (isSupported && candidate != url.trim() && candidate != lastDismissedClipboardUrl) {
            clipboardSuggestion = candidate
        }
    }

    fun dismissClipboardSuggestion() {
        lastDismissedClipboardUrl = clipboardSuggestion
        clipboardSuggestion = null
    }

    fun applyClipboardSuggestion() {
        val s = clipboardSuggestion ?: return
        url = s
        clipboardSuggestion = null
        previewLink(s)
    }

    fun previewLink(targetUrl: String = url) {
        val cleanUrl = LinkPreviewer.extractFirstUrl(targetUrl)
        if (cleanUrl == null) {
            previewError = "Paste a valid http/https link to preview."
            return
        }
        url = cleanUrl
        previewError = null
        isPreviewLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preview = LinkPreviewer.inspect(cleanUrl, cookies.takeIf { it.exists() })
                val preferred = preview.options.firstOrNull {
                    it.qualityLabel.contains(prefs.defaultQuality, ignoreCase = true) ||
                        it.format == prefs.defaultFormat
                } ?: preview.options.firstOrNull()
                withContext(Dispatchers.Main) {
                    currentPreview = preview
                    selectedOption = preferred
                    isPreviewLoading = false
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    isPreviewLoading = false
                    previewError = "Couldn't load preview: ${e.message ?: "Unknown error"}"
                }
            }
        }
    }

    fun clearPreview() {
        currentPreview = null
        selectedOption = null
        previewError = null
    }

    fun downloadFromPreview(selectedSlideIndices: Set<Int>? = null, forceAudioMp3: Boolean = false) {
        val preview = currentPreview ?: return
        val opt = if (forceAudioMp3) {
            PreviewFormatOption("mp3_quick", "Audio MP3", OutputFormat.MP3, null, null, "bestaudio/best", "MP3")
        } else {
            selectedOption ?: preview.options.firstOrNull()
        }
        val directUrls = if (!forceAudioMp3 && preview.slides.isNotEmpty()) {
            val chosen = if (selectedSlideIndices.isNullOrEmpty()) preview.slides
            else preview.slides.filter { it.index in selectedSlideIndices }
            chosen.map { it.mediaUrl }
        } else emptyList()

        val task = DownloadTask(
            url = preview.url,
            title = preview.title,
            platform = preview.platform,
            creator = preview.creator,
            thumbnailUrl = preview.thumbnailUrl,
            mediaKind = preview.mediaKind,
            qualityLabel = opt?.qualityLabel ?: "Original",
            format = opt?.format ?: OutputFormat.MP4,
            formatSelector = opt?.formatSelector ?: "best[ext=mp4]/best",
            directMediaUrls = directUrls,
            totalBytes = opt?.estimatedSizeBytes ?: 0L,
            status = DownloadStatus.QUEUED,
            stage = "Queued"
        )
        enqueueTask(task)
        clearPreview()
        url = ""
        activeTab = NavTab.DOWNLOADS
    }

    fun enqueueUrl(rawText: String, audioOnly: Boolean = false) {
        val links = LinkPreviewer.extractUrls(rawText)
        if (links.isEmpty()) {
            status = "Please paste a valid link."
            return
        }
        links.forEach { link ->
            val platform = LinkPreviewer.platformOf(link)
            val format = if (audioOnly) OutputFormat.MP3 else prefs.defaultFormat
            val selector = if (audioOnly) "bestaudio/best" else when (prefs.defaultQuality) {
                "1080p" -> "best[height<=1080][ext=mp4]/best[height<=1080]/best"
                "720p" -> "best[height<=720][ext=mp4]/best[height<=720]/best"
                "480p" -> "best[height<=480][ext=mp4]/best[height<=480]/best"
                "360p" -> "best[height<=360][ext=mp4]/best[height<=360]/best"
                else -> "best[ext=mp4]/best"
            }
            enqueueTask(
                DownloadTask(
                    url = link,
                    title = Names.fromLink(link),
                    platform = platform,
                    qualityLabel = if (audioOnly) "Audio MP3" else prefs.defaultQuality,
                    format = format,
                    formatSelector = selector,
                    status = DownloadStatus.QUEUED,
                    stage = "Queued"
                )
            )
        }
        url = ""
    }

    private fun enqueueTask(task: DownloadTask) {
        tasks.add(0, task)
        startQueueWorkerIfNeeded()
    }

    private fun startQueueWorkerIfNeeded() {
        if (queueJob?.isActive == true) return
        queueJob = viewModelScope.launch(Dispatchers.IO) {
            queueLock.withLock {
                if (!ready) return@withLock
                startFg()
                val ticker = launch {
                    while (isActive) {
                        val active = tasks.firstOrNull {
                            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONVERTING
                        }
                        if (active != null) {
                            val pct = (active.progress * 100).toInt()
                            val speedPart = if (active.speedBytesPerSec > 0) " · ${formatBytes(active.speedBytesPerSec)}/s" else ""
                            val etaPart = active.etaSeconds?.let { " · ETA ${formatDuration(it)}" } ?: ""
                            val text = "${active.title.take(28)}$speedPart$etaPart"
                            Notifier.updateProgress(app, text, pct, active.indeterminate, prefs.notifyProgress)
                        }
                        delay(700)
                    }
                }

                while (isActive) {
                    val next = tasks.lastOrNull { it.status == DownloadStatus.QUEUED } ?: break
                    runSingleTask(next)
                }

                ticker.cancel()
                stopFg()
                Notifier.cancelProgress(app)
            }
        }
    }

    private suspend fun runSingleTask(initial: DownloadTask) {
        activeTaskId = initial.id
        activeActionCancel = false
        activeActionPause = false

        updateTask(initial.id) {
            it.copy(status = DownloadStatus.DOWNLOADING, indeterminate = true, stage = "Starting…", errorMessage = null)
        }

        val dir = File(app.cacheDir, "dl_${initial.id}").apply { mkdirs() }
        var savedItems = 0
        var lastErr = ""

        for (attempt in 0..1) {
            try {
                val current = tasks.firstOrNull { it.id == initial.id } ?: initial
                if (current.directMediaUrls.isNotEmpty() && !current.format.isAudio) {
                    val base = Names.clean(current.title.ifBlank { Names.fromLink(current.url) })
                    val g = try { runUrls(current.directMediaUrls.map { MediaItem(it, base) }, dir, current) } catch (_: Throwable) { 0 }
                    if (g > 0) { savedItems = g; break }
                }
                if (!current.format.isAudio && Instagram.isInstagram(current.url)) {
                    val g = try {
                        val rich = Instagram.resolveRich(current.url, cookies.takeIf { it.exists() })
                        if (rich != null && rich.slides.isNotEmpty()) {
                            runUrls(rich.slides.map { MediaItem(it.mediaUrl, Names.clean(rich.title)) }, dir, current)
                        } else if (igLoggedIn) {
                            runInstagram(current, dir)
                        } else 0
                    } catch (_: Throwable) { 0 }
                    if (g > 0) { savedItems = g; break }
                }
                val n = runYtDlp(current, dir)
                if (n > 0) { savedItems = n; break }
                lastErr = "No downloadable media found at this link."
            } catch (e: Throwable) {
                if (activeActionCancel || activeActionPause) break
                val partial = saveAll(mediaFiles(dir, initial.format.isAudio), initial)
                if (partial > 0) { savedItems = partial; break }
                lastErr = friendly(e)
            }

            if (!initial.format.isAudio && !activeActionCancel && !activeActionPause) {
                try {
                    val n = runImages(initial, dir)
                    if (n > 0) { savedItems = n; break }
                } catch (_: Throwable) {}
            }

            if (attempt == 0 && !activeActionCancel && !activeActionPause &&
                System.currentTimeMillis() - lastUpdate > 10 * 60_000L
            ) {
                updateEngine()
            } else break
        }

        when {
            activeActionPause -> {
                updateTask(initial.id) {
                    it.copy(status = DownloadStatus.PAUSED, stage = "Paused (resumable)", speedBytesPerSec = 0L, etaSeconds = null)
                }
            }
            activeActionCancel -> {
                dir.deleteRecursively()
                updateTask(initial.id) {
                    it.copy(status = DownloadStatus.CANCELLED, stage = "Cancelled", speedBytesPerSec = 0L, etaSeconds = null)
                }
            }
            savedItems > 0 -> {
                dir.deleteRecursively()
                val latest = history.firstOrNull()
                updateTask(initial.id) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        indeterminate = false,
                        stage = "Saved $savedItems file(s)",
                        speedBytesPerSec = 0L,
                        etaSeconds = null,
                        savedUriString = latest?.uriString,
                        savedMime = latest?.mime
                    )
                }
                status = "✅ Saved $savedItems file(s)"
                celebrationTitle = latest?.name ?: "Saved $savedItems file(s)"
                addNotification(
                    NotificationLogItem(
                        title = "Download complete",
                        message = latest?.name ?: "Saved $savedItems file(s) from ${initial.platform}",
                        isError = false,
                        uriString = latest?.uriString,
                        mime = latest?.mime
                    )
                )
                Notifier.done(
                    app,
                    "Download complete",
                    latest?.name ?: "Saved $savedItems file(s)",
                    latest?.uri,
                    latest?.mime,
                    isError = false,
                    prefs = prefs
                )
                MediaDownWidgetProvider.requestUpdate(app)
                viewModelScope.launch {
                    delay(3200)
                    if (celebrationTitle == (latest?.name ?: "Saved $savedItems file(s)")) {
                        celebrationTitle = null
                    }
                }
            }
            else -> {
                dir.deleteRecursively()
                val msg = lastErr.ifBlank { "Download failed." }
                updateTask(initial.id) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        indeterminate = false,
                        stage = "Failed",
                        errorMessage = msg,
                        speedBytesPerSec = 0L,
                        etaSeconds = null
                    )
                }
                status = msg
                addNotification(
                    NotificationLogItem(
                        title = "Download failed",
                        message = "${initial.platform}: $msg",
                        isError = true,
                        retryUrl = initial.url
                    )
                )
                Notifier.done(
                    app,
                    "Download failed",
                    msg,
                    null,
                    null,
                    isError = true,
                    retryUrl = initial.url,
                    prefs = prefs
                )
            }
        }

        activeTaskId = null
        refreshTempCacheSize()
    }

    private suspend fun runYtDlp(task: DownloadTask, dir: File): Int = engineLock.withLock {
        val req = YoutubeDLRequest(task.url).apply {
            addOption("-o", "${dir.absolutePath}/%(title).90B%(playlist_index& - {}|)s.%(ext)s")
            addOption("--no-mtime")
            addOption("--newline")
            addOption("--continue")
            addOption("-i")
            addOption("--socket-timeout", "20")
            addOption("--retries", "3")
            if (task.format.isAudio) {
                addOption("-f", "bestaudio/best")
                addOption("-x")
                addOption("--audio-format", task.format.ext)
                addOption("--audio-quality", "0")
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
            } else {
                addOption("-f", task.formatSelector)
                if (task.format == OutputFormat.MP4) {
                    addOption("--merge-output-format", "mp4")
                } else if (task.format == OutputFormat.WEBM) {
                    addOption("--merge-output-format", "webm")
                }
            }
            if (cookies.exists()) addOption("--cookies", cookies.absolutePath)
        }

        var lastRaw = 0f
        var fileNo = 1
        YoutubeDL.getInstance().execute(req, task.id) { p, etaSec, line ->
            val l = line.orEmpty()
            val speed = parseSpeedBytes(l)
            val parsedEta = if (etaSec > 0) etaSec else parseEtaSeconds(l)
            if ("[ExtractAudio]" in l || "[Merger]" in l || "[VideoConvertor]" in l || "[FixupM3u8]" in l) {
                updateTask(task.id) {
                    it.copy(status = DownloadStatus.CONVERTING, indeterminate = true, stage = "Converting…", speedBytesPerSec = 0L)
                }
            } else if (p >= 0f) {
                if (p + 30f < lastRaw) fileNo++
                lastRaw = p
                val v = (p / 100f).coerceIn(0f, 1f)
                val s = if (fileNo > 1) "File $fileNo" else "Downloading"
                updateTask(task.id) { prev ->
                    if (v >= 0.999f || abs(v - prev.progress) >= 0.01f || speed > 0L) {
                        prev.copy(
                            status = DownloadStatus.DOWNLOADING,
                            progress = v,
                            indeterminate = false,
                            stage = s,
                            speedBytesPerSec = if (speed > 0L) speed else prev.speedBytesPerSec,
                            etaSeconds = parsedEta ?: prev.etaSeconds
                        )
                    } else prev
                }
            }
        }
        saveAll(mediaFiles(dir, task.format.isAudio), task)
    }

    private fun runImages(task: DownloadTask, dir: File): Int {
        val urls = Images.find(task.url, cookies.takeIf { it.exists() })
        val base = Names.clean(Images.lastTitle.ifBlank { Names.fromLink(task.url) })
        return runUrls(urls.map { MediaItem(it, base) }, dir, task)
    }

    private fun runInstagram(task: DownloadTask, dir: File): Int =
        runUrls(Instagram.resolve(task.url, cookies), dir, task)

    private fun runUrls(items: List<MediaItem>, dir: File, task: DownloadTask): Int {
        if (items.isEmpty()) return 0
        var n = 0
        items.forEachIndexed { i, item ->
            if (activeActionCancel || activeActionPause) return n
            val base = if (items.size > 1) "${item.name} - ${i + 1}" else item.name
            val startMs = System.currentTimeMillis()
            val f = Images.download(item.url, dir, base) { frac ->
                val v = (i + frac) / items.size
                val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000.0).coerceAtLeast(0.3)
                updateTask(task.id) { prev ->
                    if (abs(v - prev.progress) >= 0.02f) {
                        prev.copy(
                            progress = v,
                            indeterminate = false,
                            stage = if (items.size > 1) "Item ${i + 1} of ${items.size}" else "Downloading",
                            etaSeconds = if (v > 0.05f) ((elapsedSec / frac) * (1f - frac)).toLong() else null
                        )
                    } else prev
                }
            }
            if (f != null) {
                saveToGallery(f, task.platform, task.url)
                n++
            }
        }
        return n
    }

    fun pauseTask(id: String) {
        val t = tasks.firstOrNull { it.id == id } ?: return
        when (t.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.CONVERTING -> {
                activeActionPause = true
                runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
            }
            DownloadStatus.QUEUED -> {
                updateTask(id) { it.copy(status = DownloadStatus.PAUSED, stage = "Paused") }
            }
            else -> {}
        }
    }

    fun resumeTask(id: String) {
        val t = tasks.firstOrNull { it.id == id } ?: return
        if (t.status == DownloadStatus.PAUSED) {
            updateTask(id) { it.copy(status = DownloadStatus.QUEUED, stage = "Resuming…") }
            startQueueWorkerIfNeeded()
        }
    }

    fun cancelTask(id: String) {
        val t = tasks.firstOrNull { it.id == id } ?: return
        if (t.status == DownloadStatus.DOWNLOADING || t.status == DownloadStatus.CONVERTING) {
            activeActionCancel = true
            runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
        } else {
            File(app.cacheDir, "dl_$id").deleteRecursively()
            updateTask(id) { it.copy(status = DownloadStatus.CANCELLED, stage = "Cancelled", speedBytesPerSec = 0L, etaSeconds = null) }
        }
    }

    fun retryTask(id: String) {
        val t = tasks.firstOrNull { it.id == id } ?: return
        updateTask(id) {
            it.copy(
                status = DownloadStatus.QUEUED,
                progress = 0f,
                indeterminate = true,
                stage = "Retrying…",
                errorMessage = null
            )
        }
        startQueueWorkerIfNeeded()
    }

    fun deleteTask(id: String) {
        val t = tasks.firstOrNull { it.id == id } ?: return
        if (t.status == DownloadStatus.DOWNLOADING || t.status == DownloadStatus.CONVERTING) {
            cancelTask(id)
        }
        File(app.cacheDir, "dl_$id").deleteRecursively()
        tasks.removeAll { it.id == id }
    }

    fun clearFinishedTasks() {
        tasks.removeAll {
            it.status == DownloadStatus.COMPLETED ||
                it.status == DownloadStatus.CANCELLED ||
                it.status == DownloadStatus.FAILED
        }
    }

    private fun updateTask(id: String, transform: (DownloadTask) -> DownloadTask) {
        val idx = tasks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            tasks[idx] = transform(tasks[idx])
        }
    }

    private fun parseSpeedBytes(line: String): Long {
        val m = speedRegex.find(line) ?: return 0L
        val num = m.groupValues[1].toDoubleOrNull() ?: return 0L
        val unit = m.groupValues[2].uppercase()
        val mult = when {
            unit.startsWith("G") -> 1024.0 * 1024.0 * 1024.0
            unit.startsWith("M") -> 1024.0 * 1024.0
            unit.startsWith("K") -> 1024.0
            else -> 1.0
        }
        return (num * mult).toLong()
    }

    private fun parseEtaSeconds(line: String): Long? {
        val m = etaRegex.find(line)?.groupValues?.get(1) ?: return null
        val parts = m.split(':').mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private fun mediaFiles(dir: File, audio: Boolean): List<File> =
        dir.listFiles()?.filter {
            it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.endsWith(".json") &&
                (!audio || it.extension.lowercase() in setOf("mp3", "m4a", "ogg", "opus", "wav"))
        }.orEmpty().sortedBy { it.name }

    private fun saveAll(files: List<File>, task: DownloadTask): Int {
        var n = 0
        files.forEach {
            try {
                saveToGallery(it, task.platform, task.url)
                n++
            } catch (_: Throwable) {}
        }
        return n
    }

    private fun applySmartName(originalFile: File, platform: String): String {
        if (!prefs.smartFileNaming) return originalFile.name
        val ext = originalFile.extension
        val rawBase = originalFile.nameWithoutExtension
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val prefix = if (platform.isNotBlank() && platform != "Web" && !rawBase.startsWith(platform, ignoreCase = true)) {
            "${platform}_${dateStr}_"
        } else ""
        val cleaned = Names.clean("$prefix$rawBase", max = 95)
        return if (ext.isNotBlank()) "$cleaned.$ext" else cleaned
    }

    private fun saveToGallery(f: File, platform: String, sourceUrl: String): SavedMedia? {
        val ext = f.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val (collection, folder, kind) = when {
            mime.startsWith("video") -> Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_MOVIES, MediaKind.VIDEO)
            mime.startsWith("image") -> Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_PICTURES, MediaKind.PHOTO)
            mime.startsWith("audio") -> Triple(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_MUSIC, MediaKind.AUDIO)
            else -> Triple(MediaStore.Downloads.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_DOWNLOADS, MediaKind.VIDEO)
        }
        val finalName = applySmartName(f, platform)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/MediaDown")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        val sizeBytes = f.length()
        val saved = SavedMedia(
            name = finalName,
            uriString = uri.toString(),
            mime = mime,
            platform = platform,
            kind = kind,
            sizeBytes = sizeBytes,
            timestamp = System.currentTimeMillis(),
            isFavorite = false,
            existsInGallery = true,
            sourceUrl = sourceUrl
        )
        if (prefs.saveHistoryEnabled) {
            history.add(0, saved)
            store.saveHistory(history)
        }
        stats = stats.copy(
            totalDownloads = stats.totalDownloads + 1,
            photosSaved = stats.photosSaved + if (kind == MediaKind.PHOTO) 1 else 0,
            videosSaved = stats.videosSaved + if (kind == MediaKind.VIDEO) 1 else 0,
            audioSaved = stats.audioSaved + if (kind == MediaKind.AUDIO) 1 else 0,
            totalBytesSaved = stats.totalBytesSaved + sizeBytes
        )
        store.saveStats(stats)
        return saved
    }

    // ------------------------------------------------------------ Library, History & Storage
    fun toggleFavorite(id: String) {
        val idx = history.indexOfFirst { it.id == id }
        if (idx >= 0) {
            history[idx] = history[idx].copy(isFavorite = !history[idx].isFavorite)
            store.saveHistory(history)
        }
    }

    fun deleteSavedMedia(item: SavedMedia, deleteFromDevice: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteFromDevice) {
                runCatching { app.contentResolver.delete(item.uri, null, null) }
            }
            withContext(Dispatchers.Main) {
                history.removeAll { it.id == item.id }
                store.saveHistory(history)
                MediaDownWidgetProvider.requestUpdate(app)
            }
        }
    }

    fun verifyGalleryFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = history.map { item ->
                val exists = try {
                    app.contentResolver.openAssetFileDescriptor(item.uri, "r")?.use { true } ?: false
                } catch (_: Throwable) {
                    false
                }
                if (exists != item.existsInGallery) item.copy(existsInGallery = exists) else item
            }
            withContext(Dispatchers.Main) {
                history.clear()
                history.addAll(updated)
                store.saveHistory(history)
            }
        }
    }

    fun removeMissingHistoryItems() {
        history.removeAll { !it.existsInGallery }
        store.saveHistory(history)
    }

    fun clearHistory() {
        history.clear()
        store.saveHistory(history)
    }

    fun exportHistoryJson(): String = store.exportHistoryJson(history)

    fun storageSummary(): StorageSummary {
        var photoC = 0; var photoB = 0L
        var videoC = 0; var videoB = 0L
        var audioC = 0; var audioB = 0L
        var missingC = 0
        history.forEach { m ->
            if (!m.existsInGallery) missingC++
            when (m.kind) {
                MediaKind.PHOTO, MediaKind.CAROUSEL -> { photoC++; photoB += m.sizeBytes }
                MediaKind.AUDIO -> { audioC++; audioB += m.sizeBytes }
                else -> { videoC++; videoB += m.sizeBytes }
            }
        }
        return StorageSummary(
            totalFiles = history.size,
            totalBytes = photoB + videoB + audioB,
            photoCount = photoC,
            photoBytes = photoB,
            videoCount = videoC,
            videoBytes = videoB,
            audioCount = audioC,
            audioBytes = audioB,
            tempCacheBytes = tempCacheBytes,
            missingCount = missingC
        )
    }

    fun refreshTempCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = app.cacheDir.listFiles()
                ?.filter { it.name.startsWith("dl_") || it.name == "toolbox_tmp" }
                ?.sumOf { dirSize(it) } ?: 0L
            withContext(Dispatchers.Main) { tempCacheBytes = bytes }
        }
    }

    fun clearTempCache() {
        viewModelScope.launch(Dispatchers.IO) {
            app.cacheDir.listFiles()
                ?.filter {
                    (it.name.startsWith("dl_") && it.name != "dl_${activeTaskId}") || it.name == "toolbox_tmp"
                }
                ?.forEach { it.deleteRecursively() }
            refreshTempCacheSize()
        }
    }

    private fun dirSize(f: File): Long =
        if (f.isFile) f.length() else f.listFiles()?.sumOf { dirSize(it) } ?: 0L

    // ------------------------------------------------------------ Media Toolbox
    fun runToolbox(
        media: SavedMedia,
        action: ToolboxAction,
        targetAudioExt: String = "mp3",
        startSec: Int = 0,
        endSec: Int = 10,
        crf: Int = 28
    ) {
        if (toolboxBusy) return
        toolboxBusy = true
        toolboxMessage = "Processing ${action.label}…"
        viewModelScope.launch(Dispatchers.IO) {
            val res = when (action) {
                ToolboxAction.EXTRACT_AUDIO -> MediaToolbox.extractAudio(app, media, targetAudioExt)
                ToolboxAction.CONVERT_AUDIO -> MediaToolbox.convertAudio(app, media, targetAudioExt)
                ToolboxAction.TRIM_MEDIA -> MediaToolbox.trimMedia(app, media, startSec, endSec)
                ToolboxAction.COMPRESS_VIDEO -> MediaToolbox.compressVideo(app, media, crf)
                ToolboxAction.EXTRACT_THUMBNAIL -> MediaToolbox.extractThumbnail(app, media, startSec.toLong())
            }
            withContext(Dispatchers.Main) {
                toolboxBusy = false
                res.onSuccess { outFile ->
                    val saved = saveToGallery(outFile, "Toolbox", media.sourceUrl)
                    outFile.delete()
                    stats = stats.copy(toolboxRuns = stats.toolboxRuns + 1)
                    store.saveStats(stats)
                    toolboxMessage = "✅ Saved ${saved?.name ?: outFile.name} to Library"
                }.onFailure { err ->
                    toolboxMessage = "❌ ${err.message ?: "Processing failed"}"
                }
            }
        }
    }

    // ------------------------------------------------------------ Notifications & Privacy
    private fun addNotification(item: NotificationLogItem) {
        notifications.add(0, item)
        if (notifications.size > 40) notifications.removeAt(notifications.lastIndex)
        store.saveNotifications(notifications)
    }

    fun clearNotifications() {
        notifications.clear()
        store.saveNotifications(notifications)
    }

    fun importCookies(src: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.contentResolver.openInputStream(src)?.use { i -> cookies.outputStream().use { o -> i.copyTo(o) } }
                refreshCookies()
                status = "Cookies imported."
            } catch (e: Throwable) {
                status = "Cookie import failed: ${e.message}"
            }
        }
    }

    fun removeCookies() {
        cookies.delete()
        hasCookies = false
        igLoggedIn = false
        try { android.webkit.CookieManager.getInstance().removeAllCookies(null) } catch (_: Throwable) {}
        status = "Logged out / cookies removed."
    }

    fun refreshCookies() {
        hasCookies = cookies.exists()
        igLoggedIn = hasCookies && try { cookies.readText().contains("sessionid") } catch (_: Exception) { false }
        if (igLoggedIn && ready) status = "Instagram connected."
    }

    private fun startFg() {
        try {
            Notifier.ensureChannels(app)
            androidx.core.content.ContextCompat.startForegroundService(app, android.content.Intent(app, DownloadService::class.java))
        } catch (_: Throwable) {}
    }

    private fun stopFg() {
        try { app.stopService(android.content.Intent(app, DownloadService::class.java)) } catch (_: Throwable) {}
    }

    private fun friendly(e: Throwable): String {
        val t = (e.message ?: "").lowercase()
        return when {
            listOf("login", "cookie", "rate-limit", "private", "not available", "sign in").any { it in t } ->
                "Private post or login needed. Connect Instagram in Settings."
            "unsupported url" in t -> "No downloadable media found at that link."
            else -> "Couldn't download that. Check the link and try again."
        }
    }
}
