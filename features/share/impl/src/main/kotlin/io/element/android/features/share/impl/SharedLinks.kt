/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.share.impl

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** What the page of a shared link shows in link previews, to send it with the link. */
class SharedLinkPreview(
    /** The picture (`og:image`), downloaded, with its MIME type. */
    val image: Pair<File, String>?,
    /** For Spotify, the name and the artist, to go above the link (Spotify only shares the link). */
    val heading: String?,
)

fun interface LinkPreview {
    /** The preview of the first link in [text]; null when its page has none or does not answer. */
    suspend fun fetch(text: String): SharedLinkPreview?
}

@ContributesBinding(AppScope::class)
class DefaultLinkPreview(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) : LinkPreview {
    override suspend fun fetch(text: String): SharedLinkPreview? = withContext(dispatchers.io) {
        val link = LINK.find(text)?.value ?: return@withContext null
        try {
            val page = open(link) ?: return@withContext null
            val html = page.inputStream.use { it.readUpTo(MAX_PAGE_BYTES) }.toString(Charsets.UTF_8)
            val heading = if (page.url.host == SPOTIFY_HOST) spotifyHeadingOf(html) else null
            val image = previewImageOf(html)?.let { download(URL(page.url, it)) }
            SharedLinkPreview(image, heading).takeIf { image != null || heading != null }
        } catch (e: Exception) {
            Timber.w(e, "No preview for the shared link")
            null
        }
    }

    /** The picture at [url] in a file of the cache, with its MIME type; null if it is not a picture or is too big. */
    private fun download(url: URL): Pair<File, String>? {
        val connection = open(url.toString()) ?: return null
        val mimeType = connection.contentType?.substringBefore(';')?.trim()?.takeIf { it.startsWith("image/") } ?: return null
        val bytes = connection.inputStream.use { it.readUpTo(MAX_IMAGE_BYTES + 1) }
        if (bytes.size > MAX_IMAGE_BYTES) return null
        val file = File(context.cacheDir, "share-previews/${UUID.randomUUID()}").apply { parentFile?.mkdirs() }
        file.writeBytes(bytes)
        return file to mimeType
    }

    /** A GET of [url] that answered 200 (after redirects), or null. */
    private fun open(url: String): HttpURLConnection? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        // the agent of a link preview: sites answer it with the tags of the preview, without the page's scripts
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; MatriX link preview)")
        return connection.takeIf { it.responseCode == HttpURLConnection.HTTP_OK }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
        const val MAX_PAGE_BYTES = 512 * 1024
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        const val SPOTIFY_HOST = "open.spotify.com"
        val LINK = Regex("""https?://\S+""")
    }
}

/** At most [max] bytes of the stream (InputStream.readNBytes needs Android 13). */
private fun InputStream.readUpTo(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < max) {
        val n = read(buffer, 0, minOf(buffer.size, max - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/** The picture of a link preview in the [html] of a page: `og:image`, else `twitter:image`, as written in it. */
internal fun previewImageOf(html: String): String? =
    metaContent(html, "og:image", "og:image:url", "og:image:secure_url", "twitter:image")

/**
 * The name and the artist of a page of Spotify: "Never Gonna Give You Up · Rick Astley" for a song (the artist is
 * the start of the description); for an album or a playlist the title without Spotify's "Listen to … | Spotify",
 * which already names the artist.
 */
internal fun spotifyHeadingOf(html: String): String? {
    val title = metaContent(html, "og:title")?.removePrefix("Listen to ")?.removeSuffix(" | Spotify")?.trim() ?: return null
    val artist = metaContent(html, "og:description")?.substringBefore(" · ")?.trim()
    return if (artist.isNullOrEmpty() || title.contains(artist)) title else "$title · $artist"
}

/** The content of the first of the meta tags [names] in [html] that has one. */
private fun metaContent(html: String, vararg names: String): String? {
    val tags = META.findAll(html).map { it.value }.toList()
    return names.firstNotNullOfOrNull { name ->
        tags.firstOrNull { tag -> NAME.find(tag)?.groupValues?.get(2)?.equals(name, ignoreCase = true) == true }
            ?.let { CONTENT.find(it)?.groupValues?.get(2) }
            ?.let(::decodeEntities)
            ?.takeIf { it.isNotBlank() }
    }
}

/** The HTML entities of an attribute (&amp;, &quot;, &#39;, &#x27;…) as their characters. */
private fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
    val name = match.groupValues[1]
    when {
        name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
        name.startsWith("#") -> name.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
        else -> NAMED_ENTITIES[name]
    } ?: match.value
}

private val ENTITY = Regex("""&(#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);""")
private val NAMED_ENTITIES = mapOf("amp" to "&", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">", "nbsp" to "\u00A0")

private val META = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
private val NAME = Regex("""(?:property|name)\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
private val CONTENT = Regex("""content\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)

/** The links of Spotify (open.spotify.com) in [text] with only their path: the query (`?si=…`) says who shared them. */
fun cleanSpotifyLinks(text: String): String = SPOTIFY_LINK.replace(text) { it.groupValues[1] }

private val SPOTIFY_LINK = Regex("""(https?://open\.spotify\.com/[^\s?#]*)[?#]\S*""")
