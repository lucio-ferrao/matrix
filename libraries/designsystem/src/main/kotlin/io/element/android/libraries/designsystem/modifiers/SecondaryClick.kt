/*
 * Copyright (c) 2026 Lúcio Ferrão.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.modifiers

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.IntSize

/**
 * MatriX: a right click (the secondary mouse button) runs [action], the context menu a long press opens with a finger,
 * for a mouse in Android's desktop mode. Clickables only react to the primary button, and the press is consumed here, so
 * a left click and a long press work as before.
 */
fun Modifier.onSecondaryClick(action: (() -> Unit)?): Modifier =
    if (action == null) this else this then SecondaryClickElement(action)

private data class SecondaryClickElement(val action: () -> Unit) : ModifierNodeElement<SecondaryClickNode>() {
    override fun create() = SecondaryClickNode(action)

    override fun update(node: SecondaryClickNode) {
        node.action = action
    }
}

private class SecondaryClickNode(var action: () -> Unit) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass == PointerEventPass.Initial &&
            pointerEvent.type == PointerEventType.Press &&
            pointerEvent.buttons.isSecondaryPressed
        ) {
            pointerEvent.changes.forEach { it.consume() }
            action()
        }
    }

    override fun onCancelPointerInput() = Unit
}
