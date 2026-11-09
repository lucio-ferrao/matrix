/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.diff

import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemReadMarkerModel
import io.element.android.libraries.androidutils.diff.DefaultDiffCacheInvalidator
import io.element.android.libraries.androidutils.diff.DiffCacheInvalidator
import io.element.android.libraries.androidutils.diff.MutableDiffCache

/**
 * [DiffCacheInvalidator] implementation for [TimelineItem].
 * It uses [DefaultDiffCacheInvalidator] and invalidate the cache around the updated item so that those items are computed again.
 * This is needed because a timeline item is computed based on the previous and next items.
 */
internal class TimelineItemsCacheInvalidator : DiffCacheInvalidator<TimelineItem> {
    private val delegate = DefaultDiffCacheInvalidator<TimelineItem>()

    override fun onChanged(position: Int, count: Int, cache: MutableDiffCache<TimelineItem>) {
        delegate.onChanged(position, count, cache)
    }

    override fun onMoved(fromPosition: Int, toPosition: Int, cache: MutableDiffCache<TimelineItem>) {
        delegate.onMoved(fromPosition, toPosition, cache)
    }

    override fun onInserted(position: Int, count: Int, cache: MutableDiffCache<TimelineItem>) {
        cache.invalidateAround(position)
        delegate.onInserted(position, count, cache)
    }

    override fun onRemoved(position: Int, count: Int, cache: MutableDiffCache<TimelineItem>) {
        cache.invalidateAround(position)
        delegate.onRemoved(position, count, cache)
    }
}

/**
 * Invalidate the cache around the given position.
 * It invalidates the previous and next items.
 */
private fun MutableDiffCache<TimelineItem>.invalidateAround(position: Int) {
    // MatriX: past the items that are not shown (never cached) and the read marker, as they do not split a group of messages
    fun isSkipped(index: Int) = get(index).let { it == null || it is TimelineItem.Virtual && it.model == TimelineItemReadMarkerModel }
    var before = position - 1
    while (before >= 0 && isSkipped(before)) before--
    if (before >= 0) {
        set(before, null)
    }
    var after = position + 1
    while (after <= indices().last && isSkipped(after)) after++
    if (after <= indices().last) {
        set(after, null)
    }
}
