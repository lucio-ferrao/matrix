/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.TimelineEvent
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.isEdited
import io.element.android.features.messages.impl.timeline.model.event.isRedacted
import io.element.android.libraries.core.bool.orFalse
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun TimelineEventTimestampView(
    event: TimelineItem.Event,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    modifier: Modifier = Modifier,
    isLayoutDirectionMismatched: Boolean = false,
    showReadMark: Boolean = false,
    onReadMarkClick: () -> Unit = {},
) {
    // as in wassup, the separator of my message's time says where it is: "." on its way, ":" at the server
    val formattedTime = if (event.localSendState is LocalEventSendState.Sending) event.sentTime.replace(':', '.') else event.sentTime
    val hasError = event.failedToSend
    val hasEncryptionCritical = event.messageShield?.isCritical.orFalse()
    val isMessageEdited = event.content.isEdited()
    val isMessageRedacted = event.content.isRedacted()
    val tint = if (hasError || hasEncryptionCritical && !isMessageRedacted) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary

    val shield = event.messageShield
    val isVerifiedUserSendFailure = event.localSendState is LocalEventSendState.Failed.VerifiedUser
    val onClickLabel = when {
        hasError -> stringResource(CommonStrings.a11y_view_details)
        shield != null -> stringResource(CommonStrings.a11y_view_details)
        isVerifiedUserSendFailure -> stringResource(CommonStrings.action_open_context_menu)
        else -> null
    }
    // my message is the last one the others have read
    val isRead = showReadMark && event.isMine && event.readReceiptState.receipts.isNotEmpty() && !hasError
    val readMarkClickLabel = stringResource(CommonStrings.a11y_read_receipts_tap_to_show_all_android)
    val shape = RoundedCornerShape(8.dp)
    val clickableModifier = remember(event, isRead) {
        when {
            hasError -> {
                Modifier.clickable(
                    interactionSource = null,
                    indication = ripple(focusRingShape = shape),
                    onClickLabel = onClickLabel,
                ) {
                    eventSink(TimelineEvent.ShowSendFailureDialog(event))
                }
            }
            shield != null -> {
                Modifier.clickable(
                    interactionSource = null,
                    indication = ripple(focusRingShape = shape),
                    onClickLabel = onClickLabel,
                ) {
                    eventSink(TimelineEvent.ShowShieldDialog(shield))
                }
            }
            // the time with the read mark shows who has read
            isRead -> {
                Modifier
                    .clickable(
                        interactionSource = null,
                        indication = ripple(focusRingShape = shape),
                        onClickLabel = readMarkClickLabel,
                        onClick = onReadMarkClick,
                    )
                    .testTag(TestTags.messageReadReceipts.value)
            }
            else -> Modifier
        }
    }

    val padding = if (!isLayoutDirectionMismatched) {
        PaddingValues(start = TimelineEventTimestampViewDefaults.spacing)
    } else {
        PaddingValues(end = TimelineEventTimestampViewDefaults.spacing)
    }

    Row(
        modifier = Modifier
            .padding(padding)
            // For a better click target, make the corners rounded
            .clip(shape)
            .then(clickableModifier)
            .then(modifier),
    ) {
        // the read mark is drawn in the margin after the time, so the time does not move when it appears
        Row(
            modifier = if (isRead) Modifier.drawReadMark(tint) else Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isMessageEdited) {
                Text(
                    stringResource(CommonStrings.common_edited_suffix),
                    style = ElementTheme.typography.fontBodyXsRegular,
                    color = tint,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                formattedTime,
                style = ElementTheme.typography.fontBodyXsRegular,
                color = tint,
            )
            if (hasError) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = CompoundIcons.ErrorSolid(),
                    contentDescription = stringResource(id = CommonStrings.common_sending_failed),
                    tint = tint,
                    modifier = Modifier.size(15.dp, 18.dp),
                )
            }

            if (!isMessageRedacted) {
                shield?.let { shield ->
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = shield.toIcon(),
                        contentDescription = stringResource(id = CommonStrings.a11y_encryption_details),
                        modifier = Modifier.size(15.dp),
                        tint = shield.toIconColor(),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineEventTimestampViewPreview(@PreviewParameter(
    TimelineItemEventForTimestampViewPreviewParam::class
) event: TimelineItem.Event) = ElementPreview {
    TimelineEventTimestampView(
        event = event,
        eventSink = {},
    )
}

object TimelineEventTimestampViewDefaults {
    val spacing = 16.dp
}

/** A small open chevron (❮) just after the end of the content: the others have read up to this message. */
private fun Modifier.drawReadMark(color: Color): Modifier = drawWithContent {
    drawContent()
    // wide open: about 120 degrees
    val width = 2.5.dp.toPx()
    val height = 9.dp.toPx()
    val gap = 2.5.dp.toPx()
    val left = if (layoutDirection == LayoutDirection.Ltr) size.width + gap else -gap - width
    val top = (size.height - height) / 2
    // pointing back to the time
    val (from, to) = if (layoutDirection == LayoutDirection.Ltr) left + width to left else left to left + width
    val path = Path().apply {
        moveTo(from, top)
        lineTo(to, top + height / 2)
        lineTo(from, top + height)
    }
    drawPath(path, color = color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
