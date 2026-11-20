/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.api

import java.io.File

/** What the page of a link shows in link previews, to send it with the link (shared, or typed in the composer). */
class LinkPreviewContent(
    /** The picture (`og:image`), downloaded, with its MIME type. */
    val image: Pair<File, String>?,
    /** For Spotify, the name of the song, album or playlist, to go above the link (Spotify only shares the link). */
    val name: String?,
    /** For a song of Spotify, the artist, after the name. */
    val artist: String?,
)

fun interface LinkPreview {
    /** The preview of the first link in [text]; null when its page has none or does not answer. */
    suspend fun fetch(text: String): LinkPreviewContent?
}

/**
 * The caption of [text] with [name] (in bold in the HTML) and [artist] above it, each on its line, as plain text and
 * as HTML; [html] is the text's own HTML, if it has one. Without a name, or with a text that already has it, the text
 * as it is.
 */
fun linkCaption(name: String?, artist: String?, text: String, html: String?): Pair<String, String?> {
    if (name == null || text.contains(name)) return text to html
    val plain = listOfNotNull(name, artist).joinToString("\n")
    val heading = "<strong>${escapeHtml(name)}</strong>" + (artist?.let { "<br>${escapeHtml(it)}" } ?: "")
    return "$plain\n$text" to "$heading<br>${html ?: textToHtml(text)}"
}

/** [text] as HTML: escaped, its links as links and its new lines as line breaks. */
private fun textToHtml(text: String): String =
    LINK.replace(escapeHtml(text)) { """<a href="${it.value}">${it.value}</a>""" }.replace("\n", "<br>")

private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** The first link in [text], if any. */
fun firstLink(text: String): String? = LINK.find(text)?.value

private val LINK = Regex("""https?://[^\s"'<>]+""")

/**
 * The links of Spotify (open.spotify.com) in [text] with only their path: the query (`?si=…`) says who shared them.
 * Also in HTML, where the link ends at a quote.
 */
fun cleanSpotifyLinks(text: String): String = SPOTIFY_LINK.replace(text) { it.groupValues[1] }

private val SPOTIFY_LINK = Regex("""(https?://open\.spotify\.com/[^\s?#"'<>]*)[?#][^\s"'<>]*""")
