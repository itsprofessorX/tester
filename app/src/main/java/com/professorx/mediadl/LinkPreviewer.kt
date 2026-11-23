package com.professorx.mediadl

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File

object LinkPreviewer {
    private val urlRegex = Regex("https?://[^\\s<>\"']+")

    fun extractFirstUrl(text: String): String? = urlRegex.find(text)?.value

    fun extractUrls(text: String, limit: Int = 5): List<String> =
        urlRegex.findAll(text).map { it.value }.distinct().take(limit).toList()

    fun platformOf(t: String): String {
        val s = t.lowercase()
        return when {
            "instagram.com" in s || "instagr.am" in s -> "Instagram"
            "tiktok.com" in s -> "TikTok"
            "twitter.com" in s || "//x.com" in s || ".x.com" in s -> "X"
            "facebook.com" in s || "fb.watch" in s -> "Facebook"
            "reddit.com" in s -> "Reddit"
            "pinterest." in s || "pin.it" in s -> "Pinterest"
            "youtube.com" in s || "youtu.be" in s -> "YouTube"
            else -> "Web"
        }
    }

    fun detectMediaKind(url: String, itemCount: Int = 1, hasVideo: Boolean = true): MediaKind {
        val l = url.lowercase()
        return when {
            itemCount > 1 -> MediaKind.CAROUSEL
            "/reel" in l || "/reels" in l -> MediaKind.REEL
            !hasVideo -> MediaKind.PHOTO
            else -> MediaKind.VIDEO
        }
    }

    fun inspect(url: String, cookieFile: File?): LinkPreview {
        val platform = platformOf(url)

        // 1. Instaloader-style Instagram resolver (cookie-free for public posts, reels, carousels & DPs)
        if (platform == "Instagram") {
            val hasCookies = cookieFile != null && cookieFile.exists()
            val needsLogin = Instagram.isStoryOrHighlight(url) && !hasCookies
            if (!needsLogin) {
                try {
                    val ig = Instagram.resolveRich(url, cookieFile)
                    if (ig != null && ig.slides.isNotEmpty()) {
                        val hasVideo = ig.slides.any { it.isVideo }
                        val kind = detectMediaKind(url, ig.slides.size, hasVideo)
                        return LinkPreview(
                            url = url,
                            title = ig.title,
                            creator = ig.creator,
                            platform = platform,
                            thumbnailUrl = ig.slides.firstNotNullOfOrNull { it.thumbnailUrl ?: if (!it.isVideo) it.mediaUrl else null },
                            mediaKind = kind,
                            itemCount = ig.slides.size,
                            durationSeconds = null,
                            caption = ig.caption,
                            slides = ig.slides,
                            requiresLoginHint = false,
                            options = defaultOptions(isPhotoOnly = !hasVideo)
                        )
                    }
                } catch (_: Throwable) {}
            } else {
                return LinkPreview(
                    url = url,
                    title = "Instagram Story / Highlight",
                    creator = "Instagram",
                    platform = platform,
                    thumbnailUrl = null,
                    mediaKind = MediaKind.VIDEO,
                    itemCount = 1,
                    requiresLoginHint = true,
                    options = defaultOptions(isPhotoOnly = false)
                )
            }
        }

        // 2. yt-dlp --dump-single-json for rich metadata & exact format/quality options
        try {
            val req = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--no-warnings")
                addOption("--flat-playlist")
                addOption("--socket-timeout", "15")
                if (cookieFile != null && cookieFile.exists()) {
                    addOption("--cookies", cookieFile.absolutePath)
                }
            }
            val resp = YoutubeDL.getInstance().execute(req, null)
            val jsonText = resp.out.orEmpty().trim()
            if (jsonText.startsWith("{")) {
                val obj = JSONObject(jsonText)
                val title = obj.optString("title").ifBlank { Names.fromLink(url) }
                val creator = obj.optString("uploader")
                    .ifBlank { obj.optString("channel") }
                    .ifBlank { obj.optString("uploader_id") }
                    .ifBlank { platform }
                val thumb = obj.optString("thumbnail").ifBlank { null }
                val duration = obj.optLong("duration", 0L).takeIf { it > 0L }
                val caption = obj.optString("description")
                val entries = obj.optJSONArray("entries")
                val entryCount = entries?.length()?.coerceAtLeast(1) ?: 1
                val formats = buildFormatOptions(obj)
                val kind = detectMediaKind(url, entryCount, hasVideo = true)

                return LinkPreview(
                    url = url,
                    title = title,
                    creator = creator,
                    platform = platform,
                    thumbnailUrl = thumb,
                    mediaKind = kind,
                    itemCount = entryCount,
                    durationSeconds = duration,
                    caption = caption,
                    options = formats
                )
            }
        } catch (_: Throwable) {}

        // 3. Fallback to Images / OpenGraph photo detection
        try {
            val imgs = Images.find(url, cookieFile)
            if (imgs.isNotEmpty()) {
                val title = Images.lastTitle.ifBlank { Names.fromLink(url) }
                return LinkPreview(
                    url = url,
                    title = title,
                    creator = platform,
                    platform = platform,
                    thumbnailUrl = imgs.firstOrNull(),
                    mediaKind = if (imgs.size > 1) MediaKind.CAROUSEL else MediaKind.PHOTO,
                    itemCount = imgs.size,
                    slides = imgs.mapIndexed { idx, u -> PreviewSlide(idx, u, u, false) },
                    options = defaultOptions(isPhotoOnly = true)
                )
            }
        } catch (_: Throwable) {}

