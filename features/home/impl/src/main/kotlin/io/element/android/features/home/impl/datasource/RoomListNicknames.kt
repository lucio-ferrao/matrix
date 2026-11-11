/*
 * Copyright (c) 2026 Lúcio Ferrão.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.datasource

import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.libraries.matrix.api.core.UserId

/** MatriX: a room of two with the name given to the other person (its only hero), if any. */
internal fun RoomListRoomSummary.withNickname(nicknames: Map<UserId, String>): RoomListRoomSummary {
    if (!isDm) return this
    val nickname = heroes.singleOrNull()?.let { nicknames[UserId(it.id)] } ?: return this
    return copy(name = nickname)
}
