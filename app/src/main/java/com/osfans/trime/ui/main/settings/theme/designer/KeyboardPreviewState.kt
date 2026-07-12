/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme.designer

data class KeyboardPreviewState(
    val orientation: Orientation = Orientation.BOTH,
    val asciiMode: Boolean = false,
    val composing: Boolean = false,
    val paging: Boolean = false,
    val hasMenu: Boolean = false,
) {
    enum class Orientation {
        BOTH,
        PORTRAIT,
        LANDSCAPE,
    }

    fun next(): KeyboardPreviewState = when {
        !hasCondition && orientation == Orientation.BOTH -> copy(orientation = Orientation.PORTRAIT)
        !hasCondition && orientation == Orientation.PORTRAIT -> copy(orientation = Orientation.LANDSCAPE)
        !hasCondition && orientation == Orientation.LANDSCAPE -> copy(orientation = Orientation.BOTH, asciiMode = true)
        asciiMode -> KeyboardPreviewState(composing = true)
        composing -> KeyboardPreviewState(paging = true)
        paging -> KeyboardPreviewState(hasMenu = true)
        else -> KeyboardPreviewState()
    }

    private val hasCondition: Boolean
        get() = asciiMode || composing || paging || hasMenu

    fun label(): String {
        val parts = mutableListOf(
            when (orientation) {
                Orientation.BOTH -> "Portrait + landscape"
                Orientation.PORTRAIT -> "Portrait"
                Orientation.LANDSCAPE -> "Landscape"
            },
        )
        if (asciiMode) parts += "ASCII"
        if (composing) parts += "Composing"
        if (paging) parts += "Paging"
        if (hasMenu) parts += "Menu"
        return parts.joinToString(" / ")
    }
}
