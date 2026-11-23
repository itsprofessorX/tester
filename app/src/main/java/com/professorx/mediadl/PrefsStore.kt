package com.professorx.mediadl

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("mediadown", Context.MODE_PRIVATE)

    fun loadPreferences(): UserPreferences {
        return UserPreferences(
            themeMode = runCatching { AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.AMOLED.name)!!) }.getOrDefault(AppThemeMode.AMOLED),
            accentPreset = runCatching { AccentPreset.valueOf(prefs.getString("accent_preset", AccentPreset.CORAL.name)!!) }.getOrDefault(AccentPreset.CORAL),
            dynamicColor = prefs.getBoolean("dynamic_color", false),
            hapticsEnabled = prefs.getBoolean("haptics", true),
            reducedMotion = prefs.getBoolean("reduced_motion", false),
            largeTextScale = prefs.getBoolean("large_text", false),
            autoShare = prefs.getBoolean("auto", false),
            smartFileNaming = prefs.getBoolean("smart_naming", true),
            defaultQuality = prefs.getString("default_quality", "1080p") ?: "1080p",
            defaultFormat = runCatching { OutputFormat.valueOf(prefs.getString("default_format", OutputFormat.MP4.name)!!) }.getOrDefault(OutputFormat.MP4),
            localOnlyHistory = prefs.getBoolean("local_only", true),
            saveHistoryEnabled = prefs.getBoolean("save_history", true),
            clipboardDetectEnabled = prefs.getBoolean("clipboard_detect", true),
            notifyComplete = prefs.getBoolean("notify_complete", true),
            notifyFailure = prefs.getBoolean("notify_failure", true),
            notifyProgress = prefs.getBoolean("notify_progress", true),
            notifySound = prefs.getBoolean("notify_sound", false)
        )
    }

    fun savePreferences(p: UserPreferences) {
        prefs.edit()
            .putString("theme_mode", p.themeMode.name)
            .putString("accent_preset", p.accentPreset.name)
            .putBoolean("dynamic_color", p.dynamicColor)
            .putBoolean("haptics", p.hapticsEnabled)
            .putBoolean("reduced_motion", p.reducedMotion)
            .putBoolean("large_text", p.largeTextScale)
            .putBoolean("auto", p.autoShare)
            .putBoolean("smart_naming", p.smartFileNaming)
            .putString("default_quality", p.defaultQuality)
            .putString("default_format", p.defaultFormat.name)
            .putBoolean("local_only", p.localOnlyHistory)
            .putBoolean("save_history", p.saveHistoryEnabled)
            .putBoolean("clipboard_detect", p.clipboardDetectEnabled)
            .putBoolean("notify_complete", p.notifyComplete)
            .putBoolean("notify_failure", p.notifyFailure)
            .putBoolean("notify_progress", p.notifyProgress)
            .putBoolean("notify_sound", p.notifySound)
            .apply()
    }

    fun loadHistory(): List<SavedMedia> {
        val raw = prefs.getString("history", "[]") ?: "[]"
        val out = mutableListOf<SavedMedia>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                // Supports both v1.2 keys ("n", "u", "m") and v2.0 keys
                val name = o.optString("name").ifBlank { o.optString("n") }
                val uri = o.optString("uri").ifBlank { o.optString("u") }
                val mime = o.optString("mime").ifBlank { o.optString("m", "video/mp4") }
                if (name.isBlank() || uri.isBlank()) continue
                val kind = runCatching { MediaKind.valueOf(o.optString("kind")) }.getOrElse {
                    when {
                        mime.startsWith("image") -> MediaKind.PHOTO
                        mime.startsWith("audio") -> MediaKind.AUDIO
                        else -> MediaKind.VIDEO
                    }
                }
                out.add(
                    SavedMedia(
                        id = o.optString("id").ifBlank { "${uri.hashCode()}_$i" },
                        name = name,
                        uriString = uri,
                        mime = mime,
                        platform = o.optString("platform", "Unknown"),
                        kind = kind,
                        sizeBytes = o.optLong("size", 0L),
                        timestamp = o.optLong("ts", System.currentTimeMillis()),
                        isFavorite = o.optBoolean("fav", false),
                        existsInGallery = true,
                        sourceUrl = o.optString("src", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return out
    }

    fun saveHistory(items: List<SavedMedia>) {
        val arr = JSONArray()
        items.take(250).forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("uri", item.uriString)
                    .put("mime", item.mime)
                    .put("platform", item.platform)
                    .put("kind", item.kind.name)
                    .put("size", item.sizeBytes)
                    .put("ts", item.timestamp)
                    .put("fav", item.isFavorite)
                    .put("src", item.sourceUrl)
            )
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun loadStats(): DownloadStats {
        return DownloadStats(
            totalDownloads = prefs.getInt("stat_total", 0),
            photosSaved = prefs.getInt("stat_photos", 0),
            videosSaved = prefs.getInt("stat_videos", 0),
            audioSaved = prefs.getInt("stat_audio", 0),
            totalBytesSaved = prefs.getLong("stat_bytes", 0L),
            toolboxRuns = prefs.getInt("stat_toolbox", 0)
        )
    }

    fun saveStats(s: DownloadStats) {
        prefs.edit()
            .putInt("stat_total", s.totalDownloads)
            .putInt("stat_photos", s.photosSaved)
            .putInt("stat_videos", s.videosSaved)
            .putInt("stat_audio", s.audioSaved)
            .putLong("stat_bytes", s.totalBytesSaved)
            .putInt("stat_toolbox", s.toolboxRuns)
            .apply()
    }

    fun loadNotifications(): List<NotificationLogItem> {
        val raw = prefs.getString("notif_log", "[]") ?: "[]"
        val out = mutableListOf<NotificationLogItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    NotificationLogItem(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        message = o.optString("msg"),
                        timestamp = o.optLong("ts"),
                        isError = o.optBoolean("err", false),
                        uriString = o.optString("uri").ifBlank { null },
                        mime = o.optString("mime").ifBlank { null },
                        retryUrl = o.optString("retry").ifBlank { null }
                    )
                )
            }
        } catch (_: Exception) {}
        return out
    }

    fun saveNotifications(items: List<NotificationLogItem>) {
        val arr = JSONArray()
        items.take(40).forEach { n ->
            arr.put(
                JSONObject()
                    .put("id", n.id)
                    .put("title", n.title)
                    .put("msg", n.message)
                    .put("ts", n.timestamp)
                    .put("err", n.isError)
                    .put("uri", n.uriString ?: "")
                    .put("mime", n.mime ?: "")
                    .put("retry", n.retryUrl ?: "")
            )
        }
        prefs.edit().putString("notif_log", arr.toString()).apply()
    }

    fun exportHistoryJson(items: List<SavedMedia>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("name", item.name)
                    .put("platform", item.platform)
                    .put("kind", item.kind.label)
                    .put("sizeBytes", item.sizeBytes)
                    .put("mime", item.mime)
                    .put("timestamp", item.timestamp)
                    .put("favorite", item.isFavorite)
                    .put("sourceUrl", item.sourceUrl)
            )
        }
        return arr.toString(2)
    }
}
