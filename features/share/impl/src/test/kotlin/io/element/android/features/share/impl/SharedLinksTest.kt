/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.share.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SharedLinksTest {
    @Test
    fun `Spotify links keep only their path`() {
        assertThat(cleanSpotifyLinks("Ouve https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=abc123&context=x"))
            .isEqualTo("Ouve https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC")
        assertThat(cleanSpotifyLinks("https://open.spotify.com/intl-pt/album/1?si=a https://example.com/?a=1"))
            .isEqualTo("https://open.spotify.com/intl-pt/album/1 https://example.com/?a=1")
        assertThat(cleanSpotifyLinks("https://open.spotify.com/playlist/37i9")).isEqualTo("https://open.spotify.com/playlist/37i9")
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
    fun `the heading of Spotify has the name and the artist`() {
        assertThat(spotifyHeadingOf("""<meta property="og:title" content="Never Gonna Give You Up"/>""" +
            """<meta property="og:description" content="Rick Astley · Whenever You Need Somebody · Song · 1987"/>"""))
            .isEqualTo("Never Gonna Give You Up · Rick Astley")
        assertThat(spotifyHeadingOf("""<meta property="og:title" content="Listen to Global Warming - Album by Pitbull | Spotify"/>""" +
            """<meta property="og:description" content="Pitbull · album · 2012 · 18 songs"/>"""))
            .isEqualTo("Global Warming - Album by Pitbull")
        assertThat(spotifyHeadingOf("""<meta property="og:title" content="Don&#x27;t Stop Me Now"/>""")).isEqualTo("Don't Stop Me Now")
        assertThat(spotifyHeadingOf("""<title>Spotify</title>""")).isNull()
    }
}
