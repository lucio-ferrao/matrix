/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.stickers

import android.content.Context
import android.graphics.BitmapFactory
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * MatriX: the stickers to send, the picture files in a folder of MatriX (`Android/media/org.ferrao.matrix/Autocolantes`),
 * which a file manager or the PC can also fill or empty. Sent as stickers (`m.sticker`); their picture is uploaded
 * unencrypted, as other Matrix clients do with stickers.
 */
interface StickerStore {
    /** The stickers, the last used or added first. */
    suspend fun list(): List<File>

    /** Keeps the picture of a received sticker or image ([source], of [mimeType]) as a sticker. */
    suspend fun add(source: MediaSource, mimeType: String): Result<Unit>

    suspend fun remove(file: File)

    /** Sends [file] to [room] as a sticker, in the thread [threadRoot] if there is one; it becomes the last used. */
    suspend fun send(room: JoinedRoom, file: File, threadRoot: ThreadId?): Result<Unit>
}

@ContributesBinding(SessionScope::class)
class DefaultStickerStore(
    @ApplicationContext private val context: Context,
    private val client: MatrixClient,
    private val dispatchers: CoroutineDispatchers,
) : StickerStore {
    private fun folder(): File? = context.externalMediaDirs.firstOrNull()?.let { File(it, FOLDER) }?.apply {
        mkdirs()
        // not in the gallery
        File(this, ".nomedia").createNewFile()
    }

    override suspend fun list(): List<File> = withContext(dispatchers.io) {
        folder()?.listFiles { file -> file.isFile && file.extension.lowercase() in MIME_TYPES }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    override suspend fun add(source: MediaSource, mimeType: String): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            val bytes = client.matrixMediaLoader.loadMediaContent(source).getOrThrow()
            val extension = MIME_TYPES.entries.firstOrNull { it.value == mimeType }?.key ?: error("not a picture: $mimeType")
            val folder = folder() ?: error("no folder for the stickers")
            // named by their content, so the same sticker is kept once
            val name = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
            File(folder, "$name.$extension").apply {
                writeBytes(bytes)
                setLastModified(System.currentTimeMillis())
            }
            Unit
        }
    }

    override suspend fun remove(file: File) {
        withContext(dispatchers.io) { file.delete() }
    }

    override suspend fun send(room: JoinedRoom, file: File, threadRoot: ThreadId?): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            val mimeType = MIME_TYPES[file.extension.lowercase()] ?: error("not a picture: ${file.name}")
            val bytes = file.readBytes()
            val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                .also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
            val url = client.uploadMedia(mimeType, bytes).getOrThrow()
            val content = JSONObject()
                .put("body", file.name)
                .put("url", url)
                .put(
                    "info",
                    JSONObject()
                        .put("mimetype", mimeType)
                        .put("size", bytes.size)
                        .apply {
                            if (size.outWidth > 0) put("w", size.outWidth).put("h", size.outHeight)
                        }
                )
            if (threadRoot != null) {
                content.put(
                    "m.relates_to",
                    JSONObject().put("rel_type", "m.thread").put("event_id", threadRoot.value).put("is_falling_back", true)
                )
            }
            room.sendRaw("m.sticker", content.toString()).getOrThrow()
            file.setLastModified(System.currentTimeMillis())
            Unit
        }
    }

    private companion object {
        const val FOLDER = "Autocolantes"
        val MIME_TYPES = mapOf("webp" to "image/webp", "png" to "image/png", "gif" to "image/gif", "jpg" to "image/jpeg", "jpeg" to "image/jpeg")
    }
}
