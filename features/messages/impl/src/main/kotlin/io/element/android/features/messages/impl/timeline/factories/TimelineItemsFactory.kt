/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories

import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.messages.impl.timeline.diff.TimelineItemsCacheInvalidator
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemEventFactory
import io.element.android.features.messages.impl.timeline.factories.virtual.TimelineItemVirtualFactory
import io.element.android.features.messages.impl.timeline.groups.TimelineItemGrouper
import io.element.android.features.messages.impl.timeline.model.ReadReceiptData
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemReadReceipts
import io.element.android.libraries.androidutils.diff.DiffCacheUpdater
import io.element.android.libraries.androidutils.diff.MutableListDiffCache
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.EventTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.OtherMessageType
import io.element.android.libraries.matrix.ui.contacts.ContactNicknames
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToDetails
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@AssistedInject
class TimelineItemsFactory(
    @Assisted config: TimelineItemsFactoryConfig,
    eventItemFactoryCreator: TimelineItemEventFactory.Creator,
    private val dispatchers: CoroutineDispatchers,
    private val virtualItemFactory: TimelineItemVirtualFactory,
    private val timelineItemGrouper: TimelineItemGrouper,
    private val matrixClient: MatrixClient,
    private val contactNicknames: ContactNicknames,
) {
    @AssistedFactory
    interface Creator {
        fun create(config: TimelineItemsFactoryConfig): TimelineItemsFactory
    }

    private val eventItemFactory = eventItemFactoryCreator.create(config)
    private val _timelineItems = MutableSharedFlow<ImmutableList<TimelineItem>>(replay = 1)
    private val lock = Mutex()
    private val diffCache = MutableListDiffCache<TimelineItem>()
    private val diffCacheUpdater = DiffCacheUpdater<MatrixTimelineItem, TimelineItem>(
        diffCache = diffCache,
        detectMoves = false,
        cacheInvalidator = TimelineItemsCacheInvalidator()
    ) { old, new ->
        when (old) {
            is MatrixTimelineItem.Event if new is MatrixTimelineItem.Event -> old.uniqueId == new.uniqueId
            is MatrixTimelineItem.Virtual if new is MatrixTimelineItem.Virtual -> old.uniqueId == new.uniqueId
            else -> false
        }
    }

    val timelineItems: Flow<ImmutableList<TimelineItem>> = _timelineItems.distinctUntilChanged()

    suspend fun replaceWith(timelineItems: List<MatrixTimelineItem>) = withContext(dispatchers.computation) {
        lock.withLock {
            diffCacheUpdater.updateWith(timelineItems)
            buildAndEmitTimelineItemStates(timelineItems)
        }
    }

    private suspend fun buildAndEmitTimelineItemStates(timelineItems: List<MatrixTimelineItem>) {
        val newTimelineItemStates = ArrayList<TimelineItem>()
        for (index in diffCache.indices().reversed()) {
            val cacheItem = diffCache.get(index)
            if (cacheItem == null) {
                buildAndCacheItem(timelineItems, index)?.also { timelineItemState ->
                    newTimelineItemStates.add(timelineItemState)
                }
            } else {
                newTimelineItemStates.add(cacheItem)
            }
        }
        val result = timelineItemGrouper.group(newTimelineItemStates).withReadMark(matrixClient.sessionId).withNicknames(contactNicknames).toImmutableList()
        this._timelineItems.emit(result)
    }

    private suspend fun buildAndCacheItem(
        timelineItems: List<MatrixTimelineItem>,
        index: Int,
    ): TimelineItem? {
        val timelineItem =
            when (val currentTimelineItem = timelineItems[index]) {
                is MatrixTimelineItem.Event -> if (currentTimelineItem.event.isKeyVerificationRequest()) {
                    null
                } else {
                    eventItemFactory.create(currentTimelineItem, index, timelineItems)
                }
                is MatrixTimelineItem.Virtual -> virtualItemFactory.create(currentTimelineItem)
                MatrixTimelineItem.Other -> null
            }
        diffCache[index] = timelineItem
        return timelineItem
    }
}

/**
 * MatriX: puts the read mark on the latest message the others have read (mine or theirs), that is the latest one at or
 * before their read receipts. It gets everyone but [me] who has read up to it or further.
 * The items are the newest first.
 */
private fun List<TimelineItem>.withReadMark(me: UserId): List<TimelineItem> {
    val receipts = LinkedHashMap<UserId, ReadReceiptData>()
    forEachIndexed { index, item ->
        val events = when (item) {
            is TimelineItem.Event -> listOf(item)
            is TimelineItem.GroupedEvents -> item.events
            is TimelineItem.Virtual -> emptyList()
        }
        events.forEach { event -> event.readReceiptState.receipts.forEach { receipts.putIfAbsent(it.userId, it) } }
        if (item is TimelineItem.Event) {
            val others = receipts.values.filter { it.userId != me }
            if (others.isNotEmpty()) {
                return toMutableList().apply {
                    set(index, item.copy(isReadMark = true, readReceiptState = TimelineItemReadReceipts(others.toImmutableList())))
                }
            }
        }
    }
    return this
}

/** MatriX: the senders, and the senders of the quoted messages, with the names given to them, if any. */
private fun List<TimelineItem>.withNicknames(nicknames: ContactNicknames): List<TimelineItem> = map { item ->
    when (item) {
        is TimelineItem.Event -> item.withNickname(nicknames)
        is TimelineItem.GroupedEvents -> item.copy(events = item.events.map { it.withNickname(nicknames) }.toImmutableList())
        is TimelineItem.Virtual -> item
    }
}

private fun TimelineItem.Event.withNickname(nicknames: ContactNicknames): TimelineItem.Event {
    val profile = nicknames.profileOf(senderId, senderProfile)
    val reply = inReplyTo
    val namedReply = if (reply is InReplyToDetails.Ready) {
        val replyProfile = nicknames.profileOf(reply.senderId, reply.senderProfile)
        if (replyProfile === reply.senderProfile) reply else reply.copy(senderProfile = replyProfile)
    } else {
        reply
    }
    return if (profile === senderProfile && namedReply === reply) this else copy(senderProfile = profile, inReplyTo = namedReply)
}

internal fun EventTimelineItem.isKeyVerificationRequest(): Boolean {
    val messageType = (content as? MessageContent)?.type
    return messageType is OtherMessageType && messageType.isKeyVerificationRequest
}
