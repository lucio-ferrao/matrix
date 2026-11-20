/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.share.impl

import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.share.api.OnSharedData
import io.element.android.features.share.api.ShareIntentData
import io.element.android.libraries.core.bool.orFalse
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfigProviderFactory
import io.element.android.libraries.mediaupload.api.MediaSenderRoomFactory
import io.element.android.services.appnavstate.api.ActiveRoomsHolder
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

@ContributesBinding(AppScope::class)
class DefaultShareDataSender(
    private val mediaSenderRoomFactory: MediaSenderRoomFactory,
    private val mediaOptimizationConfigProviderFactory: MediaOptimizationConfigProviderFactory,
    private val activeRoomsHolder: ActiveRoomsHolder,
    private val onSharedData: OnSharedData,
    private val linkPreview: LinkPreview,
) : ShareDataSender {
    override suspend fun send(
        client: MatrixClient,
        shareIntentData: ShareIntentData,
        roomIds: List<RoomId>,
    ): Result<Unit> = runCatchingExceptions {
        val result = when (shareIntentData) {
            is ShareIntentData.PlainText -> {
                val shared = cleanSpotifyLinks(shareIntentData.content)
                // a link goes as the caption of the picture of its preview, when its page has one, below the name and
                // the artist for Spotify
                val preview = linkPreview.fetch(shared)
                val content = preview?.heading?.takeUnless { shared.contains(it) }?.let { "$it\n$shared" } ?: shared
                val image = preview?.image
                try {
                    roomIds
                        .map { roomId ->
                            if (image != null) {
                                sendImage(client, roomId, image.first, image.second, caption = content)
                            } else {
                                client.findJoinedRoom(roomId)?.liveTimeline?.sendMessage(
                                    body = content,
                                    htmlBody = null,
                                    intentionalMentions = emptyList(),
                                )?.isSuccess.orFalse()
                            }
                        }
                        .all { it }
                } finally {
                    image?.first?.delete()
                }
            }
            is ShareIntentData.Uris -> {
                val filesToShare = shareIntentData.uris
                if (filesToShare.isEmpty()) {
                    false
                } else {
                    // The media optimization config is the same for all the files and all the rooms
                    val mediaOptimizationConfig = mediaOptimizationConfigProviderFactory.create(client).get()
                    roomIds
                        .map { roomId ->
                            val room = client.findJoinedRoom(roomId) ?: return@map false
                            val mediaSender = mediaSenderRoomFactory.create(client = client, room = room)
                            filesToShare
                                .map { fileToShare ->
                                    val result = mediaSender.sendMedia(
                                        caption = shareIntentData.text,
                                        uri = fileToShare.uri,
                                        mimeType = fileToShare.mimeType,
                                        mediaOptimizationConfig = mediaOptimizationConfig,
                                    )
                                    // If the coroutine was cancelled, destroy the room and rethrow the exception
                                    val cancellationException = result.exceptionOrNull() as? CancellationException
                                    if (cancellationException != null) {
                                        if (activeRoomsHolder.getActiveRoomMatching(client.sessionId, roomId) == null) {
                                            room.destroy()
                                        }
                                        throw cancellationException
                                    }
                                    result.isSuccess
                                }
                                .all { isSuccess -> isSuccess }
                                .also {
                                    if (activeRoomsHolder.getActiveRoomMatching(client.sessionId, roomId) == null) {
                                        room.destroy()
                                    }
                                }
                        }
                        .all { it }
                }
            }
        }

        // Handle post-processing of shared data
        onSharedData(shareIntentData)

        if (!result) {
            error("Failed to handle incoming share intent")
        }
    }

    /** Sends the picture [file] with [caption] to [roomId], as a shared media is sent. */
    private suspend fun sendImage(client: MatrixClient, roomId: RoomId, file: File, mimeType: String, caption: String): Boolean {
        val room = client.findJoinedRoom(roomId) ?: return false
        val result = mediaSenderRoomFactory.create(client = client, room = room).sendMedia(
            uri = Uri.fromFile(file),
            mimeType = mimeType,
            caption = caption,
            mediaOptimizationConfig = mediaOptimizationConfigProviderFactory.create(client).get(),
        )
        if (activeRoomsHolder.getActiveRoomMatching(client.sessionId, roomId) == null) {
            room.destroy()
        }
        (result.exceptionOrNull() as? CancellationException)?.let { throw it }
        return result.isSuccess
    }

    private suspend fun MatrixClient.findJoinedRoom(roomId: RoomId): JoinedRoom? {
        return activeRoomsHolder.getActiveRoom(sessionId)
            ?.takeIf { it.roomId == roomId }
            ?: getJoinedRoom(roomId)
    }
}