        // 4. Clean minimal fallback
        val showIgLoginHint = platform == "Instagram" && (cookieFile == null || !cookieFile.exists())
        return LinkPreview(
            url = url,
            title = Names.fromLink(url),
            creator = platform,
            platform = platform,
            thumbnailUrl = null,
            mediaKind = detectMediaKind(url, 1, true),
            itemCount = 1,
            requiresLoginHint = showIgLoginHint,
            options = defaultOptions(isPhotoOnly = false)
        )
    }

    private fun buildFormatOptions(obj: JSONObject): List<PreviewFormatOption> {
        val formatsArr = obj.optJSONArray("formats") ?: return defaultOptions(isPhotoOnly = false)
        val heights = linkedMapOf<Int, Pair<String, Long?>>()
        var bestAudioSize: Long? = null

        for (i in 0 until formatsArr.length()) {
            val f = formatsArr.optJSONObject(i) ?: continue
            val h = f.optInt("height", 0)
            val ext = f.optString("ext").lowercase()
            val vcodec = f.optString("vcodec", "none")
            val size = f.optLong("filesize", 0L)
                .takeIf { it > 0L }
                ?: f.optLong("filesize_approx", 0L).takeIf { it > 0L }

            if (vcodec == "none" && size != null) {
                bestAudioSize = maxOf(bestAudioSize ?: 0L, size)
            }
            if (h >= 144 && vcodec != "none") {
                val prev = heights[h]
                if (prev == null || (ext == "mp4" && prev.first != "mp4")) {
                    heights[h] = ext to size
                }
            }
        }

        val options = mutableListOf<PreviewFormatOption>()
        options.add(
            PreviewFormatOption(
                id = "orig_mp4",
                qualityLabel = "Original",
                format = OutputFormat.ORIGINAL,
                resolutionHeight = heights.keys.maxOrNull(),
                estimatedSizeBytes = heights.values.mapNotNull { it.second }.maxOrNull(),
                formatSelector = "bestvideo+bestaudio/best",
                note = "Highest available"
            )
        )

        val standardHeights = listOf(2160, 1440, 1080, 720, 480, 360, 144)
        for (target in standardHeights) {
            val matched = heights.keys.filter { it in (target - 60)..(target + 60) }.maxOrNull()
            if (matched != null || (heights.isEmpty() && target in listOf(1080, 720, 480, 360))) {
                val estSize = matched?.let { heights[it]?.second }?.let { s ->
                    if (bestAudioSize != null) s + bestAudioSize else s
                }
                val label = if (target >= 2160) "2160p (4K)" else "${target}p"
                options.add(
                    PreviewFormatOption(
                        id = "mp4_$target",
                        qualityLabel = label,
                        format = OutputFormat.MP4,
                        resolutionHeight = target,
                        estimatedSizeBytes = estSize,
                        formatSelector = "bestvideo[height<=$target][ext=mp4]+bestaudio[ext=m4a]/best[height<=$target][ext=mp4]/best[height<=$target]",
                        note = "MP4"
                    )
                )
            }
        }

        options.add(
            PreviewFormatOption(
                id = "webm_best",
                qualityLabel = "Best WebM",
                format = OutputFormat.WEBM,
                resolutionHeight = heights.keys.maxOrNull(),
                estimatedSizeBytes = null,
                formatSelector = "bestvideo[ext=webm]+bestaudio[ext=webm]/best[ext=webm]/best",
                note = "WebM"
            )
        )
        options.add(
            PreviewFormatOption(
                id = "audio_mp3",
                qualityLabel = "Audio MP3",
                format = OutputFormat.MP3,
                resolutionHeight = null,
                estimatedSizeBytes = bestAudioSize,
                formatSelector = "bestaudio/best",
                note = "320kbps"
            )
        )
        options.add(
            PreviewFormatOption(
                id = "audio_m4a",
                qualityLabel = "Audio M4A",
                format = OutputFormat.M4A,
                resolutionHeight = null,
                estimatedSizeBytes = bestAudioSize,
                formatSelector = "bestaudio[ext=m4a]/bestaudio/best",
                note = "AAC"
            )
        )
        return options
    }

    fun defaultOptions(isPhotoOnly: Boolean): List<PreviewFormatOption> {
        if (isPhotoOnly) {
            return listOf(
                PreviewFormatOption(
                    id = "photo_orig",
                    qualityLabel = "Original",
                    format = OutputFormat.ORIGINAL,
                    resolutionHeight = null,
                    estimatedSizeBytes = null,
                    formatSelector = "best",
                    note = "Full resolution"
                )
            )
        }
        return listOf(
            PreviewFormatOption("orig", "Original", OutputFormat.ORIGINAL, null, null, "bestvideo+bestaudio/best", "Highest available"),
            PreviewFormatOption("1080", "1080p", OutputFormat.MP4, 1080, null, "best[height<=1080][ext=mp4]/best[height<=1080]/best", "MP4"),
            PreviewFormatOption("720", "720p", OutputFormat.MP4, 720, null, "best[height<=720][ext=mp4]/best[height<=720]/best", "MP4"),
            PreviewFormatOption("480", "480p", OutputFormat.MP4, 480, null, "best[height<=480][ext=mp4]/best[height<=480]/best", "MP4"),
            PreviewFormatOption("360", "360p", OutputFormat.MP4, 360, null, "best[height<=360][ext=mp4]/best[height<=360]/best", "MP4"),
            PreviewFormatOption("144", "144p", OutputFormat.MP4, 144, null, "best[height<=144][ext=mp4]/best[height<=144]/best", "MP4"),
            PreviewFormatOption("webm", "Best WebM", OutputFormat.WEBM, null, null, "best[ext=webm]/best", "WebM"),
            PreviewFormatOption("mp3", "Audio MP3", OutputFormat.MP3, null, null, "bestaudio/best", "MP3"),
            PreviewFormatOption("m4a", "Audio M4A", OutputFormat.M4A, null, null, "bestaudio/best", "M4A")
        )
    }
}
