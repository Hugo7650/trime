/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme.designer

import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyBehavior

object KeyboardPreviewResolver {
    data class Action(
        val behavior: KeyBehavior,
        val value: String,
    )

    fun landscapeKeyboard(
        keyboard: TextKeyboard,
        keyboards: Map<String, TextKeyboard>,
    ): TextKeyboard = keyboards[keyboard.landscapeKeyboard] ?: keyboard

    fun landscapeSplitPercent(
        keyboard: TextKeyboard,
        preferredSplitPercent: Int,
    ): Int = listOf(keyboard.landscapeSplitPercent, preferredSplitPercent)
        .firstOrNull { it > 0 }
        ?: 0

    fun action(
        key: TextKeyboard.TextKey,
        state: KeyboardPreviewState,
    ): Action {
        val conditionalBehaviors = listOfNotNull(
            KeyBehavior.ASCII.takeIf { state.asciiMode },
            KeyBehavior.PAGING.takeIf { state.paging },
            KeyBehavior.HAS_MENU.takeIf { state.hasMenu },
            KeyBehavior.COMPOSING.takeIf { state.composing },
        )
        val behavior = conditionalBehaviors.firstOrNull { !key.behaviors[it].isNullOrEmpty() }
            ?: KeyBehavior.CLICK
        return Action(behavior, key.behaviors[behavior].orEmpty())
    }

    fun label(
        key: TextKeyboard.TextKey,
        action: Action,
        state: KeyboardPreviewState,
        presetKeys: Map<String, PresetKey>,
    ): String {
        if (
            action.behavior == KeyBehavior.CLICK &&
            key.label.isNotEmpty() &&
            KeyBehavior.ASCII !in key.behaviors &&
            !state.asciiMode
        ) {
            return key.label
        }
        val presetKey = presetKeys[action.value.removeSurrounding("{", "}")]
        return presetKey?.label?.takeIf { it.isNotEmpty() }
            ?: action.value
    }

    fun isFunctional(
        action: Action,
        presetKeys: Map<String, PresetKey>,
    ): Boolean = presetKeys[action.value.removeSurrounding("{", "}")]
        ?.let { it.functional || it.sticky }
        ?: false
}
