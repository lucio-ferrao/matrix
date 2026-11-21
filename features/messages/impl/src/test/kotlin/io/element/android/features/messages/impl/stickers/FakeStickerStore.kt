/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.stickers

import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.room.JoinedRoom
import java.io.File

class FakeStickerStore(
    private val stickers: List<File> = emptyList(),
    private val addResult: (MediaSource, String) -> Result<Unit> = { _, _ -> Result.success(Unit) },
) : StickerStore {
    override suspend fun list(): List<File> = stickers

    override suspend fun add(source: MediaSource, mimeType: String): Result<Unit> = addResult(source, mimeType)

    override suspend fun remove(file: File) = Unit

    override suspend fun send(room: JoinedRoom, file: File, threadRoot: ThreadId?): Result<Unit> = Result.success(Unit)
}
