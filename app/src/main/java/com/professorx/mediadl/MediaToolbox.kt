package com.professorx.mediadl

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.nio.ByteBuffer

enum class ToolboxAction(val label: String, val subtitle: String) {
    EXTRACT_AUDIO("Extract Audio", "Save MP3 or M4A from a video"),
    CONVERT_AUDIO("Convert Audio", "Convert between MP3, M4A, WAV & OPUS"),
    TRIM_MEDIA("Trim Audio / Video", "Cut a clip using start & end seconds"),
    COMPRESS_VIDEO("Compress Video", "Reduce file size for sharing"),
    EXTRACT_THUMBNAIL("Extract Thumbnail", "Save a frame as a JPG image")
}

object MediaToolbox {
    private fun ensureFfmpegReady(ctx: Context) {
        runCatching { YoutubeDL.getInstance().init(ctx) }
        runCatching { FFmpeg.getInstance().init(ctx) }
    }

    private fun findFfmpegBinary(ctx: Context): File? {
        val searchRoots = listOfNotNull(
            File(ctx.noBackupFilesDir, "youtubedl-android"),
            File(ctx.filesDir, "youtubedl-android"),
            ctx.noBackupFilesDir,
            ctx.filesDir,
            File(ctx.applicationInfo.nativeLibraryDir)
        )
        for (root in searchRoots) {
            if (!root.exists()) continue
            val direct = listOf(
                File(root, "packages/ffmpeg/usr/bin/ffmpeg"),
                File(root, "ffmpeg"),
                File(root, "libffmpeg.so")
            ).firstOrNull { it.exists() && it.isFile }
            if (direct != null) {
                runCatching { direct.setExecutable(true, false) }
                return direct
            }
            val found = root.walkTopDown().maxDepth(6).firstOrNull {
                it.isFile && (it.name == "ffmpeg" || it.name == "libffmpeg.so")
            }
            if (found != null) {
                runCatching { found.setExecutable(true, false) }
                return found
            }
        }
        return null
    }

