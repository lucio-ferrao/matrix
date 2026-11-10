/*
 * Copyright (c) 2026 Lúcio Ferrão.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.loggedin

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.PersistableBundle
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * MatriX: whether someone is typing in a room with a conversation shortcut, put in that shortcut's extras
 * ([EXTRA_TYPING]: when it was last seen, 0 when nobody types) for the Keyarchy launcher, which animates the icon of
 * the person. Only while the sync runs: the launcher drops a time older than its limit, so a stop missed when the sync
 * ends does not leave someone typing.
 */
@Inject
class TypingShortcuts(
    @ApplicationContext private val context: Context,
    private val matrixClient: MatrixClient,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) {
    private var job: Job? = null

    /** watches the rooms that have a shortcut now; called when the session shows again, as they change little */
    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        job?.cancel()
        job = sessionCoroutineScope.launch {
            val manager = context.getSystemService(ShortcutManager::class.java) ?: return@launch
            val prefix = "${matrixClient.sessionId.value}-"
            manager.dynamicShortcuts.map { it.id }.filter { it.startsWith(prefix) }.forEach { id ->
                launch { watch(manager, id, RoomId(id.removePrefix(prefix))) }
            }
        }
    }

    private suspend fun watch(manager: ShortcutManager, shortcutId: String, roomId: RoomId) {
        val room = matrixClient.getJoinedRoom(roomId) ?: return
        try {
            room.roomTypingMembersFlow.map { it.isNotEmpty() }.distinctUntilChanged().collectLatest { typing ->
                if (!typing) return@collectLatest publish(manager, shortcutId, 0L)
                // again while it lasts, before the launcher takes it as stopped
                while (true) {
                    publish(manager, shortcutId, System.currentTimeMillis())
                    delay(REFRESH_MS)
                }
            }
        } finally {
            room.destroy()
        }
    }

    private fun publish(manager: ShortcutManager, shortcutId: String, time: Long) {
        // only the extras change: an update keeps the rest of the shortcut
        val extras = PersistableBundle().apply { putLong(EXTRA_TYPING, time) }
        runCatching { manager.updateShortcuts(listOf(ShortcutInfo.Builder(context, shortcutId).setExtras(extras).build())) }
            .onFailure { Timber.w(it, "Could not mark typing on shortcut $shortcutId") }
    }

    private companion object {
        const val EXTRA_TYPING = "org.ferrao.homekey.typing"
        const val REFRESH_MS = 20_000L
    }
}
