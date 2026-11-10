/*
 * Copyright (c) 2026 Lúcio Ferrão.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.loggedin

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder

/**
 * MatriX: the rooms someone (not me) types in, for the Keyarchy launcher, live (it observes [uri]): one row per room,
 * its id, when someone was last seen typing there and when I last left it (0 when not known), so the launcher shows
 * the chat I just left as the most recent. Only Keyarchy may read it, by the UID Android stamps on the call;
 * any other caller gets a [SecurityException].
 */
class TypingProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
                       sortOrder: String?): Cursor {
        val packages = context?.packageManager?.getPackagesForUid(Binder.getCallingUid())
        if (packages.isNullOrEmpty() || !packages.all { it in KEYARCHY_PACKAGES }) {
            throw SecurityException("Only Keyarchy may read who types in MatriX")
        }
        return MatrixCursor(arrayOf("room", "seen", "left")).apply {
            synchronized(typing) {
                (typing.keys + left.keys).forEach { room -> addRow(arrayOf<Any>(room, typing[room] ?: 0L, left[room] ?: 0L)) }
            }
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        /** The packages of the Keyarchy keyboard (release and debug). */
        private val KEYARCHY_PACKAGES = setOf("org.ferrao.homekey", "org.ferrao.homekey.debug")

        /** When someone was last seen typing, by room id. */
        private val typing = mutableMapOf<String, Long>()

        /** When I last left a room, by room id. */
        private val left = mutableMapOf<String, Long>()

        /** What Keyarchy reads and observes: content://<package>.typing/typing. */
        fun uri(context: Context): Uri = Uri.parse("content://${context.packageName}.typing/typing")

        /** Someone typing in [roomId], last seen at [seen], or nobody when [seen] is 0; tells the observers. */
        fun set(context: Context, roomId: String, seen: Long) {
            val changed = synchronized(typing) {
                if (seen > 0) typing.put(roomId, seen) != seen else typing.remove(roomId) != null
            }
            if (changed) context.contentResolver.notifyChange(uri(context), null)
        }

        /** I left [roomId] at [time]; tells the observers. */
        fun setLeft(context: Context, roomId: String, time: Long) {
            synchronized(typing) { left[roomId] = time }
            context.contentResolver.notifyChange(uri(context), null)
        }
    }
}
