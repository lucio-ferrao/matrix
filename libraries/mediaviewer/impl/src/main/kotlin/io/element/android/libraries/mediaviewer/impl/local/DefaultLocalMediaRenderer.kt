/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.local

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.viewfolder.api.TextFileViewer
import io.element.android.libraries.audio.api.AudioFocus
import io.element.android.libraries.mediaviewer.api.local.LocalMedia
import io.element.android.libraries.mediaviewer.api.local.LocalMediaRenderer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import me.saket.telephoto.zoomable.spatial.isSpecified
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

@ContributesBinding(AppScope::class)
class DefaultLocalMediaRenderer(
    private val textFileViewer: TextFileViewer,
    private val audioFocus: AudioFocus,
) : LocalMediaRenderer {
    @Composable
    override fun Render(localMedia: LocalMedia) {
        // MatriX: the attachments preview swaps the image for the one prepared with another compression choice, which has
        // another resolution. The new one is loaded invisible below the one on screen, put on the same zoomed region, and
        // only then shown, so the image changes once and stays in place.
        var shown by remember { mutableStateOf(localMedia) }
        val incoming = localMedia.takeIf { it != shown }
        val viewStates = remember { mutableMapOf<LocalMedia, LocalMediaViewState>() }
        Box(modifier = Modifier.fillMaxSize()) {
            listOfNotNull(incoming, shown).forEach { media ->
                key(media) {
                    val localMediaViewState = rememberLocalMediaViewState(
                        zoomableState = rememberZoomableState(
                            zoomSpec = ZoomSpec(maxZoomFactor = 4f, overzoomEffect = OverzoomEffect.NoLimits)
                        )
                    )
                    DisposableEffect(localMediaViewState) {
                        viewStates[media] = localMediaViewState
                        onDispose { viewStates.remove(media) }
                    }
                    LocalMediaView(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (media == shown) 1f else 0f },
                        bottomPaddingInPixels = 0,
                        localMedia = media,
                        localMediaViewState = localMediaViewState,
                        textFileViewer = textFileViewer,
                        audioFocus = audioFocus,
                        forPreview = true,
                        onClick = {},
                        onOpenWith = null,
                    )
                }
            }
        }
        if (incoming != null) {
            LaunchedEffect(incoming) {
                val incomingState = viewStates[incoming]
                if (incomingState != null) {
                    withTimeoutOrNull(3.seconds) {
                        // A large image is first shown from a smaller bitmap, then from its tiles
                        snapshotFlow { incomingState.isShownInFullQuality }.first { it }
                        val target = viewStates[shown]?.zoomableState?.viewportContentBounds()
                        val current = incomingState.zoomableState.viewportContentBounds()
                        if (target != null && current != null && !current.isCloseTo(target)) {
                            incomingState.zoomableState.zoomBy(target.width / current.width, current.topLeft, snap())
                            incomingState.zoomableState.viewportContentBounds()?.let { bounds ->
                                incomingState.zoomableState.panBy(target.topLeft - bounds.topLeft, snap())
                            }
                            // Wait for the tiles of the zoomed region
                            withFrameNanos {}
                            snapshotFlow { incomingState.isShownInFullQuality }.first { it }
                        }
                    }
                }
                shown = incoming
            }
        }
    }
}

@OptIn(ExperimentalTelephotoApi::class)
private fun ZoomableState.viewportContentBounds(): Rect? {
    return with(coordinateSystem) {
        contentBounds(clipToViewport = false).takeIf { it.isSpecified }?.rectIn(CoordinateSpace.Viewport)
    }?.takeIf { it.isFinite && !it.isEmpty }
}

private fun Rect.isCloseTo(other: Rect): Boolean =
    abs(left - other.left) < 1f && abs(top - other.top) < 1f && abs(right - other.right) < 1f && abs(bottom - other.bottom) < 1f
