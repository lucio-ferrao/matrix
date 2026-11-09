/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.aTimelineItemReactions
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

/**
 * A flow layout for reactions that will show a collapse/expand button when the layout wraps over a defined number of rows.
 * @param expandButton The expand button
 * @param modifier The modifier to apply to this layout
 * @param itemSpacing The horizontal spacing between items
 * @param rowSpacing The vertical spacing between rows
 * @param expanded Whether the layout should display in expanded or collapsed state
 * @param rowsBeforeCollapsible The number of rows before the collapse/expand button is shown
 * @param reactions The reaction buttons
 */
@Composable
fun TimelineItemReactionsLayout(
    expandButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 0.dp,
    rowSpacing: Dp = 0.dp,
    expanded: Boolean = false,
    rowsBeforeCollapsible: Int? = 2,
    reactions: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        // Given the placeables and returns a structure representing
        // how they should wrap on to multiple rows given the constraints max width.
        fun calculateRows(placeables: List<Placeable>): List<List<Placeable>> {
            val rows = mutableListOf<List<Placeable>>()
            var currentRow = mutableListOf<Placeable>()
            var rowX = 0

            placeables.forEach { placeable ->
                val horizontalSpacing = if (currentRow.isEmpty()) 0 else itemSpacing.toPx().toInt()
                // If the current view does not fit on this row bump to the next
                if (rowX + placeable.width > constraints.maxWidth) {
                    rows.add(currentRow)
                    currentRow = mutableListOf()
                    rowX = 0
                }
                rowX += horizontalSpacing + placeable.width
                currentRow.add(placeable)
            }
            // If there are items in the current row remember to append it to the returned value
            if (currentRow.size > 0) {
                rows.add(currentRow)
            }
            return rows
        }

        // Used to render the collapsed state, this takes the rows inputted and adds the extra button to the last row,
        // removing only as many trailing reactions as needed to make space for it.
        fun replaceTrailingItemsWithButtons(rowsIn: List<List<Placeable>>, expandButton: Placeable): List<List<Placeable>> {
            val rows = rowsIn.toMutableList()
            val lastRow = rows.last()
            val buttonsWidth = expandButton.width
            var rowX = 0
            lastRow.forEachIndexed { i, placeable ->
                val horizontalSpacing = if (i == 0) 0 else itemSpacing.toPx().toInt()
                rowX += placeable.width + horizontalSpacing
                if (rowX > constraints.maxWidth - (buttonsWidth + horizontalSpacing)) {
                    val lastRowWithButton = lastRow.take(i) + expandButton
                    rows[rows.size - 1] = lastRowWithButton
                    return rows
                }
            }
            val lastRowWithButton = lastRow + expandButton
            rows[rows.size - 1] = lastRowWithButton
            return rows
        }

        // Given a list of rows place them in the layout.
        fun layoutRows(rows: List<List<Placeable>>): MeasureResult {
            var width = 0
            var height = 0
            val placeables = rows.mapIndexed { i, row ->
                var rowX = 0
                var rowHeight = 0
                val verticalSpacing = if (i == 0) 0 else rowSpacing.toPx().toInt()
                val rowWithPoints = row.mapIndexed { j, placeable ->
                    val horizontalSpacing = if (j == 0) 0 else itemSpacing.toPx().toInt()
                    val point = IntOffset(rowX + horizontalSpacing, height + verticalSpacing)
                    rowX += placeable.width + horizontalSpacing
                    rowHeight = maxOf(rowHeight, placeable.height)
                    Pair(placeable, point)
                }
                height += rowHeight + verticalSpacing
                width = maxOf(width, rowX)
                rowWithPoints
            }.flatten()

            return layout(width = width, height = height) {
                placeables.forEach {
                    val (placeable, origin) = it
                    placeable.placeRelative(origin.x, origin.y)
                }
            }
        }

        var reactionsPlaceables = subcompose(0, reactions).map { it.measure(constraints) }
        if (reactionsPlaceables.isEmpty()) {
            return@SubcomposeLayout layoutRows(listOf())
        }
        var expandPlaceable = subcompose(1, expandButton).first().measure(constraints)
        // Enforce all reaction buttons have the same height
        val maxHeight = (reactionsPlaceables + listOf(expandPlaceable)).maxOf { it.height }
        val newConstrains = constraints.copy(minHeight = maxHeight)
        reactionsPlaceables = subcompose(2, reactions).map { it.measure(newConstrains) }
        expandPlaceable = subcompose(3, expandButton).first().measure(newConstrains)

        // Calculate the layout of the rows with the reactions
        val reactionRows = calculateRows(reactionsPlaceables)
        // If we have extended beyond the defined number of rows we are showing the expand/collapse ui
        if (rowsBeforeCollapsible?.let { reactionRows.size > it } == true) {
            if (expanded) {
                // Show all subviews with the collapse button at the end
                layoutRows(calculateRows(reactionsPlaceables + expandPlaceable))
            } else {
                // Truncate to `rowsBeforeCollapsible` number of rows and replace the reactions at the end of the last row with the button
                val collapsedRows = reactionRows.take(rowsBeforeCollapsible)
                layoutRows(replaceTrailingItemsWithButtons(collapsedRows, expandPlaceable))
            }
        } else {
            // Otherwise we are just showing all items without the expand button
            layoutRows(reactionRows)
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemReactionsLayoutPreview() = ElementPreview {
    TimelineItemReactionsLayout(
        expanded = false,
        expandButton = {
            MessagesReactionButton(
                content = MessagesReactionsButtonContent.Text(
                    text = stringResource(id = R.string.screen_room_timeline_less_reactions)
                ),
                onClick = {},
                onLongClick = {}
            )
        },
        reactions = {
            aTimelineItemReactions(count = 18).reactions.forEach {
                MessagesReactionButton(
                    content = MessagesReactionsButtonContent.Reaction(
                        it
                    ),
                    onClick = {},
                    onLongClick = {}
                )
            }
        }
    )
}
