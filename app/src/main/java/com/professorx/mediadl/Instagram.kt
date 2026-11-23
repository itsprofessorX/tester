package com.professorx.mediadl

import org.json.JSONObject
import java.io.File
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class IgResolvedResult(
    val title: String,
    val creator: String,
    val caption: String,
    val slides: List<PreviewSlide>
)

/**
 * Hybrid Instaloader-style Kotlin Instagram resolver:
 * 1. Tries cookie-free endpoints first (POST /api/graphql, GET /graphql/query, ?__a=1&__d=dis, embed/captioned/).
 * 2. Uses saved session cookies when available for private posts, stories, highlights, and full carousels.
 */
object Instagram {
    private const val APP_ID = "936619743392459"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val postRe = Regex("instagram\\.com/(?:[^/?#]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)")
    private val storyRe = Regex("instagram\\.com/stories/([^/?#]+)(?:/(\\d+))?")
    private val profileRe = Regex("instagram\\.com/([A-Za-z0-9._]+)/?(?:\\?.*)?$")

    fun isInstagram(link: String): Boolean {
        val l = link.lowercase()
        return "instagram.com" in l || "instagr.am" in l
    }

    fun isStoryOrHighlight(link: String): Boolean = storyRe.containsMatchIn(link)

    fun extractProfileUsername(link: String): String? {
        if (postRe.containsMatchIn(link) || storyRe.containsMatchIn(link)) return null
        val user = profileRe.find(link)?.groupValues?.get(1) ?: return null
        val reserved = setOf("p", "reel", "reels", "tv", "stories", "explore", "accounts", "direct")
        return if (user.lowercase() in reserved) null else user
    }

    /** Resolves public or logged-in Instagram post/reel/carousel/profile DP with full metadata. */
    fun resolveRich(link: String, cookieFile: File?): IgResolvedResult? {
        val cookie = Images.cookieHeader(cookieFile)
        val headers = baseHeaders(cookie)

        // 1. Profile DP (HD)
        extractProfileUsername(link)?.let { username ->
            resolveProfileDp(username, headers)?.let { return it }
        }

        // 2. Stories / Highlights — requires login cookie
        val story = storyRe.find(link)
        if (story != null) {
            if (cookie == null) return null
            val items = resolve(link, cookieFile ?: return null)
            if (items.isEmpty()) return null
            return IgResolvedResult(
                title = items.first().name,
                creator = story.groupValues[1],
                caption = "",
                slides = items.mapIndexed { idx, item ->
                    val isVid = ".mp4" in item.url.lowercase() || "video" in item.url.lowercase()
                    PreviewSlide(idx, item.url, if (isVid) null else item.url, isVid)
                }
            )
        }

        val code = postRe.find(link)?.groupValues?.get(1) ?: return null

        // 3. Authenticated API first when logged in (returns full carousel_media + private posts)
        if (cookie != null) {
            resolveAuthenticatedPost(code, headers)?.let { return it }
        }

        // 4. Cookie-free RelayModern POST /api/graphql
        resolvePostGraphql(code, headers)?.let { return it }

        // 5. Cookie-free GET /graphql/query
        resolvePublicGraphql(code, headers)?.let { return it }

        // 6. Cookie-free ?__a=1&__d=dis JSON endpoint
        resolveJsonEndpoint(code, headers)?.let { return it }

        // 7. Cookie-free embed fallback
        val embedPhotos = Images.find(link, null)
        if (embedPhotos.isNotEmpty()) {
            return IgResolvedResult(
                title = Images.lastTitle.ifBlank { "Instagram $code" },
                creator = "Instagram",
                caption = "",
                slides = embedPhotos.mapIndexed { idx, u -> PreviewSlide(idx, u, u, false) }
            )
        }
        return null
    }

    /** Returns direct media URLs (photos and videos) or an empty list. */
    fun resolve(link: String, cookieFile: File): List<MediaItem> {
        val cookie = Images.cookieHeader(cookieFile) ?: return emptyList()
        val h = baseHeaders(cookie)

        val story = storyRe.find(link)
        if (story != null) {
            val who = story.groupValues[1]
            val sid = story.groupValues[2]
            return if (who == "highlights") reel("highlight:$sid", sid, h) else stories(who, sid, h)
        }
        val code = postRe.find(link)?.groupValues?.get(1) ?: return emptyList()
        return post(code, h)
    }

