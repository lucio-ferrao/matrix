/*
 * Copyright (c) 2026 Lúcio Ferrão.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.loggedin

import android.content.Context
import android.content.pm.ShortcutManager
import android.os.Build
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
import org.json.JSONObject

/**
 * MatriX: whether someone is typing in a room with a conversation shortcut, for the Keyarchy launcher, which animates
 * the icon of the person: told by [TypingProvider], live, and not in the shortcuts (Android lets an app in the
 * background update its shortcuts only 10 times a day). Only while the sync runs: the time is told again while the
 * typing lasts, and the launcher drops one older than its limit, so a stop missed when the sync ends does not leave
 * someone typing.
 */
@Inject
class TypingRooms(
    @ApplicationContext private val context: Context,
    private val matrixClient: MatrixClient,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) {
    private var job: Job? = null

    /** watches the rooms that have a shortcut now; called when the session shows again, as they change little */
    fun start() {
        sessionCoroutineScope.launch { publishDirectRooms() }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        job?.cancel()
        job = sessionCoroutineScope.launch {
            val manager = context.getSystemService(ShortcutManager::class.java) ?: return@launch
            val prefix = "${matrixClient.sessionId.value}-"
            manager.dynamicShortcuts.map { it.id }.filter { it.startsWith(prefix) }.forEach { id ->
                launch { watch(RoomId(id.removePrefix(prefix))) }
            }
        }
    }

    /**
     * The room of two with each person, for a Keyarchy contact that has only a Matrix id: from the rooms of two of the
     * account (m.direct), the first one I am in.
     */
    private suspend fun publishDirectRooms() {
        val content = matrixClient.getAccountData("m.direct").getOrNull() ?: return
        val joined = matrixClient.getJoinedRoomIds().getOrNull() ?: return
        val json = JSONObject(content)
        val rooms = json.keys().asSequence().mapNotNull { user ->
            val ids = json.getJSONArray(user)
            (0 until ids.length()).map { ids.getString(it) }.firstOrNull { RoomId(it) in joined }?.let { user to it }
        }.toMap()
        TypingProvider.setDirect(context, rooms)
    }

    private suspend fun watch(roomId: RoomId) {
        val room = matrixClient.getJoinedRoom(roomId) ?: return
        try {
            room.roomTypingMembersFlow.map { it.isNotEmpty() }.distinctUntilChanged().collectLatest { typing ->
                if (!typing) return@collectLatest TypingProvider.set(context, roomId.value, 0L)
                // again while it lasts, before the launcher takes it as stopped
                while (true) {
                    TypingProvider.set(context, roomId.value, System.currentTimeMillis())
                    delay(REFRESH_MS)
                }
            }
        } finally {
            TypingProvider.set(context, roomId.value, 0L)
            room.destroy()
        }
    }

    private companion object {
        const val REFRESH_MS = 20_000L
    }
}
