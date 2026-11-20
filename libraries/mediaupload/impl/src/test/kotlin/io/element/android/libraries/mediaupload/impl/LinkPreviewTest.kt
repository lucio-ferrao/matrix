/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.mediaupload.api.cleanSpotifyLinks
import io.element.android.libraries.mediaupload.api.linkCaption
import org.junit.Test

class LinkPreviewTest {
    @Test
    fun `Spotify links keep only their path`() {
        assertThat(cleanSpotifyLinks("Ouve https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=abc123&context=x"))
            .isEqualTo("Ouve https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC")
        assertThat(cleanSpotifyLinks("https://open.spotify.com/intl-pt/album/1?si=a https://example.com/?a=1"))
            .isEqualTo("https://open.spotify.com/intl-pt/album/1 https://example.com/?a=1")
        assertThat(cleanSpotifyLinks("https://open.spotify.com/playlist/37i9")).isEqualTo("https://open.spotify.com/playlist/37i9")
        assertThat(cleanSpotifyLinks("""<a href="https://open.spotify.com/track/x?si=a">https://open.spotify.com/track/x?si=a</a>"""))
            .isEqualTo("""<a href="https://open.spotify.com/track/x">https://open.spotify.com/track/x</a>""")
    }

    @Test
    fun `the preview picture comes from og image, in any order of the attributes`() {
        assertThat(previewImageOf("""<meta content="https://i.scdn.co/image/ab&amp;x=1" property="og:image" />"""))
            .isEqualTo("https://i.scdn.co/image/ab&x=1")
        assertThat(previewImageOf("""<meta name="twitter:image" content="/t.png"><meta property='og:image' content='/o.png'>"""))
            .isEqualTo("/o.png")
        assertThat(previewImageOf("""<meta name="twitter:image" content="/t.png">""")).isEqualTo("/t.png")
        assertThat(previewImageOf("""<title>sem imagem</title>""")).isNull()
    }

    @Test
    fun `Spotify gives the name and the artist`() {
        assertThat(spotifyNameOf("""<meta property="og:title" content="Never Gonna Give You Up"/>""" +
            """<meta property="og:description" content="Rick Astley · Whenever You Need Somebody · Song · 1987"/>"""))
            .isEqualTo("Never Gonna Give You Up" to "Rick Astley")
        assertThat(spotifyNameOf("""<meta property="og:title" content="Listen to Global Warming - Album by Pitbull | Spotify"/>""" +
            """<meta property="og:description" content="Pitbull · album · 2012 · 18 songs"/>"""))
            .isEqualTo("Global Warming - Album by Pitbull" to null)
        assertThat(spotifyNameOf("""<meta property="og:title" content="Don&#x27;t Stop Me Now"/>""")).isEqualTo("Don't Stop Me Now" to null)
        assertThat(spotifyNameOf("""<title>Spotify</title>""")).isNull()
    }

    @Test
    fun `the caption has the name in bold above the text`() {
        val link = "https://open.spotify.com/track/x"
        assertThat(linkCaption("Rock & Roll", "Led Zeppelin", "ouve $link", null)).isEqualTo(
            "Rock & Roll\nLed Zeppelin\nouve $link" to
                """<strong>Rock &amp; Roll</strong><br>Led Zeppelin<br>ouve <a href="$link">$link</a>"""
        )
        assertThat(linkCaption("Nome", null, link, "<em>já</em> formatado").second).isEqualTo("<strong>Nome</strong><br><em>já</em> formatado")
        assertThat(linkCaption(null, null, link, null)).isEqualTo(link to null)
    }
}