    private fun baseHeaders(cookie: String?): MutableMap<String, String> {
        val h = mutableMapOf(
            "x-ig-app-id" to APP_ID,
            "x-requested-with" to "XMLHttpRequest",
            "x-ig-www-claim" to "0",
            "Referer" to "https://www.instagram.com/",
            "Origin" to "https://www.instagram.com"
        )
        if (cookie != null) {
            h["Cookie"] = cookie
            Regex("csrftoken=([^;]+)").find(cookie)?.groupValues?.get(1)?.let { h["x-csrftoken"] = it }
        }
        return h
    }

    private fun resolveProfileDp(username: String, h: Map<String, String>): IgResolvedResult? {
        val body = get("https://www.instagram.com/api/v1/users/web_profile_info/?username=" + URLEncoder.encode(username, "UTF-8"), h)
            ?: return null
        return try {
            val user = JSONObject(body).optJSONObject("data")?.optJSONObject("user") ?: return null
            val hd = user.optString("profile_pic_url_hd").ifBlank { user.optString("profile_pic_url") }
            if (!hd.startsWith("http")) return null
            val fullName = user.optString("full_name").ifBlank { username }
            val bio = user.optString("biography")
            IgResolvedResult(
                title = "$fullName (@$username) Profile Photo",
                creator = "@$username",
                caption = bio,
                slides = listOf(PreviewSlide(0, hd, hd, false))
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveAuthenticatedPost(code: String, h: Map<String, String>): IgResolvedResult? {
        var item = mediaInfo(shortcodeToId(code), h)
        if (item == null) item = mediaInfo(graphqlId(code, h), h)
        val it = item ?: return null
        return parseV1MediaItem(it, code)
    }

    private fun parseV1MediaItem(it: JSONObject, code: String): IgResolvedResult? {
        val user = it.optJSONObject("user")?.optString("username").orEmpty()
        val cap = it.optJSONObject("caption")?.optString("text").orEmpty()
        val firstLine = cap.lineSequence().firstOrNull().orEmpty()
        val base = Names.clean(listOf(user, firstLine).filter { p -> p.isNotBlank() }.joinToString(" - ").ifBlank { "Instagram $code" })
        val slides = mutableListOf<PreviewSlide>()
        val carousel = it.optJSONArray("carousel_media")
        if (carousel != null) {
            for (i in 0 until carousel.length()) {
                val m = carousel.getJSONObject(i)
                val u = mediaUrl(m) ?: continue
                val thumb = m.optJSONObject("image_versions2")?.optJSONArray("candidates")?.optJSONObject(0)?.optString("url")
                slides.add(PreviewSlide(i, u, thumb, m.optInt("media_type") != 1))
            }
        } else {
            val u = mediaUrl(it)
            if (u != null) {
                val thumb = it.optJSONObject("image_versions2")?.optJSONArray("candidates")?.optJSONObject(0)?.optString("url")
                slides.add(PreviewSlide(0, u, thumb, it.optInt("media_type") != 1))
            }
        }
        return if (slides.isEmpty()) null else IgResolvedResult(base, if (user.isNotBlank()) "@$user" else "Instagram", cap, slides)
    }

    private fun resolvePostGraphql(code: String, h: Map<String, String>): IgResolvedResult? {
        val form = "fb_api_caller_class=RelayModern" +
            "&fb_api_req_friendly_name=PolarisPostActionLoadPostQueryQuery" +
            "&doc_id=8845758582119845" +
            "&variables=" + URLEncoder.encode(
                "{\"shortcode\":\"$code\",\"fetch_tagged_user_count\":null,\"hoisted_comment_id\":null,\"hoisted_reply_id\":null}",
                "UTF-8"
            )
        val hh = h.toMutableMap()
        hh["content-type"] = "application/x-www-form-urlencoded"
        hh["x-fb-friendly-name"] = "PolarisPostActionLoadPostQueryQuery"
        val body = request("https://www.instagram.com/api/graphql", hh, form)
            ?: request("https://www.instagram.com/graphql/query/", hh, form)
            ?: return null
        return parseXdtShortcodeBody(body, code)
    }

    private fun resolvePublicGraphql(code: String, h: Map<String, String>): IgResolvedResult? {
        val vars = URLEncoder.encode(
            "{\"shortcode\":\"$code\",\"fetch_tagged_user_count\":null,\"hoisted_comment_id\":null,\"hoisted_reply_id\":null}",
            "UTF-8"
        )
        val body = get("https://www.instagram.com/graphql/query/?doc_id=8845758582119845&variables=$vars", h) ?: return null
        return parseXdtShortcodeBody(body, code)
    }

    private fun resolveJsonEndpoint(code: String, h: Map<String, String>): IgResolvedResult? {
        val body = get("https://www.instagram.com/p/$code/?__a=1&__d=dis", h) ?: return null
        return try {
            val root = JSONObject(body)
            val v1Item = root.optJSONArray("items")?.optJSONObject(0)
            if (v1Item != null) return parseV1MediaItem(v1Item, code)
            val gql = root.optJSONObject("graphql")?.optJSONObject("shortcode_media")
            if (gql != null) return parseShortcodeMediaObject(gql, code)
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseXdtShortcodeBody(body: String, code: String): IgResolvedResult? = try {
        val data = JSONObject(body).optJSONObject("data")
        val m = data?.optJSONObject("xdt_shortcode_media") ?: data?.optJSONObject("shortcode_media")
        if (m != null) parseShortcodeMediaObject(m, code) else null
    } catch (_: Exception) {
        null
    }

    private fun parseShortcodeMediaObject(m: JSONObject, code: String): IgResolvedResult? {
        val user = m.optJSONObject("owner")?.optString("username").orEmpty()
        val cap = m.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")
            ?.optJSONObject(0)?.optJSONObject("node")?.optString("text").orEmpty()
        val firstLine = cap.lineSequence().firstOrNull().orEmpty()
        val title = Names.clean(listOf(user, firstLine).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Instagram $code" })

        val slides = mutableListOf<PreviewSlide>()
        val edges = m.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
        if (edges != null) {
            for (i in 0 until edges.length()) {
                val n = edges.getJSONObject(i).optJSONObject("node") ?: continue
                val isVid = n.optBoolean("is_video")
                val mediaUrl = if (isVid) n.optString("video_url") else n.optString("display_url")
                val thumbUrl = n.optString("display_url").ifBlank { null }
                if (mediaUrl.startsWith("http")) {
                    slides.add(PreviewSlide(i, mediaUrl, thumbUrl, isVid))
                }
            }
        } else {
            val isVid = m.optBoolean("is_video")
            val mediaUrl = if (isVid) m.optString("video_url") else m.optString("display_url")
            val thumbUrl = m.optString("display_url").ifBlank { null }
            if (mediaUrl.startsWith("http")) {
                slides.add(PreviewSlide(0, mediaUrl, thumbUrl, isVid))
            }
        }
        return if (slides.isEmpty()) null else IgResolvedResult(title, if (user.isNotBlank()) "@$user" else "Instagram", cap, slides)
    }

    // ------------------------------------------------------------ posts / reels
    private fun post(code: String, h: Map<String, String>): List<MediaItem> {
        var item = mediaInfo(shortcodeToId(code), h)
        if (item == null) item = mediaInfo(graphqlId(code, h), h)
        val it = item ?: return emptyList()
        val carousel = it.optJSONArray("carousel_media")
        val user = it.optJSONObject("user")?.optString("username").orEmpty()
        val cap = it.optJSONObject("caption")?.optString("text").orEmpty().lineSequence().firstOrNull().orEmpty()
        val base = Names.clean(listOf(user, cap).filter { p -> p.isNotBlank() }.joinToString(" - ").ifBlank { "Instagram $code" })
        val out = mutableListOf<MediaItem>()
        if (carousel != null) {
            for (i in 0 until carousel.length()) mediaUrl(carousel.getJSONObject(i))?.let { u -> out.add(MediaItem(u, base)) }
        } else {
            mediaUrl(it)?.let { u -> out.add(MediaItem(u, base)) }
        }
        return out
    }

    private fun mediaInfo(id: String?, h: Map<String, String>): JSONObject? {
        if (id == null) return null
        val body = get("https://www.instagram.com/api/v1/media/$id/info/", h) ?: return null
        return try { JSONObject(body).optJSONArray("items")?.optJSONObject(0) } catch (_: Exception) { null }
    }

    private fun graphqlId(code: String, h: Map<String, String>): String? {
        val form = "fb_api_caller_class=RelayModern" +
            "&fb_api_req_friendly_name=PolarisPostActionLoadPostQueryQuery" +
            "&doc_id=8845758582119845" +
            "&variables=" + URLEncoder.encode("{\"shortcode\":\"$code\"}", "UTF-8")
        val hh = h.toMutableMap()
        hh["content-type"] = "application/x-www-form-urlencoded"
        hh["x-fb-friendly-name"] = "PolarisPostActionLoadPostQueryQuery"
        val body = request("https://www.instagram.com/graphql/query/", hh, form) ?: return null
        return try { JSONObject(body).optJSONObject("data")?.optJSONObject("xdt_shortcode_media")?.optString("id")?.ifBlank { null } }
        catch (_: Exception) { null }
    }

    // ------------------------------------------------------------ stories / highlights
    private fun stories(username: String, storyId: String, h: Map<String, String>): List<MediaItem> {
        val body = get("https://www.instagram.com/api/v1/users/web_profile_info/?username=" + URLEncoder.encode(username, "UTF-8"), h)
            ?: return emptyList()
        val uid = try { JSONObject(body).optJSONObject("data")?.optJSONObject("user")?.optString("id") } catch (_: Exception) { null }
        if (uid.isNullOrBlank()) return emptyList()
        return reel(uid, storyId, h)
    }

    private fun reel(reelId: String, onlyId: String, h: Map<String, String>): List<MediaItem> {
        val body = get("https://www.instagram.com/api/v1/feed/reels_media/?reel_ids=" + URLEncoder.encode(reelId, "UTF-8"), h)
            ?: return emptyList()
        return try {
            val r = JSONObject(body).optJSONObject("reels")?.optJSONObject(reelId) ?: return emptyList()
            val who = r.optJSONObject("user")?.optString("username").orEmpty()
            val title = r.optString("title")
            val items = r.optJSONArray("items") ?: return emptyList()
            val all = (0 until items.length()).map { items.getJSONObject(it) }
            val chosen = if (onlyId.isNotBlank() && !reelId.startsWith("highlight:")) {
                all.filter { it.optString("pk") == onlyId || it.optString("id").startsWith("${onlyId}_") }.ifEmpty { all }
            } else all
            chosen.mapNotNull { m ->
                mediaUrl(m)?.let { u ->
                    val name = if (reelId.startsWith("highlight:")) listOf(who, title).filter { p -> p.isNotBlank() }.joinToString(" - ")
                    else "$who story ${Names.date(m.optLong("taken_at"))}"
                    MediaItem(u, Names.clean(name.ifBlank { "Instagram story" }))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    // ------------------------------------------------------------ helpers
    private fun mediaUrl(m: JSONObject): String? {
        val u = if (m.optInt("media_type") == 1) {
            m.optJSONObject("image_versions2")?.optJSONArray("candidates")?.optJSONObject(0)?.optString("url")
        } else {
            m.optJSONArray("video_versions")?.optJSONObject(0)?.optString("url")
        }
        return u?.takeIf { it.startsWith("http") }
    }

    private fun shortcodeToId(code: String): String? {
        var n = BigInteger.ZERO
        val b = BigInteger.valueOf(64)
        for (ch in code.take(11)) {
            val i = ALPHABET.indexOf(ch)
            if (i < 0) return null
            n = n.multiply(b).add(BigInteger.valueOf(i.toLong()))
        }
        return n.toString()
    }

    private fun get(u: String, h: Map<String, String>): String? = request(u, h, null)

    private fun request(u: String, h: Map<String, String>, form: String?): String? = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 25000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        h.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (form != null) {
            c.requestMethod = "POST"; c.doOutput = true
            c.outputStream.use { it.write(form.toByteArray()) }
        }
        if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) { null }
}
