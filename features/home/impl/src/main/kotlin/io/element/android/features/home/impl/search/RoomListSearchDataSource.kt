/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.home.impl.datasource.RoomListRoomSummaryFactory
import io.element.android.features.home.impl.datasource.withNickname
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.api.roomlist.updateVisibleRange
import io.element.android.libraries.matrix.ui.contacts.ContactNicknames
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

private const val PAGE_SIZE = 30

@AssistedInject
class RoomListSearchDataSource(
    @Assisted coroutineScope: CoroutineScope,
    roomListService: RoomListService,
    coroutineDispatchers: CoroutineDispatchers,
    private val roomSummaryFactory: RoomListRoomSummaryFactory,
    private val contactNicknames: ContactNicknames,
    private val matrixClient: MatrixClient,
) {
    @AssistedFactory
    interface Factory {
        fun create(coroutineScope: CoroutineScope): RoomListSearchDataSource
    }

    private val roomList = roomListService.createRoomList(
        pageSize = PAGE_SIZE,
        source = RoomList.Source.All,
        coroutineScope = coroutineScope
    )

    val loadingState = roomList.loadingState

    // MatriX: a room of two has the name given to the other person, if any
    val roomSummaries: Flow<ImmutableList<RoomListRoomSummary>> = combine(roomList.summaries, contactNicknames.all) { roomSummaries, nicknames ->
        roomSummaries
            .map { roomSummaryFactory.create(it).withNickname(nicknames) }
            .toImmutableList()
    }
        .flowOn(coroutineDispatchers.computation)

    suspend fun updateVisibleRange(visibleRange: IntRange) {
        roomList.updateVisibleRange(visibleRange)
    }

    suspend fun setSearchQuery(searchQuery: String, additionalFilters: RoomListFilter?) = coroutineScope {
        val filter = if (searchQuery.isBlank()) {
            RoomListFilter.None
        } else {
            // MatriX: the rooms whose name matches, and the rooms of two with someone whose given name matches
            val dmRooms = contactNicknames.usersMatching(searchQuery).mapNotNull { matrixClient.findDM(it).getOrNull() }
            val nameFilter = RoomListFilter.NormalizedMatchRoomName(searchQuery)
            val matching = if (dmRooms.isEmpty()) nameFilter else RoomListFilter.any(nameFilter, RoomListFilter.Identifiers(dmRooms))
            if (additionalFilters != null) {
                RoomListFilter.all(additionalFilters, matching)
            } else {
                matching
            }
        }
        roomList.updateFilter(filter)
    }
}