    private fun copyUriToTemp(ctx: Context, uri: Uri, extHint: String): File? {
        val dir = File(ctx.cacheDir, "toolbox_tmp").apply { mkdirs() }
        val out = File(dir, "in_${System.currentTimeMillis()}.$extHint")
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                out.outputStream().use { os -> ins.copyTo(os) }
            }
            if (out.exists() && out.length() > 0L) out else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun runFfmpeg(ctx: Context, args: List<String>): Pair<Boolean, String> {
        ensureFfmpegReady(ctx)
        val bin = findFfmpegBinary(ctx)
            ?: return false to "FFmpeg binary not found. Please wait for engine initialization or update engine in Settings."

        val libDirs = mutableListOf<String>()
        bin.parentFile?.parentFile?.let { base ->
            File(base, "lib").takeIf { it.exists() }?.let { libDirs.add(it.absolutePath) }
        }
        File(ctx.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")
            .takeIf { it.exists() }?.let { libDirs.add(it.absolutePath) }
        libDirs.add(ctx.applicationInfo.nativeLibraryDir)

        return try {
            val pb = ProcessBuilder(listOf(bin.absolutePath, "-y") + args)
            val env = pb.environment()
            val cur = env["LD_LIBRARY_PATH"].orEmpty()
            val joined = libDirs.distinct().joinToString(":")
            env["LD_LIBRARY_PATH"] = if (cur.isBlank()) joined else "$joined:$cur"
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            val code = proc.waitFor()
            (code == 0) to output.takeLast(240)
        } catch (e: Throwable) {
            false to (e.message ?: "FFmpeg execution error")
        }
    }

    fun extractThumbnail(ctx: Context, media: SavedMedia, atSeconds: Long = 1L): Result<File> {
        val dir = File(ctx.cacheDir, "toolbox_tmp").apply { mkdirs() }
        val base = Names.clean(media.name.substringBeforeLast('.'))
        val out = File(dir, "${base}_thumb.jpg")
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(ctx, media.uri)
            val bmp = mmr.getFrameAtTime(atSeconds.coerceAtLeast(0L) * 1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: mmr.getFrameAtTime(0L)
            mmr.release()
            if (bmp == null) return Result.failure(IllegalStateException("Could not read video frame."))
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            Result.success(out)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun extractAudio(ctx: Context, media: SavedMedia, targetExt: String = "mp3"): Result<File> {
        val inExt = media.name.substringAfterLast('.', "mp4")
        val input = copyUriToTemp(ctx, media.uri, inExt)
            ?: return Result.failure(IllegalStateException("Source file is unavailable."))
        val dir = File(ctx.cacheDir, "toolbox_tmp").apply { mkdirs() }
        val base = Names.clean(media.name.substringBeforeLast('.'))
        val out = File(dir, "${base}_audio.$targetExt")
        val codecArgs = when (targetExt.lowercase()) {
            "m4a" -> listOf("-vn", "-c:a", "aac", "-b:a", "192k")
            "opus" -> listOf("-vn", "-c:a", "libopus", "-b:a", "160k")
            "wav" -> listOf("-vn", "-c:a", "pcm_s16le")
            else -> listOf("-vn", "-c:a", "libmp3lame", "-q:a", "2")
        }
        val (ok, err) = runFfmpeg(ctx, listOf("-i", input.absolutePath) + codecArgs + listOf(out.absolutePath))
        if (ok && out.exists() && out.length() > 0L) {
            input.delete()
            return Result.success(out)
        }
        // Native fallback for M4A audio extraction using MediaExtractor + MediaMuxer
        if (targetExt.lowercase() == "m4a") {
            val nativeOut = File(dir, "${base}_audio.m4a")
            if (nativeMux(input, nativeOut, audioOnly = true, startUs = -1L, endUs = -1L)) {
                input.delete()
                return Result.success(nativeOut)
            }
        }
        input.delete()
        return Result.failure(IllegalStateException(err.ifBlank { "Audio extraction failed." }))
    }

    fun convertAudio(ctx: Context, media: SavedMedia, targetExt: String): Result<File> =
        extractAudio(ctx, media, targetExt)

    fun trimMedia(ctx: Context, media: SavedMedia, startSec: Int, endSec: Int): Result<File> {
        if (endSec <= startSec) return Result.failure(IllegalArgumentException("End time must be greater than start time."))
        val inExt = media.name.substringAfterLast('.', "mp4")
        val input = copyUriToTemp(ctx, media.uri, inExt)
            ?: return Result.failure(IllegalStateException("Source file is unavailable."))
        val dir = File(ctx.cacheDir, "toolbox_tmp").apply { mkdirs() }
        val base = Names.clean(media.name.substringBeforeLast('.'))
        val out = File(dir, "${base}_trim_${startSec}_${endSec}.$inExt")

        // Try native MediaExtractor + MediaMuxer first for MP4/M4A (instant, zero external binary needed)
        if (inExt.lowercase() in setOf("mp4", "m4a", "mov")) {
            val nativeOk = nativeMux(
                input = input,
                output = out,
                audioOnly = false,
                startUs = startSec * 1_000_000L,
                endUs = endSec * 1_000_000L
            )
            if (nativeOk && out.exists() && out.length() > 0L) {
                input.delete()
                return Result.success(out)
            }
        }

        val (ok, err) = runFfmpeg(
            ctx,
            listOf(
                "-ss", startSec.toString(),
                "-to", endSec.toString(),
                "-i", input.absolutePath,
                "-c", "copy",
                out.absolutePath
            )
        )
        input.delete()
        return if (ok && out.exists() && out.length() > 0L) Result.success(out)
        else Result.failure(IllegalStateException(err.ifBlank { "Trim failed." }))
    }

    fun compressVideo(ctx: Context, media: SavedMedia, crf: Int = 28): Result<File> {
        val inExt = media.name.substringAfterLast('.', "mp4")
        val input = copyUriToTemp(ctx, media.uri, inExt)
            ?: return Result.failure(IllegalStateException("Source file is unavailable."))
        val dir = File(ctx.cacheDir, "toolbox_tmp").apply { mkdirs() }
        val base = Names.clean(media.name.substringBeforeLast('.'))
        val out = File(dir, "${base}_compressed.mp4")
        val (ok, err) = runFfmpeg(
            ctx,
            listOf(
                "-i", input.absolutePath,
                "-vcodec", "libx264",
                "-crf", crf.coerceIn(18, 36).toString(),
                "-preset", "veryfast",
                "-acodec", "aac",
                "-b:a", "128k",
                out.absolutePath
            )
        )
        input.delete()
        return if (ok && out.exists() && out.length() > 0L) Result.success(out)
        else Result.failure(IllegalStateException(err.ifBlank { "Compression failed." }))
    }

    private fun nativeMux(
        input: File,
        output: File,
        audioOnly: Boolean,
        startUs: Long,
        endUs: Long
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(input.absolutePath)
            val trackCount = extractor.trackCount
            val indexMap = HashMap<Int, Int>(trackCount)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var maxBuf = 256 * 1024

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                val include = if (audioOnly) mime.startsWith("audio/") else (mime.startsWith("audio/") || mime.startsWith("video/"))
                if (include) {
                    extractor.selectTrack(i)
                    val dstTrack = muxer.addTrack(format)
                    indexMap[i] = dstTrack
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxBuf = maxOf(maxBuf, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                }
            }
            if (indexMap.isEmpty()) return false

            muxer.start()
            if (startUs > 0L) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val buf = ByteBuffer.allocate(maxBuf)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break
                val sampleTime = extractor.sampleTime
                if (endUs > 0L && sampleTime > endUs) break
                val sampleSize = extractor.readSampleData(buf, 0)
                if (sampleSize < 0) break
                val dstTrack = indexMap[trackIndex]
                if (dstTrack != null) {
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = if (startUs > 0L) (sampleTime - startUs).coerceAtLeast(0L) else sampleTime
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buf, info)
                }
                extractor.advance()
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }
}
